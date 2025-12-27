package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import sh.calvin.reorderable.*

/**
 * Grid view with 2D drag & drop, spanning groups, and compact layout
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabSheetGridCompose(
    tabs: List<TabSessionState>,
    orderManager: TabOrderManager,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onChangeGroupColor: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    val scope = rememberCoroutineScope()
    val order by orderManager.currentOrder.collectAsState()
    val dragDropState = rememberTabDragDropState()
    val gridState = rememberLazyGridState()
    
    val reorderableLazyGridState = rememberReorderableLazyGridState(gridState) { from, to ->
        scope.launch {
            orderManager.reorderItem(from.index, to.index)
        }
    }
    
    order?.let { currentOrder ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize()
        ) {
            currentOrder.primaryOrder.forEach { item ->
                when (item) {
                    is UnifiedTabOrder.OrderItem.SingleTab -> {
                        val tab = tabs.find { it.id == item.tabId }
                        if (tab != null) {
                            item(
                                key = item.tabId,
                                span = { GridItemSpan(1) }
                            ) {
                                ReorderableItem(reorderableLazyGridState, key = item.tabId) { isDragging ->
                                    val scope = this // Capture the ReorderableCollectionItemScope
                                    GridTabItem(
                                        tab = tab,
                                        isSelected = tab.id == selectedTabId,
                                        isDragging = isDragging,
                                        dragDropState = dragDropState,
                                        onTabClick = onTabClick,
                                        onTabClose = onTabClose,
                                        orderManager = orderManager,
                                        reorderableItemScope = scope,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                    is UnifiedTabOrder.OrderItem.TabGroup -> {
                        item(
                            key = item.groupId,
                            span = { GridItemSpan(columns) }
                        ) {
                            ReorderableItem(reorderableLazyGridState, key = item.groupId) { isDragging ->
                                val scope = this // Capture the ReorderableCollectionItemScope
                                GridGroupItem(
                                    group = item,
                                    tabs = tabs,
                                    selectedTabId = selectedTabId,
                                    isDragging = isDragging,
                                    dragDropState = dragDropState,
                                    onTabClick = onTabClick,
                                    onTabClose = onTabClose,
                                    orderManager = orderManager,
                                    onChangeGroupColor = onChangeGroupColor,
                                    reorderableItemScope = scope,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridTabItem(
    tab: TabSessionState,
    isSelected: Boolean,
    isDragging: Boolean,
    dragDropState: TabDragDropState,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    orderManager: TabOrderManager,
    reorderableItemScope: ReorderableCollectionItemScope,
    modifier: Modifier = Modifier
) {
    val isTarget = dragDropState.currentTarget is DragTarget.Tab &&
                   (dragDropState.currentTarget as DragTarget.Tab).tabId == tab.id
    val interactionSource = remember { MutableInteractionSource() }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(TabVisualConstants.TAB_HEIGHT_GRID)
            .dragFeedbackScale(isTarget, isDragging)
            .clickable { onTabClick(tab.id) },
        shape = RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS),
        elevation = CardDefaults.cardElevation(
            defaultElevation = getTabElevation(isDragging)
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isTarget -> getDragTargetColor(DragFeedback.HIGHLIGHT_TAB)
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (tab.contextId == null) {
            BorderStroke(2.dp, Color(0xFFFF9800))
        } else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Drag handle in top-left corner
            IconButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(32.dp)
                    .then(with(reorderableItemScope) {
                        Modifier.longPressDraggableHandle(
                            onDragStarted = {},
                            onDragStopped = {},
                            interactionSource = interactionSource
                        )
                    }),
                onClick = {}
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Drag handle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Favicon placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                
                IconButton(
                    onClick = { onTabClose(tab.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tab title
            Text(
                text = tab.content.title.takeIf { it.isNotBlank() } ?: "New Tab",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // URL
            Text(
                text = tab.content.url.takeIf { it.isNotBlank() } ?: "about:blank",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            }
        }
    }
}

@Composable
private fun GridGroupItem(
    group: UnifiedTabOrder.OrderItem.TabGroup,
    tabs: List<TabSessionState>,
    selectedTabId: String?,
    isDragging: Boolean,
    dragDropState: TabDragDropState,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    orderManager: TabOrderManager,
    onChangeGroupColor: (String) -> Unit,
    reorderableItemScope: ReorderableCollectionItemScope,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isTarget = dragDropState.currentTarget is DragTarget.Group &&
                   (dragDropState.currentTarget as DragTarget.Group).groupId == group.groupId
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .dragFeedbackScale(isTarget, false),
        shape = RoundedCornerShape(TabVisualConstants.GROUP_CORNER_RADIUS),
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget) {
                getDragTargetColor(DragFeedback.HIGHLIGHT_GROUP)
            } else {
                Color(group.color).copy(alpha = 0.15f)
            }
        )
    ) {
        Column {
            // Group header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TabVisualConstants.GROUP_HEADER_HEIGHT)
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                scope.launch {
                                    orderManager.toggleGroupExpansion(group.groupId)
                                }
                            },
                            onLongPress = { showMenu = true }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag handle",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .then(with(reorderableItemScope) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = {},
                                onDragStopped = {},
                                interactionSource = interactionSource
                            )
                        }),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                
                // Group color indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(group.color))
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Icon(
                    imageVector = if (group.isExpanded) {
                        Icons.Default.ExpandMore
                    } else {
                        Icons.Default.ChevronRight
                    },
                    contentDescription = "Toggle group"
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = group.groupName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${group.tabIds.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Group options")
                }
            }
            
            // Group context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename Group") },
                    onClick = { showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Change Color") },
                    onClick = {
                        onChangeGroupColor(group.groupId)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Ungroup All") },
                    onClick = {
                        scope.launch {
                            orderManager.disbandGroup(group.groupId)
                        }
                        showMenu = false
                    }
                )
            }
            
            // Tabs in horizontal scroll if expanded
            AnimatedVisibility(visible = group.isExpanded) {
                LazyRow(
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(group.tabIds) { tabId ->
                        val tab = tabs.find { it.id == tabId }
                        if (tab != null) {
                            GridGroupedTabItem(
                                tab = tab,
                                groupId = group.groupId,
                                isSelected = tab.id == selectedTabId,
                                dragDropState = dragDropState,
                                onTabClick = onTabClick,
                                onTabClose = onTabClose,
                                orderManager = orderManager
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridGroupedTabItem(
    tab: TabSessionState,
    groupId: String,
    isSelected: Boolean,
    dragDropState: TabDragDropState,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    orderManager: TabOrderManager
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    val isDragging = false
    
    Card(
        modifier = Modifier
            .width(100.dp)
            .height(100.dp)
            .dragFeedbackScale(false, isDragging)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTabClick(tab.id) },
                    onLongPress = {
                        dragDropState.startDrag(tab.id, groupId)
                        showMenu = true
                    }
                )
            },
        shape = RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS),
        elevation = CardDefaults.cardElevation(
            defaultElevation = getTabElevation(isDragging)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                
                IconButton(
                    onClick = { onTabClose(tab.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = tab.content.title.takeIf { it.isNotBlank() } ?: "New Tab",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
    
    // Tab context menu
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text("Remove from Group") },
            onClick = {
                scope.launch {
                    orderManager.removeTabFromGroup(tab.id)
                }
                showMenu = false
            }
        )
    }
}
