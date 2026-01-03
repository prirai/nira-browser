package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
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
import sh.calvin.reorderable.*


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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TabSheetListView(
    viewModel: TabViewModel,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState, Boolean) -> Unit = { _, _ -> },
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
    
    // Menu state
    var menuTab by remember { mutableStateOf<TabSessionState?>(null) }
    var menuIsInGroup by remember { mutableStateOf(false) }
    var showTabMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var menuGroupId by remember { mutableStateOf<String?>(null) }
    var menuGroupName by remember { mutableStateOf<String?>(null) }
    
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
    
    // Use reorderable library for unified drag-and-drop
    val reorderableState = sh.calvin.reorderable.rememberReorderableLazyColumnState(listState) { from, to ->
        scope.launch {
            val result = UnifiedDragDropHandler.handleDrop(
                viewModel = viewModel,
                fromIndex = from.index,
                toIndex = to.index,
                items = uniqueListItems,
                tabs = tabs,
                groups = groups
            )
            
            // Provide feedback based on result
            when (result) {
                is DropResult.GroupCreated -> {
                    android.util.Log.d("TabSheet", "Group created successfully")
                }
                is DropResult.Error -> {
                    android.util.Log.e("TabSheet", "Drop failed: ${result.message}")
                }
                else -> {}
            }
        }
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
            ReorderableItem(reorderableState, key = when (item) {
                is ListItem.GroupHeader -> "group_${item.groupId}"
                is ListItem.Tab -> if (item.groupId != null) "group_${item.groupId}_tab_${item.tab.id}" else "tab_${item.tab.id}"
            }) { isDragging ->
                when (item) {
                    is ListItem.GroupHeader -> {
                        GroupHeaderItem(
                            groupId = item.groupId,
                            title = item.title,
                            color = item.color,
                            tabCount = item.tabCount,
                            isExpanded = item.isExpanded,
                            contextId = item.contextId,
                            isDragging = isDragging,
                            onHeaderClick = { onGroupClick(item.groupId) },
                            onOptionsClick = { 
                                menuGroupId = item.groupId
                                menuGroupName = item.title
                                showGroupMenu = true
                                onGroupOptionsClick(item.groupId)
                            },
                            modifier = Modifier
                                .animateItem()
                                .longPressDraggableHandle(
                                    onDragStarted = {},
                                    onDragStopped = {}
                                )
                        )
                    }
                    is ListItem.Tab -> {
                        val group = groups.find { it.tabIds.contains(item.tab.id) }
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    onTabClose(item.tab.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        )
                        
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            },
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true
                        ) {
                            TabListItem(
                                tab = item.tab,
                                isSelected = item.tab.id == selectedTabId,
                                isInGroup = item.isInGroup,
                                isLastInGroup = item.isLastInGroup,
                                groupId = item.groupId,
                                groupColor = group?.color,
                                isDragging = isDragging,
                                onTabClick = { onTabClick(item.tab.id) },
                                onTabClose = { onTabClose(item.tab.id) },
                                onTabLongPress = { 
                                    menuTab = item.tab
                                    menuIsInGroup = item.isInGroup
                                    showTabMenu = true
                                    onTabLongPress(item.tab, item.isInGroup)
                                },
                                modifier = Modifier
                                    .animateItem()
                                    .longPressDraggableHandle(
                                        onDragStarted = {},
                                        onDragStopped = {}
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Show tab menu
    if (showTabMenu && menuTab != null) {
        TabContextMenu(
            tab = menuTab!!,
            isInGroup = menuIsInGroup,
            onDismiss = { showTabMenu = false },
            viewModel = viewModel,
            scope = scope
        )
    }
    
    // Show group menu  
    if (showGroupMenu && menuGroupId != null) {
        GroupContextMenu(
            groupId = menuGroupId!!,
            groupName = menuGroupName ?: "Group",
            onDismiss = { showGroupMenu = false },
            viewModel = viewModel,
            scope = scope
        )
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
    isDragging: Boolean,
    onHeaderClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    // Swipe state for swipe-right-for-menu gesture
    val offsetX = remember { Animatable(0f) }
    
    // Group container with color background - NO border/stroke
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .scale(scale),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = if (!isExpanded) 12.dp else 0.dp, bottomEnd = if (!isExpanded) 12.dp else 0.dp),
        color = Color(color).copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(groupId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value > 100f) {
                                    onOptionsClick()
                                }
                                offsetX.animateTo(0f, animationSpec = tween(200))
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, animationSpec = tween(200))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount > 0) {
                                scope.launch {
                                    val newValue = (offsetX.value + dragAmount).coerceAtMost(150f)
                                    offsetX.snapTo(newValue)
                                }
                                change.consume()
                            }
                        }
                    )
                }
                .clickable(onClick = onHeaderClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(color),
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = title.ifEmpty { "Unnamed Group" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Count badge with border
                Surface(
                    shape = CircleShape,
                    color = Color(color).copy(alpha = 0.2f),
                    border = BorderStroke(2.dp, Color(color))
                ) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$tabCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(color)
                        )
                    }
                }
            }
            
            IconButton(onClick = onOptionsClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Group options",
                    tint = Color(color)
                )
            }
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
    isDragging: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    // Swipe state for swipe-right-for-menu gesture
    val offsetX = remember { Animatable(0f) }
    
    // When dragging, treat as ungrouped for uniform appearance
    val effectiveIsInGroup = isInGroup && !isDragging
    val effectiveIsLastInGroup = isLastInGroup && !isDragging
    
    // Determine background color - use group color background for tabs in groups
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        effectiveIsInGroup && groupColor != null -> Color(groupColor).copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    // Determine corner radius based on position in group
    val shape = when {
        !effectiveIsInGroup -> RoundedCornerShape(12.dp)
        effectiveIsLastInGroup -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
        else -> RoundedCornerShape(0.dp)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (effectiveIsInGroup) 8.dp else 12.dp,
                vertical = if (effectiveIsInGroup) 0.dp else 4.dp
            )
            .scale(scale)
            .clip(shape)
            .background(backgroundColor)
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value > 100f) {
                                onTabLongPress()
                            }
                            offsetX.animateTo(0f, animationSpec = tween(200))
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, animationSpec = tween(200))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount > 0) {
                            scope.launch {
                                val newValue = (offsetX.value + dragAmount).coerceAtMost(150f)
                                offsetX.snapTo(newValue)
                            }
                            change.consume()
                        }
                    }
                )
            }
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



