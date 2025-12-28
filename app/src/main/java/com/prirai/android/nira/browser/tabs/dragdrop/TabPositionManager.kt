package com.prirai.android.nira.browser.tabs.dragdrop

import mozilla.components.browser.state.state.TabSessionState

/**
 * Manages tab positions for reordering support.
 * Tracks the global order of all tabs and groups.
 */
class TabPositionManager {
    
    // Map of tab ID to global position
    private val tabPositions = mutableMapOf<String, Int>()
    
    // Map of group ID to global position
    private val groupPositions = mutableMapOf<String, Int>()
    
    /**
     * Initialize positions from current tab order
     */
    fun initializeFromTabs(tabs: List<TabSessionState>, groupTabIds: Map<String, List<String>>) {
        tabPositions.clear()
        groupPositions.clear()
        
        // Build map of tab to group
        val tabToGroup = mutableMapOf<String, String>()
        groupTabIds.forEach { (groupId, tabIds) ->
            tabIds.forEach { tabId ->
                tabToGroup[tabId] = groupId
            }
        }
        
        var position = 0
        val processedGroups = mutableSetOf<String>()
        
        tabs.forEach { tab ->
            val groupId = tabToGroup[tab.id]
            
            if (groupId != null && !processedGroups.contains(groupId)) {
                // First tab of a group - assign position to the group
                groupPositions[groupId] = position
                processedGroups.add(groupId)
                position++
            } else if (groupId == null) {
                // Ungrouped tab
                tabPositions[tab.id] = position
                position++
            }
        }
    }
    
    /**
     * Reorder a tab to a new position
     */
    fun reorderTab(tabId: String, newPosition: Int, groupTabIds: Map<String, List<String>>) {
        // Find if tab is in a group
        val groupId = groupTabIds.entries.find { (_, tabIds) -> tabIds.contains(tabId) }?.key
        
        val oldPosition = if (groupId != null) {
            groupPositions[groupId] ?: 0
        } else {
            tabPositions[tabId] ?: 0
        }
        
        if (oldPosition == newPosition) return
        
        // Shift positions
        if (newPosition < oldPosition) {
            // Moving up - shift items down
            if (groupId != null) {
                groupPositions.entries.forEach { (id, pos) ->
                    if (pos in newPosition until oldPosition) {
                        groupPositions[id] = pos + 1
                    }
                }
                groupPositions[groupId] = newPosition
            } else {
                tabPositions.entries.forEach { (id, pos) ->
                    if (pos in newPosition until oldPosition) {
                        tabPositions[id] = pos + 1
                    }
                }
                tabPositions[tabId] = newPosition
            }
        } else {
            // Moving down - shift items up
            if (groupId != null) {
                groupPositions.entries.forEach { (id, pos) ->
                    if (pos in (oldPosition + 1)..newPosition) {
                        groupPositions[id] = pos - 1
                    }
                }
                groupPositions[groupId] = newPosition
            } else {
                tabPositions.entries.forEach { (id, pos) ->
                    if (pos in (oldPosition + 1)..newPosition) {
                        tabPositions[id] = pos - 1
                    }
                }
                tabPositions[tabId] = newPosition
            }
        }
    }
    
    /**
     * Get sorted list of items by position
     */
    fun getSortedItems(): List<Pair<String, Boolean>> {
        // Combine tabs and groups, return list of (id, isGroup)
        val allItems = mutableListOf<Triple<String, Int, Boolean>>()
        
        tabPositions.forEach { (id, pos) ->
            allItems.add(Triple(id, pos, false))
        }
        
        groupPositions.forEach { (id, pos) ->
            allItems.add(Triple(id, pos, true))
        }
        
        return allItems.sortedBy { it.second }.map { Pair(it.first, it.third) }
    }
    
    /**
     * Get position for a tab or group
     */
    fun getPosition(id: String, isGroup: Boolean): Int {
        return if (isGroup) {
            groupPositions[id] ?: -1
        } else {
            tabPositions[id] ?: -1
        }
    }
}
