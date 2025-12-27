package com.prirai.android.nira.browser.tabgroups

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.core.net.toUri

/**
 * Unified Tab Group Manager - Single source of truth for all tab grouping operations.
 * Synchronizes between tab bar and tab sheet automatically.
 * 
 * Best Practices:
 * - Single Responsibility: Manages groups and their persistence
 * - Observer Pattern: UI components observe changes via StateFlow
 * - Immutable Data: Groups are immutable, changes create new instances
 * - Thread Safety: All DB operations on IO dispatcher
 * - Lifecycle Aware: Cleanup methods for memory management
 */
class UnifiedTabGroupManager private constructor(private val context: Context) {

    private val database = TabGroupDatabase.getInstance(context)
    private val dao = database.tabGroupDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    // In-memory cache of groups for fast access
    private val groupsCache = mutableMapOf<String, TabGroupData>()
    
    // Map tab ID to group ID for quick lookup
    private val tabToGroupMap = mutableMapOf<String, String>()

    // State flows for reactive UI updates
    private val _groupsState = MutableStateFlow<List<TabGroupData>>(emptyList())
    val groupsState: StateFlow<List<TabGroupData>> = _groupsState.asStateFlow()

    // Event flow for fine-grained updates (e.g., single group changed)
    private val _groupEvents = MutableSharedFlow<GroupEvent>(replay = 0)
    val groupEvents: SharedFlow<GroupEvent> = _groupEvents.asSharedFlow()

    companion object {
        private val AVAILABLE_COLORS = listOf(
            0xFFE57373.toInt(), // Red
            0xFF81C784.toInt(), // Green
            0xFF64B5F6.toInt(), // Blue
            0xFFFFB74D.toInt(), // Orange
            0xFFBA68C8.toInt(), // Purple
            0xFFF06292.toInt(), // Pink
            0xFF4DD0E1.toInt(), // Cyan
            0xFFFFF176.toInt()  // Yellow
        )

        @Volatile
        private var instance: UnifiedTabGroupManager? = null

        fun getInstance(context: Context): UnifiedTabGroupManager {
            return instance ?: synchronized(this) {
                instance ?: UnifiedTabGroupManager(context.applicationContext).also { 
                    instance = it
                    it.initialize()
                }
            }
        }
    }

    private fun initialize() {
        scope.launch {
            loadGroupsFromDatabase()
        }
    }

    private suspend fun loadGroupsFromDatabase() = withContext(Dispatchers.IO) {
        try {
            val groups = dao.getAllActiveGroups().first()
            val groupDataList = mutableListOf<TabGroupData>()
            
            for (dbGroup in groups) {
                val tabIds = dao.getTabIdsInGroup(dbGroup.id)
                val groupData = TabGroupData(
                    id = dbGroup.id,
                    name = dbGroup.name,
                    color = parseColor(dbGroup.color),
                    tabIds = tabIds,
                    createdAt = dbGroup.createdAt,
                    contextId = null  // Will be determined from tabs
                )
                groupDataList.add(groupData)
            }

            groupsCache.clear()
            tabToGroupMap.clear()

            groupDataList.forEach { groupData ->
                groupsCache[groupData.id] = groupData
                groupData.tabIds.forEach { tabId ->
                    tabToGroupMap[tabId] = groupData.id
                }
            }

            _groupsState.value = groupDataList
        } catch (e: Exception) {
        }
    }

    /**
     * Creates a new group with the specified tabs
     */
    suspend fun createGroup(
        tabIds: List<String>,
        name: String? = null,
        color: Int? = null,
        profileId: String? = null,
        contextId: String? = null
    ): TabGroupData = withContext(Dispatchers.IO) {
        if (tabIds.isEmpty()) {
            throw IllegalArgumentException("Cannot create group with no tabs")
        }

        // Remove tabs from existing groups first
        tabIds.forEach { tabId ->
            removeTabFromGroup(tabId, notifyChange = false)
        }

        val groupId = UUID.randomUUID().toString()
        val groupName = name ?: ""
        val groupColor = color ?: AVAILABLE_COLORS.random()
        val now = System.currentTimeMillis()

        // Create database entity
        val group = TabGroup(
            id = groupId,
            name = groupName,
            color = colorToString(groupColor),
            createdAt = now,
            isActive = true
        )

        dao.insertGroup(group)

        // Add tab associations
        tabIds.forEachIndexed { index, tabId ->
            val member = TabGroupMember(
                tabId = tabId,
                groupId = groupId,
                position = index
            )
            dao.insertTabGroupMember(member)
        }

        val groupData = TabGroupData(
            id = groupId,
            name = groupName,
            color = groupColor,
            tabIds = tabIds,
            createdAt = now,
            contextId = contextId
        )

        // Update cache
        groupsCache[groupId] = groupData
        tabIds.forEach { tabId ->
            tabToGroupMap[tabId] = groupId
        }

        emitStateUpdate()
        _groupEvents.emit(GroupEvent.GroupCreated(groupData))

        groupData
    }

    /**
     * Adds a tab to an existing group
     */
    suspend fun addTabToGroup(
        tabId: String,
        groupId: String,
        position: Int? = null,
        notifyChange: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val group = groupsCache[groupId] ?: return@withContext false

        // Check if tab is already in this group
        if (group.tabIds.contains(tabId)) {
            return@withContext true
        }

        // Remove from other groups first
        removeTabFromGroup(tabId, notifyChange = false)

        val insertPosition = position ?: group.tabIds.size
        val newTabIds = group.tabIds.toMutableList().apply {
            add(insertPosition.coerceIn(0, size), tabId)
        }

        // Update database
        val member = TabGroupMember(
            tabId = tabId,
            groupId = groupId,
            position = insertPosition
        )
        dao.insertTabGroupMember(member)

        // Update cache
        val updatedGroup = group.copy(tabIds = newTabIds)
        groupsCache[groupId] = updatedGroup
        tabToGroupMap[tabId] = groupId

        if (notifyChange) {
            emitStateUpdate()
            _groupEvents.emit(GroupEvent.TabAddedToGroup(tabId, groupId))
        }

        true
    }

    /**
     * Removes a tab from its group
     */
    suspend fun removeTabFromGroup(
        tabId: String,
        notifyChange: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val groupId = tabToGroupMap[tabId] ?: return@withContext false
        val group = groupsCache[groupId] ?: return@withContext false

        // Remove from database
        dao.removeTabFromGroup(tabId, groupId)

        // Update cache
        val newTabIds = group.tabIds - tabId
        tabToGroupMap.remove(tabId)

        if (newTabIds.isEmpty()) {
            // Delete empty group
            deleteGroup(groupId, notifyChange = false)
        } else {
            val updatedGroup = group.copy(tabIds = newTabIds)
            groupsCache[groupId] = updatedGroup
        }

        if (notifyChange) {
            emitStateUpdate()
            _groupEvents.emit(GroupEvent.TabRemovedFromGroup(tabId, groupId))
        }

        true
    }

    /**
     * Renames a group
     */
    suspend fun renameGroup(groupId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val group = groupsCache[groupId] ?: return@withContext false
        val dbGroup = dao.getGroupById(groupId) ?: return@withContext false

        val updatedDbGroup = dbGroup.copy(name = newName)
        dao.updateGroup(updatedDbGroup)

        val updatedGroup = group.copy(name = newName)
        groupsCache[groupId] = updatedGroup

        emitStateUpdate()
        _groupEvents.emit(GroupEvent.GroupRenamed(groupId, newName))

        true
    }

    /**
     * Changes group color
     */
    suspend fun changeGroupColor(groupId: String, newColor: Int): Boolean = withContext(Dispatchers.IO) {
        val group = groupsCache[groupId] ?: return@withContext false
        val dbGroup = dao.getGroupById(groupId) ?: return@withContext false

        val updatedDbGroup = dbGroup.copy(color = colorToString(newColor))
        dao.updateGroup(updatedDbGroup)

        val updatedGroup = group.copy(color = newColor)
        groupsCache[groupId] = updatedGroup

        emitStateUpdate()
        _groupEvents.emit(GroupEvent.GroupColorChanged(groupId, newColor))

        true
    }

    /**
     * Updates a group with new properties
     */
    suspend fun updateGroup(
        groupId: String,
        name: String? = null,
        color: Int? = null,
        tabIds: List<String>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val group = groupsCache[groupId] ?: return@withContext false
        val dbGroup = dao.getGroupById(groupId) ?: return@withContext false

        val updatedName = name ?: group.name
        val updatedColor = color ?: group.color
        val updatedTabIds = tabIds ?: group.tabIds

        // Update database
        val updatedDbGroup = dbGroup.copy(
            name = updatedName,
            color = colorToString(updatedColor)
        )
        dao.updateGroup(updatedDbGroup)

        // Update tab associations if changed
        if (tabIds != null) {
            dao.removeAllTabsFromGroup(groupId)
            tabIds.forEachIndexed { index, tabId ->
                val member = TabGroupMember(
                    tabId = tabId,
                    groupId = groupId,
                    position = index
                )
                dao.insertTabGroupMember(member)
            }
        }

        // Update cache
        val updatedGroup = group.copy(
            name = updatedName,
            color = updatedColor,
            tabIds = updatedTabIds
        )
        groupsCache[groupId] = updatedGroup

        // Update tab to group map
        if (tabIds != null) {
            group.tabIds.forEach { tabToGroupMap.remove(it) }
            updatedTabIds.forEach { tabToGroupMap[it] = groupId }
        }

        emitStateUpdate()
        _groupEvents.emit(GroupEvent.GroupUpdated(groupId))

        true
    }

    /**
     * Deletes a group (ungroups all tabs)
     */
    suspend fun deleteGroup(groupId: String, notifyChange: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val group = groupsCache[groupId] ?: return@withContext false

        // Remove all tab associations
        dao.removeAllTabsFromGroup(groupId)
        dao.markGroupAsInactive(groupId)

        // Update cache
        group.tabIds.forEach { tabId ->
            tabToGroupMap.remove(tabId)
        }
        groupsCache.remove(groupId)

        if (notifyChange) {
            emitStateUpdate()
            _groupEvents.emit(GroupEvent.GroupDeleted(groupId))
        }

        true
    }

    /**
     * Gets the group containing the specified tab
     */
    fun getGroupForTab(tabId: String): TabGroupData? {
        val groupId = tabToGroupMap[tabId] ?: return null
        return groupsCache[groupId]
    }

    /**
     * Gets a group by ID
     */
    fun getGroup(groupId: String): TabGroupData? {
        return groupsCache[groupId]
    }

    /**
     * Gets all groups
     */
    fun getAllGroups(): List<TabGroupData> {
        return groupsCache.values.sortedBy { it.createdAt }
    }

    /**
     * Checks if a tab is in any group
     */
    fun isTabGrouped(tabId: String): Boolean {
        return tabToGroupMap.containsKey(tabId)
    }

    /**
     * Moves a tab from one group to another
     */
    suspend fun moveTabBetweenGroups(
        tabId: String,
        fromGroupId: String,
        toGroupId: String,
        position: Int? = null
    ): Boolean {
        if (fromGroupId == toGroupId) return false

        removeTabFromGroup(tabId, notifyChange = false)
        val result = addTabToGroup(tabId, toGroupId, position)
        
        if (result) {
            emitStateUpdate()
            _groupEvents.emit(GroupEvent.TabMovedBetweenGroups(tabId, fromGroupId, toGroupId))
        }
        
        return result
    }

    /**
     * Merges two groups together
     */
    suspend fun mergeGroups(sourceGroupId: String, targetGroupId: String): Boolean {
        if (sourceGroupId == targetGroupId) return false

        val sourceGroup = groupsCache[sourceGroupId] ?: return false
        
        withContext(Dispatchers.IO) {
            // Add all tabs from source to target without individual notifications
            sourceGroup.tabIds.forEach { tabId ->
                val member = TabGroupMember(
                    tabId = tabId,
                    groupId = targetGroupId,
                    position = groupsCache[targetGroupId]?.tabIds?.size ?: 0
                )
                dao.insertTabGroupMember(member)
                tabToGroupMap[tabId] = targetGroupId
            }

            // Delete source group from database
            dao.removeAllTabsFromGroup(sourceGroupId)
            dao.markGroupAsInactive(sourceGroupId)
            groupsCache.remove(sourceGroupId)

            // Update target group cache
            val targetGroup = groupsCache[targetGroupId]
            if (targetGroup != null) {
                val newTabIds = targetGroup.tabIds + sourceGroup.tabIds
                groupsCache[targetGroupId] = targetGroup.copy(tabIds = newTabIds)
            }

            emitStateUpdate()
            _groupEvents.emit(GroupEvent.GroupsMerged(sourceGroupId, targetGroupId))

        }
        return true
    }

    /**
     * Handles tab closure - removes from group
     */
    suspend fun onTabClosed(tabId: String) {
        removeTabFromGroup(tabId)
    }

    /**
     * Clears all groups
     */
    suspend fun clearAllGroups() = withContext(Dispatchers.IO) {
        val groupIds = groupsCache.keys.toList()
        groupIds.forEach { groupId ->
            deleteGroup(groupId, notifyChange = false)
        }
        emitStateUpdate()
        _groupEvents.emit(GroupEvent.AllGroupsCleared)
    }

    /**
     * Handles new tab creation from a link in another tab.
     *
     * This method automatically groups tabs opened from links to maintain browsing context.
     * It works for both same-domain and cross-domain links, creating or extending groups
     * to keep related tabs together.
     *
     * Grouping behavior:
     * - If source tab is already in a group → adds new tab to that group
     * - If source tab is not in a group → creates new group for both tabs
     * - Skips system URLs (about:, chrome:) which should not be grouped
     *
     * This is typically called by TabGroupMiddleware when it detects a tab was
     * opened from a link in another tab (via context menu, middle-click, etc.)
     *
     * Thread safety:
     * - Runs on IO dispatcher via withContext
     * - Safe to call from any thread
     *
     * @param newTabId ID of the newly created tab
     * @param newTabUrl URL being loaded in the new tab
     * @param sourceTabId ID of the tab the link was opened from (null if unknown)
     * @param sourceTabUrl URL of the source tab (null if unknown)
     */
    suspend fun handleNewTabFromLink(
        newTabId: String,
        newTabUrl: String,
        sourceTabId: String?,
        sourceTabUrl: String?
    ) = withContext(Dispatchers.IO) {
        // Only proceed if we have source information
        if (sourceTabId == null || sourceTabUrl == null) {
            return@withContext
        }

        // Skip system URLs
        if (newTabUrl.startsWith("about:") || newTabUrl.startsWith("chrome:")) {
            return@withContext
        }

        // Get the source tab's group
        val sourceGroupId = tabToGroupMap[sourceTabId]

        if (sourceGroupId != null) {
            // Add new tab to the same group as source
            addTabToGroup(newTabId, sourceGroupId)
        } else {
            // Source tab is not in a group, create a new group for both
            createGroup(tabIds = listOf(sourceTabId, newTabId))
        }
    }

    /**
     * Extract domain from URL for comparison
     */
    private fun extractDomain(url: String): String {
        return try {
            url.toUri().host?.replace("www.", "") ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private suspend fun emitStateUpdate() {
        _groupsState.value = groupsCache.values.sortedBy { it.createdAt }
    }

    private fun parseColor(colorString: String): Int {
        return com.prirai.android.nira.theme.ColorConstants.TabGroups.parseColor(colorString)
    }

    private fun colorToString(color: Int): String {
        return com.prirai.android.nira.theme.ColorConstants.TabGroups.getColorName(color)
    }

    /**
     * Cleanup method - call when no longer needed
     */
    fun cleanup() {
        groupsCache.clear()
        tabToGroupMap.clear()
    }
}

/**
 * Immutable data class representing a tab group
 */
data class TabGroupData(
    val id: String,
    val name: String,
    val color: Int,
    val tabIds: List<String>,
    val createdAt: Long,
    val isCollapsed: Boolean = false,
    val contextId: String? = null
) {
    val tabCount: Int get() = tabIds.size
    val isEmpty: Boolean get() = tabIds.isEmpty()
    val isNotEmpty: Boolean get() = tabIds.isNotEmpty()
}

/**
 * Events emitted when groups change
 */
sealed class GroupEvent {
    data class GroupCreated(val group: TabGroupData) : GroupEvent()
    data class GroupDeleted(val groupId: String) : GroupEvent()
    data class GroupRenamed(val groupId: String, val newName: String) : GroupEvent()
    data class GroupColorChanged(val groupId: String, val newColor: Int) : GroupEvent()
    data class GroupUpdated(val groupId: String) : GroupEvent()
    data class TabAddedToGroup(val tabId: String, val groupId: String) : GroupEvent()
    data class TabRemovedFromGroup(val tabId: String, val groupId: String) : GroupEvent()
    data class TabMovedBetweenGroups(val tabId: String, val fromGroupId: String, val toGroupId: String) : GroupEvent()
    data class GroupsMerged(val sourceGroupId: String, val targetGroupId: String) : GroupEvent()
    object AllGroupsCleared : GroupEvent()
}
