package com.prirai.android.nira.browser.tabgroups

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TabGroup entity and related data classes.
 *
 * These tests verify that the Room entity models, domain models, and helper
 * classes used by the tab grouping system are correct. They do NOT test Room
 * persistence (that requires instrumentation tests with a real database) —
 * they focus on the data transformation logic and default values.
 */
class TabGroupEntityTest {

    // -------------------------------------------------------------------------
    // TabGroup entity
    // -------------------------------------------------------------------------

    @Test
    fun `TabGroup has a random UUID as default id`() {
        val group1 = TabGroup()
        val group2 = TabGroup()

        // Each new group gets a unique UUID
        assertNotNull(group1.id)
        assertNotNull(group2.id)
        assertTrue(group1.id.isNotEmpty())
        assertTrue(group2.id.isNotEmpty())
        assertTrue(group1.id != group2.id)
    }

    @Test
    fun `TabGroup has empty name as default`() {
        val group = TabGroup()
        assertEquals("", group.name)
    }

    @Test
    fun `TabGroup has blue as default color`() {
        val group = TabGroup()
        assertEquals("blue", group.color)
    }

    @Test
    fun `TabGroup is active by default`() {
        val group = TabGroup()
        assertTrue(group.isActive)
    }

    @Test
    fun `TabGroup has null contextId by default`() {
        val group = TabGroup()
        assertNull(group.contextId)
    }

    @Test
    fun `TabGroup is not collapsed by default`() {
        val group = TabGroup()
        assertFalse(group.isCollapsed)
    }

    @Test
    fun `TabGroup constructor sets all fields correctly`() {
        val group = TabGroup(
            id = "test-id",
            name = "Test Group",
            color = "red",
            createdAt = 1000L,
            isActive = false,
            contextId = "profile_abc",
            isCollapsed = true
        )

        assertEquals("test-id", group.id)
        assertEquals("Test Group", group.name)
        assertEquals("red", group.color)
        assertEquals(1000L, group.createdAt)
        assertFalse(group.isActive)
        assertEquals("profile_abc", group.contextId)
        assertTrue(group.isCollapsed)
    }

    // -------------------------------------------------------------------------
    // TabGroupMember entity
    // -------------------------------------------------------------------------

    @Test
    fun `TabGroupMember has position 0 as default`() {
        val member = TabGroupMember(tabId = "tab-1", groupId = "group-1")
        assertEquals("tab-1", member.tabId)
        assertEquals("group-1", member.groupId)
        assertEquals(0, member.position)
    }

    @Test
    fun `TabGroupMember constructor sets all fields`() {
        val member = TabGroupMember(tabId = "tab-1", groupId = "group-1", position = 5)
        assertEquals("tab-1", member.tabId)
        assertEquals("group-1", member.groupId)
        assertEquals(5, member.position)
    }

    @Test
    fun `TabGroupMember data class equality works`() {
        val member1 = TabGroupMember(tabId = "tab-1", groupId = "group-1", position = 5)
        val member2 = TabGroupMember(tabId = "tab-1", groupId = "group-1", position = 5)
        assertEquals(member1, member2)
    }

    @Test
    fun `TabGroupMember data class inequality works`() {
        val member1 = TabGroupMember(tabId = "tab-1", groupId = "group-1")
        val member2 = TabGroupMember(tabId = "tab-2", groupId = "group-1")
        assertTrue(member1 != member2)
    }

    // -------------------------------------------------------------------------
    // TabGroupWithTabs domain model
    // -------------------------------------------------------------------------

    @Test
    fun `TabGroupWithTabs has empty tabIds as default`() {
        val group = TabGroup()
        val groupWithTabs = TabGroupWithTabs(group)

        assertEquals(group, groupWithTabs.group)
        assertTrue(groupWithTabs.tabIds.isEmpty())
        assertEquals(0, groupWithTabs.tabCount)
        assertTrue(groupWithTabs.isEmpty)
        assertFalse(groupWithTabs.isNotEmpty)
    }

    @Test
    fun `TabGroupWithTabs with tabs has correct count`() {
        val group = TabGroup()
        val tabIds = listOf("tab-1", "tab-2", "tab-3")
        val groupWithTabs = TabGroupWithTabs(group, tabIds)

        assertEquals(3, groupWithTabs.tabCount)
        assertEquals(tabIds, groupWithTabs.tabIds)
        assertFalse(groupWithTabs.isEmpty)
        assertTrue(groupWithTabs.isNotEmpty)
    }

    @Test
    fun `TabGroupWithTabs preserves group reference`() {
        val group = TabGroup(id = "g-1", name = "My Group")
        val groupWithTabs = TabGroupWithTabs(group)

        // group property should return the exact same object
        assertTrue(group === groupWithTabs.group)
    }

    // -------------------------------------------------------------------------
    // TabGroupWithTabIds Room query result
    // -------------------------------------------------------------------------

    @Test
    fun `TabGroupWithTabIds toTabGroupWithTabs converts correctly`() {
        val queryResult = TabGroupWithTabIds(
            id = "g-1",
            name = "My Group",
            color = "red",
            createdAt = 1000L,
            isActive = true,
            contextId = null,
            isCollapsed = false,
            tabIds = "tab-1,tab-2,tab-3"
        )

        val result = queryResult.toTabGroupWithTabs()

        assertEquals("g-1", result.group.id)
        assertEquals("My Group", result.group.name)
        assertEquals(listOf("tab-1", "tab-2", "tab-3"), result.tabIds)
    }

    @Test
    fun `TabGroupWithTabIds with null tabIds returns empty list`() {
        val queryResult = TabGroupWithTabIds(
            id = "g-1", name = "", color = "blue", createdAt = 0L,
            isActive = true, contextId = null, isCollapsed = false, tabIds = null
        )

        val result = queryResult.toTabGroupWithTabs()
        assertTrue(result.tabIds.isEmpty())
    }

    @Test
    fun `TabGroupWithTabIds with empty tabIds returns empty list`() {
        val queryResult = TabGroupWithTabIds(
            id = "g-1", name = "", color = "blue", createdAt = 0L,
            isActive = true, contextId = null, isCollapsed = false, tabIds = ""
        )

        val result = queryResult.toTabGroupWithTabs()
        assertTrue(result.tabIds.isEmpty())
    }

    @Test
    fun `TabGroupWithTabIds with whitespace tabIds filters blanks`() {
        val queryResult = TabGroupWithTabIds(
            id = "g-1", name = "", color = "blue", createdAt = 0L,
            isActive = true, contextId = null, isCollapsed = false, tabIds = "tab-1,,tab-2,"
        )

        val result = queryResult.toTabGroupWithTabs()
        assertEquals(listOf("tab-1", "tab-2"), result.tabIds)
    }

    // -------------------------------------------------------------------------
    // TabGroupNameGenerator
    // -------------------------------------------------------------------------

    @Test
    fun `TabGroupNameGenerator returns empty string`() {
        val generator = TabGroupNameGenerator()
        assertEquals("", generator.generateName())
    }

    @Test
    fun `TabGroupNameGenerator releaseName does not throw`() {
        val generator = TabGroupNameGenerator()
        // Should not throw or crash
        generator.releaseName("")
        generator.releaseName("test-name")
    }
}
