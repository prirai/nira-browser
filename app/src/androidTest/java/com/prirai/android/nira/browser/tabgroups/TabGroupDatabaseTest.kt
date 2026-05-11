package com.prirai.android.nira.browser.tabgroups

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for TabGroupDatabase and TabGroupDao.
 *
 * Tests run on a real Android device/emulator using an in-memory Room database.
 * This validates actual SQL queries, Room entity mapping, and Flow emissions.
 *
 * IMPORTANT: These are Android instrumentation tests — run with:
 *   ./gradlew app:connectedAndroidDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class TabGroupDatabaseTest {

    // System under test
    private lateinit var database: TabGroupDatabase
    private lateinit var dao: TabGroupDao

    // Application context provided by AndroidX Test
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Create an in-memory Room database for testing.
        // In-memory databases don't persist to disk and are fully isolated between tests.
        database = Room.inMemoryDatabaseBuilder(
            context,
            TabGroupDatabase::class.java
        ).build()

        dao = database.tabGroupDao()
    }

    @After
    fun tearDown() {
        // Close the database to free memory
        database.close()
    }

    // -------------------------------------------------------------------------
    // Basic CRUD — TabGroup entity
    // -------------------------------------------------------------------------

    @Test
    fun `insert and read a TabGroup`() = runBlocking {
        val group = TabGroup(
            id = "test-group-1",
            name = "Test Group",
            color = "red",
            createdAt = 1000L,
            isActive = true,
            contextId = null,
            isCollapsed = false
        )

        dao.insertGroup(group)

        val loaded = dao.getGroupById("test-group-1")
        assertNotNull(loaded)
        assertEquals("Test Group", loaded!!.name)
        assertEquals("red", loaded.color)
        assertTrue(loaded.isActive)
    }

    @Test
    fun `insert and read multiple groups`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Group 1", color = "red"))
        dao.insertGroup(TabGroup(id = "g-2", name = "Group 2", color = "blue"))
        dao.insertGroup(TabGroup(id = "g-3", name = "Group 3", color = "green"))

        val groups = dao.getAllActiveGroups().first()
        assertEquals(3, groups.size)
    }

    @Test
    fun `update a TabGroup`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Original", color = "red"))

        // Update the name and color
        val loaded = dao.getGroupById("g-1")!!
        val updated = loaded.copy(name = "Updated", color = "blue")
        dao.updateGroup(updated)

        val reloaded = dao.getGroupById("g-1")
        assertEquals("Updated", reloaded!!.name)
        assertEquals("blue", reloaded.color)
    }

    @Test
    fun `delete a TabGroup`() = runBlocking {
        val group = TabGroup(id = "g-1", name = "Delete Me", color = "red")
        dao.insertGroup(group)
        assertNotNull(dao.getGroupById("g-1"))

        dao.deleteGroup(group)
        assertNull(dao.getGroupById("g-1"))
    }

    @Test
    fun `mark a group as inactive`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Active", color = "blue"))

        dao.markGroupAsInactive("g-1")

        val loaded = dao.getGroupById("g-1")
        assertNotNull(loaded)
        assertTrue(!loaded!!.isActive) // isActive should be false after marking inactive
    }

    @Test
    fun `inactive groups are excluded from getAllActiveGroups`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Active", color = "blue"))
        dao.insertGroup(TabGroup(id = "g-2", name = "Inactive", color = "red"))
        dao.markGroupAsInactive("g-2")

        val activeGroups = dao.getAllActiveGroups().first()
        assertEquals(1, activeGroups.size)
        assertEquals("g-1", activeGroups[0].id)
    }

    // -------------------------------------------------------------------------
    // TabGroupMember CRUD
    // -------------------------------------------------------------------------

    @Test
    fun `add tab to group and read members`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Group 1", color = "blue"))
        dao.insertTabGroupMember(TabGroupMember(tabId = "tab-a", groupId = "g-1", position = 0))
        dao.insertTabGroupMember(TabGroupMember(tabId = "tab-b", groupId = "g-1", position = 1))

        val tabIds = dao.getTabIdsInGroup("g-1")
        assertEquals(2, tabIds.size)
        assertEquals("tab-a", tabIds[0])
        assertEquals("tab-b", tabIds[1])
    }

    @Test
    fun `remove tab from group`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Group 1", color = "blue"))
        dao.insertTabGroupMember(TabGroupMember(tabId = "tab-a", groupId = "g-1", position = 0))
        dao.insertTabGroupMember(TabGroupMember(tabId = "tab-b", groupId = "g-1", position = 1))

        dao.removeTabFromGroup("tab-a", "g-1")

        val tabIds = dao.getTabIdsInGroup("g-1")
        assertEquals(1, tabIds.size)
        assertEquals("tab-b", tabIds[0])
    }

    @Test
    fun `remove tab from all groups`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Group 1", color = "blue"))
        dao.insertGroup(TabGroup(id = "g-2", name = "Group 2", color = "red"))
        dao.insertTabGroupMember(TabGroupMember(tabId = "tab-a", groupId = "g-1", position = 0))
        dao.insertTabGroupMember(TabGroupMember(tabId = "tab-a", groupId = "g-2", position = 0))

        dao.removeTabFromAllGroups("tab-a")

        // Tab should be removed from both groups
        assertNull(dao.getGroupIdForTab("tab-a"))
    }

    @Test
    fun `remove all tabs from group`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Group 1", color = "blue"))
        dao.insertTabGroupMember(TabGroupMember(tabId = "tab-a", groupId = "g-1", position = 0))
        dao.insertTabGroupMember(TabGroupMember(tabId = "tab-b", groupId = "g-1", position = 1))

        dao.removeAllTabsFromGroup("g-1")

        val tabIds = dao.getTabIdsInGroup("g-1")
        assertTrue(tabIds.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Query operations
    // -------------------------------------------------------------------------

    @Test
    fun `getGroupIdForTab returns correct group`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Group 1", color = "blue"))
        dao.insertTabGroupMember(TabGroupMember(tabId = "tab-a", groupId = "g-1", position = 0))

        val groupId = dao.getGroupIdForTab("tab-a")
        assertEquals("g-1", groupId)
    }

    @Test
    fun `getGroupIdForTab returns null for ungrouped tab`() = runBlocking {
        assertNull(dao.getGroupIdForTab("non-existent-tab"))
    }

    @Test
    fun `getGroupByName finds active groups only`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "My Group", color = "blue"))
        dao.insertGroup(TabGroup(id = "g-2", name = "My Group", color = "red"))
        dao.markGroupAsInactive("g-2")

        val found = dao.getGroupByName("My Group")
        assertNotNull(found)
        assertEquals("g-1", found!!.id)
    }

    @Test
    fun `getTabCountInGroup returns correct count`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Group", color = "blue"))
        dao.insertTabGroupMember(TabGroupMember(tabId = "t1", groupId = "g-1", position = 0))
        dao.insertTabGroupMember(TabGroupMember(tabId = "t2", groupId = "g-1", position = 1))
        dao.insertTabGroupMember(TabGroupMember(tabId = "t3", groupId = "g-1", position = 2))

        val count = dao.getTabCountInGroup("g-1")
        assertEquals(3, count)
    }

    // -------------------------------------------------------------------------
    // getAllGroupsWithTabs — JOIN query
    // -------------------------------------------------------------------------

    @Test
    fun `getAllGroupsWithTabs returns groups with tab IDs`() = runBlocking {
        dao.insertGroup(TabGroup(id = "g-1", name = "Group 1", color = "blue"))
        dao.insertGroup(TabGroup(id = "g-2", name = "Group 2", color = "red"))
        dao.insertTabGroupMember(TabGroupMember(tabId = "t1", groupId = "g-1", position = 0))
        dao.insertTabGroupMember(TabGroupMember(tabId = "t2", groupId = "g-1", position = 1))

        val groupsWithTabs = dao.getAllGroupsWithTabs().first()
        // Two groups, but only g-1 has members
        assertEquals(2, groupsWithTabs.size)

        val group1 = groupsWithTabs.find { it.id == "g-1" }
        assertNotNull(group1)
        // tabIds are comma-separated in the GROUP_CONCAT result
        assertNotNull(group1!!.tabIds)
    }
}
