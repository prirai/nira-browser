package com.prirai.android.nira.browser.tabs

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Enhanced drag & drop helper for tab grouping in grid view.
 * Supports all the same operations as list view with grid-specific behavior.
 */
class TabGridDragHelper(
    private val adapter: TabsGridAdapter,
    private val groupManager: UnifiedTabGroupManager,
    private val scope: CoroutineScope,
    private val onUpdate: () -> Unit
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
    0
) {
    private var draggedItem: TabGridItem? = null
    private var draggedTabId: String? = null
    private var dragStartPosition = -1
    private var currentTargetPosition = -1
    private var isOverUngroupZone = false
    private var lastHapticFeedbackTime = 0L
    
    private val ungroupThreshold = 180f
    
    private val dropZonePaint = Paint().apply {
        style = Paint.Style.FILL
        alpha = 120
    }
    
    private val highlightPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#4CAF50")
        alpha = 200
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition
        
        if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
            currentTargetPosition = -1
            return false
        }

        val targetItem = adapter.currentList.getOrNull(toPosition)
        
        if (targetItem != null && toPosition != fromPosition) {
            currentTargetPosition = toPosition
            
            // Haptic feedback
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastHapticFeedbackTime > 100) {
                viewHolder.itemView.performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                lastHapticFeedbackTime = currentTime
            }
        } else {
            currentTargetPosition = -1
        }
        
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return 0

        val item = adapter.currentList.getOrNull(position)
        
        // Allow dragging individual tabs only (not group headers)
        return when (item) {
            is TabGridItem.Tab -> {
                makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN or 
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 
                    0
                )
            }
            is TabGridItem.GroupHeader -> 0
            else -> 0
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)

        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                viewHolder?.let { vh ->
                    val position = vh.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        dragStartPosition = position
                        draggedItem = adapter.currentList.getOrNull(position)
                        
                        // Extract tab ID
                        draggedTabId = when (val item = draggedItem) {
                            is TabGridItem.Tab -> item.tab.id
                            else -> null
                        }
                        
                        // Visual feedback
                        vh.itemView.alpha = 0.75f
                        vh.itemView.scaleX = 1.08f
                        vh.itemView.scaleY = 1.08f
                        vh.itemView.elevation = 16f
                        vh.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                // Handle drop
                draggedItem?.let { dragged ->
                    draggedTabId?.let { tabId ->
                        handleDrop(dragged, tabId, currentTargetPosition)
                    }
                }
                
                // Reset state
                draggedItem = null
                draggedTabId = null
                dragStartPosition = -1
                currentTargetPosition = -1
                isOverUngroupZone = false
                lastHapticFeedbackTime = 0L
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        
        // Reset visual state
        viewHolder.itemView.alpha = 1.0f
        viewHolder.itemView.scaleX = 1.0f
        viewHolder.itemView.scaleY = 1.0f
        viewHolder.itemView.elevation = 0f
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
            // Calculate drag distance from start position
            val dragDistance = kotlin.math.sqrt((dX * dX + dY * dY).toDouble()).toFloat()
            
            // Get current drag position
            val currentX = viewHolder.itemView.x + dX
            val currentY = viewHolder.itemView.y + dY
            val currentCenterX = currentX + viewHolder.itemView.width / 2
            val currentCenterY = currentY + viewHolder.itemView.height / 2
            
            // Reset state
            var foundTarget = false
            currentTargetPosition = -1
            isOverUngroupZone = false
            
            // Check if the dragged tab is in a group by checking the groupId field
            val isInGroup = when (val item = draggedItem) {
                is TabGridItem.Tab -> item.groupId != null
                else -> false
            }
            
            // Only show ungroup zone if tab is actually in a group AND dragged far
            if (isInGroup && dragDistance > ungroupThreshold) {
                isOverUngroupZone = true
            }
            
            if (!isOverUngroupZone) {
                // Find target in grid
                for (i in 0 until adapter.itemCount) {
                    val targetView = recyclerView.layoutManager?.findViewByPosition(i) ?: continue
                    
                    val targetLeft = targetView.left.toFloat()
                    val targetRight = targetView.right.toFloat()
                    val targetTop = targetView.top.toFloat()
                    val targetBottom = targetView.bottom.toFloat()
                    
                    // Check if center overlaps
                    if (currentCenterX >= targetLeft && currentCenterX <= targetRight &&
                        currentCenterY >= targetTop && currentCenterY <= targetBottom) {
                        
                        val targetItem = adapter.currentList.getOrNull(i)
                        
                        if (targetItem != null && i != dragStartPosition) {
                            foundTarget = true
                            currentTargetPosition = i
                            
                            when (targetItem) {
                                is TabGridItem.GroupHeader -> {
                                    drawGroupHighlight(c, targetView)
                                }
                                is TabGridItem.Tab -> {
                                    drawTabMergeHighlight(c, targetView)
                                }
                            }
                            
                            break
                        }
                    }
                }
            }
            
            // Draw ungroup zone AFTER other drawing (so it appears on top)
            if (isOverUngroupZone) {
                drawUngroupZone(c, recyclerView)
                viewHolder.itemView.alpha = 0.5f
            } else if (foundTarget) {
                viewHolder.itemView.alpha = 0.75f
            } else {
                viewHolder.itemView.alpha = 0.6f
            }
        }
    }

    private fun drawUngroupZone(canvas: Canvas, recyclerView: RecyclerView) {
        // Draw semi-transparent overlay across entire view
        val rect = RectF(
            0f,
            0f,
            recyclerView.width.toFloat(),
            recyclerView.height.toFloat()
        )
        
        dropZonePaint.color = Color.parseColor("#FF5722")
        dropZonePaint.alpha = 100
        canvas.drawRect(rect, dropZonePaint)
        
        // Draw text in center
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        canvas.drawText(
            "Release to Ungroup",
            rect.centerX(),
            rect.centerY(),
            textPaint
        )
    }

    private fun drawGroupHighlight(canvas: Canvas, targetView: android.view.View) {
        val rect = RectF(
            targetView.left.toFloat() + 8f,
            targetView.top.toFloat() + 8f,
            targetView.right.toFloat() - 8f,
            targetView.bottom.toFloat() - 8f
        )
        
        // Fill
        dropZonePaint.color = Color.parseColor("#4CAF50")
        dropZonePaint.alpha = 80
        canvas.drawRoundRect(rect, 16f, 16f, dropZonePaint)
        
        // Stroke
        highlightPaint.color = Color.parseColor("#4CAF50")
        highlightPaint.alpha = 220
        canvas.drawRoundRect(rect, 16f, 16f, highlightPaint)
    }

    private fun drawTabMergeHighlight(canvas: Canvas, targetView: android.view.View) {
        val rect = RectF(
            targetView.left.toFloat() + 6f,
            targetView.top.toFloat() + 6f,
            targetView.right.toFloat() - 6f,
            targetView.bottom.toFloat() - 6f
        )
        
        // Fill
        dropZonePaint.color = Color.parseColor("#2196F3")
        dropZonePaint.alpha = 100
        canvas.drawRoundRect(rect, 12f, 12f, dropZonePaint)
        
        // Stroke
        highlightPaint.color = Color.parseColor("#2196F3")
        highlightPaint.alpha = 240
        highlightPaint.strokeWidth = 5f
        canvas.drawRoundRect(rect, 12f, 12f, highlightPaint)
    }

    private fun handleDrop(
        draggedItem: TabGridItem,
        draggedTabId: String,
        targetPosition: Int
    ) {
        scope.launch {
            when (draggedItem) {
                is TabGridItem.Tab -> {
                    handleTabDrop(draggedItem.tab, draggedTabId, targetPosition)
                }
                is TabGridItem.GroupHeader -> {
                    // Not draggable
                }
            }
        }
    }

    private suspend fun handleTabDrop(
        draggedTab: TabSessionState,
        draggedTabId: String,
        targetPosition: Int
    ) {
        // Check if dropping in ungroup zone
        if (isOverUngroupZone) {
            val currentGroup = groupManager.getGroupForTab(draggedTabId)
            if (currentGroup != null) {
                groupManager.removeTabFromGroup(draggedTabId)
                onUpdate()
            }
            return
        }

        // Get target item
        if (targetPosition < 0 || targetPosition >= adapter.currentList.size) {
            return
        }

        when (val targetItem = adapter.currentList[targetPosition]) {
            is TabGridItem.Tab -> {
                // Prevent self-drop
                if (draggedTabId == targetItem.tab.id) {
                    return
                }
                
                // Merge tabs
                val draggedGroup = groupManager.getGroupForTab(draggedTabId)
                val targetGroup = groupManager.getGroupForTab(targetItem.tab.id)
                
                when {
                    draggedGroup == null && targetGroup == null -> {
                        // Create new group
                        groupManager.createGroup(
                            tabIds = listOf(draggedTabId, targetItem.tab.id)
                        )
                    }
                    draggedGroup != null && targetGroup == null -> {
                        // Add target to dragged's group
                        groupManager.addTabToGroup(targetItem.tab.id, draggedGroup.id)
                    }
                    draggedGroup == null && targetGroup != null -> {
                        // Add dragged to target's group
                        groupManager.addTabToGroup(draggedTabId, targetGroup.id)
                    }
                    draggedGroup != null && targetGroup != null && draggedGroup.id != targetGroup.id -> {
                        // Move between groups
                        groupManager.moveTabBetweenGroups(
                            draggedTabId,
                            draggedGroup.id,
                            targetGroup.id
                        )
                    }
                }
                onUpdate()
            }
            
            is TabGridItem.GroupHeader -> {
                // Add to group
                val draggedGroup = groupManager.getGroupForTab(draggedTabId)
                
                // Prevent adding to same group
                if (draggedGroup?.id == targetItem.groupId) {
                    return
                }
                
                if (draggedGroup == null) {
                    groupManager.addTabToGroup(draggedTabId, targetItem.groupId)
                } else {
                    groupManager.moveTabBetweenGroups(
                        draggedTabId,
                        draggedGroup.id,
                        targetItem.groupId
                    )
                }
                onUpdate()
            }
        }
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        val itemTouchHelper = ItemTouchHelper(this)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
