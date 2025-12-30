package com.prirai.android.nira.browser.tabs.modern

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Simple drag and drop state for tabs
 */
class DragDropState {
    var draggingItemId by mutableStateOf<String?>(null)
        private set
    
    var draggingOffset by mutableStateOf(Offset.Zero)
        private set
    
    var dropTargetId by mutableStateOf<String?>(null)
        private set
    
    fun startDrag(itemId: String) {
        draggingItemId = itemId
    }
    
    fun updateOffset(offset: Offset) {
        draggingOffset = offset
    }
    
    fun setDropTarget(targetId: String?) {
        dropTargetId = targetId
    }
    
    fun endDrag() {
        draggingItemId = null
        draggingOffset = Offset.Zero
        dropTargetId = null
    }
    
    fun isDragging(itemId: String) = draggingItemId == itemId
    fun isDropTarget(itemId: String) = dropTargetId == itemId
}

@Composable
fun rememberDragDropState(): DragDropState {
    return remember { DragDropState() }
}

/**
 * Modifier for draggable items
 */
fun Modifier.draggableTab(
    itemId: String,
    dragDropState: DragDropState,
    onDragStart: () -> Unit = {},
    onDragEnd: (targetId: String?) -> Unit = {}
): Modifier = this.then(
    if (dragDropState.isDragging(itemId)) {
        Modifier
            .graphicsLayer {
                alpha = 0.8f
                scaleX = 1.05f
                scaleY = 1.05f
            }
            .offset {
                IntOffset(
                    dragDropState.draggingOffset.x.roundToInt(),
                    dragDropState.draggingOffset.y.roundToInt()
                )
            }
    } else {
        Modifier
    }
).pointerInput(itemId) {
    detectDragGesturesAfterLongPress(
        onDragStart = {
            dragDropState.startDrag(itemId)
            onDragStart()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            dragDropState.updateOffset(dragDropState.draggingOffset + dragAmount)
        },
        onDragEnd = {
            val target = dragDropState.dropTargetId
            dragDropState.endDrag()
            onDragEnd(target)
        },
        onDragCancel = {
            dragDropState.endDrag()
        }
    )
}

/**
 * Modifier for drop targets
 */
fun Modifier.dropTarget(
    itemId: String,
    dragDropState: DragDropState,
    enabled: Boolean = true
): Modifier = this.then(
    if (enabled && dragDropState.isDropTarget(itemId)) {
        Modifier.graphicsLayer {
            alpha = 0.6f
        }
    } else {
        Modifier
    }
)
