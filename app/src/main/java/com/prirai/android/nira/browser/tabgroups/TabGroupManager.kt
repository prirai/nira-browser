package com.prirai.android.nira.browser.tabgroups

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.prirai.android.nira.ext.components

/**
 * Manages tab groups functionality based on Mozilla's architecture.
 * Handles creation, management, and auto-grouping of tabs.
 */
class TabGroupManager(private val context: Context) {

    private val database = TabGroupDatabase.getInstance(context)
    private val dao = database.tabGroupDao()
    private val nameGenerator = TabGroupNameGenerator()

    // Current active group state
    private val _currentGroup = MutableStateFlow<TabGroupWithTabs?>(null)
    val currentGroup: StateFlow<TabGroupWithTabs?> = _currentGroup.asStateFlow()

    // All groups
    val allGroups: Flow<List<TabGroupWithTabs>> = dao.getAllGroupsWithTabs()
        .mapToTabGroupWithTabs()

    /**
     * Creates a new tab group with auto-generated name.
     */
    suspend fun createGroup(name: String? = null, color: String? = null): TabGroupWithTabs {
        val groupName = name ?: nameGenerator.generateName()
        val groupColor = color ?: getRandomColor()
        val group = TabGroup(
            name = groupName,
            color = groupColor,
            createdAt = System.currentTimeMillis()
        )

        dao.insertGroup(group)
        val newGroup = TabGroupWithTabs(group, emptyList())
        _currentGroup.value = newGroup
        return newGroup
    }

    /**
     * Adds a tab to the current group, creating one if necessary.
     */
    suspend fun addTabToCurrentGroup(tabId: String): TabGroupWithTabs {
        var group = _currentGroup.value

        // Create new group if none exists
        if (group == null) {
            group = createGroup()
        }

        // Remove tab from any existing group first
        removeTabFromGroups(tabId)

        // Add to current group
        val member = TabGroupMember(
            tabId = tabId,
            groupId = group.group.id,
            position = group.tabIds.size
        )
        dao.insertTabGroupMember(member)

        // Update current group state
        val updatedGroup = group.copy(tabIds = group.tabIds + tabId)
        _currentGroup.value = updatedGroup

        return updatedGroup
    }

    /**
     * Auto-groups tabs based on domain similarity.
     * Called when a new tab is created or URL changes.
     * NOTE: This is now primarily used for same-domain navigation, not cross-domain linking.
     */
    // Keep track of tabs we've already processed to avoid repeated calls
    private val processedTabs = mutableSetOf<String>()

    suspend fun autoGroupTab(tabId: String, url: String) {
        // Prevent repeated processing of the same tab
        val tabUrlKey = "$tabId-$url"
        if (processedTabs.contains(tabUrlKey)) {
            return
        }
        processedTabs.add(tabUrlKey)

        if (!shouldAutoGroup(url)) {
            return
        }

        // For same domain navigation, only add to current group if it exists and has same domain
        val currentGroup = _currentGroup.value
        if (currentGroup != null && currentGroup.tabCount > 0) {
            // Check if the new URL has the same domain as tabs in the current group
            val currentGroupDomain = getCurrentGroupDomain(currentGroup)
            val newDomain = extractDomain(url)

            if (currentGroupDomain != null && currentGroupDomain == newDomain) {
                // Same domain as current group, add to it
                addTabToGroup(tabId, currentGroup.group.id)
            }
            // If different domain, don't auto-group unless handled by handleNewTabFromLink
        }

        // Don't create new groups automatically - only through explicit cross-domain linking
    }

    /**
     * Adds a tab to a specific group.
     * Prevents mixing private and normal tabs in the same group.
     */
    suspend fun addTabToGroup(tabId: String, groupId: String) {
        // Check if tab privacy mode matches the group
        val tab = context.components.store.state.tabs.find { it.id == tabId }
        val isPrivateTab = tab?.content?.private ?: false
        
        // Get existing tabs in the group to check their privacy mode
        val existingTabIds = dao.getTabIdsInGroup(groupId)
        if (existingTabIds.isNotEmpty()) {
            val firstExistingTab = context.components.store.state.tabs.find { it.id == existingTabIds.first() }
            val isPrivateGroup = firstExistingTab?.content?.private ?: false
            
            // Don't allow mixing private and normal tabs
            if (isPrivateTab != isPrivateGroup) {
                return
            }
        }
        
        removeTabFromGroups(tabId)

        val tabCount = dao.getTabCountInGroup(groupId)
        val member = TabGroupMember(
            tabId = tabId,
            groupId = groupId,
            position = tabCount
        )
        dao.insertTabGroupMember(member)

        // Update current group if this is the active group
        if (_currentGroup.value?.group?.id == groupId) {
            val updatedGroup = _currentGroup.value?.copy(
                tabIds = _currentGroup.value!!.tabIds + tabId
            )
            _currentGroup.value = updatedGroup
        }
    }

    /**
     * Removes a tab from all groups.
     */
    suspend fun removeTabFromGroups(tabId: String) {
        dao.removeTabFromAllGroups(tabId)

        // Update current group if affected
        _currentGroup.value?.let { current ->
            if (tabId in current.tabIds) {
                val updatedGroup = current.copy(
                    tabIds = current.tabIds - tabId
                )
                _currentGroup.value = updatedGroup
            }
        }
    }

    /**
     * Removes a tab from a specific group.
     */
    suspend fun removeTabFromGroup(tabId: String, groupId: String) {
        dao.removeTabFromGroup(tabId, groupId)

        // Update current group if affected
        if (_currentGroup.value?.group?.id == groupId) {
            val updatedGroup = _currentGroup.value?.copy(
                tabIds = _currentGroup.value!!.tabIds - tabId
            )
            _currentGroup.value = updatedGroup
        }
        
        // If group becomes empty, delete it
        val remainingTabs = dao.getTabIdsInGroup(groupId)
        if (remainingTabs.isEmpty()) {
            deleteGroup(groupId)
        }
    }

    /**
     * Get all active groups.
     */
    suspend fun getAllGroups(): List<TabGroup> {
        return dao.getAllActiveGroups().first()
    }
    
    /**
     * Gets the group ID for a specific tab.
     */
    suspend fun getGroupIdForTab(tabId: String): String? {
        return dao.getGroupIdForTab(tabId)
    }

    /**
     * Get tab IDs in a specific group.
     */
    suspend fun getTabIdsInGroup(groupId: String): List<String> {
        return dao.getTabIdsInGroup(groupId)
    }

    /**
     * Switches to a specific group.
     */
    suspend fun switchToGroup(groupId: String) {
        val group = dao.getGroupById(groupId)
        if (group != null) {
            val tabIds = dao.getTabIdsInGroup(groupId)
            _currentGroup.value = TabGroupWithTabs(group, tabIds)
        }
    }

    /**
     * Renames a group.
     */
    suspend fun renameGroup(groupId: String, newName: String) {
        val group = dao.getGroupById(groupId)
        if (group != null) {
            val updatedGroup = group.copy(name = newName)
            dao.updateGroup(updatedGroup)

            // Update current group if it's the one being renamed
            if (_currentGroup.value?.group?.id == groupId) {
                _currentGroup.value = _currentGroup.value?.copy(group = updatedGroup)
            }
        }
    }
    
    /**
     * Changes the color of a group.
     */
    suspend fun changeGroupColor(groupId: String, newColor: String) {
        val group = dao.getGroupById(groupId)
        if (group != null) {
            val updatedGroup = group.copy(color = newColor)
            dao.updateGroup(updatedGroup)

            // Update current group if it's the one being updated
            if (_currentGroup.value?.group?.id == groupId) {
                _currentGroup.value = _currentGroup.value?.copy(group = updatedGroup)
            }
        }
    }

    /**
     * Deletes a group and removes all tab associations.
     */
    suspend fun deleteGroup(groupId: String) {
        dao.removeAllTabsFromGroup(groupId)
        dao.markGroupAsInactive(groupId)

        // Clear current group if it's the one being deleted
        if (_currentGroup.value?.group?.id == groupId) {
            _currentGroup.value = null
        }
    }

    /**
     * Gets the group ID for a specific tab.
     */
    suspend fun getGroupForTab(tabId: String): String? {
        return dao.getGroupIdForTab(tabId)
    }

    /**
     * Gets the group name for a specific tab.
     */
    suspend fun getGroupNameForTab(tabId: String): String? {
        val groupId = dao.getGroupIdForTab(tabId)
        return if (groupId != null) {
            dao.getGroupById(groupId)?.name
        } else {
            null
        }
    }

    /**
     * Handles new tab creation by checking if it should be added to the source tab's group.
     * This is called when a link from one domain opens a tab with a different domain.
     */
    suspend fun handleNewTabFromLink(
        newTabId: String,
        newTabUrl: String,
        sourceTabId: String?,
        sourceTabUrl: String?
    ) {
        // Only proceed if we have source information
        if (sourceTabId == null || sourceTabUrl == null) {
            // Fallback to normal auto-grouping
            autoGroupTab(newTabId, newTabUrl)
            return
        }
        
        // Check that both tabs are in the same privacy mode
        val sourceTab = context.components.store.state.tabs.find { it.id == sourceTabId }
        val newTab = context.components.store.state.tabs.find { it.id == newTabId }
        val sourceIsPrivate = sourceTab?.content?.private ?: false
        val newIsPrivate = newTab?.content?.private ?: false
        
        // Don't group tabs from different privacy modes
        if (sourceIsPrivate != newIsPrivate) {
            autoGroupTab(newTabId, newTabUrl)
            return
        }

        // Check if domains are different
        val sourceDomain = extractDomain(sourceTabUrl)
        val newDomain = extractDomain(newTabUrl)

        if (sourceDomain != newDomain && shouldAutoGroup(newTabUrl)) {
            // Get the source tab's group
            val sourceGroupId = dao.getGroupIdForTab(sourceTabId)

            if (sourceGroupId != null) {
                // Add new tab to the same group as source
                addTabToGroup(newTabId, sourceGroupId)

                // Switch to the source group to maintain context
                switchToGroup(sourceGroupId)
            } else {
                // Source tab is not in a group, create a new group for both
                val newGroup = createGroup()
                addTabToGroup(sourceTabId, newGroup.group.id)
                addTabToGroup(newTabId, newGroup.group.id)
                switchToGroup(newGroup.group.id)
            }
        } else {
            // Same domain or other case - use normal auto-grouping logic
            autoGroupTab(newTabId, newTabUrl)
        }
    }

    // Helper methods

    /**
     * Gets the domain of the first tab in the current group for comparison.
     */
    private fun getCurrentGroupDomain(group: TabGroupWithTabs): String? {
        if (group.tabIds.isEmpty()) return null

        val firstTabId = group.tabIds.first()
        val tab = context.components.store.state.tabs.find { it.id == firstTabId }
        return tab?.let { extractDomain(it.content.url) }
    }

    private suspend fun findGroupByDomain(domain: String): TabGroupWithTabs? {
        // This is a simplified implementation
        // In a real implementation, you'd want to check for similar domain groups
        return dao.getGroupByName(domain)?.let { group ->
            val tabIds = dao.getTabIdsInGroup(group.id)
            TabGroupWithTabs(group, tabIds)
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            Uri.parse(url).host?.replace("www.", "") ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun shouldAutoGroup(url: String): Boolean {
        // Don't auto-group internal pages
        return !url.startsWith("about:") &&
                !url.startsWith("chrome:") &&
                !url.startsWith("file:") &&
                url.isNotBlank()
    }

    private fun getRandomColor(): String {
        val colors = listOf("blue", "green", "purple", "orange", "red", "pink", "cyan", "yellow")
        return colors.random()
    }

    /**
     * Updates the current tab context to refresh tab group bar.
     */
    suspend fun updateCurrentTabContext(tabId: String) {
        val groupId = dao.getGroupIdForTab(tabId)
        if (groupId != null) {
            val group = dao.getGroupById(groupId)
            val tabIds = dao.getTabIdsInGroup(groupId)
            if (group != null) {
                val groupWithTabs = TabGroupWithTabs(group, tabIds)
                _currentGroup.value = groupWithTabs
            }
        } else {
            _currentGroup.value = null
        }
    }

    /**
     * Clean up processed tabs cache to prevent memory leaks.
     */
    fun cleanupProcessedTabs() {
        processedTabs.clear()
    }
}

// Extension function to convert Flow of database results to domain models
private fun Flow<List<TabGroupWithTabIds>>.mapToTabGroupWithTabs(): Flow<List<TabGroupWithTabs>> {
    return this.map { list ->
        list.map { it.toTabGroupWithTabs() }
    }
}
