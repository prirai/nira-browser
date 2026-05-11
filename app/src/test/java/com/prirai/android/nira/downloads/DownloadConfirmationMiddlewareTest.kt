package com.prirai.android.nira.downloads

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.lib.state.Store
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for DownloadConfirmationMiddleware.
 *
 * When AddDownloadAction fires, this middleware persists to DownloadStorage
 * and dispatches UpdateDownloadAction instead of calling next(), which prevents
 * DownloadMiddleware from starting the download service until confirmation.
 * Non-download actions pass through normally.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadConfirmationMiddlewareTest {

    private lateinit var middleware: DownloadConfirmationMiddleware
    private val mockContext: Context = mockk(relaxed = true)
    private val mockStore: Store<BrowserState, BrowserAction> = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        middleware = DownloadConfirmationMiddleware(
            applicationContext = mockContext,
            coroutineContext = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `AddDownloadAction dispatches UpdateDownloadAction instead of calling next`() {
        val download = createDownloadState(isPrivate = false)
        var nextCalled = false
        val action = DownloadAction.AddDownloadAction(download)

        middleware.invoke(mockStore, { nextCalled = true }, action)

        assert(!nextCalled) { "next() was called — download would start immediately" }
        verify { mockStore.dispatch(any<DownloadAction.UpdateDownloadAction>()) }
    }

    @Test
    fun `AddDownloadAction for private download still intercepts next`() {
        val download = createDownloadState(isPrivate = true)
        var nextCalled = false
        val action = DownloadAction.AddDownloadAction(download)

        middleware.invoke(mockStore, { nextCalled = true }, action)

        assert(!nextCalled) { "next() was called for private download" }
        verify { mockStore.dispatch(any<DownloadAction.UpdateDownloadAction>()) }
    }

    @Test
    fun `non-download actions call next without side effects`() {
        var nextCalled = false
        middleware.invoke(mockStore, { nextCalled = true }, mockk(relaxed = true))
        assert(nextCalled) { "next() was not called for non-download action" }
        verify(inverse = true) { mockStore.dispatch(any<DownloadAction.UpdateDownloadAction>()) }
    }

    @Test
    fun `UpdateDownloadAction passes through to next`() {
        val download = createDownloadState(isPrivate = false)
        val action = DownloadAction.UpdateDownloadAction(download)
        var nextCalled = false

        middleware.invoke(mockStore, { nextCalled = true }, action)
        assert(nextCalled) { "next() was not called for UpdateDownloadAction" }
    }

    @Test
    fun `RemoveDownloadAction passes through to next`() {
        val action = DownloadAction.RemoveDownloadAction("download-id-123")
        var nextCalled = false

        middleware.invoke(mockStore, { nextCalled = true }, action)
        assert(nextCalled) { "next() was not called for RemoveDownloadAction" }
    }

    private fun createDownloadState(isPrivate: Boolean = false): DownloadState {
        return mockk<DownloadState>(relaxed = true).apply {
            every { this@apply.private } returns isPrivate
            every { this@apply.url } returns "https://example.com/file.zip"
            every { this@apply.fileName } returns "file.zip"
        }
    }
}
