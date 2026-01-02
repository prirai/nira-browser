package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Unified drag-and-drop system for all tab views.
 * Clean architecture from scratch - handles drag gestures, drop targets, and operations.
 */

/**
 * Types of drop targets
 */
enum class DropTargetType {
    TAB,           // Drop on another tab (creates group)
    GROUP,         // Drop on group (adds to group)
    EMPTY_SPACE    // Drop in empty space (moves to root/reorders)
}

/**
 * Drop target information
 */
data class DropTarget(
    val id: String,
    val bounds: Rect,
    val type: DropTargetType
)

/**
 * Current drag state
 */
data class UnifiedDragState(
    val draggedNode: TabNode? = null,
    val fromGroupId: String? = null,
    val dragOffset: Offset = Offset.Zero,
    val isDragging: Boolean = false,
    val currentDropTarget: DropTarget? = null
)

/**
 * Main drag system class
 */
class UnifiedDragSystem(
    private val scope: CoroutineScope,
    private val viewModel: TabViewModel
) {
    
    var dragState by mutableStateOf(UnifiedDragState())
        private set
    
    // Track all drop targets
    private val dropTargets = mutableMapOf<String, DropTarget>()
    
    // Track positions for hover detection
    private val nodePositions = mutableMapOf<String, Offset>()
    private val nodeSizes = mutableMapOf<String, IntSize>()
    
    /**
     * Register a drop target zone
     */
    fun registerDropTarget(id: String, bounds: Rect, type: DropTargetType) {
        dropTargets[id] = DropTarget(id, bounds, type)
    }
    
    /**
     * Unregister a drop target
     */
    fun unregisterDropTarget(id: String) {
        dropTargets.remove(id)
    }
    
    /**
     * Update position tracking for a node
     */
    fun updateNodePosition(nodeId: String, position: Offset, size: IntSize) {
        nodePositions[nodeId] = position
        nodeSizes[nodeId] = size
    }
    
    /**
     * Start dragging
     */
    fun startDrag(node: TabNode, fromGroupId: String?) {
        dragState = UnifiedDragState(
            draggedNode = node,
            fromGroupId = fromGroupId,
            isDragging = true,
            dragOffset = Offset.Zero
        )
    }
    
    /**
     * Update drag position and detect hover targets
     */
    fun updateDrag(dragAmount: Offset) {
        if (!dragState.isDragging) return
        
        val newOffset = dragState.dragOffset + dragAmount
        val draggedPos = nodePositions[dragState.draggedNode?.id] ?: Offset.Zero
        val currentPos = draggedPos + newOffset
        
        // Find drop target under cursor
        val target = findDropTargetAt(currentPos, dragState.draggedNode?.id)
        
        dragState = dragState.copy(
            dragOffset = newOffset,
            currentDropTarget = target
        )
    }
    
    /**
     * End drag and perform drop operation
     */
    fun endDrag() {
        val node = dragState.draggedNode
        val target = dragState.currentDropTarget
        
        if (node != null && target != null) {
            performDrop(node, target)
        }
        
        // Clear drag state
        dragState = UnifiedDragState()
    }
    
    /**
     * Cancel drag without performing drop
     */
    fun cancelDrag() {
        dragState = UnifiedDragState()
    }
    
    /**
     * Check if a specific node is being dragged
     */
    fun isDragging(nodeId: String): Boolean {
        return dragState.isDragging && dragState.draggedNode?.id == nodeId
    }
    
    /**
     * Check if hovering over a specific target
     */
    fun isHoveringOver(targetId: String): Boolean {
        return dragState.currentDropTarget?.id == targetId
    }
    
    /**
     * Find drop target at position
     */
    private fun findDropTargetAt(position: Offset, excludeId: String?): DropTarget? {
        return dropTargets.values.firstOrNull { target ->
            target.id != excludeId && target.bounds.contains(position)
        }
    }
    
    /**
     * Perform drop operation based on dragged node and target
     */
    private fun performDrop(node: TabNode, target: DropTarget) {
        scope.launch {
            when (node) {
                is Tab -> handleTabDrop(node, target)
                is TabGroup -> handleGroupDrop(node, target)
            }
        }
    }
    
    /**
     * Handle dropping a tab
     */
    private suspend fun handleTabDrop(tab: Tab, target: DropTarget) {
        when (target.type) {
            DropTargetType.TAB -> {
                // Drop tab on another tab → create new group
                val targetTabId = target.id.removePrefix("tab_")
                if (targetTabId != tab.id) {
                    viewModel.createGroupWithTabs(listOf(tab.id, targetTabId))
                }
            }
            DropTargetType.GROUP -> {
                // Drop tab on group → add to group
                val groupId = target.id.removePrefix("group_")
                viewModel.addTabToGroup(tab.id, groupId)
            }
            DropTargetType.EMPTY_SPACE -> {
                // Drop in empty space → remove from group (if in one)
                if (dragState.fromGroupId != null) {
                    viewModel.removeTabFromGroup(tab.id)
                }
            }
        }
    }
    
    /**
     * Handle dropping a group
     */
    private suspend fun handleGroupDrop(group: TabGroup, target: DropTarget) {
        // Groups can only be reordered, not nested
        // Reordering is handled by the reorderable library
    }
}

/**
 * Composable to remember drag system instance
 */
@Composable
fun rememberUnifiedDragSystem(
    scope: CoroutineScope,
    viewModel: TabViewModel
): UnifiedDragSystem {
    return remember(scope, viewModel) {
        UnifiedDragSystem(scope, viewModel)
    }
}

/**
 * Modifier extension for draggable items
 */
fun Modifier.draggableItem(
    node: TabNode,
    dragSystem: UnifiedDragSystem,
    fromGroupId: String? = null,
    enabled: Boolean = true
): Modifier = this
    .onGloballyPositioned { coordinates ->
        dragSystem.updateNodePosition(
            node.id,
            coordinates.positionInRoot(),
            coordinates.size
        )
    }
    .then(
        if (enabled) {
            Modifier.pointerInput(node.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragSystem.startDrag(node, fromGroupId)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragSystem.updateDrag(dragAmount)
                    },
                    onDragEnd = {
                        dragSystem.endDrag()
                    },
                    onDragCancel = {
                        dragSystem.cancelDrag()
                    }
                )
            }
        } else Modifier
    )

/**
 * Modifier extension for drop target zones
 */
fun Modifier.dropTarget(
    id: String,
    type: DropTargetType,
    dragSystem: UnifiedDragSystem
): Modifier = this.onGloballyPositioned { coordinates ->
    val size = coordinates.size.toSize()
    val position = coordinates.positionInRoot()
    val bounds = Rect(
        offset = position,
        size = size
    )
    dragSystem.registerDropTarget(id, bounds, type)
}
