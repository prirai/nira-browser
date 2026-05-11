package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import com.prirai.android.nira.browser.tabgroups.GroupEvent
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.lib.state.Store
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for TabViewModel — bridges Store, UnifiedTabGroupManager, and TabOrderManager.
 *
 * Uses MockK to mock dependencies and a StandardTestDispatcher for
 * deterministic coroutine execution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TabViewModelTest {

    private lateinit var viewModel: TabViewModel
    private val mockContext: Context = mockk(relaxed = true)
    private val mockGroupManager: UnifiedTabGroupManager = mockk(relaxed = true)
    private val mockStore: Store<BrowserState, BrowserAction> = mockk(relaxed = true)
    private val groupEventsFlow = MutableSharedFlow<GroupEvent>(replay = 0)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockGroupManager.groupEvents } returns groupEventsFlow
        every { mockStore.state } returns mockk<BrowserState>(relaxed = true) {
            every { tabs } returns emptyList()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial tabs StateFlow is empty`() {
        viewModel = TabViewModel(mockContext, mockGroupManager)
        assertTrue(viewModel.tabs.value.isEmpty())
    }

    @Test
    fun `initial groups StateFlow is empty`() {
        viewModel = TabViewModel(mockContext, mockGroupManager)
        assertTrue(viewModel.groups.value.isEmpty())
    }

    @Test
    fun `initial expandedGroups is empty`() {
        viewModel = TabViewModel(mockContext, mockGroupManager)
        assertTrue(viewModel.expandedGroups.value.isEmpty())
    }

    @Test
    fun `initial selectedTabId is null`() {
        viewModel = TabViewModel(mockContext, mockGroupManager)
        assertNull(viewModel.selectedTabId.value)
    }

    @Test
    fun `initial currentProfileId is null`() {
        viewModel = TabViewModel(mockContext, mockGroupManager)
        assertNull(viewModel.currentProfileId.value)
    }

    @Test
    fun `currentOrder is initially null`() {
        viewModel = TabViewModel(mockContext, mockGroupManager)
        assertNull(viewModel.currentOrder.value)
    }

    @Test
    fun `onTabRemove callback is null initially`() {
        viewModel = TabViewModel(mockContext, mockGroupManager)
        assertNull(viewModel.onTabRemove)
    }

    @Test
    fun `onTabRestore callback is null initially`() {
        viewModel = TabViewModel(mockContext, mockGroupManager)
        assertNull(viewModel.onTabRestore)
    }

    @Test
    fun `PendingTabDeletion stores all fields`() {
        val tab = mockk<TabSessionState>(relaxed = true)
        every { tab.id } returns "tab-1"
        val deletion = PendingTabDeletion(
            tabId = "tab-1",
            tab = tab,
            groupId = "group-1",
            position = 3
        )

        assertEquals("tab-1", deletion.tabId)
        assertEquals(tab, deletion.tab)
        assertEquals("group-1", deletion.groupId)
        assertEquals(3, deletion.position)
    }
}
