package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import sh.calvin.reorderable.*



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
    
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    
    // Menu state
    var menuTab by remember { mutableStateOf<TabSessionState?>(null) }
    var menuIsInGroup by remember { mutableStateOf(false) }
    var showTabMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var menuGroupId by remember { mutableStateOf<String?>(null) }
    var menuGroupName by remember { mutableStateOf<String?>(null) }
    
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
    
    // Use reorderable library for unified drag-and-drop
    val reorderableState = sh.calvin.reorderable.rememberReorderableLazyGridState(gridState) { from, to ->
        scope.launch {
            // Convert grid items to list items for unified handler
            val listItems = uniqueGridItems.map { gridItem ->
                when (gridItem) {
                    is GridItem.GroupHeader -> ListItem.GroupHeader(
                        groupId = gridItem.groupId,
                        title = gridItem.title,
                        color = gridItem.color,
                        tabCount = gridItem.tabCount,
                        isExpanded = gridItem.isExpanded,
                        contextId = gridItem.contextId
                    )
                    is GridItem.Tab -> ListItem.Tab(
                        tab = gridItem.tab,
                        groupId = gridItem.groupId,
                        isInGroup = gridItem.isInGroup,
                        isLastInGroup = false
                    )
                    is GridItem.GroupRow -> {
                        // GroupRow items are full-width, treat as group header for dragging
                        val group = groups.find { it.id == gridItem.groupId }
                        ListItem.GroupHeader(
                            groupId = gridItem.groupId,
                            title = group?.name ?: "Group",
                            color = gridItem.groupColor,
                            tabCount = gridItem.tabs.size,
                            isExpanded = true,
                            contextId = group?.contextId
                        )
                    }
                }
            }
            
            val result = UnifiedDragDropHandler.handleDrop(
                viewModel = viewModel,
                fromIndex = from.index,
                toIndex = to.index,
                items = listItems,
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
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
                        is GridItem.GroupHeader -> 3
                        is GridItem.GroupRow -> 3
                        else -> 1
                    }
                )
            }
        ) { item ->
            ReorderableItem(reorderableState, key = when (item) {
                is GridItem.GroupHeader -> "group_${item.groupId}"
                is GridItem.GroupRow -> "grouprow_${item.groupId}"
                is GridItem.Tab -> if (item.groupId != null) "group_${item.groupId}_tab_${item.tab.id}" else "tab_${item.tab.id}"
            }) { isDragging ->
                when (item) {
                    is GridItem.GroupHeader -> {
                        GroupHeaderGridItem(
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
                    is GridItem.GroupRow -> {
                        GroupTabsRow(
                            groupId = item.groupId,
                            tabs = item.tabs,
                            groupColor = item.groupColor,
                            selectedTabId = selectedTabId,
                            isDragging = isDragging,
                            onTabClick = onTabClick,
                            onTabClose = onTabClose,
                            onTabLongPress = onTabLongPress,
                            modifier = Modifier
                                .animateItem()
                                .longPressDraggableHandle(
                                    onDragStarted = {},
                                    onDragStopped = {}
                                )
                        )
                    }
                    is GridItem.Tab -> {
                        TabGridItem(
                            tab = item.tab,
                            isSelected = item.tab.id == selectedTabId,
                            isInGroup = item.isInGroup,
                            groupId = item.groupId,
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
private fun GroupHeaderGridItem(
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
    val borderColor = if (contextId == null) Color(0xFFFF9800) else Color.Transparent
    
    // Swipe state for swipe-right-for-menu gesture
    val offsetX = remember { Animatable(0f) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .scale(if (isDragging) 1.05f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(color).copy(alpha = 0.15f))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .pointerInput(groupId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value > 100f) {
                                // Swipe right threshold met - show menu
                                onOptionsClick()
                            }
                            // Always snap back
                            offsetX.animateTo(0f, animationSpec = tween(200))
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, animationSpec = tween(200))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        // Only allow rightward swipes
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
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(color),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = title.ifEmpty { "Unnamed Group" },
            fontSize = 12.sp,
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
    }
}

@Composable
private fun GroupTabsRow(
    groupId: String,
    tabs: List<TabSessionState>,
    groupColor: Int,
    selectedTabId: String?,
    isDragging: Boolean,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState, Boolean) -> Unit,
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
                isDragging = false,
                onTabClick = { onTabClick(tab.id) },
                onTabClose = { onTabClose(tab.id) },
                onTabLongPress = { onTabLongPress(tab, true) }
            )
        }
    }
}

@Composable
private fun TabGridItemUnified(
    tab: TabSessionState,
    isSelected: Boolean,
    isInGroup: Boolean,
    groupId: String?,
    isDragging: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
    thumbnailHeight: androidx.compose.ui.unit.Dp = 100.dp,
    compactWidth: androidx.compose.ui.unit.Dp? = null
) {
    val scope = rememberCoroutineScope()
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        tab.contextId == null -> Color(0xFFFF9800)
        else -> Color.Transparent
    }

    val m = modifier
        .then(if (compactWidth != null) Modifier.width(compactWidth) else Modifier.fillMaxWidth())
        .height(if (compactWidth != null) 150.dp else 180.dp)
        .scale(if (isDragging) 1.05f else 1f)

    Card(
        modifier = m,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier
                .fillMaxSize()
                .pointerInput(tab.id) {
                    detectTapGestures(
                        onPress = {
                            val pressStartTime = System.currentTimeMillis()
                            
                            // Wait for release
                            val released = tryAwaitRelease()
                            
                            if (released) {
                                val pressDuration = System.currentTimeMillis() - pressStartTime
                                
                                // If held for a "long-press" duration but released before drag threshold
                                // Show menu. Typical long-press is ~500ms, drag threshold is similar.
                                // We want menu on quick long-press, drag on extended hold.
                                if (pressDuration in 300..600) {
                                    onTabLongPress()
                                }
                            }
                        },
                        onTap = { onTabClick() }
                    )
                }
            ) {
                // Thumbnail at top - takes most of the space
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .zIndex(0f)) {
                    ThumbnailImageView(tab = tab, modifier = Modifier.fillMaxSize())
                }

                // Divider between thumbnail and footer
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                // Title at bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.content.title.ifEmpty { "New Tab" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Close button in top-right corner
            IconButton(
                onClick = { onTabClose() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .zIndex(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = CircleShape
                        )
                        .padding(2.dp)
                )
            }
        }
    }
}

@Composable
private fun TabGridItemCompact(
    tab: TabSessionState,
    isSelected: Boolean,
    groupId: String,
    isDragging: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    TabGridItemUnified(
        tab = tab,
        isSelected = isSelected,
        isInGroup = true,
        groupId = groupId,
        isDragging = isDragging,
        onTabClick = onTabClick,
        onTabClose = onTabClose,
        onTabLongPress = onTabLongPress,
        modifier = modifier,
        thumbnailHeight = 90.dp,
        compactWidth = 110.dp
    )
}

@Composable
private fun TabGridItem(
    tab: TabSessionState,
    isSelected: Boolean,
    isInGroup: Boolean,
    groupId: String?,
    isDragging: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Use unified material3 expressive grid item for all tabs
    TabGridItemUnified(
        tab = tab,
        isSelected = isSelected,
        isInGroup = isInGroup,
        groupId = groupId,
        isDragging = isDragging,
        onTabClick = onTabClick,
        onTabClose = onTabClose,
        onTabLongPress = onTabLongPress,
        modifier = modifier,
        thumbnailHeight = 120.dp,
        compactWidth = null
    )
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

