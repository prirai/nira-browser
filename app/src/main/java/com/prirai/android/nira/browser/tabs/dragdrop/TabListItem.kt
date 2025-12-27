package com.prirai.android.nira.browser.tabs.dragdrop

import mozilla.components.browser.state.state.TabSessionState

/**
 * Flat list representation of tabs and groups for unified drag and drop.
 * This eliminates the need for nested RecyclerViews.
 */
sealed class TabListItem {
    /**
     * A group header - acts as the drag handle for the entire group
     */
    data class GroupHeader(
        val groupId: String,
        val name: String,
        val color: Int,
        val tabCount: Int,
        val isExpanded: Boolean
    ) : TabListItem() {
        // Unique key for DiffUtil
        val key: String get() = "group_header_$groupId"
    }
    
    /**
     * A tab that belongs to a group
     * Only visible when the group is expanded
     */
    data class GroupedTab(
        val tab: TabSessionState,
        val groupId: String,
        val positionInGroup: Int
    ) : TabListItem() {
        val key: String get() = "grouped_tab_${tab.id}_in_$groupId"
    }
    
    /**
     * An ungrouped standalone tab
     */
    data class UngroupedTab(
        val tab: TabSessionState,
        val globalPosition: Int
    ) : TabListItem() {
        val key: String get() = "ungrouped_tab_${tab.id}"
    }
    
    /**
     * Get the tab ID for any item type that contains a tab
     */
    val tabId: String? get() = when (this) {
        is GroupedTab -> tab.id
        is UngroupedTab -> tab.id
        is GroupHeader -> null
    }
    
    /**
     * Check if this item can be dragged
     */
    val isDraggable: Boolean get() = when (this) {
        is GroupedTab -> true
        is UngroupedTab -> true
        is GroupHeader -> false // Headers are not draggable themselves
    }
}
