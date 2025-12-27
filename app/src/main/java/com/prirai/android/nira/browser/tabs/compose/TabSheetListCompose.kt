package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prirai.android.nira.R
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.launch

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
        val isInGroup: Boolean = false
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
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dragDropState = rememberTabDragDropState()
    
    // Build list items from tabs and groups
    val listItems = remember(tabs, groups, expandedGroups) {
        buildListItems(tabs, groups, expandedGroups)
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
        items(
            items = uniqueListItems,
            key = { item ->
                when (item) {
                    is ListItem.GroupHeader -> "group_${item.groupId}"
                    is ListItem.Tab -> if (item.groupId != null) "group_${item.groupId}_tab_${item.tab.id}" else "tab_${item.tab.id}"
                }
            }
        ) { item ->
            when (item) {
                is ListItem.GroupHeader -> {
                    GroupHeaderItem(
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
                                handleDragEnd(
                                    viewModel,
                                    draggedId,
                                    hoveredId,
                                    fromGroupId,
                                    tabs,
                                    groups
                                )
                            }
                        },
                        modifier = Modifier
                    )
                }
                is ListItem.Tab -> {
                    TabListItem(
                        tab = item.tab,
                        isSelected = item.tab.id == selectedTabId,
                        isInGroup = item.isInGroup,
                        groupId = item.groupId,
                        dragDropState = dragDropState,
                        onTabClick = { onTabClick(item.tab.id) },
                        onTabClose = { onTabClose(item.tab.id) },
                        onDragEnd = { draggedId, hoveredId, fromGroupId ->
                            scope.launch {
                                handleDragEnd(
                                    viewModel,
                                    draggedId,
                                    hoveredId,
                                    fromGroupId,
                                    tabs,
                                    groups
                                )
                            }
                        },
                        modifier = Modifier
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
    dragDropState: TabDragDropState,
    onHeaderClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onDragEnd: (String, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHovered = dragDropState.isHovered("group_$groupId")
    val scale by animateDpAsState(if (isHovered) 4.dp else 0.dp)
    val borderColor = if (contextId == null) Color(0xFFFF9800) else Color.Transparent
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
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
    groupId: String?,
    dragDropState: TabDragDropState,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onDragEnd: (String, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isHovered = dragDropState.isHovered(tab.id)
    val borderColor = if (tab.contextId == null) Color(0xFFFF9800) else Color.Transparent
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isInGroup) 24.dp else 12.dp,
                vertical = 4.dp
            )
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

private fun buildListItems(
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
            groupTabs.forEach { tab ->
                items.add(
                    ListItem.Tab(
                        tab = tab,
                        groupId = group.id,
                        isInGroup = true
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

private suspend fun handleDragEnd(
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
