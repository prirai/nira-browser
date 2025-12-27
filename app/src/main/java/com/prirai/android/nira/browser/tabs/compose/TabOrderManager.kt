package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.orderDataStore: DataStore<Preferences> by preferencesDataStore(name = "tab_order")

/**
 * Manages tab ordering and grouping across all views.
 * Single source of truth for tab positions.
 */
class TabOrderManager(
    private val context: Context,
    private val groupManager: UnifiedTabGroupManager
) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _currentOrder = MutableStateFlow<UnifiedTabOrder?>(null)
    val currentOrder: StateFlow<UnifiedTabOrder?> = _currentOrder.asStateFlow()
    
    /**
     * Load order for a specific profile
     */
    suspend fun loadOrder(profileId: String): UnifiedTabOrder {
        val key = stringPreferencesKey("order_$profileId")
        val order = context.orderDataStore.data
            .map { prefs ->
                prefs[key]?.let { json.decodeFromString<UnifiedTabOrder>(it) }
            }
            .first()
            ?: UnifiedTabOrder(profileId, emptyList())
        
        _currentOrder.value = order
        return order
    }
    
    /**
     * Save current order
     */
    suspend fun saveOrder(order: UnifiedTabOrder) {
        val key = stringPreferencesKey("order_${order.profileId}")
        context.orderDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(order)
        }
        _currentOrder.value = order.copy(lastModified = System.currentTimeMillis())
    }
    
    // === REORDERING OPERATIONS ===
    
    /**
     * Reorder an item in the primary order
     */
    suspend fun reorderItem(fromIndex: Int, toIndex: Int) {
        val current = _currentOrder.value ?: return
        if (fromIndex == toIndex) return
        
        val newOrder = current.primaryOrder.toMutableList()
        val item = newOrder.removeAt(fromIndex)
        newOrder.add(toIndex, item)
        saveOrder(current.copy(primaryOrder = newOrder))
        syncToGroupManager()
    }
    
    /**
     * Reorder a tab within a group
     */
    suspend fun reorderTabInGroup(groupId: String, fromIndex: Int, toIndex: Int) {
        val current = _currentOrder.value ?: return
        if (fromIndex == toIndex) return
        
        val newOrder = current.primaryOrder.map { item ->
            if (item is UnifiedTabOrder.OrderItem.TabGroup && item.groupId == groupId) {
                val newTabIds = item.tabIds.toMutableList()
                val tabId = newTabIds.removeAt(fromIndex)
                newTabIds.add(toIndex, tabId)
                item.copy(tabIds = newTabIds)
            } else {
                item
            }
        }
        saveOrder(current.copy(primaryOrder = newOrder))
        syncToGroupManager()
    }
    
    // === GROUPING OPERATIONS ===
    
    /**
     * Create a new group from multiple tabs
     */
    suspend fun createGroup(tabIds: List<String>, groupName: String, color: Int) {
        val current = _currentOrder.value ?: return
        if (tabIds.isEmpty()) return
        
        val groupId = "group_${System.currentTimeMillis()}"
        
        // Find first tab position
        val firstTabIndex = current.primaryOrder.indexOfFirst { item ->
            item is UnifiedTabOrder.OrderItem.SingleTab && item.tabId == tabIds.first()
        }
        
        // Remove tabs from current positions
        val remainingOrder = current.primaryOrder.filterNot { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId in tabIds
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    // Remove tabs from other groups
                    val remaining = item.tabIds.filterNot { it in tabIds }
                    if (remaining.isEmpty()) return@filterNot true // Remove empty group
                    false
                }
            }
        }.map { item ->
            // Update existing groups to remove moved tabs
            if (item is UnifiedTabOrder.OrderItem.TabGroup) {
                val remaining = item.tabIds.filterNot { it in tabIds }
                if (remaining.size == 1) {
                    // Ungroup if only one tab left
                    UnifiedTabOrder.OrderItem.SingleTab(remaining.first())
                } else {
                    item.copy(tabIds = remaining)
                }
            } else {
                item
            }
        }.toMutableList()
        
        // Create new group
        val newGroup = UnifiedTabOrder.OrderItem.TabGroup(
            groupId = groupId,
            groupName = groupName,
            color = color,
            isExpanded = true,
            tabIds = tabIds
        )
        
        // Insert group at first tab position
        val insertPos = firstTabIndex.coerceAtLeast(0).coerceAtMost(remainingOrder.size)
        remainingOrder.add(insertPos, newGroup)
        
        saveOrder(current.copy(primaryOrder = remainingOrder))
        syncToGroupManager()
    }
    
    /**
     * Add a tab to an existing group
     */
    suspend fun addTabToGroup(tabId: String, groupId: String, positionInGroup: Int? = null) {
        val current = _currentOrder.value ?: return
        
        // Remove tab from current position
        val newOrder = current.primaryOrder.mapNotNull { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> 
                    if (item.tabId == tabId) null else item
                    
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    if (item.groupId == groupId) {
                        // Add to target group
                        val newTabIds = item.tabIds.toMutableList()
                        val insertIndex = positionInGroup?.coerceIn(0, newTabIds.size) ?: newTabIds.size
                        newTabIds.add(insertIndex, tabId)
                        item.copy(tabIds = newTabIds)
                    } else {
                        // Remove from other groups
                        val filtered = item.tabIds.filter { it != tabId }
                        when {
                            filtered.isEmpty() -> null // Remove empty group
                            filtered.size == 1 -> UnifiedTabOrder.OrderItem.SingleTab(filtered.first()) // Ungroup
                            else -> item.copy(tabIds = filtered)
                        }
                    }
                }
            }
        }
        
        saveOrder(current.copy(primaryOrder = newOrder))
        syncToGroupManager()
    }
    
    /**
     * Remove a tab from its group
     */
    suspend fun removeTabFromGroup(tabId: String, newPosition: Int? = null) {
        val current = _currentOrder.value ?: return
        
        val newOrder = current.primaryOrder.flatMap { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    if (tabId in item.tabIds) {
                        val remainingTabs = item.tabIds.filter { it != tabId }
                        when (remainingTabs.size) {
                            0 -> emptyList() // Remove empty group
                            1 -> listOf(UnifiedTabOrder.OrderItem.SingleTab(remainingTabs.first())) // Ungroup
                            else -> listOf(item.copy(tabIds = remainingTabs))
                        }
                    } else {
                        listOf(item)
                    }
                }
                else -> listOf(item)
            }
        }.toMutableList()
        
        // Add ungrouped tab at specified position
        val ungroupedTab = UnifiedTabOrder.OrderItem.SingleTab(tabId)
        val insertPos = newPosition?.coerceIn(0, newOrder.size) ?: newOrder.size
        newOrder.add(insertPos, ungroupedTab)
        
        saveOrder(current.copy(primaryOrder = newOrder))
        syncToGroupManager()
    }
    
    /**
     * Remove a tab completely from the order
     */
    suspend fun removeTab(tabId: String) {
        val current = _currentOrder.value ?: return
        
        val newOrder = current.primaryOrder.mapNotNull { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> {
                    if (item.tabId == tabId) null else item
                }
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    val remainingTabs = item.tabIds.filter { it != tabId }
                    when (remainingTabs.size) {
                        0 -> null // Remove empty group
                        1 -> UnifiedTabOrder.OrderItem.SingleTab(remainingTabs.first()) // Ungroup last tab
                        else -> item.copy(tabIds = remainingTabs) // Keep group with remaining tabs
                    }
                }
            }
        }
        
        saveOrder(current.copy(primaryOrder = newOrder))
    }
    
    /**
     * Toggle group expansion state
     */
    suspend fun toggleGroupExpansion(groupId: String) {
        // Just update local order for UI state
        val current = _currentOrder.value ?: return
        val newOrder = current.primaryOrder.map { item ->
            if (item is UnifiedTabOrder.OrderItem.TabGroup && item.groupId == groupId) {
                item.copy(isExpanded = !item.isExpanded)
            } else {
                item
            }
        }
        saveOrder(current.copy(primaryOrder = newOrder))
    }
    
    /**
     * Rename a group
     */
    suspend fun renameGroup(groupId: String, newName: String) {
        // Update in UnifiedTabGroupManager
        groupManager.renameGroup(groupId, newName)
        
        // Update local order
        val current = _currentOrder.value ?: return
        val newOrder = current.primaryOrder.map { item ->
            if (item is UnifiedTabOrder.OrderItem.TabGroup && item.groupId == groupId) {
                item.copy(groupName = newName)
            } else {
                item
            }
        }
        saveOrder(current.copy(primaryOrder = newOrder))
    }
    
    /**
     * Change group color
     */
    suspend fun changeGroupColor(groupId: String, newColor: Int) {
        // Update in UnifiedTabGroupManager
        groupManager.changeGroupColor(groupId, newColor)
        
        // Update local order
        val current = _currentOrder.value ?: return
        val newOrder = current.primaryOrder.map { item ->
            if (item is UnifiedTabOrder.OrderItem.TabGroup && item.groupId == groupId) {
                item.copy(color = newColor)
            } else {
                item
            }
        }
        saveOrder(current.copy(primaryOrder = newOrder))
    }
    
    /**
     * Disband a group (ungroup all tabs)
     */
    suspend fun disbandGroup(groupId: String) {
        // Remove from UnifiedTabGroupManager
        groupManager.deleteGroup(groupId)
        
        // Update local order - ungroup all tabs
        val current = _currentOrder.value ?: return
        val newOrder = current.primaryOrder.flatMap { item ->
            if (item is UnifiedTabOrder.OrderItem.TabGroup && item.groupId == groupId) {
                item.tabIds.map { UnifiedTabOrder.OrderItem.SingleTab(it) }
            } else {
                listOf(item)
            }
        }
        saveOrder(current.copy(primaryOrder = newOrder))
    }
    
    /**
     * Initialize order from current tab state and existing groups
     * This syncs the Compose order with existing groups
     */
    suspend fun initializeFromTabs(profileId: String, tabIds: List<String>) {
        // Get existing groups from UnifiedTabGroupManager
        val existingGroups = groupManager.getAllGroups()
        
        val groupedTabIds = existingGroups.flatMap { it.tabIds }.toSet()
        val ungroupedTabIds = tabIds.filter { it !in groupedTabIds }
        
        // Build order: groups first, then ungrouped tabs
        // This maintains a stable order based on creation time
        val primaryOrder = mutableListOf<UnifiedTabOrder.OrderItem>()
        
        // Add groups sorted by creation time
        existingGroups.sortedBy { it.createdAt }.forEach { group ->
            // Only include tabs that still exist
            val validTabIds = group.tabIds.filter { it in tabIds }
            if (validTabIds.isNotEmpty()) {
                primaryOrder.add(
                    UnifiedTabOrder.OrderItem.TabGroup(
                        groupId = group.id,
                        groupName = group.name,
                        color = group.color,
                        isExpanded = !group.isCollapsed,
                        tabIds = validTabIds
                    )
                )
            }
        }
        
        // Add ungrouped tabs in the order they appear in tabIds
        ungroupedTabIds.forEach { tabId ->
            primaryOrder.add(UnifiedTabOrder.OrderItem.SingleTab(tabId))
        }
        
        val order = UnifiedTabOrder(
            profileId = profileId,
            primaryOrder = primaryOrder
        )
        saveOrder(order)
    }
    
    /**
     * Initialize from current state with groups
     */
    suspend fun initializeFromCurrentState(profileId: String, tabIds: List<String>, groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>) {
        val items = mutableListOf<UnifiedTabOrder.OrderItem>()
        val processedTabIds = mutableSetOf<String>()
        
        // Add groups
        groups.forEach { group ->
            val validTabIds = group.tabIds.filter { it in tabIds }
            if (validTabIds.isNotEmpty()) {
                items.add(UnifiedTabOrder.OrderItem.TabGroup(
                    groupId = group.id,
                    groupName = group.name,
                    color = group.color,
                    isExpanded = true,
                    tabIds = validTabIds
                ))
                processedTabIds.addAll(validTabIds)
            }
        }
        
        // Add ungrouped tabs
        tabIds.forEach { tabId ->
            if (!processedTabIds.contains(tabId)) {
                items.add(UnifiedTabOrder.OrderItem.SingleTab(tabId))
            }
        }
        
        val order = UnifiedTabOrder(
            profileId = profileId,
            primaryOrder = items,
            lastModified = System.currentTimeMillis()
        )
        saveOrder(order)
    }
    
    /**
     * Sync changes back to UnifiedTabGroupManager
     * Updates group membership and properties
     */
    suspend fun syncToGroupManager() {
        val current = _currentOrder.value ?: return
        
        current.primaryOrder.forEach { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> {
                    // Single tabs don't need syncing
                }
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    // Sync group state
                    val existingGroup = groupManager.getAllGroups().find { it.id == item.groupId }
                    if (existingGroup != null) {
                        // Update existing group's tab membership if changed
                        val currentTabIds = existingGroup.tabIds.toSet()
                        val newTabIds = item.tabIds.toSet()
                        
                        // Add new tabs to group
                        newTabIds.filter { it !in currentTabIds }.forEach { tabId ->
                            groupManager.addTabToGroup(tabId, item.groupId)
                        }
                        
                        // Remove tabs no longer in group
                        currentTabIds.filter { it !in newTabIds }.forEach { tabId ->
                            groupManager.removeTabFromGroup(tabId)
                        }
                    } else {
                        // Create new group
                        groupManager.createGroup(
                            tabIds = item.tabIds,
                            name = item.groupName,
                            color = item.color
                        )
                    }
                }
            }
        }
    }
}
