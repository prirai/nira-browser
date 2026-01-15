package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.runtime.*
import com.prirai.android.nira.browser.tabs.compose.DragCoordinator
import com.prirai.android.nira.browser.tabs.compose.DraggableItemType
import com.prirai.android.nira.browser.tabs.compose.DropTargetType
import com.prirai.android.nira.browser.tabs.compose.UnifiedItem

/**
 * Hover state manager for tab sheet drag and drop operations.
 * Centralizes logic for determining what should be highlighted based on what's being dragged.
 */
class TabSheetHoverState(
    private val coordinator: DragCoordinator,
    private val uniqueItems: List<UnifiedItem>
) {
    /**
     * Gets the ID of the group being hovered, but only when dragging an ungrouped tab.
     * This is used to enlarge entire group containers when appropriate.
     */
    @Composable
    fun getHoveredGroupIdForUngroupedDrag(): String? {
        return remember {
            derivedStateOf {
                val draggedItem = coordinator.dragState.value.draggedItem
                val isDraggingUngroupedTab = draggedItem is DraggableItemType.Tab && draggedItem.groupId == null

                // Only enlarge group container when dragging an UNGROUPED tab
                if (isDraggingUngroupedTab) {
                    val dropTarget = coordinator.dragState.value.currentDropTarget
                    when {
                        dropTarget != null && dropTarget.type == DropTargetType.GROUP_HEADER -> {
                            // Directly hovering over group header
                            dropTarget.metadata["groupId"] as? String
                        }

                        dropTarget != null && dropTarget.type == DropTargetType.TAB -> {
                            // Check if the hovered tab is in a group
                            // With new GroupContainer structure, check if drop target is a tab within a container
                            val groupContainer = uniqueItems.filterIsInstance<UnifiedItem.GroupContainer>()
                                .find { container -> container.children.any { it.id == dropTarget.id } }
                            groupContainer?.groupId
                        }

                        else -> null
                    }
                } else {
                    // When dragging grouped tab or group header, don't enlarge group container
                    null
                }
            }
        }.value
    }

    /**
     * Gets the ID of the group being hovered in grid view (checks GroupRow tabs too).
     */
    @Composable
    fun getHoveredGroupIdForUngroupedDragGrid(): String? {
        return remember {
            derivedStateOf {
                val draggedItem = coordinator.dragState.value.draggedItem
                val isDraggingUngroupedTab = draggedItem is DraggableItemType.Tab && draggedItem.groupId == null

                // Only enlarge group container when dragging an UNGROUPED tab
                if (isDraggingUngroupedTab) {
                    val dropTarget = coordinator.dragState.value.currentDropTarget
                    when {
                        dropTarget != null && dropTarget.type == DropTargetType.GROUP_HEADER -> {
                            // Directly hovering over group header
                            dropTarget.metadata["groupId"] as? String
                        }

                        dropTarget != null && dropTarget.type == DropTargetType.TAB -> {
                            // Check if the hovered tab is in a group
                            // With new GroupContainer structure, check if drop target is a tab within a container
                            val groupContainer = uniqueItems.filterIsInstance<UnifiedItem.GroupContainer>()
                                .find { container -> container.children.any { it.id == dropTarget.id } }
                            groupContainer?.groupId
                        }

                        else -> null
                    }
                } else {
                    // When dragging grouped tab or group header, don't enlarge group container
                    null
                }
            }
        }.value
    }

    /**
     * Checks if we're currently dragging an ungrouped tab.
     */
    fun isDraggingUngroupedTab(): Boolean {
        val draggedItem = coordinator.dragState.value.draggedItem
        return draggedItem is DraggableItemType.Tab && draggedItem.groupId == null
    }

    /**
     * Checks if we're currently dragging a grouped tab.
     */
    fun isDraggingGroupedTab(): Boolean {
        val draggedItem = coordinator.dragState.value.draggedItem
        return draggedItem is DraggableItemType.Tab && draggedItem.groupId != null
    }

    /**
     * Checks if we're currently dragging a group header.
     */
    fun isDraggingGroup(): Boolean {
        val draggedItem = coordinator.dragState.value.draggedItem
        return draggedItem is DraggableItemType.Group
    }

    /**
     * Determines if a divider should be shown before the given item.
     * Rules:
     * - Never show before first item
     * - When dragging ungrouped tab: hide dividers between grouped tabs in same group
     * - When dragging grouped tab: show dividers everywhere (including between grouped tabs)
     */
    fun shouldShowDivider(
        index: Int,
        currentItem: UnifiedItem,
        previousItem: UnifiedItem?
    ): Boolean {
        if (index == 0) return false // Never show before first item

        val isDraggingGroupedTab = isDraggingGroupedTab()

        // With GroupContainer, dividers are only between top-level items (containers and single tabs)
        // Tabs within containers are managed by the container itself
        return true // Always show dividers between top-level items
    }

    /**
     * Determines if a group header should show hover feedback.
     * Group headers should only react when dragging grouped tabs (for moving to different group).
     */
    fun shouldShowGroupHeaderHover(groupId: String): Boolean {
        return isDraggingGroupedTab() && coordinator.isHoveringOver(groupId)
    }

    /**
     * Determines if an individual grouped tab should show hover feedback.
     * Rules:
     * - When dragging ungrouped tab: grouped tabs don't show individual hover (group container handles it)
     * - When dragging grouped tab: grouped tabs show individual hover (for reordering within/between groups)
     */
    fun shouldShowGroupedTabHover(tabId: String, groupId: String, hoveredGroupId: String?): Boolean {
        val isDraggingUngrouped = isDraggingUngroupedTab()

        return if (isDraggingUngrouped) {
            // When dragging ungrouped: use group hover state
            groupId == hoveredGroupId
        } else {
            // When dragging grouped: use individual tab hover state
            coordinator.isHoveringOver(tabId)
        }
    }

    /**
     * Determines if an ungrouped tab should show hover feedback.
     * Ungrouped tabs should always react (can group with them or reorder).
     */
    fun shouldShowUngroupedTabHover(tabId: String): Boolean {
        return coordinator.isHoveringOver(tabId)
    }

    /**
     * Determines if a group row (in grid view) should enlarge.
     * Only when dragging ungrouped tab and hovering over any tab in that group.
     */
    fun shouldShowGroupRowHover(groupId: String, hoveredGroupId: String?): Boolean {
        return isDraggingUngroupedTab() && groupId == hoveredGroupId
    }

    /**
     * Determines if individual tabs in a group row should show hover feedback.
     * - When dragging ungrouped tab: no individual feedback (group row handles it)
     * - When dragging grouped tab: show individual feedback (for moving between groups)
     */
    fun shouldShowGroupRowTabHover(tabId: String): Boolean {
        return !isDraggingUngroupedTab() && coordinator.isHoveringOver(tabId)
    }
}

/**
 * Remember a TabSheetHoverState instance.
 */
@Composable
fun rememberTabSheetHoverState(
    coordinator: DragCoordinator,
    uniqueItems: List<UnifiedItem>
): TabSheetHoverState {
    return remember(coordinator, uniqueItems) {
        TabSheetHoverState(coordinator, uniqueItems)
    }
}
