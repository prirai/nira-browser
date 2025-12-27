package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.runtime.*

/**
 * Represents what the user is currently dragging over
 */
sealed class DragTarget {
    data class Tab(val tabId: String) : DragTarget()
    data class Group(val groupId: String) : DragTarget()
    data class InsertionPoint(val index: Int, val inGroupId: String? = null) : DragTarget()
    data class GroupHeader(val groupId: String) : DragTarget()
    object None : DragTarget()
}

/**
 * Visual feedback type for drag operations
 */
enum class DragFeedback {
    NONE,
    HIGHLIGHT_TAB,      // Hovering over tab to create group
    HIGHLIGHT_GROUP,    // Hovering over group to add
    INSERTION_LINE,     // Show line for reordering
    SCALE_SPACE         // Scale adjacent items to make space
}

/**
 * State for drag & drop operations across all views
 */
@Stable
class TabDragDropState {
    var draggedTabId by mutableStateOf<String?>(null)
        private set
    
    var draggedFromGroupId by mutableStateOf<String?>(null)
        private set
    
    var currentTarget by mutableStateOf<DragTarget>(DragTarget.None)
        private set
    
    var feedbackType by mutableStateOf(DragFeedback.NONE)
        private set
    
    /**
     * Start dragging a tab
     */
    fun startDrag(tabId: String, fromGroupId: String?) {
        draggedTabId = tabId
        draggedFromGroupId = fromGroupId
    }
    
    /**
     * Update drag target and feedback
     */
    fun updateTarget(target: DragTarget, feedback: DragFeedback) {
        currentTarget = target
        feedbackType = feedback
    }
    
    /**
     * End drag operation
     */
    fun endDrag() {
        draggedTabId = null
        draggedFromGroupId = null
        currentTarget = DragTarget.None
        feedbackType = DragFeedback.NONE
    }
    
    /**
     * Check if currently dragging
     */
    fun isDragging() = draggedTabId != null
}

/**
 * Remember drag drop state across recompositions
 */
@Composable
fun rememberTabDragDropState(): TabDragDropState {
    return remember { TabDragDropState() }
}
