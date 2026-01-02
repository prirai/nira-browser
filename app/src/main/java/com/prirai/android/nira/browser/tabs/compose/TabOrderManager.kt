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
import com.prirai.android.nira.ext.components

private val Context.orderDataStore: DataStore<Preferences> by preferencesDataStore(name = "tab_order")

/**
 * Manages tab ordering and grouping across all views.
 * Single source of truth for tab positions.
 * Singleton to ensure all components share the same order state.
 */
class TabOrderManager private constructor(
    private val context: Context,
    private val groupManager: UnifiedTabGroupManager
) {
    
    companion object {
        @Volatile
        private var instance: TabOrderManager? = null
        
        fun getInstance(context: Context, groupManager: UnifiedTabGroupManager): TabOrderManager {
            return instance ?: synchronized(this) {
                instance ?: TabOrderManager(context.applicationContext, groupManager).also { instance = it }
            }
        }
    }
    
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
        
        // Clean up orphaned group references
        val cleanedOrder = cleanupOrphanedGroups(order)
        _currentOrder.value = cleanedOrder
        return cleanedOrder
    }
    
    /**
     * Clean up orphaned group IDs from unified order
     */
    private suspend fun cleanupOrphanedGroups(order: UnifiedTabOrder): UnifiedTabOrder {
        val allGroups = groupManager.getAllGroups()
        val validGroupIds = allGroups.map { it.id }.toSet()
        
        val cleanedPrimaryOrder = order.primaryOrder.filter { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    // Only keep group items if the group still exists
                    validGroupIds.contains(item.groupId)
                }
                is UnifiedTabOrder.OrderItem.SingleTab -> true
            }
        }
        
        return if (cleanedPrimaryOrder.size != order.primaryOrder.size) {
            // Some items were removed, save the cleaned version
            order.copy(primaryOrder = cleanedPrimaryOrder).also { cleaned ->
                saveOrder(cleaned)
            }
        } else {
            order
        }
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
        val size = newOrder.size
        
        // Bounds checking - critical to prevent IndexOutOfBoundsException
        if (fromIndex < 0 || fromIndex >= size) {
            android.util.Log.e("TabOrderManager", "Invalid fromIndex: $fromIndex, size: $size")
            return
        }
        if (toIndex < 0 || toIndex >= size) {
            android.util.Log.e("TabOrderManager", "Invalid toIndex: $toIndex, size: $size")
            return
        }
        
        val item = newOrder.removeAt(fromIndex)
        newOrder.add(toIndex, item)
        saveOrder(current.copy(primaryOrder = newOrder))
        syncToGroupManager()
    }

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
     * Add a new tab to a specific group
     */
    suspend fun addNewTabToGroup(groupId: String) {
        android.util.Log.d("TabOrderManager", "addNewTabToGroup called for group: $groupId")
        
        // Get the group to determine its contextId
        val group = groupManager.getGroup(groupId)
        if (group == null) {
            android.util.Log.e("TabOrderManager", "Group $groupId not found")
            return
        }
        
        val contextId = group.contextId
        android.util.Log.d("TabOrderManager", "Group contextId: $contextId, tabIds: ${group.tabIds}")
        
        // Use context to get components
        val store = context.components.store
        val tabsUseCases = context.components.tabsUseCases
        
        // Add a new tab
        android.util.Log.d("TabOrderManager", "Creating new tab with contextId: $contextId")
        tabsUseCases.addTab(
            url = "about:homepage",
            private = contextId == "private",
            contextId = contextId,
            selectTab = true
        )
        
        // Wait longer and retry to ensure tab is created
        kotlinx.coroutines.delay(300)
        var newTabId = store.state.selectedTabId
        
        // Retry up to 3 times if tab ID not available
        var retries = 0
        while (newTabId == null && retries < 3) {
            kotlinx.coroutines.delay(200)
            newTabId = store.state.selectedTabId
            retries++
        }
        
        if (newTabId == null) {
            android.util.Log.e("TabOrderManager", "Failed to get new tab ID after creating tab")
            return
        }
        
        android.util.Log.d("TabOrderManager", "New tab created with ID: $newTabId")
        
        // Verify the tab exists in store
        val newTab = store.state.tabs.find { it.id == newTabId }
        if (newTab == null) {
            android.util.Log.e("TabOrderManager", "Tab $newTabId not found in store")
            return
        }
        
        android.util.Log.d("TabOrderManager", "New tab contextId: ${newTab.contextId}")
        
        // Add the new tab to the group in the order
        android.util.Log.d("TabOrderManager", "Adding tab $newTabId to group $groupId in order")
        addTabToGroup(newTabId, groupId)
        
        // Also add it directly to the group manager if not already there
        android.util.Log.d("TabOrderManager", "Syncing tab to group manager")
        groupManager.addTabToGroup(newTabId, groupId)
        
        // Force refresh by updating the order timestamp
        val current = _currentOrder.value ?: return
        _currentOrder.value = current.copy(lastModified = System.currentTimeMillis())
        
        android.util.Log.d("TabOrderManager", "Successfully added new tab $newTabId to group $groupId")
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
     * Auto-expand group containing the selected tab
     */
    suspend fun expandGroupContainingTab(tabId: String) {
        val current = _currentOrder.value ?: return
        var hasChanges = false
        val newOrder = current.primaryOrder.map { item ->
            if (item is UnifiedTabOrder.OrderItem.TabGroup && tabId in item.tabIds) {
                if (!item.isExpanded) {
                    hasChanges = true
                    item.copy(isExpanded = true)
                } else {
                    item
                }
            } else {
                item
            }
        }
        if (hasChanges) {
            saveOrder(current.copy(primaryOrder = newOrder))
        }
    }

    /**
     * Initialize from current state with groups, preserving tab positions
     */
    suspend fun initializeFromCurrentState(profileId: String, tabIds: List<String>, groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>) {
        val items = mutableListOf<UnifiedTabOrder.OrderItem>()
        val processedTabIds = mutableSetOf<String>()
        
        // Map each group to the position of its first tab
        val groupPositions = groups.mapNotNull { group ->
            val validTabIds = group.tabIds.filter { it in tabIds }
            if (validTabIds.isEmpty()) return@mapNotNull null
            
            val firstTabPosition = tabIds.indexOf(validTabIds.first())
            if (firstTabPosition == -1) return@mapNotNull null
            
            firstTabPosition to UnifiedTabOrder.OrderItem.TabGroup(
                groupId = group.id,
                groupName = group.name,
                color = group.color,
                isExpanded = true,
                tabIds = validTabIds
            )
        }.toMap()
        
        val groupedTabIds = groups.flatMap { it.tabIds }.toSet()
        
        // Process tabs in their original order
        tabIds.forEachIndexed { index, tabId ->
            if (tabId in processedTabIds) return@forEachIndexed
            
            // If a group starts at this position, add the group
            if (index in groupPositions) {
                val groupItem = groupPositions[index]!!
                items.add(groupItem)
                // Mark all tabs in this group as processed
                groupItem.tabIds.forEach { processedTabIds.add(it) }
            } else if (tabId !in groupedTabIds) {
                // Add ungrouped tab
                items.add(UnifiedTabOrder.OrderItem.SingleTab(tabId))
                processedTabIds.add(tabId)
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
        
        // Determine contextId from profileId
        val contextId = when (current.profileId) {
            "private" -> "private"
            "default" -> "profile_default"
            else -> "profile_${current.profileId}"
        }
        
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
                        // Create new group with proper contextId
                        groupManager.createGroup(
                            tabIds = item.tabIds,
                            name = item.groupName,
                            color = item.color,
                            contextId = contextId
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Rebuild order for a profile from current tabs and groups.
     * Used when groups are created/deleted to ensure order stays in sync.
     */
    suspend fun rebuildOrderForProfile(profileId: String, tabs: List<mozilla.components.browser.state.state.SessionState>) {
        // Get all groups for this profile
        val allGroups = groupManager.getAllGroups()
        
        // Determine contextId for filtering
        val contextId = when (profileId) {
            "private" -> "private"
            "default" -> "profile_default"
            else -> "profile_$profileId"
        }
        
        // Filter groups by contextId
        val profileGroups = allGroups.filter { group ->
            when {
                profileId == "default" -> group.contextId == "profile_default" || group.contextId == null
                else -> group.contextId == contextId
            }
        }
        
        // Get tab IDs from tabs
        val tabIds = tabs.map { it.id }
        
        // Load existing order or create new one
        val existingOrder = try {
            loadOrder(profileId)
        } catch (e: Exception) {
            UnifiedTabOrder(profileId, emptyList())
        }
        
        // Build a map of existing positions
        val existingPositions = mutableMapOf<String, Int>()
        existingOrder.primaryOrder.forEachIndexed { index, item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> existingPositions[item.tabId] = index
                is UnifiedTabOrder.OrderItem.TabGroup -> existingPositions["group_${item.groupId}"] = index
            }
        }
        
        // Create new order items
        val newOrder = mutableListOf<UnifiedTabOrder.OrderItem>()
        val processedTabIds = mutableSetOf<String>()
        
        // First, add items that exist in the old order in their old positions
        val itemsWithPositions = mutableListOf<Pair<Int, UnifiedTabOrder.OrderItem>>()
        
        // Process groups
        profileGroups.forEach { group ->
            val validTabIds = group.tabIds.filter { it in tabIds }
            if (validTabIds.isNotEmpty()) {
                val position = existingPositions["group_${group.id}"] ?: Int.MAX_VALUE
                itemsWithPositions.add(
                    position to UnifiedTabOrder.OrderItem.TabGroup(
                        groupId = group.id,
                        groupName = group.name,
                        color = group.color,
                        isExpanded = true,
                        tabIds = validTabIds
                    )
                )
                processedTabIds.addAll(validTabIds)
            }
        }
        
        // Process ungrouped tabs
        tabIds.forEach { tabId ->
            if (tabId !in processedTabIds) {
                val position = existingPositions[tabId] ?: Int.MAX_VALUE
                itemsWithPositions.add(
                    position to UnifiedTabOrder.OrderItem.SingleTab(tabId)
                )
                processedTabIds.add(tabId)
            }
        }
        
        // Sort by position and extract items
        newOrder.addAll(itemsWithPositions.sortedBy { it.first }.map { it.second })
        
        // Save the new order
        val updatedOrder = UnifiedTabOrder(
            profileId = profileId,
            primaryOrder = newOrder,
            lastModified = System.currentTimeMillis()
        )
        saveOrder(updatedOrder)
        _currentOrder.value = updatedOrder
        
        android.util.Log.d("TabOrderManager", "Rebuilt order for profile $profileId: ${newOrder.size} items")
    }
}
