package com.prirai.android.nira.browser.tabs.compose

import mozilla.components.browser.state.state.TabSessionState
import com.prirai.android.nira.browser.tabgroups.TabGroupData

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
     * Group header (collapsed or expanded) - DEPRECATED, use GroupContainer
     */
    @Deprecated("Use GroupContainer instead")
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
     * Tab within a group (for list/grid views when expanded) - DEPRECATED, use GroupContainer
     */
    @Deprecated("Use GroupContainer with children instead")
    data class GroupedTab(
        override val id: String,
        override val sortKey: String,
        val tab: TabSessionState,
        val groupId: String,
        val contextId: String?,
        val isLastInGroup: Boolean
    ) : UnifiedItem()

    /**
     * Group row (for grid view - shows multiple tabs in a row) - DEPRECATED, use GroupContainer
     */
    @Deprecated("Use GroupContainer instead")
    data class GroupRow(
        override val id: String,
        override val sortKey: String,
        val groupId: String,
        val tabs: List<TabSessionState>,
        val contextId: String?
    ) : UnifiedItem()

    /**
     * Group container - header is a parent with child tabs
     * This is the new unified approach where header acts as a container
     */
    data class GroupContainer(
        override val id: String,
        override val sortKey: String,
        val groupId: String,
        val title: String,
        val color: Int,
        val contextId: String?,
        val isExpanded: Boolean,
        val children: List<TabSessionState>
    ) : UnifiedItem() {
        val tabCount: Int get() = children.size
    }
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
     * @return List of unified items ready for display
     */
    fun buildItems(
        order: UnifiedTabOrder?,
        tabs: List<TabSessionState>,
        groups: List<TabGroupData>,
        expandedGroups: Set<String>
    ): List<UnifiedItem> {
        // If no order, use fallback
        if (order == null) {
            return buildFallbackItems(tabs, groups, expandedGroups)
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
                            // Add group as a container with children
                            val groupTabs = validTabIds.mapNotNull { tabsById[it] }
                            items.add(
                                UnifiedItem.GroupContainer(
                                    id = "group_${group.id}",
                                    sortKey = "root_$index",
                                    groupId = group.id,
                                    title = group.name,
                                    color = group.color,
                                    contextId = group.contextId,
                                    isExpanded = isExpanded,
                                    children = groupTabs
                                )
                            )
                            addedTabIds.addAll(validTabIds)
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
        expandedGroups: Set<String>
    ): List<UnifiedItem> {
        val items = mutableListOf<UnifiedItem>()
        val addedTabIds = mutableSetOf<String>()

        // Create lookup map
        val tabsById = tabs.associateBy { it.id }

        // Add groups first
        groups.forEachIndexed { groupIndex, group ->
            val isExpanded = expandedGroups.contains(group.id)
            val validTabIds = group.tabIds.filter { tabsById.containsKey(it) }

            if (validTabIds.isNotEmpty()) {
                // Add group as a container with children
                val groupTabs = validTabIds.mapNotNull { tabsById[it] }
                items.add(
                    UnifiedItem.GroupContainer(
                        id = "group_${group.id}",
                        sortKey = "group_$groupIndex",
                        groupId = group.id,
                        title = group.name,
                        color = group.color,
                        contextId = group.contextId,
                        isExpanded = isExpanded,
                        children = groupTabs
                    )
                )
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
