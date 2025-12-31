package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Custom drag system for tabs that supports:
 * - Reordering tabs
 * - Dragging tab over another to group
 * - Dragging grouped tab to another group
 * - Dragging grouped tab out to ungroup
 * - Visual hover feedback
 */

sealed class DragTarget {
    data class Tab(val tabId: String) : DragTarget()
    object ReorderTarget : DragTarget()
}

sealed class DragFeedback {
    object None : DragFeedback()
    object Reorder : DragFeedback()
}

data class DragState(
    val isDragging: Boolean = false,
    val draggedItemId: String? = null,
    val dragOffset: Offset = Offset.Zero,
    val hoveredItemId: String? = null
)

class TabDragDropState {
    var dragState by mutableStateOf(DragState())
        private set
    
    private val itemPositions = mutableMapOf<String, Offset>()
    private val itemSizes = mutableMapOf<String, Offset>()
    
    // Compatibility properties for TabBarCompose
    val draggedTabId: String? get() = dragState.draggedItemId
    var currentTarget by mutableStateOf<DragTarget?>(null)
        private set
    
    fun updateItemPosition(itemId: String, position: Offset, size: Offset) {
        itemPositions[itemId] = position
        itemSizes[itemId] = size
    }
    
    fun startDrag(itemId: String) {
        dragState = dragState.copy(
            isDragging = true,
            draggedItemId = itemId,
            dragOffset = Offset.Zero
        )
    }
    
    fun updateDrag(offset: Offset) {
        if (!dragState.isDragging) return
        
        dragState = dragState.copy(dragOffset = dragState.dragOffset + offset)
        
        // Calculate which item we're hovering over
        val draggedItemPos = itemPositions[dragState.draggedItemId] ?: return
        val currentPos = draggedItemPos + dragState.dragOffset
        
        val hoveredItem = itemPositions.entries.find { (id, pos) ->
            if (id == dragState.draggedItemId) return@find false
            val size = itemSizes[id] ?: return@find false
            currentPos.x >= pos.x && 
            currentPos.x <= pos.x + size.x &&
            currentPos.y >= pos.y && 
            currentPos.y <= pos.y + size.y
        }
        
        dragState = dragState.copy(hoveredItemId = hoveredItem?.key)
        
        // Update target for compatibility
        currentTarget = hoveredItem?.key?.let { DragTarget.Tab(it) }
    }
    
    fun endDrag() {
        dragState = DragState()
        currentTarget = null
    }
    
    fun isDragging(itemId: String) = dragState.isDragging && dragState.draggedItemId == itemId
    fun isHovered(itemId: String) = dragState.hoveredItemId == itemId
    
    fun getFeedback(): DragFeedback {
        val target = currentTarget ?: return DragFeedback.None
        val draggedId = draggedTabId ?: return DragFeedback.None
        
        return when (target) {
            is DragTarget.Tab -> {
                if (target.tabId != draggedId) DragFeedback.Reorder
                else DragFeedback.None
            }
            else -> DragFeedback.None
        }
    }
}

@Composable
fun rememberTabDragDropState(): TabDragDropState {
    return remember { TabDragDropState() }
}

/**
 * Modifier for draggable tab items
 */
fun Modifier.draggableTab(
    uiItemId: String,
    logicalId: String,
    dragDropState: TabDragDropState,
    onDragEnd: (draggedId: String, hoveredId: String?) -> Unit
): Modifier = this
    .onGloballyPositioned { coordinates ->
        dragDropState.updateItemPosition(
            uiItemId,
            coordinates.positionInRoot(),
            Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        )
    }
    .pointerInput(uiItemId) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                dragDropState.startDrag(uiItemId)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropState.updateDrag(dragAmount)
            },
            onDragEnd = {
                val dragState = dragDropState.dragState
                if (dragState.isDragging && dragState.draggedItemId == uiItemId) {
                    // Map hovered UI id back to logical id
                    val hoveredUi = dragState.hoveredItemId
                    val hoveredLogical = hoveredUi?.let { uiKey ->
                        if (uiKey.startsWith("tab_")) uiKey.substringAfter("tab_") else uiKey
                    }
                    onDragEnd(logicalId, hoveredLogical)
                }
                dragDropState.endDrag()
            },
            onDragCancel = {
                dragDropState.endDrag()
            }
        )
    }
    .let { modifier ->
        if (dragDropState.isDragging(uiItemId)) {
            modifier
                .zIndex(1f)
                .offset {
                    IntOffset(
                        dragDropState.dragState.dragOffset.x.roundToInt(),
                        dragDropState.dragState.dragOffset.y.roundToInt()
                    )
                }
        } else {
            modifier
        }
    }
