package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import sh.calvin.reorderable.*

/**
 * Vertical list view with drag & drop, grouping, and expansion
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabSheetListCompose(
    tabs: List<TabSessionState>,
    orderManager: TabOrderManager,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onChangeGroupColor: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val order by orderManager.currentOrder.collectAsState()
    val dragDropState = rememberTabDragDropState()
    val listState = rememberLazyListState()
    
    val reorderableLazyColumnState = rememberReorderableLazyColumnState(listState) { from, to ->
        scope.launch {
            orderManager.reorderItem(from.index, to.index)
        }
    }
    
    order?.let { currentOrder ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(
                items = currentOrder.primaryOrder,
                key = { item ->
                    when (item) {
                        is UnifiedTabOrder.OrderItem.SingleTab -> "tab_${item.tabId}"
                        is UnifiedTabOrder.OrderItem.TabGroup -> "group_${item.groupId}"
                    }
                }
            ) { item ->
                ReorderableItem(reorderableLazyColumnState, key = when (item) {
                    is UnifiedTabOrder.OrderItem.SingleTab -> "tab_${item.tabId}"
                    is UnifiedTabOrder.OrderItem.TabGroup -> "group_${item.groupId}"
                }) { isDragging ->
                    val scope = this // Capture the ReorderableCollectionItemScope
                    when (item) {
                        is UnifiedTabOrder.OrderItem.SingleTab -> {
                            val tab = tabs.find { it.id == item.tabId }
                            if (tab != null) {
                                ListTabItem(
                                    tab = tab,
                                    isSelected = tab.id == selectedTabId,
                                    isDragging = isDragging,
                                    dragDropState = dragDropState,
                                    onTabClick = onTabClick,
                                    onTabClose = onTabClose,
                                    orderManager = orderManager,
                                    reorderableItemScope = scope,
                                    reorderableState = reorderableLazyColumnState,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        is UnifiedTabOrder.OrderItem.TabGroup -> {
                            ListGroupItem(
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
                                reorderableState = reorderableLazyColumnState,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListTabItem(
    tab: TabSessionState,
    isSelected: Boolean,
    isDragging: Boolean,
    dragDropState: TabDragDropState,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    orderManager: TabOrderManager,
    reorderableItemScope: ReorderableCollectionItemScope,
    reorderableState: ReorderableLazyListState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isTarget = dragDropState.currentTarget is DragTarget.Tab &&
                   (dragDropState.currentTarget as DragTarget.Tab).tabId == tab.id
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabVisualConstants.TAB_HEIGHT)
                .dragFeedbackScale(isTarget, isDragging),
            shape = getTabShape(false, false, false),
            elevation = CardDefaults.cardElevation(
                defaultElevation = getTabElevation(isDragging)
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isTarget -> getDragTargetColor(DragFeedback.HIGHLIGHT_TAB)
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
            ),
            border = if (tab.contextId == null) {
                BorderStroke(2.dp, Color(0xFFFF9800))
            } else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onTabClick(tab.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle indicator - this is the draggable area
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
                
                // Favicon
                FaviconImage(
                    tab = tab,
                    size = 40.dp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                
                // Tab info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = tab.content.title.takeIf { it.isNotBlank() } ?: "New Tab",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.content.url.takeIf { it.isNotBlank() } ?: "about:blank",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Close button
                IconButton(
                    onClick = { onTabClose(tab.id) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        
        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Group with...") },
                onClick = {
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.GroupWork, "Group")
                }
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = {
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, "Duplicate")
                }
            )
        }
    }
}

@Composable
private fun ListGroupItem(
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
    reorderableState: ReorderableLazyListState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isTarget = dragDropState.currentTarget is DragTarget.Group &&
                   (dragDropState.currentTarget as DragTarget.Group).groupId == group.groupId
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .dragFeedbackScale(isTarget, false)
    ) {
        // Group Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = getGroupHeaderShape(),
            colors = CardDefaults.cardColors(
                containerColor = if (isTarget) {
                    getDragTargetColor(DragFeedback.HIGHLIGHT_GROUP)
                } else {
                    Color(group.color).copy(alpha = 0.15f)
                }
            ),
            border = BorderStroke(1.dp, Color(group.color).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TabVisualConstants.GROUP_HEADER_HEIGHT)
                    .clickable {
                        scope.launch {
                            orderManager.toggleGroupExpansion(group.groupId)
                        }
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle - makes group draggable
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
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(group.color))
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Expand/collapse icon
                Icon(
                    imageVector = if (group.isExpanded) {
                        Icons.Default.ExpandMore
                    } else {
                        Icons.Default.ChevronRight
                    },
                    contentDescription = if (group.isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Group name
                Text(
                    text = group.groupName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Tab count badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(group.color).copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = "${group.tabIds.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(group.color),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                // More options button
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Group options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Group context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Rename Group") },
                onClick = {
                    showRenameDialog = true
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, "Rename")
                }
            )
            DropdownMenuItem(
                text = { Text("Change Color") },
                onClick = {
                    onChangeGroupColor(group.groupId)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Palette, "Color")
                }
            )
            DropdownMenuItem(
                text = { Text("Ungroup All") },
                onClick = {
                    scope.launch {
                        orderManager.disbandGroup(group.groupId)
                    }
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.CallSplit, "Ungroup")
                }
            )
        }
        
        // Rename dialog
        if (showRenameDialog) {
            var newName by remember { mutableStateOf(group.groupName) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Group") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Group Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            orderManager.renameGroup(group.groupId, newName)
                        }
                        showRenameDialog = false
                    }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Group tabs (if expanded)
        AnimatedVisibility(visible = group.isExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(TabVisualConstants.TAB_SPACING_IN_GROUP)
            ) {
                group.tabIds.forEachIndexed { index, tabId ->
                    val tab = tabs.find { it.id == tabId }
                    if (tab != null) {
                        GroupedTabItem(
                            tab = tab,
                            groupId = group.groupId,
                            groupColor = Color(group.color),
                            isFirst = index == 0,
                            isLast = index == group.tabIds.size - 1,
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

@Composable
private fun GroupedTabItem(
    tab: TabSessionState,
    groupId: String,
    groupColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    isSelected: Boolean,
    dragDropState: TabDragDropState,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    orderManager: TabOrderManager
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    val isDragging = false
    
    Box(modifier = Modifier.fillMaxWidth()) {
        TabCard(
            tab = tab,
            isSelected = isSelected,
            isDragging = isDragging,
            isInGroup = true,
            isFirstInGroup = isFirst,
            isLastInGroup = isLast,
            groupColor = groupColor,
            showDragHandle = true,
            onTabClick = { onTabClick(tab.id) },
            onTabClose = { onTabClose(tab.id) },
            onLongPress = {
                dragDropState.startDrag(tab.id, groupId)
                showMenu = true
            },
            modifier = Modifier.height(TabVisualConstants.TAB_HEIGHT)
        )
        
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
                },
                leadingIcon = {
                    Icon(Icons.Default.RemoveCircle, "Remove")
                }
            )
            DropdownMenuItem(
                text = { Text("Move to New Group") },
                onClick = {
                    scope.launch {
                        orderManager.removeTabFromGroup(tab.id)
                        // Create new group
                        orderManager.createGroup(
                            listOf(tab.id),
                            "New Group",
                            generateRandomGroupColor()
                        )
                    }
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.DriveFileMove, "Move")
                }
            )
        }
    }
}
