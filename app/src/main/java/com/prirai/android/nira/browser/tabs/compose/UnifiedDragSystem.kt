package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Custom unified drag-and-drop system for all tab views.
 * Implements Chromium-style two-layer rendering with floating drag overlay.
 */

/**
 * Types of items that can be dragged
 */
sealed class DraggableItemType {
    data class Tab(val tabId: String, val groupId: String? = null) : DraggableItemType()
    data class Group(val groupId: String) : DraggableItemType()
}

/**
 * Types of drop targets
 */
enum class DropTargetType {
    TAB,           // Drop on another tab
    GROUP_HEADER,  // Drop on group header (add to group)
    GROUP_BODY,    // Drop within group body (reorder within group)
    EMPTY_SPACE,   // Drop in empty space (root level)
    ROOT_POSITION  // Specific position at root level
}

/**
 * Drop target information
 */
data class DropTarget(
    val id: String,
    val bounds: Rect,
    val type: DropTargetType,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Current drag state
 */
data class UnifiedDragState(
    val draggedItem: DraggableItemType? = null,
    val dragOffset: Offset = Offset.Zero,
    val isDragging: Boolean = false,
    val currentDropTarget: DropTarget? = null,
    val dragStartPosition: Offset = Offset.Zero
)

/**
 * Drag coordinator - manages drag state and orchestrates operations
 * Implements two-layer rendering: static layer + floating drag layer
 */
class DragCoordinator(
    private val scope: CoroutineScope,
    private val viewModel: TabViewModel,
    private val orderManager: TabOrderManager
) {

    // Current drag state
    private val _dragState = mutableStateOf(UnifiedDragState())
    val dragState: State<UnifiedDragState> = _dragState

    // Drag layer position (absolute screen coordinates for floating item)
    private val _dragLayerOffset = mutableStateOf(Offset.Zero)
    val dragLayerOffset: State<Offset> = _dragLayerOffset

    // Drag layer size
    private val _dragLayerSize = mutableStateOf(IntSize.Zero)
    val dragLayerSize: State<IntSize> = _dragLayerSize

    // Original item bounds
    private val _draggedItemBounds = mutableStateOf<Rect?>(null)
    val draggedItemBounds: State<Rect?> = _draggedItemBounds

    // Drop target registry
    private val dropTargets = mutableStateMapOf<String, DropTarget>()

    // Item positions for hover detection
    private val itemPositions = mutableStateMapOf<String, Offset>()
    private val itemSizes = mutableStateMapOf<String, IntSize>()

    // Auto-scroll state
    private var autoScrollJob: Job? = null

    // Drop operation job
    private var dropOperationJob: Job? = null

    // Last drop target update time (for debouncing)
    private var lastDropTargetUpdate = 0L

    /**
     * Register a drop target zone
     */
    fun registerDropTarget(target: DropTarget) {
        dropTargets[target.id] = target
    }

    /**
     * Unregister a drop target
     */
    fun unregisterDropTarget(id: String) {
        dropTargets.remove(id)
    }

    /**
     * Update item position tracking
     */
    fun updateItemPosition(itemId: String, position: Offset, size: IntSize) {
        itemPositions[itemId] = position
        itemSizes[itemId] = size
    }

    /**
     * Start dragging an item with bounds information
     */
    fun startDrag(item: DraggableItemType, startPosition: Offset, itemBounds: Rect, itemSize: IntSize) {
        _dragState.value = UnifiedDragState(
            draggedItem = item,
            isDragging = true,
            dragStartPosition = startPosition,
            dragOffset = Offset.Zero
        )
        _draggedItemBounds.value = itemBounds
        _dragLayerOffset.value = itemBounds.topLeft
        _dragLayerSize.value = itemSize

        android.util.Log.d("DragCoordinator", "Started drag: item=$item at position=$startPosition")
    }

    /**
     * Update drag position with pointer position
     */
    fun updateDrag(dragAmount: Offset, pointerPosition: Offset) {
        val current = _dragState.value
        if (!current.isDragging) return

        val newOffset = current.dragOffset + dragAmount

        // Update drag layer position to follow pointer (centered on pointer)
        val itemBounds = _draggedItemBounds.value
        if (itemBounds != null) {
            _dragLayerOffset.value = pointerPosition - Offset(
                itemBounds.width / 2f,
                itemBounds.height / 2f
            )
        }

        // Debounce expensive drop target detection (~60 FPS)
        val now = System.currentTimeMillis()
        val target = if (now - lastDropTargetUpdate > 16) {
            lastDropTargetUpdate = now
            val draggedItemId = getDraggedItemId(current.draggedItem)
            findDropTargetAt(pointerPosition, draggedItemId)
        } else {
            current.currentDropTarget
        }

        _dragState.value = current.copy(
            dragOffset = newOffset,
            currentDropTarget = target
        )
    }

    /**
     * End drag and perform drop operation
     */
    fun endDrag() {
        val current = _dragState.value
        val draggedItem = current.draggedItem
        val target = current.currentDropTarget

        android.util.Log.d("DragCoordinator", "End drag: item=$draggedItem, target=$target")

        if (draggedItem != null && target != null) {
            // Cancel any ongoing drop operation
            dropOperationJob?.cancel()

            // Perform drop operation
            dropOperationJob = scope.launch {
                try {
                    performDrop(draggedItem, target)
                } catch (e: Exception) {
                    android.util.Log.e("DragCoordinator", "Drop operation failed", e)
                }
            }
        }

        // Clear drag state
        _dragState.value = UnifiedDragState()
        _draggedItemBounds.value = null
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    /**
     * Cancel drag without performing drop
     */
    fun cancelDrag() {
        android.util.Log.d("DragCoordinator", "Cancel drag")
        _dragState.value = UnifiedDragState()
        _draggedItemBounds.value = null
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    /**
     * Check if a specific item is being dragged
     */
    fun isDragging(itemId: String): Boolean {
        val draggedId = getDraggedItemId(_dragState.value.draggedItem)
        return _dragState.value.isDragging && draggedId == itemId
    }

    /**
     * Check if hovering over a specific target
     */
    fun isHoveringOver(targetId: String): Boolean {
        return _dragState.value.currentDropTarget?.id == targetId
    }

    /**
     * Get current drag offset for an item
     */
    fun getDragOffset(itemId: String): Offset {
        if (isDragging(itemId)) {
            return _dragState.value.dragOffset
        }
        return Offset.Zero
    }

    /**
     * Extract item ID from draggable item type
     */
    private fun getDraggedItemId(item: DraggableItemType?): String? {
        return when (item) {
            is DraggableItemType.Tab -> item.tabId
            is DraggableItemType.Group -> item.groupId
            null -> null
        }
    }

    /**
     * Find drop target at position with priority ordering
     */
    private fun findDropTargetAt(position: Offset, excludeId: String?): DropTarget? {
        // Find all targets that contain the position
        val candidateTargets = dropTargets.values.filter { target ->
            target.id != excludeId && target.bounds.contains(position)
        }

        // Priority: TAB > GROUP_BODY > GROUP_HEADER > ROOT_POSITION > EMPTY_SPACE
        return candidateTargets.maxByOrNull { target ->
            when (target.type) {
                DropTargetType.TAB -> 5
                DropTargetType.GROUP_BODY -> 4
                DropTargetType.GROUP_HEADER -> 3
                DropTargetType.ROOT_POSITION -> 2
                DropTargetType.EMPTY_SPACE -> 1
            }
        }
    }

    /**
     * Perform drop operation based on dragged item and target
     */
    private suspend fun performDrop(item: DraggableItemType, target: DropTarget) {
        android.util.Log.d("DragCoordinator", "Perform drop: item=$item, target=${target.id}, type=${target.type}")

        when (item) {
            is DraggableItemType.Tab -> handleTabDrop(item, target)
            is DraggableItemType.Group -> handleGroupDrop(item, target)
        }
    }

    /**
     * Handle dropping a tab (TabNode interactions)
     */
    private suspend fun handleTabDrop(tab: DraggableItemType.Tab, target: DropTarget) {
        when (target.type) {
            DropTargetType.TAB -> {
                // Tab → Tab: Create new group
                val targetTabId = target.metadata["tabId"] as? String ?: return
                if (targetTabId != tab.tabId) {
                    val tabs = viewModel.tabs.value
                    val draggedTab = tabs.find { it.id == tab.tabId }
                    val targetTab = tabs.find { it.id == targetTabId }

                    if (draggedTab != null && targetTab != null) {
                        // Check context compatibility
                        if (draggedTab.contextId == targetTab.contextId) {
                            android.util.Log.d("DragCoordinator", "Creating group: [${tab.tabId}, $targetTabId]")

                            // If tab is in a group, remove it first
                            if (tab.groupId != null) {
                                viewModel.removeTabFromGroup(tab.tabId)
                                delay(50)
                            }

                            // Create new group with both tabs
                            viewModel.createGroup(
                                listOf(tab.tabId, targetTabId),
                                contextId = draggedTab.contextId ?: targetTab.contextId
                            )
                        } else {
                            android.util.Log.w(
                                "DragCoordinator",
                                "Context mismatch: cannot group tabs from different profiles"
                            )
                        }
                    }
                }
            }

            DropTargetType.GROUP_HEADER -> {
                // Tab → Group: Add to group
                val groupId = target.metadata["groupId"] as? String ?: return
                val groupContextId = target.metadata["contextId"] as? String

                val tabs = viewModel.tabs.value
                val draggedTab = tabs.find { it.id == tab.tabId }

                if (draggedTab != null) {
                    // Check context compatibility
                    if (draggedTab.contextId == groupContextId) {
                        android.util.Log.d("DragCoordinator", "Adding tab ${tab.tabId} to group $groupId")

                        // Remove from old group if needed
                        if (tab.groupId != null && tab.groupId != groupId) {
                            viewModel.removeTabFromGroup(tab.tabId)
                            delay(50)
                        }

                        // Add to target group
                        viewModel.addTabToGroup(tab.tabId, groupId)
                    } else {
                        android.util.Log.w(
                            "DragCoordinator",
                            "Context mismatch: cannot add tab to group from different profile"
                        )
                    }
                }
            }

            DropTargetType.GROUP_BODY -> {
                // Tab → Group Body: Reorder within group
                val groupId = target.metadata["groupId"] as? String ?: return
                val targetTabId = target.metadata["targetTabId"] as? String ?: return

                if (tab.groupId == groupId) {
                    android.util.Log.d("DragCoordinator", "Reordering tab ${tab.tabId} within group $groupId")
                    viewModel.reorderTabInGroup(tab.tabId, targetTabId, groupId)
                }
            }

            DropTargetType.ROOT_POSITION -> {
                // Tab → Root Position: Reorder at root level
                val position = target.metadata["position"] as? Int ?: return

                android.util.Log.d("DragCoordinator", "Moving tab ${tab.tabId} to position $position")

                // If tab is in a group, ungroup it first
                if (tab.groupId != null) {
                    viewModel.removeTabFromGroup(tab.tabId)
                    delay(50)
                }

                // Move to target position
                viewModel.moveTabToPosition(tab.tabId, position)
            }

            DropTargetType.EMPTY_SPACE -> {
                // Tab → Empty Space: Ungroup if needed
                if (tab.groupId != null) {
                    android.util.Log.d("DragCoordinator", "Ungrouping tab ${tab.tabId}")
                    viewModel.removeTabFromGroup(tab.tabId)
                }
            }
        }
    }

    /**
     * Handle dropping a group (TabNode interactions)
     */
    private suspend fun handleGroupDrop(group: DraggableItemType.Group, target: DropTarget) {
        when (target.type) {
            DropTargetType.TAB -> {
                // Group → Tab: Add tab to group
                val targetTabId = target.metadata["tabId"] as? String ?: return
                val tabs = viewModel.tabs.value
                val targetTab = tabs.find { it.id == targetTabId }

                if (targetTab != null) {
                    val groups = viewModel.groups.value
                    val draggedGroup = groups.find { it.id == group.groupId }

                    if (draggedGroup != null && draggedGroup.contextId == targetTab.contextId) {
                        android.util.Log.d("DragCoordinator", "Adding tab $targetTabId to group ${group.groupId}")
                        viewModel.addTabToGroup(targetTabId, group.groupId)
                    } else {
                        android.util.Log.w(
                            "DragCoordinator",
                            "Context mismatch: cannot add tab to group from different profile"
                        )
                    }
                }
            }

            DropTargetType.GROUP_HEADER -> {
                // Group → Group: Merge groups
                val targetGroupId = target.metadata["groupId"] as? String ?: return
                val targetContextId = target.metadata["contextId"] as? String

                val groups = viewModel.groups.value
                val draggedGroup = groups.find { it.id == group.groupId }

                if (draggedGroup != null && targetGroupId != group.groupId) {
                    // Check context compatibility
                    if (draggedGroup.contextId == targetContextId) {
                        android.util.Log.d("DragCoordinator", "Merging group ${group.groupId} into $targetGroupId")
                        viewModel.mergeGroups(group.groupId, targetGroupId)
                    } else {
                        android.util.Log.w(
                            "DragCoordinator",
                            "Context mismatch: cannot merge groups from different profiles"
                        )
                    }
                }
            }

            DropTargetType.ROOT_POSITION -> {
                // Group → Root Position: Reorder group
                val position = target.metadata["position"] as? Int ?: return
                val currentPosition = viewModel.currentOrder.value?.getItemPosition(group.groupId)

                if (currentPosition != null) {
                    android.util.Log.d(
                        "DragCoordinator",
                        "Reordering group ${group.groupId} from $currentPosition to $position"
                    )
                    orderManager.reorderItem(currentPosition, position)
                }
            }

            else -> {
                // Groups can only be reordered or merged, not nested
                android.util.Log.d("DragCoordinator", "Invalid drop target for group")
            }
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        autoScrollJob?.cancel()
        dropOperationJob?.cancel()
        dropTargets.clear()
        itemPositions.clear()
        itemSizes.clear()
        _dragState.value = UnifiedDragState()
        _draggedItemBounds.value = null
    }
}

/**
 * Composable to remember drag coordinator
 */
@Composable
fun rememberDragCoordinator(
    scope: CoroutineScope = rememberCoroutineScope(),
    viewModel: TabViewModel,
    orderManager: TabOrderManager
): DragCoordinator {
    val coordinator = remember(scope, viewModel, orderManager) {
        DragCoordinator(scope, viewModel, orderManager)
    }

    // Cleanup on dispose
    DisposableEffect(coordinator) {
        onDispose {
            coordinator.cleanup()
        }
    }

    return coordinator
}

/**
 * Modifier for draggable items with proper bounds tracking
 */
fun Modifier.draggableItem(
    itemType: DraggableItemType,
    coordinator: DragCoordinator,
    enabled: Boolean = true
): Modifier = composed {
    var itemBounds by remember { mutableStateOf<Rect?>(null) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    this
        .onGloballyPositioned { coordinates ->
            val id = when (itemType) {
                is DraggableItemType.Tab -> itemType.tabId
                is DraggableItemType.Group -> itemType.groupId
            }

            itemBounds = Rect(
                offset = coordinates.positionInRoot(),
                size = coordinates.size.toSize()
            )
            itemSize = coordinates.size

            coordinator.updateItemPosition(id, coordinates.positionInRoot(), coordinates.size)
        }
        .then(
            if (enabled) {
                val id = when (itemType) {
                    is DraggableItemType.Tab -> itemType.tabId
                    is DraggableItemType.Group -> itemType.groupId
                }

                Modifier.pointerInput(id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            itemBounds?.let { bounds ->
                                // Calculate absolute pointer position
                                val absolutePosition = bounds.topLeft + offset
                                coordinator.startDrag(itemType, absolutePosition, bounds, itemSize)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Calculate absolute pointer position
                            itemBounds?.let { bounds ->
                                val absolutePointer = bounds.topLeft + change.position
                                coordinator.updateDrag(dragAmount, absolutePointer)
                            }
                        },
                        onDragEnd = {
                            coordinator.endDrag()
                        },
                        onDragCancel = {
                            coordinator.cancelDrag()
                        }
                    )
                }
            } else {
                Modifier
            }
        )
}

/**
 * Modifier for drop target zones
 */
fun Modifier.dropTarget(
    id: String,
    type: DropTargetType,
    coordinator: DragCoordinator,
    metadata: Map<String, Any> = emptyMap()
): Modifier = this.onGloballyPositioned { coordinates ->
    val bounds = Rect(
        offset = coordinates.positionInRoot(),
        size = coordinates.size.toSize()
    )
    coordinator.registerDropTarget(
        DropTarget(
            id = id,
            bounds = bounds,
            type = type,
            metadata = metadata
        )
    )
}

/**
 * Modifier for visual drag feedback with proper alpha and scale
 */
fun Modifier.dragVisualFeedback(
    itemId: String,
    coordinator: DragCoordinator,
    isDragging: Boolean = coordinator.isDragging(itemId),
    isDropTarget: Boolean = coordinator.isHoveringOver(itemId)
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            // When dragging, hide original (it will be shown in drag layer)
            launch {
                scale.animateTo(
                    0.95f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                )
            }
            launch {
                alpha.animateTo(
                    0.3f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                )
            }
        } else {
            launch {
                scale.animateTo(
                    1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                )
            }
            launch {
                alpha.animateTo(
                    1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                )
            }
        }
    }

    val targetBorderAlpha = remember { Animatable(0f) }

    LaunchedEffect(isDropTarget) {
        targetBorderAlpha.animateTo(
            if (isDropTarget) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessHigh)
        )
    }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        }
        .then(
            if (targetBorderAlpha.value > 0f) {
                Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = targetBorderAlpha.value),
                    shape = RoundedCornerShape(16.dp)
                )
            } else {
                Modifier
            }
        )
}

/**
 * Drag layer composable - renders floating item that follows pointer
 * This is the key to Chromium-style drag behavior
 */
@Composable
fun DragLayer(
    coordinator: DragCoordinator,
    modifier: Modifier = Modifier,
    content: @Composable (DraggableItemType) -> Unit
) {
    val dragState by coordinator.dragState
    val dragLayerOffset by coordinator.dragLayerOffset
    val dragLayerSize by coordinator.dragLayerSize

    if (dragState.isDragging && dragState.draggedItem != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(1000f) // Above everything
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = dragLayerOffset.x
                        translationY = dragLayerOffset.y
                        shadowElevation = 8.dp.toPx()
                        scaleX = 1.05f
                        scaleY = 1.05f
                    }
            ) {
                content(dragState.draggedItem!!)
            }
        }
    }
}

/**
 * Reorderable list state for backwards compatibility
 * (Simplified version that works with our custom system)
 */
@Composable
fun rememberReorderableListState(
    coordinator: DragCoordinator,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): ReorderableListState {
    return remember(coordinator, onMove) {
        ReorderableListState(coordinator, onMove)
    }
}

class ReorderableListState(
    val coordinator: DragCoordinator,
    val onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    // This is a simplified adapter to maintain some compatibility
    // Most functionality is handled by DragCoordinator
}
