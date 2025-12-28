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
    data class GroupWith(val targetId: String, val targetIndex: Int) : DragOperation()
    data class MoveToGroup(val groupId: String) : DragOperation()
    data class UngroupAndReorder(val targetIndex: Int) : DragOperation()
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
        private const val GROUPING_THRESHOLD = 0.4f // Less sensitive
    }
    
    data class ItemInfo(
        val id: String,
        val position: Offset,
        val size: IntSize,
        val index: Int,
        val isGroupHeader: Boolean = false,
        val groupId: String? = null,
        val isInGroup: Boolean = false
    )
    
    data class VisualState(
        var offset: Float = 0f,
        var scale: Float = 1f
    )
    
    fun registerItem(
        id: String,
        position: Offset,
        size: IntSize,
        index: Int,
        isGroupHeader: Boolean = false,
        groupId: String? = null,
        isInGroup: Boolean = false
    ) {
        items[id] = ItemInfo(id, position, size, index, isGroupHeader, groupId, isInGroup)
        if (!visualStates.containsKey(id)) {
            visualStates[id] = VisualState()
        }
    }
    
    fun getTargetOffset(id: String): Float = visualStates[id]?.offset ?: 0f
    fun getTargetScale(id: String): Float = visualStates[id]?.scale ?: 1f
    fun isHoverTarget(id: String): Boolean = hoveredItemId == id
    
    fun getDraggedItemOffset(): Float {
        val draggedId = draggedItemId ?: return 0f
        val draggedItem = items[draggedId] ?: return 0f
        
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
            is DragOperation.GroupWith -> DragFeedbackState(operation, 1.05f, true)
            is DragOperation.Reorder -> DragFeedbackState(operation, 1f, false)
            is DragOperation.MoveToGroup -> DragFeedbackState(operation, 1.05f, false)
            else -> DragFeedbackState()
        }
    }
    
    private fun findTarget(fingerPos: Offset, draggedItem: ItemInfo): Pair<String?, DragOperation> {
        var closestItem: ItemInfo? = null
        var minDistance = Float.MAX_VALUE
        
        items.values.forEach { item ->
            if (item.id == draggedItemId) return@forEach
            
            val itemVisualY = item.position.y + (visualStates[item.id]?.offset ?: 0f)
            val itemCenterY = itemVisualY + (item.size.height / 2f)
            
            // Distance from finger to item center
            val distance = abs(fingerPos.y - itemCenterY)
            
            if (distance < minDistance) {
                minDistance = distance
                closestItem = item
            }
        }
        
        val target = closestItem ?: return Pair(null, DragOperation.None)
        
        // Calculate overlap based on finger being within item bounds
        val itemVisualY = target.position.y + (visualStates[target.id]?.offset ?: 0f)
        val itemTop = itemVisualY
        val itemBottom = itemVisualY + target.size.height
        val itemCenterY = itemTop + (target.size.height / 2f)
        
        // Only activate when finger is within the center 60% of the target
        val centerThreshold = target.size.height * 0.3f // 30% from center
        val distanceFromCenter = abs(fingerPos.y - itemCenterY)
        
        if (distanceFromCenter > centerThreshold) {
            return Pair(null, DragOperation.None)
        }
        
        // Closer to center = more overlap
        val overlapRatio = 1f - (distanceFromCenter / centerThreshold)
        
        // Determine operation
        val operation = when {
            target.isGroupHeader -> {
                if (overlapRatio > 0.3f) DragOperation.MoveToGroup(target.groupId ?: target.id)
                else DragOperation.None
            }
            
            // Grouping when finger is near center (< 30% threshold from center)
            !target.isInGroup && !draggedItem.isInGroup && overlapRatio > 0.5f -> {
                DragOperation.GroupWith(target.id, target.index)
            }
            
            draggedItem.isInGroup && !target.isInGroup && overlapRatio > 0.5f -> {
                DragOperation.GroupWith(target.id, target.index)
            }
            
            // Reordering when finger passes center
            overlapRatio > 0.3f -> {
                if (draggedItem.isInGroup && !target.isInGroup && draggedItem.groupId != null) {
                    DragOperation.UngroupAndReorder(target.index)
                } else if (draggedItem.isInGroup && target.isInGroup && draggedItem.groupId == target.groupId) {
                    DragOperation.Reorder(target.index)
                } else if (!draggedItem.isInGroup && !target.isInGroup) {
                    DragOperation.Reorder(target.index)
                } else {
                    DragOperation.None
                }
            }
            
            else -> DragOperation.None
        }
        
        return Pair(target.id, operation)
    }
    
    private fun updateVisuals(operation: DragOperation, draggedItem: ItemInfo) {
        // Reset all offsets - no displacement animations
        visualStates.values.forEach { it.offset = 0f; it.scale = 1f }
        
        when (operation) {
            is DragOperation.GroupWith -> {
                hoveredItemId?.let {
                    visualStates[it]?.scale = 1.02f // Subtle scale up
                }
                draggedItemId?.let {
                    visualStates[it]?.scale = 0.98f // Subtle scale down
                }
            }
            
            else -> {
                draggedItemId?.let {
                    visualStates[it]?.scale = 0.98f // Subtle scale down
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
    isGroupHeader: Boolean = false,
    groupId: String? = null,
    isInGroup: Boolean = false,
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
                index = index,
                isGroupHeader = isGroupHeader,
                groupId = groupId,
                isInGroup = isInGroup
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
