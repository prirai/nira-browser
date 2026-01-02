package com.prirai.android.nira.browser.tabs.compose

import android.util.Log
import kotlinx.coroutines.delay
import mozilla.components.browser.state.state.TabSessionState

/**
 * Unified drag-and-drop handler for all tab views.
 * Uses sh.calvin.reorderable library for consistent behavior.
 * 
 * Handles all drop scenarios:
 * - Root elements: ungrouped tabs + groups (reorder at root level)
 * - Child elements: tabs within groups (reorder within group)
 * - Drop on tab: group two tabs together
 * - Drop on group: add tab to group
 * - Drop grouped tab outside: ungroup and place at root
 */
object UnifiedDragDropHandler {
    
    private const val TAG = "UnifiedDragDrop"
    
    /**
     * Handle drop from reorderable library callback.
     * 
     * @param viewModel The tab view model for executing operations
     * @param fromIndex Source index in the flat list
     * @param toIndex Target index in the flat list
     * @param items The flat list of items being displayed
     * @param tabs All tabs
     * @param groups All groups
     */
    suspend fun handleDrop(
        viewModel: TabViewModel,
        fromIndex: Int,
        toIndex: Int,
        items: List<ListItem>,
        tabs: List<TabSessionState>,
        groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>
    ): DropResult {
        if (fromIndex == toIndex) return DropResult.NoChange
        
        val draggedItem = items.getOrNull(fromIndex) ?: return DropResult.Error("Invalid from index")
        val targetItem = items.getOrNull(toIndex) ?: return DropResult.Error("Invalid to index")
        
        Log.d(TAG, "Drop: fromIndex=$fromIndex, toIndex=$toIndex")
        Log.d(TAG, "Dragged: ${getItemDebugName(draggedItem)}")
        Log.d(TAG, "Target: ${getItemDebugName(targetItem)}")
        
        return when {
            // Scenario 1: Root element → Root element (simple reorder)
            draggedItem is ListItem.GroupHeader && targetItem is ListItem.GroupHeader -> {
                reorderGroupToGroup(viewModel, draggedItem, targetItem, toIndex)
            }
            
            draggedItem is ListItem.GroupHeader && targetItem is ListItem.Tab && !targetItem.isInGroup -> {
                reorderGroupToRootPosition(viewModel, draggedItem, toIndex)
            }
            
            draggedItem is ListItem.Tab && !draggedItem.isInGroup && targetItem is ListItem.GroupHeader -> {
                reorderTabToRootPosition(viewModel, draggedItem, toIndex)
            }
            
            draggedItem is ListItem.Tab && !draggedItem.isInGroup && 
            targetItem is ListItem.Tab && !targetItem.isInGroup -> {
                // When moving a root tab to another root tab's position:
                // - If they're different tabs, reorder
                // - This provides smooth reordering UX
                // For grouping, users can:
                // 1. Drag tab to group header
                // 2. Use context menu to create/join groups
                // 3. Use batch selection (future enhancement)
                reorderRootTabs(viewModel, draggedItem, targetItem, toIndex)
            }
            
            // Scenario 2: Tab → Group header (add to group)
            draggedItem is ListItem.Tab && targetItem is ListItem.GroupHeader -> {
                addTabToGroup(viewModel, draggedItem, targetItem, tabs, groups)
            }
            
            // Scenario 3: Grouped tab → Outside group (ungroup)
            draggedItem is ListItem.Tab && draggedItem.isInGroup && 
            targetItem is ListItem.Tab && !targetItem.isInGroup -> {
                ungroupTab(viewModel, draggedItem, toIndex)
            }
            
            // Scenario 4: Tab within group → Same group (reorder within)
            draggedItem is ListItem.Tab && draggedItem.isInGroup &&
            targetItem is ListItem.Tab && targetItem.isInGroup &&
            draggedItem.groupId == targetItem.groupId -> {
                reorderWithinGroup(viewModel, draggedItem, targetItem)
            }
            
            // Scenario 5: Tab → Different group (move between groups)
            draggedItem is ListItem.Tab && draggedItem.isInGroup &&
            targetItem is ListItem.Tab && targetItem.isInGroup &&
            draggedItem.groupId != targetItem.groupId -> {
                moveBetweenGroups(viewModel, draggedItem, targetItem)
            }
            
            // Scenario 6: Group → Group (merge groups - on exact drop)
            draggedItem is ListItem.GroupHeader && targetItem is ListItem.GroupHeader &&
            fromIndex == toIndex -> {
                mergeGroups(viewModel, draggedItem, targetItem)
            }
            
            else -> {
                Log.w(TAG, "Unhandled drag scenario")
                DropResult.NoChange
            }
        }
    }
    
    /**
     * Determine if two tabs should be grouped together based on proximity
     * 
     * When using the reorderable library, tabs are grouped when:
     * - They are dropped adjacent to each other (distance of 1)
     * - This allows for intuitive grouping by dragging one tab next to another
     */
    private fun shouldGroupTogether(fromIndex: Int, toIndex: Int): Boolean {
        // Group when dropped adjacent (next to each other)
        // This happens when you drag a tab and drop it right next to another tab
        val distance = kotlin.math.abs(fromIndex - toIndex)
        return distance == 1
    }
    
    private suspend fun reorderGroupToGroup(
        viewModel: TabViewModel,
        dragged: ListItem.GroupHeader,
        target: ListItem.GroupHeader,
        targetIndex: Int
    ): DropResult {
        Log.d(TAG, "Reorder group ${dragged.groupId} to position $targetIndex")
        viewModel.reorderGroup(dragged.groupId, targetIndex)
        return DropResult.Reordered
    }
    
    private suspend fun reorderGroupToRootPosition(
        viewModel: TabViewModel,
        dragged: ListItem.GroupHeader,
        targetIndex: Int
    ): DropResult {
        Log.d(TAG, "Reorder group ${dragged.groupId} to root position $targetIndex")
        viewModel.reorderGroup(dragged.groupId, targetIndex)
        return DropResult.Reordered
    }
    
    private suspend fun reorderTabToRootPosition(
        viewModel: TabViewModel,
        dragged: ListItem.Tab,
        targetIndex: Int
    ): DropResult {
        Log.d(TAG, "Reorder tab ${dragged.tab.id} to root position $targetIndex")
        viewModel.moveTabToPosition(dragged.tab.id, targetIndex)
        return DropResult.Reordered
    }
    
    private suspend fun reorderRootTabs(
        viewModel: TabViewModel,
        dragged: ListItem.Tab,
        target: ListItem.Tab,
        targetIndex: Int
    ): DropResult {
        Log.d(TAG, "Reorder root tabs: ${dragged.tab.id} to position near ${target.tab.id}")
        viewModel.moveTabToPosition(dragged.tab.id, targetIndex)
        return DropResult.Reordered
    }
    
    private suspend fun groupTwoTabs(
        viewModel: TabViewModel,
        dragged: ListItem.Tab,
        target: ListItem.Tab,
        tabs: List<TabSessionState>
    ): DropResult {
        // Check context compatibility
        if (dragged.tab.contextId != target.tab.contextId) {
            Log.w(TAG, "Cannot group tabs with different contexts")
            return DropResult.Error("Incompatible contexts")
        }
        
        Log.d(TAG, "Grouping tabs: ${dragged.tab.id} and ${target.tab.id}")
        val contextId = dragged.tab.contextId ?: target.tab.contextId
        viewModel.createGroup(listOf(dragged.tab.id, target.tab.id), contextId = contextId)
        return DropResult.GroupCreated
    }
    
    private suspend fun addTabToGroup(
        viewModel: TabViewModel,
        dragged: ListItem.Tab,
        targetGroup: ListItem.GroupHeader,
        tabs: List<TabSessionState>,
        groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>
    ): DropResult {
        val group = groups.find { it.id == targetGroup.groupId }
        if (group == null) {
            return DropResult.Error("Group not found")
        }
        
        // Check context compatibility
        if (dragged.tab.contextId != group.contextId) {
            Log.w(TAG, "Cannot add tab to group with different context")
            return DropResult.Error("Incompatible contexts")
        }
        
        // If tab is in a different group, remove it first
        if (dragged.groupId != null && dragged.groupId != targetGroup.groupId) {
            Log.d(TAG, "Moving tab ${dragged.tab.id} from group ${dragged.groupId} to ${targetGroup.groupId}")
            viewModel.removeTabFromGroup(dragged.tab.id)
            delay(50)
        }
        
        Log.d(TAG, "Adding tab ${dragged.tab.id} to group ${targetGroup.groupId}")
        viewModel.addTabToGroup(dragged.tab.id, targetGroup.groupId)
        return DropResult.AddedToGroup
    }
    
    private suspend fun ungroupTab(
        viewModel: TabViewModel,
        dragged: ListItem.Tab,
        targetIndex: Int
    ): DropResult {
        if (dragged.groupId == null) {
            return DropResult.Error("Tab not in group")
        }
        
        Log.d(TAG, "Ungrouping tab ${dragged.tab.id} from group ${dragged.groupId}")
        viewModel.removeTabFromGroup(dragged.tab.id)
        
        // Position the ungrouped tab at target index
        delay(50)
        viewModel.moveTabToPosition(dragged.tab.id, targetIndex)
        return DropResult.Ungrouped
    }
    
    private suspend fun reorderWithinGroup(
        viewModel: TabViewModel,
        dragged: ListItem.Tab,
        target: ListItem.Tab
    ): DropResult {
        val groupId = dragged.groupId ?: return DropResult.Error("Tab not in group")
        
        Log.d(TAG, "Reorder within group $groupId: ${dragged.tab.id} to ${target.tab.id}")
        viewModel.reorderTabInGroup(dragged.tab.id, target.tab.id, groupId)
        return DropResult.Reordered
    }
    
    private suspend fun moveBetweenGroups(
        viewModel: TabViewModel,
        dragged: ListItem.Tab,
        target: ListItem.Tab
    ): DropResult {
        val fromGroupId = dragged.groupId ?: return DropResult.Error("Tab not in group")
        val toGroupId = target.groupId ?: return DropResult.Error("Target not in group")
        
        // Check context compatibility
        if (dragged.tab.contextId != target.tab.contextId) {
            Log.w(TAG, "Cannot move tab between groups with different contexts")
            return DropResult.Error("Incompatible contexts")
        }
        
        Log.d(TAG, "Move tab ${dragged.tab.id} from group $fromGroupId to $toGroupId")
        viewModel.removeTabFromGroup(dragged.tab.id)
        delay(50)
        viewModel.addTabToGroup(dragged.tab.id, toGroupId)
        return DropResult.Reordered
    }
    
    private suspend fun mergeGroups(
        viewModel: TabViewModel,
        dragged: ListItem.GroupHeader,
        target: ListItem.GroupHeader
    ): DropResult {
        if (dragged.groupId == target.groupId) {
            return DropResult.NoChange
        }
        
        // Check context compatibility
        if (dragged.contextId != target.contextId) {
            Log.w(TAG, "Cannot merge groups with different contexts")
            return DropResult.Error("Incompatible contexts")
        }
        
        Log.d(TAG, "Merging group ${dragged.groupId} into ${target.groupId}")
        viewModel.mergeGroups(dragged.groupId, target.groupId)
        return DropResult.GroupsMerged
    }
    
    private fun getItemDebugName(item: ListItem): String {
        return when (item) {
            is ListItem.GroupHeader -> "GroupHeader(${item.groupId})"
            is ListItem.Tab -> "Tab(${item.tab.id}, inGroup=${item.isInGroup}, groupId=${item.groupId})"
        }
    }
}

/**
 * Result of a drag-drop operation
 */
sealed class DropResult {
    object NoChange : DropResult()
    object Reordered : DropResult()
    object GroupCreated : DropResult()
    object AddedToGroup : DropResult()
    object Ungrouped : DropResult()
    object GroupsMerged : DropResult()
    data class Error(val message: String) : DropResult()
}
