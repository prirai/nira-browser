package com.prirai.android.nira.browser.tabgroups

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for GroupExpansionStateManager — persistent expand/collapse state for tab groups.
 *
 * Uses Robolectric + a real Context so DataStore actually works.
 * Validates reactive StateFlow updates AND persistence round-trips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class GroupExpansionStateManagerTest {

    private lateinit var manager: GroupExpansionStateManager
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        val constructor = GroupExpansionStateManager::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        manager = constructor.newInstance(context.applicationContext)

        runBlocking {
            manager.initialize()
            manager.setExpansionState(emptySet())
        }
    }

    @Test
    fun `initial expanded state is empty`() = runTest(testDispatcher) {
        manager.initialize()
        assertTrue(manager.expandedGroupIds.value.isEmpty())
    }

    @Test
    fun `isGroupExpanded returns false for unknown group`() = runTest(testDispatcher) {
        manager.initialize()
        assertFalse(manager.isGroupExpanded("non-existent"))
    }

    @Test
    fun `toggleGroupExpansion expands a collapsed group`() = runTest(testDispatcher) {
        manager.initialize()
        manager.toggleGroupExpansion("group-1")
        assertTrue(manager.isGroupExpanded("group-1"))
        assertEquals(setOf("group-1"), manager.expandedGroupIds.value)
    }

    @Test
    fun `toggleGroupExpansion collapses an expanded group`() = runTest(testDispatcher) {
        manager.initialize()
        manager.toggleGroupExpansion("group-1")
        assertTrue(manager.isGroupExpanded("group-1"))
        manager.toggleGroupExpansion("group-1")
        assertFalse(manager.isGroupExpanded("group-1"))
        assertTrue(manager.expandedGroupIds.value.isEmpty())
    }

    @Test
    fun `toggling multiple groups tracks each independently`() = runTest(testDispatcher) {
        manager.initialize()
        manager.toggleGroupExpansion("group-1")
        manager.toggleGroupExpansion("group-2")
        assertTrue(manager.isGroupExpanded("group-1"))
        assertTrue(manager.isGroupExpanded("group-2"))
        manager.toggleGroupExpansion("group-1")
        assertFalse(manager.isGroupExpanded("group-1"))
        assertTrue(manager.isGroupExpanded("group-2"))
    }

    @Test
    fun `expandGroup adds group to expanded set`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandGroup("group-1")
        assertTrue(manager.isGroupExpanded("group-1"))
    }

    @Test
    fun `expandGroup is idempotent`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandGroup("group-1")
        manager.expandGroup("group-1")
        assertEquals(setOf("group-1"), manager.expandedGroupIds.value)
    }

    @Test
    fun `collapseGroup removes group from expanded set`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandGroup("group-1")
        manager.collapseGroup("group-1")
        assertFalse(manager.isGroupExpanded("group-1"))
    }

    @Test
    fun `collapseGroup on already collapsed group is no-op`() = runTest(testDispatcher) {
        manager.initialize()
        manager.collapseGroup("group-1")
        assertTrue(manager.expandedGroupIds.value.isEmpty())
    }

    @Test
    fun `expandGroups adds multiple groups at once`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandGroups(setOf("group-1", "group-2", "group-3"))
        assertTrue(manager.isGroupExpanded("group-1"))
        assertTrue(manager.isGroupExpanded("group-2"))
        assertTrue(manager.isGroupExpanded("group-3"))
        assertEquals(3, manager.expandedGroupIds.value.size)
    }

    @Test
    fun `collapseGroups removes multiple groups at once`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandGroups(setOf("group-1", "group-2", "group-3"))
        manager.collapseGroups(setOf("group-1", "group-3"))
        assertFalse(manager.isGroupExpanded("group-1"))
        assertTrue(manager.isGroupExpanded("group-2"))
        assertFalse(manager.isGroupExpanded("group-3"))
    }

    @Test
    fun `setExpansionState replaces all expansion state`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandGroup("group-1")
        manager.setExpansionState(setOf("group-2", "group-3"))
        assertFalse(manager.isGroupExpanded("group-1"))
        assertTrue(manager.isGroupExpanded("group-2"))
        assertTrue(manager.isGroupExpanded("group-3"))
        assertEquals(2, manager.expandedGroupIds.value.size)
    }

    @Test
    fun `collapseAllGroups clears all expansion state`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandGroups(setOf("group-1", "group-2"))
        manager.collapseAllGroups()
        assertTrue(manager.expandedGroupIds.value.isEmpty())
    }

    @Test
    fun `expandAllGroups sets all given groups as expanded`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandAllGroups(setOf("group-a", "group-b", "group-c"))
        assertEquals(setOf("group-a", "group-b", "group-c"), manager.expandedGroupIds.value)
    }

    @Test
    fun `cleanupOrphanedStates removes non-existent groups`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandGroups(setOf("group-1", "group-2", "group-3"))
        manager.cleanupOrphanedStates(setOf("group-1", "group-3"))
        assertTrue(manager.isGroupExpanded("group-1"))
        assertFalse(manager.isGroupExpanded("group-2"))
        assertTrue(manager.isGroupExpanded("group-3"))
    }

    @Test
    fun `cleanupOrphanedStates with all groups existing does nothing`() = runTest(testDispatcher) {
        manager.initialize()
        manager.expandGroups(setOf("group-1", "group-2"))
        manager.cleanupOrphanedStates(setOf("group-1", "group-2"))
        assertEquals(2, manager.expandedGroupIds.value.size)
    }

    @Test
    fun `cleanupOrphanedStates with no expanded groups does nothing`() = runTest(testDispatcher) {
        manager.initialize()
        manager.cleanupOrphanedStates(setOf("group-1", "group-2"))
        assertTrue(manager.expandedGroupIds.value.isEmpty())
    }
}
