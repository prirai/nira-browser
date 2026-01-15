package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
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
    val insertionIndicatorType: InsertionIndicatorType? = null,
    val currentDropZone: DropZone? = null  // Track current drop zone for visual feedback
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

    // Drop target registry (synchronized to prevent concurrent modification)
    private val dropTargets = java.util.concurrent.ConcurrentHashMap<String, DropTarget>()

    // Item positions for hover detection (synchronized to prevent race conditions)
    private val itemPositions = java.util.concurrent.ConcurrentHashMap<String, Offset>()
    private val itemSizes = java.util.concurrent.ConcurrentHashMap<String, IntSize>()

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

    // Track if container is scrolling
    private var isScrolling = false

    /**
     * Set scroll state to prevent position updates during scroll
     */
    fun setIsScrolling(scrolling: Boolean) {
        isScrolling = scrolling
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
     * Update item position tracking (skip during drag and scroll to prevent interference)
     */
    fun updateItemPosition(itemId: String, position: Offset, size: IntSize) {
        // Skip updates during drag or scroll to prevent layout interference
        if (!_dragState.value.isDragging && !isScrolling) {
            itemPositions[itemId] = position
            itemSizes[itemId] = size
        }
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

        // Update drag layer position to follow pointer
        // Position the drag visual directly under the finger (not centered, but slightly offset)
        val itemBounds = _draggedItemBounds.value
        if (itemBounds != null) {
            // Keep drag visual under finger with a small vertical offset so user can see it
            _dragLayerOffset.value = pointerPosition - Offset(
                itemBounds.width / 2f,
                itemBounds.height * 0.3f  // 30% from top - keeps item visible under finger
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
            insertionIndicatorType = indicatorType,
            currentDropZone = target?.dropZone
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

        // Priority: TAB (with zones) > GROUP_HEADER (with zones) > ROOT_POSITION (dividers) > GROUP_BODY > EMPTY_SPACE
        // Both TAB and GROUP_HEADER targets support drop zones (BEFORE/CENTER/AFTER) for reordering
        val bestTarget = candidateTargets.maxByOrNull { target ->
            when (target.type) {
                DropTargetType.TAB -> 6
                DropTargetType.GROUP_HEADER -> 5  // Same priority as ROOT_POSITION, support zones for reordering
                DropTargetType.ROOT_POSITION -> 5
                DropTargetType.GROUP_BODY -> 4
                DropTargetType.EMPTY_SPACE -> 1
            }
        } ?: return null

        // For TAB and GROUP_HEADER targets, calculate drop zone based on position within bounds
        if (bestTarget.type == DropTargetType.TAB || bestTarget.type == DropTargetType.GROUP_HEADER) {
            val zone = calculateDropZone(position, bestTarget.bounds)
            android.util.Log.d("DragCoordinator", "${bestTarget.type} target: ${bestTarget.id}, zone: $zone")
            return bestTarget.copy(dropZone = zone)
        }

        // For ROOT_POSITION (dividers), log and return directly
        if (bestTarget.type == DropTargetType.ROOT_POSITION) {
            android.util.Log.d("DragCoordinator", "Divider target found: ${bestTarget.id}")
        }

        return bestTarget
    }

    /**
     * Calculate drop zone within bounds using 25-25-50 threshold for better UX
     * BEFORE: 0-25%, CENTER: 25-75%, AFTER: 75-100%
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
                threshold < 0.25f -> DropZone.BEFORE
                threshold > 0.75f -> DropZone.AFTER
                else -> DropZone.CENTER
            }
        } else {
            // Vertical layout (List/Grid) - use Y position
            val relativeY = position.y - bounds.top
            val height = bounds.height
            val threshold = relativeY / height

            return when {
                threshold < 0.25f -> DropZone.BEFORE
                threshold > 0.75f -> DropZone.AFTER
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
                                    // Middle 50% - Create new group (both tabs are ungrouped)
                                    android.util.Log.d(
                                        "DragCoordinator",
                                        "CENTER zone: Creating new group at target position: [${tab.tabId}, $targetTabId]"
                                    )

                                    // If tab is in a group, remove it first
                                    if (tab.groupId != null) {
                                        viewModel.removeTabFromGroup(tab.tabId)
                                        delay(50)
                                    }

                                    // Get target tab's position BEFORE creating the group
                                    val currentOrder = viewModel.currentOrder.value
                                    val targetPosition = currentOrder?.primaryOrder?.indexOfFirst { item ->
                                        when (item) {
                                            is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId == targetTabId
                                            is UnifiedTabOrder.OrderItem.TabGroup -> item.tabIds.contains(targetTabId)
                                        }
                                    } ?: -1

                                    // Create new group with both tabs
                                    viewModel.createGroup(
                                        listOf(tab.tabId, targetTabId),
                                        contextId = draggedTab.contextId ?: targetTab.contextId
                                    )

                                    // Wait for group creation to complete
                                    delay(100)

                                    // Reorder the newly created group to the target position
                                    if (targetPosition >= 0) {
                                        val newOrder = viewModel.currentOrder.value
                                        val newGroupId = viewModel.groups.value.find { group ->
                                            group.tabIds.containsAll(listOf(tab.tabId, targetTabId))
                                        }?.id

                                        if (newGroupId != null) {
                                            val currentGroupPosition = newOrder?.getItemPosition(newGroupId)
                                            if (currentGroupPosition != null && currentGroupPosition != targetPosition) {
                                                android.util.Log.d(
                                                    "DragCoordinator",
                                                    "Moving new group from $currentGroupPosition to $targetPosition"
                                                )
                                                orderManager.reorderItem(currentGroupPosition, targetPosition)
                                            }
                                        }
                                    }
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
                // Tab → Group: Handle based on drop zone
                val groupId = target.metadata["groupId"] as? String ?: return
                val groupContextId = target.metadata["contextId"] as? String

                val tabs = viewModel.tabs.value
                val draggedTab = tabs.find { it.id == tab.tabId }

                if (draggedTab != null) {
                    // Check context compatibility
                    if (draggedTab.contextId == groupContextId) {
                        when (target.dropZone) {
                            DropZone.CENTER -> {
                                // CENTER zone - Add tab to group
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "CENTER: Adding tab ${tab.tabId} to group $groupId"
                                )

                                // Remove from old group if needed
                                if (tab.groupId != null && tab.groupId != groupId) {
                                    viewModel.removeTabFromGroup(tab.tabId)
                                    delay(50)
                                }

                                viewModel.addTabToGroup(tab.tabId, groupId)
                            }

                            DropZone.BEFORE -> {
                                // BEFORE zone - Reorder tab before the group
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "BEFORE: Reordering tab ${tab.tabId} before group $groupId"
                                )

                                // Remove from old group if in one
                                if (tab.groupId != null) {
                                    viewModel.removeTabFromGroup(tab.tabId)
                                    delay(50)
                                }

                                // Get group position and insert before
                                val currentOrder = viewModel.currentOrder.value
                                val groupPosition = currentOrder?.getItemPosition(groupId)

                                if (groupPosition != null && groupPosition >= 0) {
                                    viewModel.moveTabToPosition(tab.tabId, groupPosition)
                                }
                            }

                            DropZone.AFTER -> {
                                // AFTER zone - Reorder tab after the group
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "AFTER: Reordering tab ${tab.tabId} after group $groupId"
                                )

                                // Remove from old group if in one
                                if (tab.groupId != null) {
                                    viewModel.removeTabFromGroup(tab.tabId)
                                    delay(50)
                                }

                                // Get group position and insert after
                                val currentOrder = viewModel.currentOrder.value
                                val groupPosition = currentOrder?.getItemPosition(groupId)

                                if (groupPosition != null && groupPosition >= 0) {
                                    viewModel.moveTabToPosition(tab.tabId, groupPosition + 1)
                                }
                            }

                            null -> {
                                // Fallback - add to group
                                android.util.Log.d("DragCoordinator", "Adding tab ${tab.tabId} to group $groupId")

                                // Remove from old group if needed
                                if (tab.groupId != null && tab.groupId != groupId) {
                                    viewModel.removeTabFromGroup(tab.tabId)
                                    delay(50)
                                }

                                // Add to target group
                                viewModel.addTabToGroup(tab.tabId, groupId)
                            }
                        }
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
                // Group → Tab: Handle based on drop zone
                val targetTabId = target.metadata["tabId"] as? String ?: return
                val tabs = viewModel.tabs.value
                val targetTab = tabs.find { it.id == targetTabId }

                if (targetTab != null) {
                    val groups = viewModel.groups.value
                    val draggedGroup = groups.find { it.id == group.groupId }

                    if (draggedGroup != null && draggedGroup.contextId == targetTab.contextId) {
                        when (target.dropZone) {
                            DropZone.CENTER -> {
                                // CENTER - Add tab to group
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "CENTER: Adding tab $targetTabId to group ${group.groupId}"
                                )
                                viewModel.addTabToGroup(targetTabId, group.groupId)
                            }

                            DropZone.BEFORE, DropZone.AFTER -> {
                                // BEFORE/AFTER - Reorder group near the tab
                                val zone = if (target.dropZone == DropZone.BEFORE) "BEFORE" else "AFTER"
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "$zone: Reordering group ${group.groupId} near tab $targetTabId"
                                )

                                val currentOrder = viewModel.currentOrder.value
                                val targetPosition = currentOrder?.primaryOrder?.indexOfFirst { item ->
                                    when (item) {
                                        is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId == targetTabId
                                        is UnifiedTabOrder.OrderItem.TabGroup -> item.tabIds.contains(targetTabId)
                                    }
                                } ?: -1

                                val currentPosition = currentOrder?.getItemPosition(group.groupId)

                                if (targetPosition >= 0 && currentPosition != null) {
                                    val finalPosition =
                                        if (target.dropZone == DropZone.AFTER) targetPosition + 1 else targetPosition
                                    orderManager.reorderItem(currentPosition, finalPosition)
                                }
                            }

                            null -> {
                                // Fallback - add to group
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "Adding tab $targetTabId to group ${group.groupId}"
                                )
                                viewModel.addTabToGroup(targetTabId, group.groupId)
                            }
                        }
                    } else {
                        android.util.Log.w(
                            "DragCoordinator",
                            "Context mismatch: cannot add tab to group from different profile"
                        )
                    }
                }
            }

            DropTargetType.GROUP_HEADER -> {
                // Group → Group: Handle based on drop zone
                val targetGroupId = target.metadata["groupId"] as? String ?: return
                val targetContextId = target.metadata["contextId"] as? String

                val groups = viewModel.groups.value
                val draggedGroup = groups.find { it.id == group.groupId }

                if (draggedGroup != null && targetGroupId != group.groupId) {
                    // Check context compatibility
                    if (draggedGroup.contextId == targetContextId) {
                        when (target.dropZone) {
                            DropZone.CENTER -> {
                                // CENTER - Merge groups
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "CENTER: Merging group ${group.groupId} into $targetGroupId"
                                )
                                viewModel.mergeGroups(group.groupId, targetGroupId)
                            }

                            DropZone.BEFORE, DropZone.AFTER -> {
                                // BEFORE/AFTER - Reorder groups
                                val zone = if (target.dropZone == DropZone.BEFORE) "BEFORE" else "AFTER"
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "$zone: Reordering group ${group.groupId} near group $targetGroupId"
                                )

                                val currentOrder = viewModel.currentOrder.value
                                val targetPosition = currentOrder?.getItemPosition(targetGroupId)
                                val currentPosition = currentOrder?.getItemPosition(group.groupId)

                                if (targetPosition != null && currentPosition != null) {
                                    val finalPosition =
                                        if (target.dropZone == DropZone.AFTER) targetPosition + 1 else targetPosition
                                    orderManager.reorderItem(currentPosition, finalPosition)
                                }
                            }

                            null -> {
                                // Fallback - merge groups
                                android.util.Log.d(
                                    "DragCoordinator",
                                    "Merging group ${group.groupId} into $targetGroupId"
                                )
                                viewModel.mergeGroups(group.groupId, targetGroupId)
                            }
                        }
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
                    // Standard long-press drag detection
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            itemBounds?.let { bounds ->
                                val absolutePosition = bounds.topLeft + offset
                                coordinator.startDrag(itemType, absolutePosition, bounds, itemSize)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
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
 * Uses enlargement instead of stroke to signify grouping/drop target
 */
fun Modifier.dragVisualFeedback(
    itemId: String,
    coordinator: DragCoordinator,
    isDragging: Boolean = coordinator.isDragging(itemId),
    isDropTarget: Boolean = coordinator.isHoveringOver(itemId),
    draggedScale: Float = 0.95f,
    hoverScale: Float = 1.08f  // Scale up when hovering to signify grouping
): Modifier = composed {
    // Only apply visual feedback if this item is actually involved in dragging
    val isInvolved = isDragging || isDropTarget

    if (!isInvolved) {
        // Skip all animations and graphics layers if not involved - improves scroll performance
        return@composed this
    }

    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(isDragging, isDropTarget) {
        when {
            isDragging -> {
                // When dragging, hide original (it will be shown in drag layer)
                launch {
                    scale.animateTo(
                        draggedScale,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    )
                }
                launch {
                    alpha.animateTo(
                        0.3f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    )
                }
            }

            isDropTarget -> {
                // When hovered as drop target, enlarge to signify grouping
                launch {
                    scale.animateTo(
                        hoverScale,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessHigh,
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        )
                    )
                }
                // Keep full alpha when hovering
                launch {
                    alpha.animateTo(
                        1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    )
                }
            }

            else -> {
                // Return to normal state
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
    }

    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
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
 * Drop zone indicator - shows visual feedback on TAB targets during drag
 * Highlights the BEFORE/CENTER/AFTER zones
 */
@Composable
fun DropZoneIndicator(
    itemId: String,
    coordinator: DragCoordinator,
    modifier: Modifier = Modifier
) {
    val dragState by coordinator.dragState

    // Only show if this item is the current drop target and we're dragging
    if (!dragState.isDragging || dragState.currentDropTarget?.id != itemId) {
        return
    }

    val dropZone = dragState.currentDropZone
    val dropTarget = dragState.currentDropTarget

    if (dropZone != null && dropTarget?.type == DropTargetType.TAB) {
        Box(modifier = modifier.fillMaxSize()) {
            when (dropZone) {
                DropZone.BEFORE -> {
                    // Show indicator at top
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 8.dp),
                        thickness = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                DropZone.AFTER -> {
                    // Show indicator at bottom
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 8.dp),
                        thickness = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                DropZone.CENTER -> {
                    // Show a highlight overlay for grouping
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Reorderable list state for backwards compatibility
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
