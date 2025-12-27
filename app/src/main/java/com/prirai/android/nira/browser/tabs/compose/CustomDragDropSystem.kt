package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

data class DragDropItem(
    val key: String,
    val type: ItemType,
    val groupId: String? = null
) {
    enum class ItemType {
        TAB,
        GROUP_HEADER,
        GROUPED_TAB
    }
}

class DragDropState {
    var draggedItem by mutableStateOf<DragDropItem?>(null)
        private set
    
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    
    var draggedItemInitialOffset by mutableStateOf(Offset.Zero)
        private set
    
    var hoveredItem by mutableStateOf<String?>(null)
        private set
    
    var dropTargetIndex by mutableStateOf<Int?>(null)
        private set

    var canDrop by mutableStateOf(false)
        private set

    fun startDrag(item: DragDropItem, initialOffset: Offset) {
        draggedItem = item
        draggedItemInitialOffset = initialOffset
        dragOffset = Offset.Zero
    }

    fun updateDrag(offset: Offset) {
        dragOffset = offset
    }

    fun endDrag() {
        draggedItem = null
        dragOffset = Offset.Zero
        draggedItemInitialOffset = Offset.Zero
        hoveredItem = null
        dropTargetIndex = null
        canDrop = false
    }

    fun updateHovered(itemKey: String?) {
        hoveredItem = itemKey
    }

    fun updateDropTarget(index: Int?, canDrop: Boolean) {
        dropTargetIndex = index
        this.canDrop = canDrop
    }

    val isDragging: Boolean
        get() = draggedItem != null
}

@Composable
fun rememberDragDropState(): DragDropState {
    return remember { DragDropState() }
}

@Composable
fun DraggableItem(
    key: String,
    dragDropState: DragDropState,
    item: DragDropItem,
    onDragStart: () -> Unit = {},
    onDragEnd: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onGroupTogether: (draggedKey: String, targetKey: String) -> Unit = { _, _ -> },
    onMoveToGroup: (tabKey: String, groupId: String) -> Unit = { _, _ -> },
    onUngroupTab: (tabKey: String, groupId: String) -> Unit = { _, _ -> },
    content: @Composable (isDragging: Boolean, isHovered: Boolean) -> Unit
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val isDragged = dragDropState.draggedItem?.key == key
    val isHovered = dragDropState.hoveredItem == key && !isDragged

    val elevation by animateFloatAsState(
        targetValue = if (isDragged) 8f else if (isHovered) 4f else 0f,
        animationSpec = tween(200)
    )

    val scale by animateFloatAsState(
        targetValue = if (isDragged) 1.05f else if (isHovered) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                itemPosition = coordinates.positionInRoot()
            }
            .pointerInput(key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragDropState.startDrag(item, itemPosition)
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDropState.updateDrag(dragDropState.dragOffset + dragAmount)
                    },
                    onDragEnd = {
                        val draggedItem = dragDropState.draggedItem
                        val hoveredItem = dragDropState.hoveredItem
                        
                        if (draggedItem != null && hoveredItem != null && draggedItem.key != hoveredItem) {
                            // Handle different drag scenarios
                            when {
                                // Dragging ungrouped tab over another ungrouped tab -> group them
                                draggedItem.type == DragDropItem.ItemType.TAB && 
                                draggedItem.groupId == null -> {
                                    onGroupTogether(draggedItem.key, hoveredItem)
                                }
                                
                                // Dragging grouped tab over group header -> move to that group
                                draggedItem.type == DragDropItem.ItemType.GROUPED_TAB &&
                                hoveredItem.startsWith("group_") -> {
                                    val targetGroupId = hoveredItem.removePrefix("group_")
                                    if (draggedItem.groupId != targetGroupId) {
                                        draggedItem.groupId?.let { oldGroupId ->
                                            onUngroupTab(draggedItem.key, oldGroupId)
                                        }
                                        onMoveToGroup(draggedItem.key, targetGroupId)
                                    }
                                }
                                
                                // Dragging grouped tab outside group area -> ungroup
                                draggedItem.type == DragDropItem.ItemType.GROUPED_TAB &&
                                !hoveredItem.startsWith("group_") -> {
                                    draggedItem.groupId?.let { groupId ->
                                        onUngroupTab(draggedItem.key, groupId)
                                    }
                                }
                            }
                        }
                        
                        dragDropState.endDrag()
                    },
                    onDragCancel = {
                        dragDropState.endDrag()
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
                translationX = if (isDragged) dragDropState.dragOffset.x else 0f
                translationY = if (isDragged) dragDropState.dragOffset.y else 0f
            }
            .zIndex(if (isDragged) 1f else 0f)
    ) {
        content(isDragged, isHovered)
    }
}

@Composable
fun DropTarget(
    key: String,
    dragDropState: DragDropState,
    onHoverChange: (Boolean) -> Unit = {},
    content: @Composable (isHovered: Boolean) -> Unit
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val isHovered = dragDropState.hoveredItem == key

    LaunchedEffect(isHovered) {
        onHoverChange(isHovered)
    }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                itemPosition = coordinates.positionInRoot()
                
                // Check if dragged item is over this target
                if (dragDropState.isDragging) {
                    val draggedPosition = dragDropState.draggedItemInitialOffset + dragDropState.dragOffset
                    val size = coordinates.size
                    
                    val isOver = draggedPosition.x >= itemPosition.x &&
                            draggedPosition.x <= itemPosition.x + size.width &&
                            draggedPosition.y >= itemPosition.y &&
                            draggedPosition.y <= itemPosition.y + size.height
                    
                    if (isOver) {
                        dragDropState.updateHovered(key)
                    } else if (dragDropState.hoveredItem == key) {
                        dragDropState.updateHovered(null)
                    }
                }
            }
    ) {
        content(isHovered)
    }
}
