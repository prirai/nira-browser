package com.prirai.android.nira.browser.tabs.compose

import mozilla.components.browser.state.state.TabSessionState
import com.prirai.android.nira.browser.tabgroups.TabGroupData

/**
 * Unified item builder for all tab views (bar, list, grid).
 * Eliminates code duplication by providing a single source of truth for item construction.
 */

/**
 * View mode for determining item structure
 */
enum class ViewMode {
    BAR,    // Horizontal tab bar (pill-style)
    LIST,   // Vertical list view
    GRID    // Grid view
}

/**
 * Base sealed class for all unified items
 */
sealed class UnifiedItem {
    abstract val id: String
    abstract val sortKey: String  // For stable sorting

    /**
     * Single ungrouped tab
     */
    data class SingleTab(
        override val id: String,
        override val sortKey: String,
        val tab: TabSessionState,
        val contextId: String?
    ) : UnifiedItem()

    /**
     * Group header (collapsed or expanded)
     */
    data class GroupHeader(
        override val id: String,
        override val sortKey: String,
        val groupId: String,
        val title: String,
        val color: Int,
        val contextId: String?,
        val tabCount: Int,
        val isExpanded: Boolean
    ) : UnifiedItem()

    /**
     * Tab within a group (for list/grid views when expanded)
     */
    data class GroupedTab(
        override val id: String,
        override val sortKey: String,
        val tab: TabSessionState,
        val groupId: String,
        val contextId: String?,
        val isLastInGroup: Boolean
    ) : UnifiedItem()

    /**
     * Group row (for grid view - shows multiple tabs in a row)
     */
    data class GroupRow(
        override val id: String,
        override val sortKey: String,
        val groupId: String,
        val tabs: List<TabSessionState>,
        val contextId: String?
    ) : UnifiedItem()
}

/**
 * Main builder object
 */
object UnifiedItemBuilder {

    /**
     * Build unified items from order, tabs, and groups
     *
     * @param order The unified tab order (primary source of truth)
     * @param tabs All tabs for the current profile
     * @param groups All groups for the current profile
     * @param expandedGroups Set of group IDs that are expanded
     * @param viewMode The view mode (affects item structure)
     * @return List of unified items ready for display
     */
    fun buildItems(
        order: UnifiedTabOrder?,
        tabs: List<TabSessionState>,
        groups: List<TabGroupData>,
        expandedGroups: Set<String>,
        viewMode: ViewMode
    ): List<UnifiedItem> {
        // If no order, use fallback
        if (order == null) {
            return buildFallbackItems(tabs, groups, expandedGroups, viewMode)
        }

        val items = mutableListOf<UnifiedItem>()
        val addedTabIds = mutableSetOf<String>()

        // Create lookup maps for efficiency
        val tabsById = tabs.associateBy { it.id }
        val groupsById = groups.associateBy { it.id }

        // Iterate through primary order
        order.primaryOrder.forEachIndexed { index, orderItem ->
            when (orderItem) {
                is UnifiedTabOrder.OrderItem.SingleTab -> {
                    val tab = tabsById[orderItem.tabId]
                    if (tab != null && tab.id !in addedTabIds) {
                        items.add(
                            UnifiedItem.SingleTab(
                                id = "tab_${tab.id}",
                                sortKey = "root_$index",
                                tab = tab,
                                contextId = tab.contextId
                            )
                        )
                        addedTabIds.add(tab.id)
                    }
                }

                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    val group = groupsById[orderItem.groupId]
                    if (group != null) {
                        val isExpanded = expandedGroups.contains(group.id)

                        // Validate tab IDs in group
                        val validTabIds = orderItem.tabIds.filter { tabId ->
                            tabsById.containsKey(tabId) && tabId !in addedTabIds
                        }

                        if (validTabIds.isNotEmpty()) {
                            // Add group header
                            items.add(
                                UnifiedItem.GroupHeader(
                                    id = "group_${group.id}",
                                    sortKey = "root_$index",
                                    groupId = group.id,
                                    title = group.name,
                                    color = group.color,
                                    contextId = group.contextId,
                                    tabCount = validTabIds.size,
                                    isExpanded = isExpanded
                                )
                            )

                            // Add group contents if expanded
                            if (isExpanded) {
                                when (viewMode) {
                                    ViewMode.BAR, ViewMode.LIST -> {
                                        // Add tabs individually
                                        validTabIds.forEachIndexed { tabIndex, tabId ->
                                            val tab = tabsById[tabId]
                                            if (tab != null) {
                                                items.add(
                                                    UnifiedItem.GroupedTab(
                                                        id = "group_${group.id}_tab_${tab.id}",
                                                        sortKey = "root_${index}_tab_$tabIndex",
                                                        tab = tab,
                                                        groupId = group.id,
                                                        contextId = group.contextId,
                                                        isLastInGroup = tabIndex == validTabIds.size - 1
                                                    )
                                                )
                                                addedTabIds.add(tab.id)
                                            }
                                        }
                                    }

                                    ViewMode.GRID -> {
                                        // Add tabs as rows (for grid layout)
                                        val groupTabs = validTabIds.mapNotNull { tabsById[it] }
                                        items.add(
                                            UnifiedItem.GroupRow(
                                                id = "grouprow_${group.id}",
                                                sortKey = "root_${index}_row",
                                                groupId = group.id,
                                                tabs = groupTabs,
                                                contextId = group.contextId
                                            )
                                        )
                                        addedTabIds.addAll(validTabIds)
                                    }
                                }
                            } else {
                                // Mark tabs as added even if collapsed
                                addedTabIds.addAll(validTabIds)
                            }
                        }
                    }
                }
            }
        }

        // Add any remaining tabs that weren't in the order (orphaned tabs)
        tabs.filter { it.id !in addedTabIds }.forEachIndexed { index, tab ->
            items.add(
                UnifiedItem.SingleTab(
                    id = "tab_${tab.id}",
                    sortKey = "orphan_$index",
                    tab = tab,
                    contextId = tab.contextId
                )
            )
        }

        return items
    }

    /**
     * Build items when no order is available (fallback mode)
     */
    private fun buildFallbackItems(
        tabs: List<TabSessionState>,
        groups: List<TabGroupData>,
        expandedGroups: Set<String>,
        viewMode: ViewMode
    ): List<UnifiedItem> {
        val items = mutableListOf<UnifiedItem>()
        val addedTabIds = mutableSetOf<String>()

        // Create lookup map
        val tabsById = tabs.associateBy { it.id }

        // Find all tabs that are grouped
        val groupedTabIds = groups.flatMap { it.tabIds }.toSet()

        // Add groups first
        groups.forEachIndexed { groupIndex, group ->
            val isExpanded = expandedGroups.contains(group.id)
            val validTabIds = group.tabIds.filter { tabsById.containsKey(it) }

            if (validTabIds.isNotEmpty()) {
                // Add group header
                items.add(
                    UnifiedItem.GroupHeader(
                        id = "group_${group.id}",
                        sortKey = "group_$groupIndex",
                        groupId = group.id,
                        title = group.name,
                        color = group.color,
                        contextId = group.contextId,
                        tabCount = validTabIds.size,
                        isExpanded = isExpanded
                    )
                )

                // Add group contents if expanded
                if (isExpanded) {
                    when (viewMode) {
                        ViewMode.BAR, ViewMode.LIST -> {
                            validTabIds.forEachIndexed { tabIndex, tabId ->
                                val tab = tabsById[tabId]
                                if (tab != null) {
                                    items.add(
                                        UnifiedItem.GroupedTab(
                                            id = "group_${group.id}_tab_${tab.id}",
                                            sortKey = "group_${groupIndex}_tab_$tabIndex",
                                            tab = tab,
                                            groupId = group.id,
                                            contextId = group.contextId,
                                            isLastInGroup = tabIndex == validTabIds.size - 1
                                        )
                                    )
                                }
                            }
                        }

                        ViewMode.GRID -> {
                            val groupTabs = validTabIds.mapNotNull { tabsById[it] }
                            items.add(
                                UnifiedItem.GroupRow(
                                    id = "grouprow_${group.id}",
                                    sortKey = "group_${groupIndex}_row",
                                    groupId = group.id,
                                    tabs = groupTabs,
                                    contextId = group.contextId
                                )
                            )
                        }
                    }
                }

                addedTabIds.addAll(validTabIds)
            }
        }

        // Add ungrouped tabs
        tabs.filter { it.id !in addedTabIds }.forEachIndexed { index, tab ->
            items.add(
                UnifiedItem.SingleTab(
                    id = "tab_${tab.id}",
                    sortKey = "single_$index",
                    tab = tab,
                    contextId = tab.contextId
                )
            )
        }

        return items
    }

    /**
     * Helper to deduplicate items by ID (in case of data inconsistencies)
     */
    fun deduplicateItems(items: List<UnifiedItem>): List<UnifiedItem> {
        val seen = mutableSetOf<String>()
        return items.filter { item ->
            if (item.id in seen) {
                android.util.Log.w("UnifiedItemBuilder", "Duplicate item ID: ${item.id}")
                false
            } else {
                seen.add(item.id)
                true
            }
        }
    }


}
