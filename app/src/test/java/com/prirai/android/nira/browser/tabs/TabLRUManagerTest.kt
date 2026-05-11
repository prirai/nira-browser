package com.prirai.android.nira.browser.tabs

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for TabLRUManager — the least-recently-used tab navigation system.
 *
 * Maintains a queue of tab IDs ordered by last access time.
 * getTabAtLRUOffset wraps around at boundaries for circular navigation.
 * Swipe navigation is tracked separately to prevent ping-pong oscillation.
 */
class TabLRUManagerTest {

    private lateinit var manager: TabLRUManager
    private val mockContext: Context = mockk(relaxed = true)
    private val mockPrefs: SharedPreferences = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { mockPrefs.getString(any(), any()) } returns ""
        every { mockContext.getSharedPreferences("tab_lru", Context.MODE_PRIVATE) } returns mockPrefs

        val constructor = TabLRUManager::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        manager = constructor.newInstance(mockContext)
    }

    @Test
    fun `onTabSelected adds tab to front of LRU queue`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        assertEquals("tab-2", manager.getMostRecentTab())
    }

    @Test
    fun `onTabSelected with existing tab moves it to front`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        manager.onTabSelected("tab-3")
        manager.onTabSelected("tab-1")
        assertEquals("tab-1", manager.getMostRecentTab())
    }

    @Test
    fun `onTabSelected maintains LRU order for multiple tabs`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        manager.onTabSelected("tab-3")
        assertEquals("tab-3", manager.getMostRecentTab())
        // queue: [tab-3, tab-2, tab-1]; -1 from index 0 wraps to end (circular navigation)
        assertEquals("tab-1", manager.getTabAtLRUOffset("tab-3", -1))
    }

    @Test
    fun `onTabSelected with swipe navigation skips LRU update`() {
        manager.onTabSelected("tab-1")
        manager.markAsSwipeNavigation("tab-2")
        manager.onTabSelected("tab-2")
        assertEquals("tab-1", manager.getMostRecentTab())
    }

    @Test
    fun `swipe navigation followed by normal selection works correctly`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        manager.onTabSelected("tab-3")
        manager.markAsSwipeNavigation("tab-1")
        manager.onTabSelected("tab-1")
        assertEquals("tab-3", manager.getMostRecentTab())
        manager.onTabSelected("tab-2")
        assertEquals("tab-2", manager.getMostRecentTab())
    }

    @Test
    fun `onTabClosed removes tab from LRU queue`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        manager.onTabSelected("tab-3")
        manager.onTabClosed("tab-2")
        assertEquals("tab-3", manager.getMostRecentTab())
        manager.onTabSelected("tab-2")
        assertEquals("tab-2", manager.getMostRecentTab())
    }

    @Test
    fun `onTabClosed with non-existent tab does nothing`() {
        manager.onTabSelected("tab-1")
        manager.onTabClosed("non-existent")
        assertEquals("tab-1", manager.getMostRecentTab())
    }

    @Test
    fun `synchronizeWithTabs keeps accessed tabs in LRU order`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        manager.onTabSelected("tab-3")
        // queue: [tab-3, tab-2, tab-1]
        manager.synchronizeWithTabs(listOf("tab-1", "tab-2", "tab-3"))
        // rebuild: retainAll keeps all 3, no unvisited → [tab-3, tab-2, tab-1]
        assertEquals("tab-3", manager.getMostRecentTab())
        // offset -1 from index 0 wraps to last = "tab-1" (circular)
        assertEquals("tab-1", manager.getTabAtLRUOffset("tab-3", -1))
        // offset -2 from index 0 wraps to last = "tab-1" (circular, same as -1 with 3 items)
        assertEquals("tab-1", manager.getTabAtLRUOffset("tab-3", -2))
    }

    @Test
    fun `synchronizeWithTabs removes orphaned tabs`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        manager.onTabSelected("tab-3")
        // queue: [tab-3, tab-2, tab-1]
        manager.synchronizeWithTabs(listOf("tab-1", "tab-3"))
        // retainAll removes "tab-2" → [tab-3, tab-1]; no unvisited
        assertEquals("tab-3", manager.getMostRecentTab())
        // With only 2 elements, offset -1 from tab-3 wraps to tab-1.
        // Post-synchronize: tab-2 was removed (orphaned).
        assertEquals("tab-1", manager.getTabAtLRUOffset("tab-3", -1))
    }

    @Test
    fun `synchronizeWithTabs appends unvisited tabs in order`() {
        manager.onTabSelected("tab-2")
        // queue: [tab-2]
        manager.synchronizeWithTabs(listOf("tab-1", "tab-2", "tab-3"))
        // retainAll keeps tab-2 → [tab-2]; unvisited = [tab-1, tab-3]; append → [tab-2, tab-1, tab-3]
        assertEquals("tab-2", manager.getMostRecentTab())
        // offset -1 from index 0 wraps to last = "tab-3"
        assertEquals("tab-3", manager.getTabAtLRUOffset("tab-2", -1))
    }

    @Test
    fun `synchronizeWithTabs with all unvisited tabs orders by input`() {
        manager.synchronizeWithTabs(listOf("tab-a", "tab-b", "tab-c"))
        assertEquals("tab-a", manager.getMostRecentTab())
    }

    @Test
    fun `synchronizeWithTabs with empty current tabs clears queue`() {
        manager.onTabSelected("tab-1")
        manager.synchronizeWithTabs(emptyList())
        assertNull(manager.getMostRecentTab())
    }

    @Test
    fun `getTabAtLRUOffset returns correct tab at positive offset`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        manager.onTabSelected("tab-3")
        // queue: [tab-3, tab-2, tab-1] → indexes: 0=tab-3, 1=tab-2, 2=tab-1
        // non-wrapping: offset +1 from tab-2 (index 1) → index 2 → tab-1
        assertEquals("tab-1", manager.getTabAtLRUOffset("tab-2", 1))
        // wrapping: offset +1 from tab-1 (index 2) → index 3 >= size → wraps to first = tab-3
        assertEquals("tab-3", manager.getTabAtLRUOffset("tab-1", 1))
    }

    @Test
    fun `getTabAtLRUOffset returns correct tab at negative offset`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        manager.onTabSelected("tab-3")
        // queue: [tab-3, tab-2, tab-1] → indexes: 0=tab-3, 1=tab-2, 2=tab-1
        // non-wrapping: offset -1 from tab-2 (index 1) → index 0 → tab-3
        assertEquals("tab-3", manager.getTabAtLRUOffset("tab-2", -1))
        // wrapping: offset -1 from tab-3 (index 0) → index -1 < 0 → wraps to last = tab-1
        assertEquals("tab-1", manager.getTabAtLRUOffset("tab-3", -1))
    }

    @Test
    fun `getTabAtLRUOffset wraps around at negative boundary`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        assertEquals("tab-2", manager.getTabAtLRUOffset("tab-1", -1))
    }

    @Test
    fun `getTabAtLRUOffset wraps around at positive boundary`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        assertEquals("tab-1", manager.getTabAtLRUOffset("tab-2", 1))
    }

    @Test
    fun `getTabAtLRUOffset returns null for unknown tab`() {
        manager.onTabSelected("tab-1")
        assertNull(manager.getTabAtLRUOffset("non-existent", 0))
    }

    @Test
    fun `getTabAtLRUOffset with zero offset returns same tab`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        assertEquals("tab-2", manager.getTabAtLRUOffset("tab-2", 0))
    }

    @Test
    fun `getMostRecentTab returns null for empty queue`() {
        assertNull(manager.getMostRecentTab())
    }

    @Test
    fun `getMostRecentTab returns most recently selected tab`() {
        manager.onTabSelected("tab-1")
        manager.onTabSelected("tab-2")
        manager.onTabSelected("tab-3")
        assertEquals("tab-3", manager.getMostRecentTab())
    }
}
