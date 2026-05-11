package com.prirai.android.nira.middleware

import android.graphics.Bitmap
import com.prirai.android.nira.utils.FaviconCache
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.lib.state.Store
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for FaviconMiddleware — saves favicons to cache when they load.
 *
 * FaviconMiddleware intercepts ContentAction.UpdateIconAction actions dispatched
 * through the Mozilla Components Store. When a tab loads a new favicon,
 * the middleware saves it to the FaviconCache asynchronously.
 *
 * These tests use MockK to mock the Store, FaviconCache, and browser state,
 * and a StandardTestDispatcher for deterministic coroutine execution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FaviconMiddlewareTest {

    private lateinit var middleware: FaviconMiddleware
    private val mockFaviconCache: FaviconCache = mockk(relaxed = true)
    private val mockStore: Store<BrowserState, BrowserAction> = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        middleware = FaviconMiddleware(
            faviconCache = mockFaviconCache,
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `UpdateIconAction with icon saves to cache`() = runTest(testDispatcher) {
        val tabId = "tab-1"
        val url = "https://example.com"
        val icon: Bitmap = mockk(relaxed = true)
        val mockTab = mockk<TabSessionState>(relaxed = true)
        every { mockTab.id } returns tabId
        every { mockTab.content.url } returns url
        every { mockStore.state.tabs } returns listOf(mockTab)

        val action = ContentAction.UpdateIconAction(sessionId = tabId, pageUrl = url, icon = icon)
        middleware.invoke(mockStore, {}, action)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockFaviconCache.saveFavicon(url, icon) }
    }

    @Test
    fun `UpdateIconAction without icon does not save`() = runTest(testDispatcher) {
        val tabId = "tab-1"
        val mockTab = mockk<TabSessionState>(relaxed = true)
        every { mockTab.id } returns tabId
        every { mockTab.content.url } returns "https://example.com"
        every { mockStore.state.tabs } returns listOf(mockTab)

        val icon: Bitmap = mockk(relaxed = true)
        val action = ContentAction.UpdateIconAction(sessionId = tabId, pageUrl = "https://example.com", icon = icon)
        middleware.invoke(mockStore, {}, action)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `non-content actions pass through without cache interaction`() = runTest(testDispatcher) {
        val action = mockk<BrowserAction>(relaxed = true)
        middleware.invoke(mockStore, {}, action)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(inverse = true) { mockFaviconCache.saveFavicon(any(), any()) }
    }

    @Test
    fun `UpdateIconAction for tab not in store does not crash`() = runTest(testDispatcher) {
        every { mockStore.state.tabs } returns emptyList()
        val icon: Bitmap = mockk(relaxed = true)
        val action = ContentAction.UpdateIconAction(
            sessionId = "non-existent-tab",
            pageUrl = "https://example.com",
            icon = icon
        )
        middleware.invoke(mockStore, {}, action)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `next callback is always invoked`() {
        val tabId = "tab-1"
        val mockTab = mockk<TabSessionState>(relaxed = true)
        every { mockTab.id } returns tabId
        every { mockTab.content.url } returns "https://example.com"
        every { mockStore.state.tabs } returns listOf(mockTab)

        var nextCalled = false
        val icon: Bitmap = mockk(relaxed = true)
        val action = ContentAction.UpdateIconAction(sessionId = tabId, pageUrl = "https://example.com", icon = icon)
        middleware.invoke(mockStore, { nextCalled = true }, action)

        assert(nextCalled) { "next() was not called — middleware chain would be broken" }
    }

    @Test
    fun `next callback is invoked for non-icon actions`() {
        var nextCalled = false
        middleware.invoke(mockStore, { nextCalled = true }, mockk(relaxed = true))
        assert(nextCalled) { "next() was not called for non-icon action" }
    }
}
