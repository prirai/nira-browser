package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Simplified finger-tracking drag system
 */

sealed class DragOperation {
    object None : DragOperation()
    data class Reorder(val targetIndex: Int) : DragOperation()
}

/**
 * State holder with finger-based tracking
 */
@Stable
class AdvancedDragDropState {
    // Drag state
    var isDragging by mutableStateOf(false)
        private set
    
    var draggedItemId: String? by mutableStateOf(null)
        private set
    
    var fingerPosition by mutableStateOf(Offset.Zero)
        private set
    
    private var initialDragOffset = Offset.Zero
    
    // Item registry
    private val items = mutableStateMapOf<String, ItemInfo>()
    
    // Visual state per item
    private val visualStates = mutableStateMapOf<String, VisualState>()
    
    // Hover state
    var hoveredItemId: String? by mutableStateOf(null)
        private set
    
    var feedbackState by mutableStateOf(DragFeedbackState())
        private set
    
    companion object {
        // No constants needed
    }
    
    data class ItemInfo(
        val id: String,
        val position: Offset,
        val size: IntSize,
        val index: Int
    )
    
    data class VisualState(
        var offset: Float = 0f,
        var scale: Float = 1f
    )
    
    fun registerItem(
        id: String,
        position: Offset,
        size: IntSize,
        index: Int
    ) {
        items[id] = ItemInfo(id, position, size, index)
        if (!visualStates.containsKey(id)) {
            visualStates[id] = VisualState()
        }
    }
    
    fun getTargetOffset(id: String): Float = visualStates[id]?.offset ?: 0f
    fun getTargetScale(id: String): Float = visualStates[id]?.scale ?: 1f
    fun isHoverTarget(id: String): Boolean = hoveredItemId == id
    
    fun getDraggedItemOffset(): Float {
        val draggedId = draggedItemId ?: return 0f
        
        // Calculate offset: finger position - initial position
        return fingerPosition.y - initialDragOffset.y
    }
    
    fun startDrag(id: String, initialPosition: Offset) {
        isDragging = true
        draggedItemId = id
        fingerPosition = initialPosition
        initialDragOffset = initialPosition
        
        // Reset states
        visualStates.values.forEach {
            it.offset = 0f
            it.scale = 1f
        }
        visualStates[id]?.scale = 0.98f // Subtle scale down
    }
    
    suspend fun updateDrag(newPosition: Offset) {
        if (!isDragging) return
        
        fingerPosition = newPosition
        val draggedItem = items[draggedItemId] ?: return
        
        // Find closest item to finger
        val (targetId, operation) = findTarget(fingerPosition, draggedItem)
        hoveredItemId = targetId
        
        // Update visuals
        updateVisuals(operation, draggedItem)
        
        // Update feedback
        feedbackState = when (operation) {
            is DragOperation.Reorder -> DragFeedbackState(operation, 1f, false)
            else -> DragFeedbackState()
        }
    }
    
    private fun findTarget(fingerPos: Offset, draggedItem: ItemInfo): Pair<String?, DragOperation> {
        // Find items before and after finger position for insertion
        val sortedItems = items.values
            .filter { it.id != draggedItemId }
            .sortedBy { it.index }
        
        if (sortedItems.isEmpty()) {
            return Pair(null, DragOperation.None)
        }
        
        // Find the item whose center is closest to the finger
        var closestItem: ItemInfo? = null
        var minDistance = Float.MAX_VALUE
        
        sortedItems.forEach { item ->
            val itemVisualY = item.position.y + (visualStates[item.id]?.offset ?: 0f)
            val itemCenterY = itemVisualY + (item.size.height / 2f)
            
            val distance = abs(fingerPos.y - itemCenterY)
            
            if (distance < minDistance) {
                minDistance = distance
                closestItem = item
            }
        }
        
        val target = closestItem ?: return Pair(null, DragOperation.None)
        
        // Calculate if we're above or below the target center
        val itemVisualY = target.position.y + (visualStates[target.id]?.offset ?: 0f)
        val itemCenterY = itemVisualY + (target.size.height / 2f)
        val isAboveCenter = fingerPos.y < itemCenterY
        
        // Determine insertion index based on position relative to target
        val insertionIndex = if (isAboveCenter) {
            target.index
        } else {
            target.index + 1
        }
        
        // Reordering logic - show insertion point
        val operation = DragOperation.Reorder(insertionIndex)
        
        return Pair(target.id, operation)
    }
    
    private fun updateVisuals(operation: DragOperation, draggedItem: ItemInfo) {
        // Reset all offsets
        visualStates.values.forEach { it.offset = 0f; it.scale = 1f }
        
        when (operation) {
            is DragOperation.Reorder -> {
                // Create visual gap at insertion point
                val insertionIndex = operation.targetIndex
                val gapSize = draggedItem.size.height + 8f // Item height + padding
                
                // Move items down to create gap
                items.values.forEach { item ->
                    if (item.id == draggedItemId) return@forEach
                    
                    val shouldMoveDown = if (draggedItem.index < insertionIndex) {
                        // Dragging down: move items between [dragged+1, insertion-1] up
                        item.index in (draggedItem.index + 1) until insertionIndex
                    } else {
                        // Dragging up: move items between [insertion, dragged-1] down
                        item.index in insertionIndex until draggedItem.index
                    }
                    
                    if (shouldMoveDown) {
                        visualStates[item.id]?.offset = if (draggedItem.index < insertionIndex) {
                            -gapSize // Move up when dragging down
                        } else {
                            gapSize // Move down when dragging up
                        }
                    }
                }
                
                draggedItemId?.let {
                    visualStates[it]?.scale = 0.98f
                }
            }
            
            else -> {
                draggedItemId?.let {
                    visualStates[it]?.scale = 0.98f
                }
            }
        }
    }
    
    fun endDrag(): Pair<DragOperation, String?> {
        val operation = feedbackState.operation
        val draggedId = draggedItemId
        
        isDragging = false
        draggedItemId = null
        fingerPosition = Offset.Zero
        initialDragOffset = Offset.Zero
        hoveredItemId = null
        feedbackState = DragFeedbackState()
        
        visualStates.values.forEach {
            it.offset = 0f
            it.scale = 1f
        }
        
        return Pair(operation, draggedId)
    }
    
    fun cancelDrag() {
        isDragging = false
        draggedItemId = null
        fingerPosition = Offset.Zero
        initialDragOffset = Offset.Zero
        hoveredItemId = null
        feedbackState = DragFeedbackState()
        
        visualStates.values.forEach {
            it.offset = 0f
            it.scale = 1f
        }
    }
}

data class DragFeedbackState(
    val operation: DragOperation = DragOperation.None,
    val targetScale: Float = 1f,
    val showGroupingHint: Boolean = false,
    val insertionLinePosition: Float? = null
)

@Composable
fun Modifier.advancedDraggable(
    id: String,
    index: Int,
    dragDropState: AdvancedDragDropState,
    enabled: Boolean = true,
    onDragEnd: (DragOperation) -> Unit = {}
): Modifier {
    var itemGlobalPosition by remember { mutableStateOf(Offset.Zero) }
    
    return this
        .onGloballyPositioned { coordinates ->
            itemGlobalPosition = coordinates.positionInRoot()
            dragDropState.registerItem(
                id = id,
                position = itemGlobalPosition,
                size = coordinates.size,
                index = index
            )
        }
        .then(
            if (enabled) {
                Modifier.pointerInput(id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            // Calculate global position of touch point
                            val touchPositionGlobal = Offset(
                                itemGlobalPosition.x + offset.x,
                                itemGlobalPosition.y + offset.y
                            )
                            dragDropState.startDrag(id, touchPositionGlobal)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
                            scope.launch {
                                val newPos = dragDropState.fingerPosition + dragAmount
                                dragDropState.updateDrag(newPos)
                            }
                        },
                        onDragEnd = {
                            val (operation, _) = dragDropState.endDrag()
                            onDragEnd(operation)
                        },
                        onDragCancel = {
                            dragDropState.cancelDrag()
                        }
                    )
                }
            } else {
                Modifier
            }
        )
}

@Composable
fun Modifier.draggableItem(
    id: String,
    dragDropState: AdvancedDragDropState
): Modifier {
    val offset by animateFloatAsState(
        targetValue = if (dragDropState.isDragging && dragDropState.draggedItemId == id) {
            dragDropState.getDraggedItemOffset()
        } else {
            dragDropState.getTargetOffset(id)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "itemOffset"
    )
    
    val scale by animateFloatAsState(
        targetValue = dragDropState.getTargetScale(id),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "itemScale"
    )
    
    return this.then(
        if (dragDropState.isDragging && dragDropState.draggedItemId == id) {
            Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationY = offset
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.9f
                }
        } else {
            Modifier.graphicsLayer {
                translationY = offset
                scaleX = scale
                scaleY = scale
            }
        }
    )
}

@Composable
fun rememberAdvancedDragDropState(): AdvancedDragDropState {
    return remember { AdvancedDragDropState() }
}
