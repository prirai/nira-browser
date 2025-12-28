package com.prirai.android.nira.browser.tabs.dragdrop

import android.graphics.PointF
import android.graphics.RectF

/**
 * Represents different types of drop targets during drag operations
 */
sealed class DropTarget {
    /** Drop on a tab to merge/group */
    data class OnTab(val position: Int, val tabId: String) : DropTarget()
    
    /** Drop on a group header to add to group */
    data class OnGroup(val position: Int, val groupId: String) : DropTarget()
    
    /** Drop between items to reorder (shows vertical/horizontal bar) */
    data class Between(
        val afterPosition: Int,  // Insert after this position
        val yPosition: Float? = null,  // For drawing line (list view)
        val xPosition: Float? = null   // For drawing line (grid view)
    ) : DropTarget()
    
    /** Drop far away to ungroup (grid view only) */
    object UngroupZone : DropTarget()
    
    /** No valid drop target */
    object None : DropTarget()
}

/**
 * Detects drop targets based on hover position using bounds intersection.
 * This is the core of the new hover-based detection system.
 */
class DropTargetDetector(
    private val ungroupThreshold: Float = 180f
) {
    
    /**
     * Detect drop target in list view (vertical)
     */
    fun detectListViewTarget(
        draggedItemCenter: PointF,
        draggedTabId: String,
        draggedGroupId: String?,  // NEW: group ID of dragged tab
        dragStartPosition: Int,
        visibleItems: List<ViewHolderInfo>,
        isTabInGroup: Boolean,
        dragDistance: Float
    ): DropTarget {
        
        // REMOVED: Ungroup zone detection - now handled via reordering
        // Users can drag tabs out by reordering them outside the group
        
        // Check if dragging above the first item
        if (visibleItems.isNotEmpty()) {
            val firstItem = visibleItems.first()
            if (draggedItemCenter.y < firstItem.bounds.top) {
                // Above all items - insert at position 0 (before first item)
                return DropTarget.Between(
                    afterPosition = -1,  // Special case: insert at beginning
                    yPosition = firstItem.bounds.top
                )
            }
        }
        
        // Check if dragging below the last item
        if (visibleItems.isNotEmpty()) {
            val lastItem = visibleItems.last()
            if (draggedItemCenter.y > lastItem.bounds.bottom) {
                // Below all items - insert at end
                return DropTarget.Between(
                    afterPosition = lastItem.adapterPosition,
                    yPosition = lastItem.bounds.bottom
                )
            }
        }
        
        // Find item whose bounds contain the center point
        var hoveredItem: ViewHolderInfo? = null
        
        for (info in visibleItems) {
            if (info.bounds.contains(draggedItemCenter.x, draggedItemCenter.y)) {
                hoveredItem = info
                break
            }
        }
        
        if (hoveredItem == null) {
            return DropTarget.None
        }
        
        // Skip self
        if (hoveredItem.adapterPosition == dragStartPosition) {
            return DropTarget.None
        }
        
        // Skip other items in same group as dragged item
        val hoveredItemGroupId = when (hoveredItem.item) {
            is TabListItem.GroupedTab -> (hoveredItem.item as TabListItem.GroupedTab).groupId
            else -> null
        }
        if (draggedGroupId != null && hoveredItemGroupId == draggedGroupId) {
            return DropTarget.None
        }
        
        val item = hoveredItem.item
        val bounds = hoveredItem.bounds
        
        return when (item) {
            is TabListItem.GroupHeader -> {
                // Don't allow dropping on own group header
                if (draggedGroupId != null && item.groupId == draggedGroupId) {
                    return DropTarget.None
                }
                // Always add to group when hovering over header - very forgiving
                DropTarget.OnGroup(hoveredItem.adapterPosition, item.groupId)
            }
            
            is TabListItem.GroupedTab, is TabListItem.UngroupedTab -> {
                // Determine if we're near edges (reorder) or center (merge)
                val relativeY = draggedItemCenter.y - bounds.top
                val itemHeight = bounds.height()
                val edgeThreshold = 0.25f  // 25% from edges for reorder zones
                
                when {
                    relativeY < itemHeight * edgeThreshold -> {
                        // Top edge - insert before
                        DropTarget.Between(
                            afterPosition = hoveredItem.adapterPosition - 1,
                            yPosition = bounds.top
                        )
                    }
                    relativeY > itemHeight * (1 - edgeThreshold) -> {
                        // Bottom edge - insert after
                        DropTarget.Between(
                            afterPosition = hoveredItem.adapterPosition,
                            yPosition = bounds.bottom
                        )
                    }
                    else -> {
                        // Center - merge/group
                        val tabId = item.tabId ?: return DropTarget.None
                        if (tabId == draggedTabId) {
                            return DropTarget.None  // Can't merge with self
                        }
                        DropTarget.OnTab(hoveredItem.adapterPosition, tabId)
                    }
                }
            }
        }
    }
    
    /**
     * Detect drop target in grid view (horizontal + vertical)
     */
    fun detectGridViewTarget(
        draggedItemCenter: PointF,
        draggedTabId: String,
        draggedGroupId: String?,
        dragStartPosition: Int,
        visibleItems: List<ViewHolderInfo>,
        isTabInGroup: Boolean,
        dragDistance: Float,
        spanCount: Int
    ): DropTarget {
        // REMOVED: Ungroup zone detection
        // For now, grid view uses simplified logic - delegates to list view
        return detectListViewTarget(
            draggedItemCenter, draggedTabId, draggedGroupId, dragStartPosition,
            visibleItems, isTabInGroup, dragDistance
        )
    }
}

/**
 * Information about a visible ViewHolder for drop target detection
 */
data class ViewHolderInfo(
    val adapterPosition: Int,
    val bounds: RectF,
    val item: TabListItem
)
