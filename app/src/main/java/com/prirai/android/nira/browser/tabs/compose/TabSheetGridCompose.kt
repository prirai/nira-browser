package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.launch

sealed class GridItem {
    data class GroupHeader(
        val groupId: String,
        val title: String,
        val color: Int,
        val tabCount: Int,
        val isExpanded: Boolean,
        val contextId: String?
    ) : GridItem()
    
    data class GroupRow(
        val groupId: String,
        val tabs: List<TabSessionState>,
        val groupColor: Int
    ) : GridItem()

    data class Tab(
        val tab: TabSessionState,
        val groupId: String? = null,
        val isInGroup: Boolean = false
    ) : GridItem()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabSheetGridView(
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
    
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val dragDropState = rememberTabDragDropState()
    
    // Build grid items from tabs and groups using unified order
    val gridItems = remember(tabs, groups, expandedGroups, currentOrder) {
        buildGridItems(tabs, groups, expandedGroups, currentOrder)
    }

    val uniqueGridItems = remember(gridItems) {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<GridItem>()
        for (it in gridItems) {
            val key = when (it) {
                is GridItem.GroupHeader -> "group_${it.groupId}"
                is GridItem.GroupRow -> "grouprow_${it.groupId}"
                is GridItem.Tab -> if (it.groupId != null) "group_${it.groupId}_tab_${it.tab.id}" else "tab_${it.tab.id}"
            }
            if (!seen.contains(key)) {
                seen.add(key)
                out.add(it)
            }
        }
        out
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = uniqueGridItems,
            key = { item ->
                when (item) {
                    is GridItem.GroupHeader -> "group_${item.groupId}"
                    is GridItem.GroupRow -> "grouprow_${item.groupId}"
                    is GridItem.Tab -> if (item.groupId != null) "group_${item.groupId}_tab_${item.tab.id}" else "tab_${item.tab.id}"
                }
            },
            span = { item ->
                androidx.compose.foundation.lazy.grid.GridItemSpan(
                    when (item) {
                        is GridItem.GroupHeader -> 2
                        is GridItem.GroupRow -> 2
                        else -> 1
                    }
                )
            }
        ) { item ->
            when (item) {
                is GridItem.GroupHeader -> {
                    GroupHeaderGridItem(
                        groupId = item.groupId,
                        title = item.title,
                        color = item.color,
                        tabCount = item.tabCount,
                        isExpanded = item.isExpanded,
                        contextId = item.contextId,
                        dragDropState = dragDropState,
                        onHeaderClick = { onGroupClick(item.groupId) },
                        onOptionsClick = { onGroupOptionsClick(item.groupId) },
                        onDragEnd = { draggedId, hoveredId, fromGroupId ->
                            scope.launch {
                                handleGridDragEnd(
                                    viewModel,
                                    draggedId,
                                    hoveredId,
                                    fromGroupId,
                                    tabs,
                                    groups
                                )
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
                is GridItem.GroupRow -> {
                    GroupTabsRow(
                        groupId = item.groupId,
                        tabs = item.tabs,
                        groupColor = item.groupColor,
                        selectedTabId = selectedTabId,
                        dragDropState = dragDropState,
                        onTabClick = onTabClick,
                        onTabClose = onTabClose,
                        onDragEnd = { draggedId, hoveredId, fromGroupId ->
                            scope.launch {
                                handleGridDragEnd(
                                    viewModel,
                                    draggedId,
                                    hoveredId,
                                    fromGroupId,
                                    tabs,
                                    groups
                                )
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
                is GridItem.Tab -> {
                    TabGridItem(
                        tab = item.tab,
                        isSelected = item.tab.id == selectedTabId,
                        isInGroup = item.isInGroup,
                        groupId = item.groupId,
                        dragDropState = dragDropState,
                        onTabClick = { onTabClick(item.tab.id) },
                        onTabClose = { onTabClose(item.tab.id) },
                        onDragEnd = { draggedId, hoveredId, fromGroupId ->
                            scope.launch {
                                handleGridDragEnd(
                                    viewModel,
                                    draggedId,
                                    hoveredId,
                                    fromGroupId,
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
private fun GroupHeaderGridItem(
    groupId: String,
    title: String,
    color: Int,
    tabCount: Int,
    isExpanded: Boolean,
    contextId: String?,
    dragDropState: TabDragDropState,
    onHeaderClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onDragEnd: (String, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHovered = dragDropState.isHovered("group_$groupId")
    val borderColor = if (contextId == null) Color(0xFFFF9800) else Color.Transparent
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .scale(if (isHovered) 1.05f else 1f)
            .draggableTab(
                uiItemId = "group_$groupId",
                logicalId = "group_$groupId",
                dragDropState = dragDropState,
                fromGroupId = null,
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
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = "$tabCount",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(Color(color).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
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
private fun GroupTabsRow(
    groupId: String,
    tabs: List<TabSessionState>,
    groupColor: Int,
    selectedTabId: String?,
    dragDropState: TabDragDropState,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onDragEnd: (String, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Horizontal scrollable row for group tabs
    // Show 3 tabs in full, rest partially visible
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lazyListItems(
            items = tabs,
            key = { tab -> "grouprow_${groupId}_tab_${tab.id}" }
        ) { tab ->
            TabGridItemCompact(
                tab = tab,
                isSelected = tab.id == selectedTabId,
                groupId = groupId,
                dragDropState = dragDropState,
                onTabClick = { onTabClick(tab.id) },
                onTabClose = { onTabClose(tab.id) },
                onDragEnd = onDragEnd
            )
        }
    }
}

@Composable
private fun TabGridItemCompact(
    tab: TabSessionState,
    isSelected: Boolean,
    groupId: String,
    dragDropState: TabDragDropState,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onDragEnd: (String, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isHovered = dragDropState.isHovered(tab.id)
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        tab.contextId == null -> Color(0xFFFF9800)
        else -> Color.Transparent
    }
    
    // Compact width to fit 3 tabs in view
    Column(
        modifier = modifier
            .width(110.dp)
            .height(150.dp)
            .scale(if (isHovered) 1.05f else 1f)
            .draggableTab(
                uiItemId = "group_${groupId}_tab_${tab.id}",
                logicalId = tab.id,
                dragDropState = dragDropState,
                fromGroupId = groupId,
                onDragEnd = onDragEnd
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onTabClick)
    ) {
        // Thumbnail with close button overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        ) {
            // Thumbnail - Load from ThumbnailStorage
            ThumbnailImageView(
                tab = tab,
                modifier = Modifier.fillMaxSize()
            )
            
            // Close button overlay
            IconButton(
                onClick = onTabClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(20.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        
        // Tab info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(6.dp)
        ) {
            Text(
                text = tab.content.title.ifEmpty { "New Tab" },
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TabGridItem(
    tab: TabSessionState,
    isSelected: Boolean,
    isInGroup: Boolean,
    groupId: String?,
    dragDropState: TabDragDropState,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onDragEnd: (String, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isHovered = dragDropState.isHovered(tab.id)
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        tab.contextId == null -> Color(0xFFFF9800)
        else -> Color.Transparent
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .scale(if (isHovered) 1.05f else 1f)
            .draggableTab(
                uiItemId = if (groupId != null) "group_${groupId}_tab_${tab.id}" else "tab_${tab.id}",
                logicalId = tab.id,
                dragDropState = dragDropState,
                fromGroupId = groupId,
                onDragEnd = onDragEnd
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onTabClick)
    ) {
        // Thumbnail with close button overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            // Thumbnail - Load from ThumbnailStorage
            ThumbnailImageView(
                tab = tab,
                modifier = Modifier.fillMaxSize()
            )
            
            // Close button overlay
            IconButton(
                onClick = onTabClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        // Tab info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
        ) {
            Text(
                text = tab.content.title.ifEmpty { "New Tab" },
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildGridItems(
    tabs: List<TabSessionState>,
    groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>,
    expandedGroups: Set<String>,
    order: UnifiedTabOrder?
): List<GridItem> {
    val items = mutableListOf<GridItem>()
    val addedTabIds = mutableSetOf<String>()
    
    // If we have an order, use it to maintain consistency with tab bar
    if (order != null) {
        for (orderItem in order.primaryOrder) {
            when (orderItem) {
                is UnifiedTabOrder.OrderItem.SingleTab -> {
                    val tab = tabs.find { it.id == orderItem.tabId }
                    if (tab != null && tab.id !in addedTabIds) {
                        items.add(
                            GridItem.Tab(
                                tab = tab,
                                groupId = null,
                                isInGroup = false
                            )
                        )
                        addedTabIds.add(tab.id)
                    }
                }
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    val group = groups.find { it.id == orderItem.groupId }
                    if (group != null) {
                        items.add(
                            GridItem.GroupHeader(
                                groupId = group.id,
                                title = group.name,
                                color = group.color,
                                tabCount = group.tabCount,
                                isExpanded = expandedGroups.contains(group.id),
                                contextId = group.contextId
                            )
                        )
                        
                        if (expandedGroups.contains(group.id)) {
                            // Use order from UnifiedTabOrder for group tabs
                            val groupTabs = orderItem.tabIds.mapNotNull { tabId ->
                                tabs.find { it.id == tabId && it.id !in addedTabIds }
                            }
                            
                            if (groupTabs.isNotEmpty()) {
                                items.add(
                                    GridItem.GroupRow(
                                        groupId = group.id,
                                        tabs = groupTabs,
                                        groupColor = group.color
                                    )
                                )
                                groupTabs.forEach { tab ->
                                    addedTabIds.add(tab.id)
                                }
                            }
                        } else {
                            // Mark these tabs as added even when collapsed
                            orderItem.tabIds.forEach { tabId ->
                                addedTabIds.add(tabId)
                            }
                        }
                    }
                }
            }
        }
        
        // Add any remaining tabs that aren't in the order
        tabs.filter { tab -> tab.id !in addedTabIds }.forEach { tab ->
            items.add(
                GridItem.Tab(
                    tab = tab,
                    groupId = null,
                    isInGroup = false
                )
            )
            addedTabIds.add(tab.id)
        }
    } else {
        // Fallback to old behavior if no order available
        val groupedTabIds = mutableSetOf<String>()
        groups.forEach { group ->
            groupedTabIds.addAll(group.tabIds)
        }
        
        for (group in groups) {
            items.add(
                GridItem.GroupHeader(
                    groupId = group.id,
                    title = group.name,
                    color = group.color,
                    tabCount = group.tabCount,
                    isExpanded = expandedGroups.contains(group.id),
                    contextId = group.contextId
                )
            )
            
            if (expandedGroups.contains(group.id)) {
                val groupTabs = tabs.filter { tab -> 
                    tab.id in group.tabIds && tab.id !in addedTabIds
                }
                
                if (groupTabs.isNotEmpty()) {
                    items.add(
                        GridItem.GroupRow(
                            groupId = group.id,
                            tabs = groupTabs,
                            groupColor = group.color
                        )
                    )
                    groupTabs.forEach { tab ->
                        addedTabIds.add(tab.id)
                    }
                }
            } else {
                tabs.filter { tab -> tab.id in group.tabIds }.forEach { tab ->
                    addedTabIds.add(tab.id)
                }
            }
        }
        
        tabs.filter { tab -> 
            tab.id !in groupedTabIds && tab.id !in addedTabIds
        }.forEach { tab ->
            items.add(
                GridItem.Tab(
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

private suspend fun handleGridDragEnd(
    viewModel: TabViewModel,
    draggedId: String,
    hoveredId: String?,
    fromGroupId: String?,
    tabs: List<TabSessionState>,
    groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>
) {
    val isDraggingGroup = draggedId.startsWith("group_")
    val isHoveringGroup = hoveredId?.startsWith("group_") == true
    
    // If no hover target and tab is from a group, ungroup it
    if (hoveredId == null && fromGroupId != null && !isDraggingGroup) {
        viewModel.removeTabFromGroup(draggedId)
        return
    }
    
    if (hoveredId == null) return
    
    when {
        // Dragging group over tab - merge group with tab
        isDraggingGroup && !isHoveringGroup -> {
            val groupId = draggedId.removePrefix("group_")
            val hoveredTab = tabs.find { it.id == hoveredId } ?: return
            val hoveredTabGroupId = groups.find { hoveredId in it.tabIds }?.id
            
            if (hoveredTabGroupId != null && hoveredTabGroupId != groupId) {
                // Merge into existing group
                viewModel.mergeGroups(groupId, hoveredTabGroupId)
            } else if (hoveredTabGroupId == null) {
                // Add ungrouped tab to dragged group
                viewModel.addTabToGroup(hoveredId, groupId)
            }
        }
        
        // Dragging group over group - merge groups
        isDraggingGroup && isHoveringGroup -> {
            val groupId = draggedId.removePrefix("group_")
            val hoveredGroupId = hoveredId.removePrefix("group_")
            if (groupId != hoveredGroupId) {
                viewModel.mergeGroups(groupId, hoveredGroupId)
            }
        }
        
        // Dragging tab over group - add to group
        !isDraggingGroup && isHoveringGroup -> {
            val hoveredGroupId = hoveredId.removePrefix("group_")
            if (fromGroupId != null && fromGroupId != hoveredGroupId) {
                // Move from one group to another
                viewModel.removeTabFromGroup(draggedId)
                // Small delay to ensure state is updated
                kotlinx.coroutines.delay(50)
                viewModel.addTabToGroup(draggedId, hoveredGroupId)
            } else if (fromGroupId == null) {
                // Add ungrouped tab to group
                viewModel.addTabToGroup(draggedId, hoveredGroupId)
            }
        }
        
        // Dragging tab over tab
        !isDraggingGroup && !isHoveringGroup -> {
            val hoveredTab = tabs.find { it.id == hoveredId } ?: return
            val hoveredTabGroupId = groups.find { hoveredId in it.tabIds }?.id
            val draggedTab = tabs.find { it.id == draggedId } ?: return
            
            // Check contextId compatibility - tabs with null contextId can't be grouped with anyone
            // and tabs with different non-null contextIds can't be grouped
            val canGroup = when {
                draggedTab.contextId == null || hoveredTab.contextId == null -> false
                draggedTab.contextId != hoveredTab.contextId -> false
                else -> true
            }
            
            when {
                // Both tabs ungrouped - create new group (if compatible)
                fromGroupId == null && hoveredTabGroupId == null && canGroup -> {
                    val contextId = draggedTab.contextId ?: hoveredTab.contextId
                    viewModel.createGroup(listOf(draggedId, hoveredId), contextId = contextId)
                }
                // Dragged from group to ungrouped tab - ungroup and create new (if compatible)
                fromGroupId != null && hoveredTabGroupId == null && canGroup -> {
                    viewModel.removeTabFromGroup(draggedId)
                    // Small delay to ensure state is updated
                    kotlinx.coroutines.delay(50)
                    val contextId = draggedTab.contextId ?: hoveredTab.contextId
                    viewModel.createGroup(listOf(draggedId, hoveredId), contextId = contextId)
                }
                // Dragged ungrouped to tab in group - add to group (if compatible)
                fromGroupId == null && hoveredTabGroupId != null -> {
                    val hoveredGroup = groups.find { it.id == hoveredTabGroupId }
                    if (hoveredGroup != null && draggedTab.contextId != null && 
                        hoveredGroup.contextId == draggedTab.contextId) {
                        viewModel.addTabToGroup(draggedId, hoveredTabGroupId)
                    }
                }
                // Both in different groups - move to hovered group (if compatible)
                fromGroupId != null && hoveredTabGroupId != null && fromGroupId != hoveredTabGroupId -> {
                    val hoveredGroup = groups.find { it.id == hoveredTabGroupId }
                    if (hoveredGroup != null && draggedTab.contextId != null && 
                        hoveredGroup.contextId == draggedTab.contextId) {
                        viewModel.removeTabFromGroup(draggedId)
                        // Small delay to ensure state is updated
                        kotlinx.coroutines.delay(50)
                        viewModel.addTabToGroup(draggedId, hoveredTabGroupId)
                    }
                }
                // Same group - reorder within group
                fromGroupId != null && hoveredTabGroupId != null && fromGroupId == hoveredTabGroupId -> {
                    viewModel.reorderTabInGroup(draggedId, hoveredId, fromGroupId)
                }
            }
        }
    }
}
