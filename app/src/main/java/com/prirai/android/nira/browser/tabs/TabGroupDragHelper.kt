package com.prirai.android.nira.browser.tabs

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Enhanced drag & drop helper for tab grouping in the tab sheet.
 * 
 * Features:
 * - Drag ungrouped tab onto another tab to create group
 * - Drag ungrouped tab onto group header to add to group
 * - Drag tab from one group to another to move it
 * - Drag tab out of group (drag far away) to ungroup it
 * - Visual feedback during drag operations
 */
class TabGroupDragHelper(
    private val adapter: TabsWithGroupsAdapter,
    private val groupManager: UnifiedTabGroupManager,
    private val scope: CoroutineScope,
    private val onUpdate: () -> Unit
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    0
) {
    private var draggedItem: TabItem? = null
    private var dragStartY = 0f
    private var lastTargetPosition = -1
    private var isOverUngroupZone = false
    
    private val ungroupThreshold = 200f // pixels to drag out to ungroup
    private val dropZonePaint = Paint().apply {
        style = Paint.Style.FILL
        alpha = 100
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition
        
        if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
            return false
        }

        // Store target for drop handling
        lastTargetPosition = toPosition
        
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used - we handle drop in clearView
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return 0

        val item = adapter.currentList.getOrNull(position)
        
        // Allow dragging single tabs and tabs within groups
        return when (item) {
            is TabItem.SingleTab -> {
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            }
            is TabItem.Group -> {
                // Group headers are not draggable
                0
            }
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
                        draggedItem = adapter.currentList.getOrNull(position)
                        dragStartY = vh.itemView.y
                        
                        // Visual feedback
                        vh.itemView.alpha = 0.7f
                        vh.itemView.scaleX = 1.05f
                        vh.itemView.scaleY = 1.05f
                        vh.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                // Handle drop
                draggedItem?.let { dragged ->
                    handleDrop(dragged, lastTargetPosition)
                }
                
                // Reset state
                draggedItem = null
                lastTargetPosition = -1
                isOverUngroupZone = false
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        
        // Reset visual state
        viewHolder.itemView.alpha = 1.0f
        viewHolder.itemView.scaleX = 1.0f
        viewHolder.itemView.scaleY = 1.0f
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
            val dragDistance = kotlin.math.abs(dY)
            
            // Check if dragged far enough to ungroup
            val draggedItem = this.draggedItem
            if (draggedItem is TabItem.SingleTab) {
                // Single tab - can't ungroup if not in a group
                isOverUngroupZone = false
            } else {
                // Check if dragging far enough for ungroup
                isOverUngroupZone = dragDistance > ungroupThreshold
            }

            // Draw ungroup zone indicator
            if (isOverUngroupZone) {
                drawUngroupZone(c, recyclerView, viewHolder, dY > 0)
                viewHolder.itemView.alpha = 0.5f
            } else {
                viewHolder.itemView.alpha = 0.7f
            }

            // Highlight drop target
            if (lastTargetPosition >= 0 && !isOverUngroupZone) {
                highlightDropTarget(c, recyclerView, lastTargetPosition)
            }
        }
    }

    private fun drawUngroupZone(canvas: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dragDown: Boolean) {
        val zoneHeight = 120f
        val rect = RectF(
            0f,
            if (dragDown) recyclerView.height - zoneHeight else 0f,
            recyclerView.width.toFloat(),
            if (dragDown) recyclerView.height.toFloat() else zoneHeight
        )
        
        dropZonePaint.color = Color.parseColor("#FF5722") // Orange-red for ungroup
        canvas.drawRect(rect, dropZonePaint)
        
        // Draw text hint
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Release to Ungroup",
            rect.centerX(),
            rect.centerY() + 15f,
            textPaint
        )
    }

    private fun highlightDropTarget(canvas: Canvas, recyclerView: RecyclerView, targetPosition: Int) {
        val targetView = recyclerView.layoutManager?.findViewByPosition(targetPosition) ?: return
        
        val rect = RectF(
            targetView.left.toFloat(),
            targetView.top.toFloat(),
            targetView.right.toFloat(),
            targetView.bottom.toFloat()
        )
        
        dropZonePaint.color = Color.parseColor("#4CAF50") // Green for valid drop
        canvas.drawRect(rect, dropZonePaint)
    }

    private fun handleDrop(draggedItem: TabItem, targetPosition: Int) {
        scope.launch {
            when (draggedItem) {
                is TabItem.SingleTab -> {
                    handleSingleTabDrop(draggedItem.tab, targetPosition)
                }
                is TabItem.Group -> {
                    // Group items cannot be dragged
                }
            }
        }
    }

    private suspend fun handleSingleTabDrop(draggedTab: TabSessionState, targetPosition: Int) {
        // Check if dropping in ungroup zone
        if (isOverUngroupZone) {
            val currentGroup = groupManager.getGroupForTab(draggedTab.id)
            if (currentGroup != null) {
                groupManager.removeTabFromGroup(draggedTab.id)
                onUpdate()
            }
            return
        }

        // Get target item
        if (targetPosition < 0 || targetPosition >= adapter.currentList.size) {
            return
        }

        val targetItem = adapter.currentList[targetPosition]
        
        when (targetItem) {
            is TabItem.SingleTab -> {
                // Dropped on another single tab - create group or add to existing group
                val draggedGroup = groupManager.getGroupForTab(draggedTab.id)
                val targetGroup = groupManager.getGroupForTab(targetItem.tab.id)
                
                when {
                    draggedGroup == null && targetGroup == null -> {
                        // Both ungrouped - create new group
                        groupManager.createGroup(
                            tabIds = listOf(draggedTab.id, targetItem.tab.id)
                        )
                    }
                    draggedGroup != null && targetGroup == null -> {
                        // Dragged from group to ungrouped - add target to dragged's group
                        groupManager.addTabToGroup(targetItem.tab.id, draggedGroup.id)
                    }
                    draggedGroup == null && targetGroup != null -> {
                        // Dragged ungrouped to group - add dragged to target's group
                        groupManager.addTabToGroup(draggedTab.id, targetGroup.id)
                    }
                    draggedGroup != null && targetGroup != null && draggedGroup.id != targetGroup.id -> {
                        // Both in different groups - move dragged to target's group
                        groupManager.moveTabBetweenGroups(
                            draggedTab.id,
                            draggedGroup.id,
                            targetGroup.id
                        )
                    }
                }
                onUpdate()
            }
            
            is TabItem.Group -> {
                // Dropped on a group - add to that group
                val draggedGroup = groupManager.getGroupForTab(draggedTab.id)
                
                if (draggedGroup == null) {
                    // Ungrouped tab dropped on group - add it
                    groupManager.addTabToGroup(draggedTab.id, targetItem.groupId)
                } else if (draggedGroup.id != targetItem.groupId) {
                    // Tab from different group - move it
                    groupManager.moveTabBetweenGroups(
                        draggedTab.id,
                        draggedGroup.id,
                        targetItem.groupId
                    )
                }
                
                // Expand the target group to show the new tab
                adapter.expandGroup(targetItem.groupId)
                onUpdate()
            }
        }
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        val itemTouchHelper = ItemTouchHelper(this)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
