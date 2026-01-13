package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.layout.boundsInRoot
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
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import androidx.compose.foundation.background

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
 * Drop zone within a target (for threshold-based detection)
 */
enum class DropZone {
    BEFORE,  // First 15% - insert before
    CENTER,  // Middle 70% - group/merge
    AFTER    // Last 15% - insert after
}

/**
 * Drop target information
 */
data class DropTarget(
    val id: String,
    val bounds: Rect,
    val type: DropTargetType,
    val metadata: Map<String, Any> = emptyMap(),
    val dropZone: DropZone = DropZone.CENTER
)

/**
 * Current drag state
 */
data class UnifiedDragState(
    val draggedItem: DraggableItemType? = null,
    val dragOffset: Offset = Offset.Zero,
    val isDragging: Boolean = false,
    val currentDropTarget: DropTarget? = null,
    val dragStartPosition: Offset = Offset.Zero,
    val insertionIndicatorPosition: Offset? = null,
    val insertionIndicatorType: InsertionIndicatorType? = null
)

/**
 * Type of insertion indicator to show
 */
enum class InsertionIndicatorType {
    HORIZONTAL_LINE,  // For list views
    VERTICAL_LINE,    // For horizontal layouts
    GAP               // For grid views
}

/**
 * Drag coordinator - manages drag state and orchestrates operations
 * Implements two-layer rendering: static layer + floating drag layer
 */
class DragCoordinator(
    private val scope: CoroutineScope,
    private val viewModel: TabViewModel,
    private val orderManager: TabOrderManager,
    private val context: Context? = null
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
    private val _autoScrollVelocity = mutableStateOf(0f)
    val autoScrollVelocity: State<Float> = _autoScrollVelocity

    // Drop operation job
    private var dropOperationJob: Job? = null

    // Last drop target update time (for debouncing)
    private var lastDropTargetUpdate = 0L

    // Haptic feedback
    private val vibrator: Vibrator? = context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Scroll container bounds (for auto-scroll detection)
    private var scrollContainerBounds: Rect? = null

    // Auto-scroll edge threshold (10% of container height, dynamically calculated)
    private fun getAutoScrollThreshold(): Float {
        val bounds = scrollContainerBounds ?: return 0f
        val containerHeight = bounds.bottom - bounds.top
        return containerHeight * 0.1f // 10% of container height
    }

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
     * Set scroll container bounds for auto-scroll detection
     */
    fun setScrollContainerBounds(bounds: Rect) {
        scrollContainerBounds = bounds
    }

    /**
     * Trigger haptic feedback
     */
    private fun performHapticFeedback(intensity: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(10, intensity))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(10)
        }
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

        // Haptic feedback on drag start
        performHapticFeedback()

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

        // Check for auto-scroll
        checkAutoScroll(pointerPosition)

        // Debounce expensive drop target detection (~60 FPS)
        val now = System.currentTimeMillis()
        val target = if (now - lastDropTargetUpdate > 16) {
            lastDropTargetUpdate = now
            val draggedItemId = getDraggedItemId(current.draggedItem)
            findDropTargetAt(pointerPosition, draggedItemId)
        } else {
            current.currentDropTarget
        }

        // Trigger haptic feedback when hovering over a new target
        if (target != null && target.id != current.currentDropTarget?.id) {
            performHapticFeedback(128) // Medium intensity (range: 1-255)
        }

        // Calculate insertion indicator position
        val (indicatorPos, indicatorType) = calculateInsertionIndicator(target, pointerPosition)

        _dragState.value = current.copy(
            dragOffset = newOffset,
            currentDropTarget = target,
            insertionIndicatorPosition = indicatorPos,
            insertionIndicatorType = indicatorType
        )
    }

    /**
     * Check if we need to auto-scroll based on pointer position
     */
    private fun checkAutoScroll(pointerPosition: Offset) {
        val bounds = scrollContainerBounds ?: return

        val threshold = getAutoScrollThreshold()
        if (threshold == 0f) return // No scroll container set yet

        val distanceFromTop = pointerPosition.y - bounds.top
        val distanceFromBottom = bounds.bottom - pointerPosition.y

        val velocity = when {
            distanceFromTop < threshold && distanceFromTop > 0 -> {
                // Near top - scroll up (negative velocity)
                val intensity = (threshold - distanceFromTop) / threshold
                -intensity * 10f
            }

            distanceFromBottom < threshold && distanceFromBottom > 0 -> {
                // Near bottom - scroll down (positive velocity)
                val intensity = (threshold - distanceFromBottom) / threshold
                intensity * 10f
            }

            else -> 0f
        }

        if (velocity != _autoScrollVelocity.value) {
            _autoScrollVelocity.value = velocity

            if (velocity != 0f && autoScrollJob == null) {
                startAutoScroll()
            } else if (velocity == 0f) {
                stopAutoScroll()
            }
        }
    }

    /**
     * Start auto-scrolling
     */
    private fun startAutoScroll() {
        autoScrollJob = scope.launch {
            while (true) {
                val velocity = _autoScrollVelocity.value
                if (velocity == 0f) break

                // Emit scroll event - this will be handled by the UI layer
                // For now, just delay
                delay(16) // ~60fps
            }
        }
    }

    /**
     * Stop auto-scrolling
     */
    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        _autoScrollVelocity.value = 0f
    }

    /**
     * Calculate insertion indicator position and type
     */
    private fun calculateInsertionIndicator(
        target: DropTarget?,
        pointerPosition: Offset
    ): Pair<Offset?, InsertionIndicatorType?> {
        if (target == null) return Pair(null, null)

        return when (target.dropZone) {
            DropZone.BEFORE -> {
                // Show indicator at the start of target bounds
                val bounds = target.bounds
                val isHorizontal = bounds.width > bounds.height

                if (isHorizontal) {
                    Pair(Offset(bounds.left, bounds.top + bounds.height / 2f), InsertionIndicatorType.VERTICAL_LINE)
                } else {
                    Pair(Offset(bounds.left + bounds.width / 2f, bounds.top), InsertionIndicatorType.HORIZONTAL_LINE)
                }
            }

            DropZone.AFTER -> {
                // Show indicator at the end of target bounds
                val bounds = target.bounds
                val isHorizontal = bounds.width > bounds.height

                if (isHorizontal) {
                    Pair(Offset(bounds.right, bounds.top + bounds.height / 2f), InsertionIndicatorType.VERTICAL_LINE)
                } else {
                    Pair(Offset(bounds.left + bounds.width / 2f, bounds.bottom), InsertionIndicatorType.HORIZONTAL_LINE)
                }
            }

            DropZone.CENTER -> {
                // No insertion indicator for group/merge operations
                Pair(null, null)
            }
        }
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
            // Haptic feedback on successful drop
            performHapticFeedback()

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
        stopAutoScroll()
    }

    /**
     * Cancel drag without performing drop
     */
    fun cancelDrag() {
        android.util.Log.d("DragCoordinator", "Cancel drag")
        _dragState.value = UnifiedDragState()
        _draggedItemBounds.value = null
        stopAutoScroll()
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
     * Find drop target at position with priority ordering and zone detection
     * Implements 15-15-70 threshold: first 15% = BEFORE, middle 70% = CENTER, last 15% = AFTER
     */
    private fun findDropTargetAt(position: Offset, excludeId: String?): DropTarget? {
        // Find all targets that contain the position
        val candidateTargets = dropTargets.values.filter { target ->
            target.id != excludeId && target.bounds.contains(position)
        }

        // Check if any ROOT_POSITION (divider) target contains the position
        // If so, use it exclusively to prevent GROUP_HEADER/TAB from capturing the drop
        val dividerTarget = candidateTargets.firstOrNull { it.type == DropTargetType.ROOT_POSITION }
        if (dividerTarget != null) {
            android.util.Log.d("DragCoordinator", "Divider target found: ${dividerTarget.id}, excluding other targets")
            return dividerTarget
        }

        // Priority: ROOT_POSITION > TAB > GROUP_BODY > GROUP_HEADER > EMPTY_SPACE
        // ROOT_POSITION (dividers) get highest priority for precise reordering
        val bestTarget = candidateTargets.maxByOrNull { target ->
            when (target.type) {
                DropTargetType.ROOT_POSITION -> 6
                DropTargetType.TAB -> 5
                DropTargetType.GROUP_BODY -> 4
                DropTargetType.GROUP_HEADER -> 3
                DropTargetType.EMPTY_SPACE -> 1
            }
        } ?: return null

        // For TAB targets, calculate drop zone based on position within bounds
        if (bestTarget.type == DropTargetType.TAB) {
            val zone = calculateDropZone(position, bestTarget.bounds)
            return bestTarget.copy(dropZone = zone)
        }

        return bestTarget
    }

    /**
     * Calculate drop zone within bounds using 15-15-70 threshold
     * BEFORE: 0-15%, CENTER: 15-85%, AFTER: 85-100%
     */
    private fun calculateDropZone(position: Offset, bounds: Rect): DropZone {
        // Determine if horizontal or vertical layout based on aspect ratio
        val isHorizontal = bounds.width > bounds.height

        if (isHorizontal) {
            // Horizontal layout (TabBar) - use X position
            val relativeX = position.x - bounds.left
            val width = bounds.width
            val threshold = relativeX / width

            return when {
                threshold < 0.15f -> DropZone.BEFORE
                threshold > 0.85f -> DropZone.AFTER
                else -> DropZone.CENTER
            }
        } else {
            // Vertical layout (List/Grid) - use Y position
            val relativeY = position.y - bounds.top
            val height = bounds.height
            val threshold = relativeY / height

            return when {
                threshold < 0.15f -> DropZone.BEFORE
                threshold > 0.85f -> DropZone.AFTER
                else -> DropZone.CENTER
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
                // Tab → Tab: Add to group if target is grouped, otherwise create new group (CENTER) or reorder (BEFORE/AFTER)
                val targetTabId = target.metadata["tabId"] as? String ?: return
                val targetGroupId = target.metadata["groupId"] as? String

                if (targetTabId != tab.tabId) {
                    val tabs = viewModel.tabs.value
                    val draggedTab = tabs.find { it.id == tab.tabId }
                    val targetTab = tabs.find { it.id == targetTabId }

                    if (draggedTab != null && targetTab != null) {
                        // Check context compatibility
                        if (draggedTab.contextId == targetTab.contextId) {
                            // If target tab is in a group, add dragged tab to that group
                            if (targetGroupId != null) {
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "Adding tab ${tab.tabId} to existing group $targetGroupId"
                                )

                                // Remove from old group if needed
                                if (tab.groupId != null && tab.groupId != targetGroupId) {
                                    viewModel.removeTabFromGroup(tab.tabId)
                                    delay(50)
                                }

                                // Add to target group
                                viewModel.addTabToGroup(tab.tabId, targetGroupId)
                                return
                            }

                            when (target.dropZone) {
                                DropZone.CENTER -> {
                                    // Middle 70% - Create new group (both tabs are ungrouped)
                                    android.util.Log.d(
                                        "DragCoordinator",
                                        "CENTER zone: Creating new group: [${tab.tabId}, $targetTabId]"
                                    )

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
                                }

                                DropZone.BEFORE -> {
                                    // First 15% - Insert before target
                                    android.util.Log.d(
                                        "DragCoordinator",
                                        "BEFORE zone: Reordering ${tab.tabId} before $targetTabId"
                                    )

                                    // If tab is in a group, remove it first
                                    if (tab.groupId != null) {
                                        viewModel.removeTabFromGroup(tab.tabId)
                                        delay(50)
                                    }

                                    // Get target position and insert before
                                    val currentOrder = viewModel.currentOrder.value
                                    val targetPosition = currentOrder?.primaryOrder?.indexOfFirst { item ->
                                        when (item) {
                                            is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId == targetTabId
                                            is UnifiedTabOrder.OrderItem.TabGroup -> item.tabIds.contains(targetTabId)
                                        }
                                    } ?: -1

                                    if (targetPosition >= 0) {
                                        viewModel.moveTabToPosition(tab.tabId, targetPosition)
                                    }
                                }

                                DropZone.AFTER -> {
                                    // Last 15% - Insert after target
                                    android.util.Log.d(
                                        "DragCoordinator",
                                        "AFTER zone: Reordering ${tab.tabId} after $targetTabId"
                                    )

                                    // If tab is in a group, remove it first
                                    if (tab.groupId != null) {
                                        viewModel.removeTabFromGroup(tab.tabId)
                                        delay(50)
                                    }

                                    // Get target position and insert after
                                    val currentOrder = viewModel.currentOrder.value
                                    val targetPosition = currentOrder?.primaryOrder?.indexOfFirst { item ->
                                        when (item) {
                                            is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId == targetTabId
                                            is UnifiedTabOrder.OrderItem.TabGroup -> item.tabIds.contains(targetTabId)
                                        }
                                    } ?: -1

                                    if (targetPosition >= 0) {
                                        viewModel.moveTabToPosition(tab.tabId, targetPosition + 1)
                                    }
                                }
                            }
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
                // Tab → Group Body: Reorder within group (using zones)
                val groupId = target.metadata["groupId"] as? String ?: return
                val targetTabId = target.metadata["targetTabId"] as? String ?: return

                if (tab.groupId == groupId && tab.tabId != targetTabId) {
                    when (target.dropZone) {
                        DropZone.BEFORE -> {
                            android.util.Log.d(
                                "DragCoordinator",
                                "Reordering tab ${tab.tabId} BEFORE $targetTabId in group $groupId"
                            )
                            viewModel.reorderTabInGroup(tab.tabId, targetTabId, groupId, insertAfter = false)
                        }

                        DropZone.AFTER -> {
                            android.util.Log.d(
                                "DragCoordinator",
                                "Reordering tab ${tab.tabId} AFTER $targetTabId in group $groupId"
                            )
                            viewModel.reorderTabInGroup(tab.tabId, targetTabId, groupId, insertAfter = true)
                        }

                        DropZone.CENTER -> {
                            // Center zone in group body - default to after
                            android.util.Log.d(
                                "DragCoordinator",
                                "Reordering tab ${tab.tabId} near $targetTabId in group $groupId"
                            )
                            viewModel.reorderTabInGroup(tab.tabId, targetTabId, groupId, insertAfter = true)
                        }
                    }
                }
            }

            DropTargetType.ROOT_POSITION -> {
                // Tab → Root Position: Reorder at root level
                val position = target.metadata["position"] as? Int ?: return

                android.util.Log.d("DragCoordinator", "Moving tab ${tab.tabId} to position $position")

                // If tab is in a group, ungroup it first
                if (tab.groupId != null) {
                    android.util.Log.d(
                        "DragCoordinator",
                        "Removing tab ${tab.tabId} from group ${tab.groupId} before moving to position $position"
                    )
                    viewModel.removeTabFromGroup(tab.tabId)
                    delay(100)
                }

                // Move to target position
                android.util.Log.d("DragCoordinator", "Moving tab ${tab.tabId} to root position $position")
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
    orderManager: TabOrderManager,
    context: Context = androidx.compose.ui.platform.LocalContext.current
): DragCoordinator {
    val coordinator = remember(scope, viewModel, orderManager, context) {
        DragCoordinator(scope, viewModel, orderManager, context)
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
                .wrapContentSize(Alignment.TopStart, unbounded = true)
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
 * Insertion indicator composable - shows where item will be inserted
 */
@Composable
fun InsertionIndicator(
    coordinator: DragCoordinator,
    modifier: Modifier = Modifier
) {
    val dragState by coordinator.dragState
    val indicatorPosition = dragState.insertionIndicatorPosition
    val indicatorType = dragState.insertionIndicatorType

    if (indicatorPosition != null && indicatorType != null && dragState.isDragging) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(999f) // Just below drag layer
        ) {
            when (indicatorType) {
                InsertionIndicatorType.HORIZONTAL_LINE -> {
                    // Horizontal line for vertical lists
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = indicatorPosition.x - 100f
                                translationY = indicatorPosition.y
                            }
                            .width(200.dp)
                            .height(3.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(1.5.dp)
                            )
                    )
                }

                InsertionIndicatorType.VERTICAL_LINE -> {
                    // Vertical line for horizontal layouts
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = indicatorPosition.x
                                translationY = indicatorPosition.y - 50f
                            }
                            .width(3.dp)
                            .height(100.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(1.5.dp)
                            )
                    )
                }

                InsertionIndicatorType.GAP -> {
                    // Gap indicator for grid layouts
                    // This would require more complex layout changes
                    // For now, use a vertical line
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = indicatorPosition.x
                                translationY = indicatorPosition.y - 50f
                            }
                            .width(3.dp)
                            .height(100.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(1.5.dp)
                            )
                    )
                }
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
