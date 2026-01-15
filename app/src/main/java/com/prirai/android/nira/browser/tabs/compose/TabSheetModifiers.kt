package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Conditional modifier extensions for tab sheet drag and drop.
 * Provides clean, reusable modifiers that apply visual feedback based on drag context.
 */

/**
 * Applies visual feedback to a group header based on drag context.
 * Group headers should only react when dragging grouped tabs (for moving to different group).
 */
fun Modifier.groupHeaderFeedback(
    groupId: String,
    coordinator: DragCoordinator,
    hoverState: TabSheetHoverState,
    draggedScale: Float = 0.95f
): Modifier = composed {
    if (hoverState.shouldShowGroupHeaderHover(groupId)) {
        this.dragVisualFeedback(groupId, coordinator, draggedScale = draggedScale)
    } else {
        this
    }
}

/**
 * Applies visual feedback to a grouped tab based on drag context.
 * - When dragging ungrouped tab: uses group hover state (entire group enlarges)
 * - When dragging grouped tab: uses individual tab hover state (for reordering)
 */
fun Modifier.groupedTabFeedback(
    tabId: String,
    groupId: String,
    coordinator: DragCoordinator,
    hoverState: TabSheetHoverState,
    hoveredGroupId: String?,
    draggedScale: Float = 0.95f
): Modifier = composed {
    val shouldShowHover = hoverState.shouldShowGroupedTabHover(tabId, groupId, hoveredGroupId)
    this.dragVisualFeedback(
        itemId = tabId,
        coordinator = coordinator,
        isDropTarget = shouldShowHover,
        draggedScale = draggedScale
    )
}

/**
 * Applies visual feedback to an ungrouped tab.
 * Ungrouped tabs should always react when hovered (can group with them or reorder).
 */
fun Modifier.ungroupedTabFeedback(
    tabId: String,
    coordinator: DragCoordinator,
    draggedScale: Float = 0.95f
): Modifier = composed {
    this.dragVisualFeedback(tabId, coordinator, draggedScale = draggedScale)
}

/**
 * Applies visual feedback to tabs within a group row (grid view).
 * - When dragging ungrouped tab: no individual feedback (group row handles it)
 * - When dragging grouped tab: show individual feedback
 */
fun Modifier.groupRowTabFeedback(
    tabId: String,
    coordinator: DragCoordinator,
    hoverState: TabSheetHoverState,
    draggedScale: Float = 0.95f
): Modifier = composed {
    if (hoverState.shouldShowGroupRowTabHover(tabId)) {
        this.dragVisualFeedback(tabId, coordinator, draggedScale = draggedScale)
    } else {
        this
    }
}
