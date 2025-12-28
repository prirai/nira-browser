package com.prirai.android.nira.browser.tabs.dragdrop

import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Unified drag helper that works for both list and grid views.
 * Uses hover-based detection instead of position tracking.
 */
class UnifiedDragHelper(
    private val adapter: FlatTabsAdapter,
    private val groupManager: UnifiedTabGroupManager,
    private val scope: CoroutineScope,
    private val onUpdate: () -> Unit,
    private val getCurrentFlatList: () -> List<TabListItem>,
    private val onOrderChanged: () -> Unit,  // New callback for when order changes
    private val isGridView: Boolean = false,
    private val spanCount: Int = 1
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN or 
    if (isGridView) ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT else 0,
    0
) {
    
    private val detector = DropTargetDetector()
    private val visualFeedback = DragVisualFeedback()
    
    private var draggedItem: TabListItem? = null
    private var draggedTabId: String? = null
    private var draggedGroupId: String? = null  // Track if dragging from a group
    private var dragStartPosition = -1
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var currentDropTarget: DropTarget = DropTarget.None
    private var lastHapticTime = 0L
    
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return 0
        
        val item = adapter.currentList.getOrNull(position)
        
        // Only allow dragging tabs, not group headers
        return if (item?.isDraggable == true) {
            val dragFlags = if (isGridView) {
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or 
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            } else {
                ItemTouchHelper.UP or ItemTouchHelper.DOWN
            }
            makeMovementFlags(dragFlags, 0)
        } else {
            0
        }
    }
    
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // We handle movement in onChildDraw, not here
        return true
    }
    
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used
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
                        dragStartX = vh.itemView.x
                        dragStartY = vh.itemView.y
                        
                        // Extract tab ID and group ID if in group
                        draggedTabId = draggedItem?.tabId
                        draggedGroupId = when (draggedItem) {
                            is TabListItem.GroupedTab -> (draggedItem as TabListItem.GroupedTab).groupId
                            else -> null
                        }
                        
                        // Visual feedback for dragged item
                        vh.itemView.alpha = 0.75f
                        vh.itemView.scaleX = 1.05f
                        vh.itemView.scaleY = 1.05f
                        vh.itemView.elevation = 16f * vh.itemView.context.resources.displayMetrics.density
                        
                        vh.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
            
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                // Handle drop
                draggedTabId?.let { tabId ->
                    handleDrop(tabId, currentDropTarget)
                }
                
                // Reset state
                draggedItem = null
                draggedTabId = null
                draggedGroupId = null
                dragStartPosition = -1
                currentDropTarget = DropTarget.None
                lastHapticTime = 0L
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
        
        if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) {
            return
        }
        
        val draggedTabId = this.draggedTabId ?: return
        
        // Calculate center point of dragged item
        // itemView.x and itemView.y already include the drag translation
        val centerX = viewHolder.itemView.x + viewHolder.itemView.width / 2
        val centerY = viewHolder.itemView.y + viewHolder.itemView.height / 2
        val draggedItemCenter = PointF(centerX, centerY)
        
        // Calculate drag distance
        val dragDistance = kotlin.math.sqrt(
            ((dX * dX) + (dY * dY)).toDouble()
        ).toFloat()
        
        // Collect visible items info (include all items for detection)
        val visibleItems = mutableListOf<ViewHolderInfo>()
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val childVH = recyclerView.getChildViewHolder(child)
            val position = childVH.bindingAdapterPosition
            
            if (position != RecyclerView.NO_POSITION && position < adapter.currentList.size) {
                val item = adapter.currentList[position]
                
                // Skip the dragged item itself
                if (position == dragStartPosition) continue
                
                val bounds = RectF(
                    child.left.toFloat(),
                    child.top.toFloat(),
                    child.right.toFloat(),
                    child.bottom.toFloat()
                )
                visibleItems.add(ViewHolderInfo(position, bounds, item))
            }
        }
        
        // Check if tab is in a group (synchronous check using cache)
        val isTabInGroup = run {
            try {
                // Use the cached groups from UnifiedTabGroupManager
                val allGroups = groupManager.getAllGroups()
                allGroups.any { group -> group.tabIds.contains(draggedTabId) }
            } catch (e: Exception) {
                false
            }
        }
        
        // Detect drop target
        currentDropTarget = if (isGridView) {
            detector.detectGridViewTarget(
                draggedItemCenter,
                draggedTabId,
                draggedGroupId,
                dragStartPosition,
                visibleItems,
                isTabInGroup,
                dragDistance,
                spanCount
            )
        } else {
            detector.detectListViewTarget(
                draggedItemCenter,
                draggedTabId,
                draggedGroupId,
                dragStartPosition,
                visibleItems,
                isTabInGroup,
                dragDistance
            )
        }
        
        // Haptic feedback when target changes
        if (currentDropTarget !is DropTarget.None) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastHapticTime > 100) {
                viewHolder.itemView.performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                lastHapticTime = currentTime
            }
        }
        
        // Draw visual feedback
        visualFeedback.render(c, recyclerView, currentDropTarget, visibleItems)
        
        // Adjust dragged item opacity based on target
        viewHolder.itemView.alpha = when (currentDropTarget) {
            // REMOVED: UngroupZone
            is DropTarget.None -> 0.6f
            else -> 0.75f
        }
    }
    
    /**
     * Handle the drop operation
     */
    private fun handleDrop(draggedTabId: String, dropTarget: DropTarget) {
        scope.launch {
            when (dropTarget) {
                is DropTarget.OnTab -> {
                    // Merge tabs: create group or add to existing
                    handleMergeTabs(draggedTabId, dropTarget.tabId)
                    onOrderChanged()  // Save after merge
                }
                
                is DropTarget.OnGroup -> {
                    // Add to group
                    groupManager.addTabToGroup(draggedTabId, dropTarget.groupId)
                    onUpdate()
                    onOrderChanged()  // Save after adding to group
                }
                
                is DropTarget.UngroupZone -> {
                    // REMOVED: No longer used, but kept for exhaustive when
                }
                
                is DropTarget.Between -> {
                    // Check if dropping between items in a group
                    val currentList = getCurrentFlatList()
                    
                    // Handle special case: inserting at beginning (afterPosition = -1)
                    if (dropTarget.afterPosition == -1) {
                        // Insert at the very beginning
                        if (draggedGroupId != null) {
                            // Tab is leaving its group
                            groupManager.removeTabFromGroup(draggedTabId)
                        }
                        
                        val mutableList = currentList.toMutableList()
                        val draggedPosition = mutableList.indexOfFirst { 
                            when (it) {
                                is TabListItem.GroupedTab -> it.tab.id == draggedTabId
                                is TabListItem.UngroupedTab -> it.tab.id == draggedTabId
                                else -> false
                            }
                        }
                        
                        if (draggedPosition != -1) {
                            val item = mutableList.removeAt(draggedPosition)
                            
                            // Convert to ungrouped if it was grouped
                            val itemToInsert = if (item is TabListItem.GroupedTab) {
                                TabListItem.UngroupedTab(item.tab, 0)  // globalPosition will be recalculated
                            } else {
                                item
                            }
                            
                            // Insert at beginning
                            mutableList.add(0, itemToInsert)
                            
                            // Update adapter
                            adapter.updateItems(mutableList, null)
                            
                            // Save order
                            onOrderChanged()
                        }
                        return@launch
                    }
                    
                    val targetPosition = dropTarget.afterPosition + 1
                    
                    // Find the items around the drop position
                    val itemBefore = currentList.getOrNull(dropTarget.afterPosition)
                    val itemAfter = currentList.getOrNull(targetPosition)
                    
                    // Check if both neighbors are in the same group
                    val groupIdBefore = when (itemBefore) {
                        is TabListItem.GroupedTab -> itemBefore.groupId
                        is TabListItem.GroupHeader -> itemBefore.groupId
                        else -> null
                    }
                    
                    val groupIdAfter = when (itemAfter) {
                        is TabListItem.GroupedTab -> itemAfter.groupId
                        else -> null
                    }
                    
                    // Calculate position in group if dropping into group
                    val positionInGroup = when (itemAfter) {
                        is TabListItem.GroupedTab -> itemAfter.positionInGroup
                        else -> null
                    }
                    
                    // If dropping between items in same group, add to that group at specific position
                    if (groupIdBefore != null && groupIdBefore == groupIdAfter) {
                        // Adding to group at specific position
                        groupManager.removeTabFromGroup(draggedTabId) // Remove from old group if any
                        groupManager.addTabToGroup(draggedTabId, groupIdBefore, positionInGroup)
                        onUpdate()
                        onOrderChanged()  // Save order after group change
                    } else {
                        // Reordering - place tab at target position
                        // If tab was in a group and now placed outside, ungroup it
                        if (draggedGroupId != null) {
                            // Tab is leaving its group
                            groupManager.removeTabFromGroup(draggedTabId)
                        }
                        
                        // Just reordering - visual only for now
                        val mutableList = currentList.toMutableList()
                        val draggedPosition = mutableList.indexOfFirst { 
                            when (it) {
                                is TabListItem.GroupedTab -> it.tab.id == draggedTabId
                                is TabListItem.UngroupedTab -> it.tab.id == draggedTabId
                                else -> false
                            }
                        }
                        
                        if (draggedPosition != -1 && dropTarget.afterPosition >= 0 && 
                            dropTarget.afterPosition < mutableList.size) {
                            val item = mutableList.removeAt(draggedPosition)
                            
                            // Convert to ungrouped if it was grouped
                            val itemToInsert = if (item is TabListItem.GroupedTab) {
                                TabListItem.UngroupedTab(item.tab, 0)  // globalPosition will be recalculated
                            } else {
                                item
                            }
                            
                            val insertPosition = if (dropTarget.afterPosition >= draggedPosition) {
                                dropTarget.afterPosition
                            } else {
                                dropTarget.afterPosition + 1
                            }
                            mutableList.add(insertPosition.coerceIn(0, mutableList.size), itemToInsert)
                            
                            // Update adapter immediately for visual feedback
                            adapter.updateItems(mutableList, null)
                            
                            // Save order after reordering
                            onOrderChanged()
                        }
                    }
                }
                
                is DropTarget.UngroupZone -> {
                    // REMOVED: No longer used, but kept for exhaustive when
                }
                
                is DropTarget.None -> {
                    // Do nothing
                }
            }
        }
    }
    
    /**
     * Handle merging two tabs
     */
    private suspend fun handleMergeTabs(draggedTabId: String, targetTabId: String) {
        val draggedGroup = groupManager.getGroupForTab(draggedTabId)
        val targetGroup = groupManager.getGroupForTab(targetTabId)
        
        when {
            // Both ungrouped - create new group with just these two tabs
            draggedGroup == null && targetGroup == null -> {
                groupManager.createGroup(
                    tabIds = listOf(draggedTabId, targetTabId)
                )
            }
            
            // Dragged is in group, target is not
            // Remove dragged from its group and create new group with target
            draggedGroup != null && targetGroup == null -> {
                // Remove from old group first
                groupManager.removeTabFromGroup(draggedTabId)
                // Create new group with both tabs
                groupManager.createGroup(
                    tabIds = listOf(draggedTabId, targetTabId)
                )
            }
            
            // Dragged is not in group, target is in group
            // Add dragged to target's group
            draggedGroup == null && targetGroup != null -> {
                groupManager.addTabToGroup(draggedTabId, targetGroup.id)
            }
            
            // Both in different groups - move dragged to target's group
            draggedGroup != null && targetGroup != null && draggedGroup.id != targetGroup.id -> {
                groupManager.moveTabBetweenGroups(
                    draggedTabId,
                    draggedGroup.id,
                    targetGroup.id
                )
            }
            
            // Same group - do nothing
        }
        
        onUpdate()
    }
    
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        val itemTouchHelper = ItemTouchHelper(this)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
