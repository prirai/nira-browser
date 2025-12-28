package com.prirai.android.nira.browser.tabs.dragdrop

import com.prirai.android.nira.browser.tabgroups.TabGroupData
import mozilla.components.browser.state.state.TabSessionState

/**
 * Builds a flat list of TabListItems from tabs and groups.
 * This is the key to eliminating nested RecyclerViews.
 */
class TabListBuilder(
    private val collapsedGroupIds: Set<String>
) {
    
    /**
     * Builds a flat list maintaining the order:
     * 1. If custom order exists, use it
     * 2. Otherwise, tabs appear in their original order
     * 3. When a grouped tab is encountered, insert the entire group
     * 4. Ungrouped tabs appear as UngroupedTab items
     * 5. Group expansion state determines visibility of GroupedTab items
     */
    fun buildList(
        allTabs: List<TabSessionState>,
        allGroups: List<TabGroupData>,
        customOrder: List<String>? = null
    ): List<TabListItem> {
        val result = mutableListOf<TabListItem>()
        
        // Create quick lookup maps
        val tabToGroupMap = mutableMapOf<String, TabGroupData>()
        allGroups.forEach { group ->
            group.tabIds.forEach { tabId ->
                tabToGroupMap[tabId] = group
            }
        }
        
        val processedGroups = mutableSetOf<String>()
        val processedTabs = mutableSetOf<String>()
        
        var ungroupedPosition = 0
        
        // Determine order to process
        val tabsToProcess = if (customOrder != null) {
            // Use custom order, filtering to only include existing tabs
            val tabMap = allTabs.associateBy { it.id }
            customOrder.mapNotNull { tabMap[it] }
        } else {
            // Use original order
            allTabs
        }
        
        // Process tabs in order
        tabsToProcess.forEach { tab ->
            if (processedTabs.contains(tab.id)) {
                return@forEach
            }
            
            val group = tabToGroupMap[tab.id]
            
            if (group != null && !processedGroups.contains(group.id)) {
                // First tab of a group - add group header and all its tabs
                processedGroups.add(group.id)
                
                val isExpanded = !collapsedGroupIds.contains(group.id)
                
                // Add group header
                result.add(TabListItem.GroupHeader(
                    groupId = group.id,
                    name = group.name,
                    color = group.color,
                    tabCount = group.tabIds.size,
                    isExpanded = isExpanded
                ))
                
                // Add grouped tabs (only if expanded)
                if (isExpanded) {
                    // Get tabs in group order
                    group.tabIds.forEachIndexed { index, tabId ->
                        val groupedTab = allTabs.find { it.id == tabId }
                        if (groupedTab != null) {
                            result.add(TabListItem.GroupedTab(
                                tab = groupedTab,
                                groupId = group.id,
                                positionInGroup = index
                            ))
                        }
                    }
                }
                
                // Mark all group tabs as processed
                group.tabIds.forEach { processedTabs.add(it) }
                
            } else if (group == null) {
                // Ungrouped tab
                result.add(TabListItem.UngroupedTab(
                    tab = tab,
                    globalPosition = ungroupedPosition++
                ))
                processedTabs.add(tab.id)
            }
        }
        
        return result
    }
    
    /**
     * Rebuild list with updated expansion state
     */
    fun toggleGroupExpansion(
        currentList: List<TabListItem>,
        groupId: String
    ): List<TabListItem> {
        val result = mutableListOf<TabListItem>()
        var i = 0
        
        while (i < currentList.size) {
            val item = currentList[i]
            
            if (item is TabListItem.GroupHeader && item.groupId == groupId) {
                // Toggle this group
                val newExpanded = !item.isExpanded
                result.add(item.copy(isExpanded = newExpanded))
                i++
                
                // Skip or add grouped tabs based on new state
                if (newExpanded) {
                    // Find all grouped tabs (they might not be in the list if collapsed)
                    // We need to reconstruct them - this is a limitation
                    // Better approach: always keep them in the list, just hide them
                } else {
                    // Skip grouped tabs until next non-grouped item
                    while (i < currentList.size && currentList[i] is TabListItem.GroupedTab) {
                        val groupedTab = currentList[i] as TabListItem.GroupedTab
                        if (groupedTab.groupId == groupId) {
                            i++
                        } else {
                            break
                        }
                    }
                }
            } else {
                result.add(item)
                i++
            }
        }
        
        return result
    }
}
