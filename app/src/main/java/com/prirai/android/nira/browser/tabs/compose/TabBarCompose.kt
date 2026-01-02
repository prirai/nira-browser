package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import sh.calvin.reorderable.*

/**
 * Horizontal tab bar with drag & drop support (Reorderable v3.0.0)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBarCompose(
    tabs: List<TabSessionState>,
    orderManager: TabOrderManager,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: ((TabSessionState, Boolean) -> Unit)? = null,
    onGroupLongPress: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val order by orderManager.currentOrder.collectAsState()
    val dragDropState = rememberTabDragDropState()
    val listState = rememberLazyListState()
    
    // Auto-scroll to selected tab when it changes
    LaunchedEffect(selectedTabId, order) {
        val currentOrder = order
        if (selectedTabId != null && currentOrder != null) {
            // Find index of selected tab in the order
            val selectedIndex = currentOrder.primaryOrder.indexOfFirst { item ->
                when (item) {
                    is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId == selectedTabId
                    is UnifiedTabOrder.OrderItem.TabGroup -> selectedTabId in item.tabIds
                }
            }
            if (selectedIndex >= 0) {
                listState.animateScrollToItem(selectedIndex)
            }
        }
    }
    
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        scope.launch {
            val currentOrderSize = order?.primaryOrder?.size ?: 0
            // Validate indices before calling reorderItem
            if (from.index >= 0 && from.index < currentOrderSize && 
                to.index >= 0 && to.index < currentOrderSize) {
                orderManager.reorderItem(from.index, to.index)
            } else {
                android.util.Log.w("TabBarCompose", 
                    "Invalid reorder indices: from=${from.index}, to=${to.index}, size=$currentOrderSize")
            }
        }
    }
    
    var showMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        // Swipe down gesture can be used to show menu/options
                        // This would need to be connected to the actual menu system
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 100) {
                            showMenu = true
                            change.consume()
                        }
                    }
                )
            },
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        order?.let { currentOrder ->
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                currentOrder.primaryOrder.forEachIndexed { orderIndex, item ->
                    when (item) {
                        is UnifiedTabOrder.OrderItem.SingleTab -> {
                            item(key = item.tabId) {
                                ReorderableItem(reorderableLazyListState, key = item.tabId) { isDragging ->
                                    Row {
                                        val tab = tabs.find { it.id == item.tabId }
                                        if (tab != null) {
                                            TabBarTabItem(
                                                tab = tab,
                                                isSelected = tab.id == selectedTabId,
                                                isDragging = isDragging,
                                                dragDropState = dragDropState,
                                                onTabClick = onTabClick,
                                                onTabClose = onTabClose,
                                                onTabLongPress = { onTabLongPress?.invoke(tab, false) },
                                                orderManager = orderManager,
                                                modifier = Modifier.longPressDraggableHandle()
                                            )
                                        }
                                        
                                        // Add divider after ungrouped tabs
                                        val nextItem = currentOrder.primaryOrder.getOrNull(orderIndex + 1)
                                        if (nextItem is UnifiedTabOrder.OrderItem.SingleTab) {
                                            VerticalDivider(
                                                modifier = Modifier
                                                    .height(40.dp)
                                                    .padding(vertical = 8.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        } else if (nextItem != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                        is UnifiedTabOrder.OrderItem.TabGroup -> {
                            // Group header
                            item(key = "${item.groupId}_header") {
                                ReorderableItem(reorderableLazyListState, key = item.groupId) { isDragging ->
                                    Row {
                                        TabBarGroupHeader(
                                            group = item,
                                            tabs = tabs,
                                            selectedTabId = selectedTabId,
                                            isDragging = isDragging,
                                            dragDropState = dragDropState,
                                            onGroupLongPress = { onGroupLongPress?.invoke(item.groupId) },
                                            orderManager = orderManager,
                                            modifier = Modifier.longPressDraggableHandle()
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                }
                            }
                            
                            // Group tabs island (if expanded)
                            if (item.isExpanded) {
                                item(key = "${item.groupId}_island") {
                                    // Container for all tabs in the group (island view)
                                    Surface(
                                        modifier = Modifier
                                            .padding(start = 4.dp, end = 4.dp),
                                        shape = RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS),
                                        color = Color(item.color).copy(alpha = 0.08f),
                                        tonalElevation = 1.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            item.tabIds.forEach { tabId ->
                                                val tab = tabs.find { it.id == tabId }
                                                if (tab != null) {
                                                    TabBarTabItem(
                                                        tab = tab,
                                                        isSelected = tab.id == selectedTabId,
                                                        isDragging = false,
                                                        dragDropState = dragDropState,
                                                        onTabClick = onTabClick,
                                                        onTabClose = onTabClose,
                                                        onTabLongPress = { onTabLongPress?.invoke(tab, true) },
                                                        orderManager = orderManager,
                                                        modifier = Modifier,
                                                        isInGroup = true,
                                                        groupColor = Color(item.color)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Spacer after group
                            val nextItem = currentOrder.primaryOrder.getOrNull(orderIndex + 1)
                            if (nextItem != null) {
                                item(key = "${item.groupId}_spacer") {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBarTabItem(
    tab: TabSessionState,
    isSelected: Boolean,
    isDragging: Boolean,
    dragDropState: TabDragDropState,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: () -> Unit = {},
    orderManager: TabOrderManager,
    modifier: Modifier = Modifier,
    isInGroup: Boolean = false,
    groupColor: Color? = null
) {
    val isTarget = dragDropState.currentTarget is DragTarget.Tab && 
                   (dragDropState.currentTarget as DragTarget.Tab).tabId == tab.id
    
    // Swipe state for swipe-to-close and swipe-for-menu gestures
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var isBeingDragged by remember { mutableStateOf(false) }
    
    val swipeModifier = Modifier.pointerInput(tab.id) {
        detectVerticalDragGestures(
            onDragStart = {
                isBeingDragged = true
            },
            onDragEnd = {
                scope.launch {
                    when {
                        offsetY.value < -100f -> {
                            // Swipe up threshold met - close tab
                            offsetY.animateTo(
                                -500f,
                                animationSpec = tween(300)
                            )
                            onTabClose(tab.id)
                        }
                        offsetY.value > 100f -> {
                            // Swipe down threshold met - show menu
                            offsetY.animateTo(0f, animationSpec = tween(200))
                            onTabLongPress()
                        }
                        else -> {
                            // Snap back
                            offsetY.animateTo(0f, animationSpec = tween(200))
                        }
                    }
                    isBeingDragged = false
                }
            },
            onDragCancel = {
                scope.launch {
                    offsetY.animateTo(0f, animationSpec = tween(200))
                    isBeingDragged = false
                }
            },
            onVerticalDrag = { change, dragAmount ->
                // Allow both upward (close) and downward (menu) swipes
                scope.launch {
                    val newValue = (offsetY.value + dragAmount).coerceIn(-500f, 150f)
                    offsetY.snapTo(newValue)
                }
                change.consume()
            }
        )
    }
    
    if (isSelected) {
        // Selected tab: show pill shape with background
        Card(
            modifier = modifier
                .width(120.dp)
                .height(40.dp)
                .graphicsLayer {
                    translationY = offsetY.value
                    alpha = 1f - (kotlin.math.abs(offsetY.value) / 500f) * 0.7f
                    scaleX = 1f - (kotlin.math.abs(offsetY.value) / 500f) * 0.3f
                    scaleY = 1f - (kotlin.math.abs(offsetY.value) / 500f) * 0.3f
                }
                .then(swipeModifier)
                .dragFeedbackScale(isTarget, isDragging)
                .then(
                    if (isInGroup && groupColor != null) {
                        Modifier.border(2.dp, groupColor, RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS))
                    } else {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS))
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTabClick(tab.id) },
                        onLongPress = { onTabLongPress() }
                    )
                },
            shape = RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS),
            elevation = CardDefaults.cardElevation(
                defaultElevation = getTabElevation(isDragging)
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isTarget) getDragTargetColor(DragFeedback.GroupWith) else MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                TabFaviconImage(
                    tab = tab,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = tab.content.title.takeIf { it.isNotBlank() } ?: tab.content.url,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        // Unfocused tab: minimal design - just content, no decoration
        Row(
            modifier = modifier
                .width(120.dp)
                .height(40.dp)
                .graphicsLayer {
                    translationY = offsetY.value
                    alpha = 1f - (kotlin.math.abs(offsetY.value) / 500f) * 0.7f
                    scaleX = 1f - (kotlin.math.abs(offsetY.value) / 500f) * 0.3f
                    scaleY = 1f - (kotlin.math.abs(offsetY.value) / 500f) * 0.3f
                }
                .then(swipeModifier)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTabClick(tab.id) },
                        onLongPress = { onTabLongPress() }
                    )
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            TabFaviconImage(
                tab = tab,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = tab.content.title.takeIf { it.isNotBlank() } ?: tab.content.url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TabBarGroupHeader(
    group: UnifiedTabOrder.OrderItem.TabGroup,
    tabs: List<TabSessionState>,
    selectedTabId: String?,
    isDragging: Boolean,
    dragDropState: TabDragDropState,
    onGroupLongPress: () -> Unit = {},
    orderManager: TabOrderManager,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isTarget = dragDropState.currentTarget is DragTarget.Group &&
                   (dragDropState.currentTarget as DragTarget.Group).groupId == group.groupId
    
    // Swipe state for swipe-down-for-menu gesture
    val offsetY = remember { Animatable(0f) }
    
    Card(
        modifier = modifier
            .width(140.dp)
            .height(40.dp)
            .dragFeedbackScale(isTarget, false)
            .pointerInput(group.groupId) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value > 100f) {
                                // Swipe down threshold met - show menu
                                onGroupLongPress()
                            }
                            // Always snap back
                            offsetY.animateTo(0f, animationSpec = tween(200))
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetY.animateTo(0f, animationSpec = tween(200))
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // Only allow downward swipes
                        if (dragAmount > 0) {
                            scope.launch {
                                val newValue = (offsetY.value + dragAmount).coerceAtMost(150f)
                                offsetY.snapTo(newValue)
                            }
                            change.consume()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        scope.launch {
                            orderManager.toggleGroupExpansion(group.groupId)
                        }
                    },
                    onLongPress = {
                        onGroupLongPress()
                    }
                )
            },
        shape = RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS),
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget) {
                getDragTargetColor(DragFeedback.MoveToGroup)
            } else {
                Color(group.color).copy(alpha = 0.15f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chevron icon indicating expand/collapse state
            Icon(
                imageVector = if (group.isExpanded) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = if (group.isExpanded) "Collapse group" else "Expand group",
                modifier = Modifier.size(20.dp),
                tint = Color(group.color)
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(group.color))
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.groupName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.tabIds.size} tabs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Add '+' button to create new tab in group
            IconButton(
                onClick = {
                    scope.launch {
                        orderManager.addNewTabToGroup(group.groupId)
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add tab to group",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * TabBarCompose with integrated menu support
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBarComposeWithMenus(
    tabs: List<TabSessionState>,
    orderManager: TabOrderManager,
    viewModel: TabViewModel,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var menuTab by remember { mutableStateOf<TabSessionState?>(null) }
    var menuIsInGroup by remember { mutableStateOf(false) }
    var showTabMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var menuGroupId by remember { mutableStateOf<String?>(null) }
    var menuGroupName by remember { mutableStateOf<String?>(null) }
    
    TabBarCompose(
        tabs = tabs,
        orderManager = orderManager,
        selectedTabId = selectedTabId,
        onTabClick = onTabClick,
        onTabClose = onTabClose,
        onTabLongPress = { tab, isInGroup ->
            menuTab = tab
            menuIsInGroup = isInGroup
            showTabMenu = true
        },
        onGroupLongPress = { groupId ->
            menuGroupId = groupId
            // Find group name
            val order = orderManager.currentOrder.value
            menuGroupName = order?.primaryOrder?.filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>()
                ?.find { it.groupId == groupId }?.groupName ?: "Group"
            showGroupMenu = true
        },
        modifier = modifier
    )
    
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

