package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.Context

sealed class ListItem {
    data class GroupHeader(
        val groupId: String,
        val title: String,
        val color: Int,
        val tabCount: Int,
        val isExpanded: Boolean,
        val contextId: String?
    ) : ListItem()

    data class Tab(
        val tab: TabSessionState,
        val groupId: String? = null,
        val isInGroup: Boolean = false,
        val isLastInGroup: Boolean = false
    ) : ListItem()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabSheetListView(
    viewModel: TabViewModel,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onGroupClick: (String) -> Unit,
    onGroupOptionsClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val expandedGroups by viewModel.expandedGroups.collectAsState()
    val selectedTabId by viewModel.selectedTabId.collectAsState()
    val currentOrder by viewModel.currentOrder.collectAsState()
    val currentProfile by viewModel.currentProfileId.collectAsState()
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dragDropState = rememberAdvancedDragDropState()
    
    // Build list items from tabs and groups using UnifiedTabOrder
    val listItems = remember(tabs, groups, expandedGroups, currentOrder) {
        buildListItemsFromOrder(tabs, groups, expandedGroups, currentOrder)
    }

    // Ensure unique keys by deduplicating items with same tab id in different groups
    val uniqueListItems = remember(listItems) {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ListItem>()
        for (it in listItems) {
            val key = when (it) {
                is ListItem.GroupHeader -> "group_${it.groupId}"
                is ListItem.Tab -> if (it.groupId != null) "group_${it.groupId}_tab_${it.tab.id}" else "tab_${it.tab.id}"
            }
            if (!seen.contains(key)) {
                seen.add(key)
                out.add(it)
            }
        }
        out
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(
            items = uniqueListItems,
            key = { _, item ->
                when (item) {
                    is ListItem.GroupHeader -> "group_${item.groupId}"
                    is ListItem.Tab -> if (item.groupId != null) "group_${item.groupId}_tab_${item.tab.id}" else "tab_${item.tab.id}"
                }
            }
        ) { index, item ->
            when (item) {
                is ListItem.GroupHeader -> {
                    GroupHeaderItem(
                        groupId = item.groupId,
                        title = item.title,
                        color = item.color,
                        tabCount = item.tabCount,
                        isExpanded = item.isExpanded,
                        contextId = item.contextId,
                        index = index,
                        dragDropState = dragDropState,
                        onHeaderClick = { onGroupClick(item.groupId) },
                        onOptionsClick = { onGroupOptionsClick(item.groupId) },
                        onDragEnd = { operation ->
                            scope.launch {
                                handleDragOperation(
                                    viewModel,
                                    operation,
                                    "group_${item.groupId}",
                                    null,
                                    tabs,
                                    groups
                                )
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
                is ListItem.Tab -> {
                    val group = groups.find { it.tabIds.contains(item.tab.id) }
                    TabListItem(
                        tab = item.tab,
                        isSelected = item.tab.id == selectedTabId,
                        isInGroup = item.isInGroup,
                        isLastInGroup = item.isLastInGroup,
                        groupId = item.groupId,
                        groupColor = group?.color,
                        index = index,
                        dragDropState = dragDropState,
                        onTabClick = { onTabClick(item.tab.id) },
                        onTabClose = { onTabClose(item.tab.id) },
                        onDragEnd = { operation ->
                            scope.launch {
                                handleDragOperation(
                                    viewModel,
                                    operation,
                                    item.tab.id,
                                    item.groupId,
                                    tabs,
                                    groups
                                )
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeaderItem(
    groupId: String,
    title: String,
    color: Int,
    tabCount: Int,
    isExpanded: Boolean,
    contextId: String?,
    index: Int,
    dragDropState: AdvancedDragDropState,
    onHeaderClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onDragEnd: (DragOperation) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = dragDropState.getTargetScale("group_$groupId"),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    val borderColor = if (contextId == null) Color(0xFFFF9800) else Color.Transparent
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .scale(scale)
            .advancedDraggable(
                id = "group_$groupId",
                index = index,
                dragDropState = dragDropState,
                isGroupHeader = true,
                groupId = groupId,
                onDragEnd = onDragEnd
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Color(color).copy(alpha = 0.15f))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onHeaderClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(color),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = title.ifEmpty { "Unnamed Group" },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = "$tabCount",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(Color(color).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Group options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TabListItem(
    tab: TabSessionState,
    isSelected: Boolean,
    isInGroup: Boolean,
    isLastInGroup: Boolean,
    groupId: String?,
    groupColor: Int?,
    index: Int,
    dragDropState: AdvancedDragDropState,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onDragEnd: (DragOperation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scale by animateFloatAsState(
        targetValue = dragDropState.getTargetScale(tab.id),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    // Check if currently being dragged
    val isBeingDragged = dragDropState.isDragging && dragDropState.draggedItemId == tab.id
    
    // When dragging, treat as ungrouped for uniform appearance
    val effectiveIsInGroup = isInGroup && !isBeingDragged
    val effectiveIsLastInGroup = isLastInGroup && !isBeingDragged
    
    // Determine border color
    val borderColor = when {
        groupColor != null && !isBeingDragged -> Color(groupColor) // Group color for tabs in groups
        tab.contextId == null -> Color(0xFFFF9800) // Orange for default profile
        else -> Color.Transparent
    }
    
    // Check if this tab is a grouping target
    val isGroupingTarget = dragDropState.isHoverTarget(tab.id) && 
                          dragDropState.feedbackState.operation is DragOperation.GroupWith
    
    // Determine corner radius based on position in group
    val cornerRadius = when {
        !effectiveIsInGroup -> 12.dp // Normal tabs have full rounding
        effectiveIsLastInGroup -> 12.dp // Last tab in group has bottom rounding
        else -> 0.dp // Other tabs in group have no rounding
    }
    
    // Determine which corners to round
    val shape = when {
        !effectiveIsInGroup -> RoundedCornerShape(12.dp) // All corners
        effectiveIsLastInGroup -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp) // Only bottom corners
        else -> RoundedCornerShape(0.dp) // No corners
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (effectiveIsInGroup) 24.dp else 12.dp,
                vertical = if (effectiveIsInGroup) 0.dp else 4.dp
            )
            .scale(scale)
            .draggableItem(tab.id, dragDropState)
            .advancedDraggable(
                id = tab.id,
                index = index,
                dragDropState = dragDropState,
                groupId = groupId,
                isInGroup = isInGroup,
                onDragEnd = onDragEnd
            )
            .clip(shape)
            .background(
                when {
                    isGroupingTarget -> Color(0xFF607D8B) // Grey background for grouping
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .then(
                // Draw borders: side + bottom for grouped tabs (not last), thinner full border for last, full 2.dp otherwise
                if (effectiveIsInGroup && !effectiveIsLastInGroup) {
                    Modifier.drawBehind {
                        val strokeWidth = 2.dp.toPx()
                        // Draw left border
                        drawLine(
                            color = borderColor,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                            strokeWidth = strokeWidth
                        )
                        // Draw right border
                        drawLine(
                            color = borderColor,
                            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            strokeWidth = strokeWidth
                        )
                        // Draw bottom border
                        drawLine(
                            color = borderColor,
                            start = androidx.compose.ui.geometry.Offset(0f, size.height),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
                } else if (effectiveIsInGroup && effectiveIsLastInGroup) {
                    // Last tab in group: use thinner border
                    Modifier.border(1.dp, borderColor, shape)
                } else {
                    // Ungrouped tabs: use normal 2.dp border
                    Modifier.border(2.dp, borderColor, shape)
                }
            )
            .clickable(onClick = onTabClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Favicon
        TabFaviconImage(
            tab = tab,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Tab title and URL
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = tab.content.title.ifEmpty { "New Tab" },
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = tab.content.url,
                fontSize = 12.sp,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Close button
        IconButton(
            onClick = onTabClose,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close tab",
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildListItemsFromOrder(
    tabs: List<TabSessionState>,
    groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>,
    expandedGroups: Set<String>,
    currentOrder: UnifiedTabOrder?
): List<ListItem> {
    val items = mutableListOf<ListItem>()
    val addedTabIds = mutableSetOf<String>()
    
    if (currentOrder == null || currentOrder.primaryOrder.isEmpty()) {
        // Fallback to old behavior if no order exists
        return buildListItemsFallback(tabs, groups, expandedGroups)
    }
    
    // Create lookup maps
    val tabsById = tabs.associateBy { it.id }
    val groupsById = groups.associateBy { it.id }
    
    // Process items in order
    for (orderItem in currentOrder.primaryOrder) {
        when (orderItem) {
            is UnifiedTabOrder.OrderItem.SingleTab -> {
                val tab = tabsById[orderItem.tabId]
                if (tab != null && tab.id !in addedTabIds) {
                    items.add(
                        ListItem.Tab(
                            tab = tab,
                            groupId = null,
                            isInGroup = false
                        )
                    )
                    addedTabIds.add(tab.id)
                }
            }
            is UnifiedTabOrder.OrderItem.TabGroup -> {
                val group = groupsById[orderItem.groupId]
                if (group != null && group.tabIds.any { it in tabsById }) {
                    // Add group header
                    items.add(
                        ListItem.GroupHeader(
                            groupId = group.id,
                            title = group.name,
                            color = group.color,
                            tabCount = group.tabCount,
                            isExpanded = expandedGroups.contains(group.id),
                            contextId = group.contextId
                        )
                    )
                    
                    // Add tabs in the group if expanded
                    if (expandedGroups.contains(group.id)) {
                        val validTabIds = group.tabIds.filter { it in tabsById && it !in addedTabIds }
                        validTabIds.forEachIndexed { index, tabId ->
                            val tab = tabsById[tabId]
                            if (tab != null) {
                                val isLastInGroup = (index == validTabIds.size - 1)
                                items.add(
                                    ListItem.Tab(
                                        tab = tab,
                                        groupId = group.id,
                                        isInGroup = true,
                                        isLastInGroup = isLastInGroup
                                    )
                                )
                                addedTabIds.add(tab.id)
                            }
                        }
                    } else {
                        // Even when collapsed, mark these tabs as added
                        group.tabIds.forEach { tabId ->
                            if (tabId in tabsById) {
                                addedTabIds.add(tabId)
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Add any remaining tabs that weren't in the order (shouldn't happen normally)
    tabs.forEach { tab ->
        if (tab.id !in addedTabIds) {
            items.add(
                ListItem.Tab(
                    tab = tab,
                    groupId = null,
                    isInGroup = false
                )
            )
            addedTabIds.add(tab.id)
        }
    }
    
    return items
}

private fun buildListItemsFallback(
    tabs: List<TabSessionState>,
    groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>,
    expandedGroups: Set<String>
): List<ListItem> {
    val items = mutableListOf<ListItem>()
    val addedTabIds = mutableSetOf<String>()
    
    // Collect all tabs that are in any group
    val groupedTabIds = mutableSetOf<String>()
    groups.forEach { group ->
        groupedTabIds.addAll(group.tabIds)
    }
    
    // Add groups and their tabs
    for (group in groups) {
        items.add(
            ListItem.GroupHeader(
                groupId = group.id,
                title = group.name,
                color = group.color,
                tabCount = group.tabCount,
                isExpanded = expandedGroups.contains(group.id),
                contextId = group.contextId
            )
        )
        
        if (expandedGroups.contains(group.id)) {
            // Only show tabs that actually exist in the tabs list and belong to this group
            val groupTabs = tabs.filter { tab -> 
                tab.id in group.tabIds && tab.id !in addedTabIds
            }
            groupTabs.forEachIndexed { index, tab ->
                val isLastInGroup = (index == groupTabs.size - 1)
                items.add(
                    ListItem.Tab(
                        tab = tab,
                        groupId = group.id,
                        isInGroup = true,
                        isLastInGroup = isLastInGroup
                    )
                )
                addedTabIds.add(tab.id)
            }
        } else {
            // Even when collapsed, mark these tabs as added
            tabs.filter { tab -> tab.id in group.tabIds }.forEach { tab ->
                addedTabIds.add(tab.id)
            }
        }
    }
    
    // Add ungrouped tabs - only those NOT in any group and not already added
    tabs.filter { tab -> 
        tab.id !in groupedTabIds && tab.id !in addedTabIds
    }.forEach { tab ->
        items.add(
            ListItem.Tab(
                tab = tab,
                groupId = null,
                isInGroup = false
            )
        )
        addedTabIds.add(tab.id)
    }
    
    return items
}

private suspend fun handleDragOperation(
    viewModel: TabViewModel,
    operation: DragOperation,
    draggedId: String,
    fromGroupId: String?,
    tabs: List<TabSessionState>,
    groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>
) {
    // Handle group header drag (draggedId starts with "group_")
    val isDraggingGroup = draggedId.startsWith("group_")
    val actualDraggedId = if (isDraggingGroup) draggedId.removePrefix("group_") else draggedId
    
    when (operation) {
        is DragOperation.GroupWith -> {
            if (isDraggingGroup) {
                // Dragging group header over ungrouped tab - add tab to group
                val targetTab = tabs.find { it.id == operation.targetId }
                val draggedGroup = groups.find { it.id == actualDraggedId }
                
                if (targetTab != null && draggedGroup != null) {
                    // Check context compatibility
                    if (targetTab.contextId == draggedGroup.contextId) {
                        android.util.Log.d("TabSheet", "Adding tab ${operation.targetId} to group $actualDraggedId")
                        viewModel.addTabToGroup(operation.targetId, actualDraggedId)
                    }
                }
            } else if (fromGroupId != null) {
                // Dragging grouped tab over ungrouped tab - remove from group and create new group
                val draggedTab = tabs.find { it.id == actualDraggedId }
                val targetTab = tabs.find { it.id == operation.targetId }
                
                android.util.Log.d("TabSheet", "Grouped tab over ungrouped: $actualDraggedId (from group $fromGroupId) and ${operation.targetId}")
                
                if (draggedTab != null && targetTab != null) {
                    if (draggedTab.contextId == targetTab.contextId) {
                        // First remove from current group
                        viewModel.removeTabFromGroup(actualDraggedId)
                        // Then create new group
                        kotlinx.coroutines.delay(50)
                        val contextId = draggedTab.contextId ?: targetTab.contextId
                        viewModel.createGroup(listOf(actualDraggedId, operation.targetId), contextId = contextId)
                    } else {
                        android.util.Log.d("TabSheet", "Context IDs don't match - cannot group")
                    }
                }
            } else {
                // Group two ungrouped tabs together
                val draggedTab = tabs.find { it.id == actualDraggedId }
                val targetTab = tabs.find { it.id == operation.targetId }
                
                android.util.Log.d("TabSheet", "Grouping tabs: $actualDraggedId and ${operation.targetId}")
                
                if (draggedTab != null && targetTab != null) {
                    // Check contextId compatibility
                    if (draggedTab.contextId == targetTab.contextId) {
                        val contextId = draggedTab.contextId ?: targetTab.contextId
                        android.util.Log.d("TabSheet", "Creating group with contextId: $contextId")
                        viewModel.createGroup(listOf(actualDraggedId, operation.targetId), contextId = contextId)
                    } else {
                        android.util.Log.d("TabSheet", "Context IDs don't match - cannot group")
                    }
                }
            }
        }
        
        is DragOperation.Reorder -> {
            if (isDraggingGroup) {
                // Reorder entire group
                android.util.Log.d("TabSheet", "Reorder group: $actualDraggedId to index ${operation.targetIndex}")
                viewModel.reorderGroup(actualDraggedId, operation.targetIndex)
            } else if (fromGroupId != null) {
                // Reordering within a group
                android.util.Log.d("TabSheet", "Reorder in group: $actualDraggedId to index ${operation.targetIndex}")
                
                // Find the target tab at the visual index
                val flatList = buildListItemsFallback(tabs, groups, emptySet())
                val targetItem = flatList.getOrNull(operation.targetIndex)
                
                if (targetItem is ListItem.Tab && targetItem.tab.id != actualDraggedId) {
                    android.util.Log.d("TabSheet", "Reorder target: ${targetItem.tab.id}")
                    viewModel.reorderTabInGroup(actualDraggedId, targetItem.tab.id, fromGroupId)
                }
            } else {
                // Reordering ungrouped tabs
                android.util.Log.d("TabSheet", "Reorder ungrouped: $actualDraggedId to index ${operation.targetIndex}")
                
                // Get only ungrouped tabs
                val ungroupedTabs = tabs.filter { tab ->
                    groups.none { group -> group.tabIds.contains(tab.id) }
                }
                
                val currentIndex = ungroupedTabs.indexOfFirst { it.id == actualDraggedId }
                val targetTab = ungroupedTabs.getOrNull(operation.targetIndex)
                
                if (currentIndex != -1 && targetTab != null && targetTab.id != actualDraggedId) {
                    val targetIndexInAll = tabs.indexOf(targetTab)
                    android.util.Log.d("TabSheet", "Reorder from $currentIndex to $targetIndexInAll")
                    viewModel.reorderTabs(tabs.indexOf(ungroupedTabs[currentIndex]), targetIndexInAll)
                }
            }
        }
        
        is DragOperation.MoveToGroup -> {
            val targetGroupId = operation.groupId.removePrefix("group_")
            
            if (isDraggingGroup && actualDraggedId != targetGroupId) {
                // Merging two groups
                android.util.Log.d("TabSheet", "Merging groups: $actualDraggedId into $targetGroupId")
                viewModel.mergeGroups(actualDraggedId, targetGroupId)
            } else if (!isDraggingGroup) {
                // Move tab to a group
                val targetGroup = groups.find { it.id == targetGroupId }
                val draggedTab = tabs.find { it.id == actualDraggedId }
                
                if (draggedTab != null && targetGroup != null) {
                    // Check contextId compatibility
                    if (draggedTab.contextId == targetGroup.contextId) {
                        if (fromGroupId != null && fromGroupId != targetGroupId) {
                            // Moving from one group to another
                            android.util.Log.d("TabSheet", "Moving tab from group $fromGroupId to $targetGroupId")
                            viewModel.removeTabFromGroup(actualDraggedId)
                            kotlinx.coroutines.delay(50)
                            viewModel.addTabToGroup(actualDraggedId, targetGroupId)
                        } else if (fromGroupId == null) {
                            // Adding ungrouped tab to group
                            android.util.Log.d("TabSheet", "Adding ungrouped tab $actualDraggedId to group $targetGroupId")
                            viewModel.addTabToGroup(actualDraggedId, targetGroupId)
                        }
                    }
                }
            }
        }
        
        is DragOperation.UngroupAndReorder -> {
            // Ungroup tab and place it at ungrouped position
            if (fromGroupId != null && !isDraggingGroup) {
                android.util.Log.d("TabSheet", "Ungrouping tab $actualDraggedId to index ${operation.targetIndex}")
                viewModel.removeTabFromGroup(actualDraggedId)
                
                // Position the ungrouped tab at the target index
                kotlinx.coroutines.delay(50)
                viewModel.moveTabToPosition(actualDraggedId, operation.targetIndex)
            }
        }
        
        DragOperation.None -> {
            // Handle dropping without a specific target
            // Issue 4-6: Ungroup if from a group, and position based on drop location
            if (fromGroupId != null && !isDraggingGroup) {
                // Dragging a grouped tab outside without a clear target - ungroup it
                android.util.Log.d("TabSheet", "Ungrouping tab $actualDraggedId (dropped without target)")
                viewModel.removeTabFromGroup(actualDraggedId)
                // The tab will automatically appear at the end of ungrouped tabs
            }
            // For other cases (ungrouped tab or group header), do nothing
        }
    }
}


