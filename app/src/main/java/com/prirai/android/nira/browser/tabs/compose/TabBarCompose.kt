package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val order by orderManager.currentOrder.collectAsState()
    val dragDropState = rememberTabDragDropState()
    val listState = rememberLazyListState()
    
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        scope.launch {
            orderManager.reorderItem(from.index, to.index)
        }
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        order?.let { currentOrder ->
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = currentOrder.primaryOrder,
                    key = { item ->
                        when (item) {
                            is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId
                            is UnifiedTabOrder.OrderItem.TabGroup -> item.groupId
                        }
                    }
                ) { item ->
                    ReorderableItem(reorderableLazyListState, key = when (item) {
                        is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId
                        is UnifiedTabOrder.OrderItem.TabGroup -> item.groupId
                    }) { isDragging ->
                        when (item) {
                            is UnifiedTabOrder.OrderItem.SingleTab -> {
                                val tab = tabs.find { it.id == item.tabId }
                                if (tab != null) {
                                    TabBarTabItem(
                                        tab = tab,
                                        isSelected = tab.id == selectedTabId,
                                        isDragging = isDragging,
                                        dragDropState = dragDropState,
                                        onTabClick = onTabClick,
                                        onTabClose = onTabClose,
                                        orderManager = orderManager,
                                        modifier = Modifier.draggableHandle()
                                    )
                                }
                            }
                            is UnifiedTabOrder.OrderItem.TabGroup -> {
                                TabBarGroupItem(
                                    group = item,
                                    tabs = tabs,
                                    selectedTabId = selectedTabId,
                                    isDragging = isDragging,
                                    dragDropState = dragDropState,
                                    onTabClick = onTabClick,
                                    orderManager = orderManager,
                                    modifier = Modifier.draggableHandle()
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
private fun TabBarTabItem(
    tab: TabSessionState,
    isSelected: Boolean,
    isDragging: Boolean,
    dragDropState: TabDragDropState,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    orderManager: TabOrderManager,
    modifier: Modifier = Modifier
) {
    val isTarget = dragDropState.currentTarget is DragTarget.Tab && 
                   (dragDropState.currentTarget as DragTarget.Tab).tabId == tab.id
    
    Card(
        modifier = modifier
            .width(120.dp)
            .height(56.dp)
            .dragFeedbackScale(isTarget, isDragging)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTabClick(tab.id) },
                    onLongPress = {
                        dragDropState.startDrag(tab.id, null)
                    }
                )
            },
        shape = RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS),
        elevation = CardDefaults.cardElevation(
            defaultElevation = getTabElevation(isDragging)
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isTarget -> getDragTargetColor(DragFeedback.GroupWith)
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = tab.content.title.takeIf { it.isNotBlank() } ?: tab.content.url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = { onTabClose(tab.id) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TabBarGroupItem(
    group: UnifiedTabOrder.OrderItem.TabGroup,
    tabs: List<TabSessionState>,
    selectedTabId: String?,
    isDragging: Boolean,
    dragDropState: TabDragDropState,
    onTabClick: (String) -> Unit,
    orderManager: TabOrderManager,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isTarget = dragDropState.currentTarget is DragTarget.Group &&
                   (dragDropState.currentTarget as DragTarget.Group).groupId == group.groupId
    
    Card(
        modifier = modifier
            .width(140.dp)
            .height(56.dp)
            .dragFeedbackScale(isTarget, false)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        scope.launch {
                            orderManager.toggleGroupExpansion(group.groupId)
                        }
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
        }
    }
}

suspend fun handleTabDrop(
    dragDropState: TabDragDropState,
    orderManager: TabOrderManager
) {
    val draggedTabId = dragDropState.draggedTabId ?: return
    val target = dragDropState.currentTarget
    
    when (target) {
        is DragTarget.Tab -> {
            orderManager.createGroup(
                tabIds = listOf(draggedTabId, target.tabId),
                groupName = "New Group",
                color = generateRandomGroupColor()
            )
        }
        is DragTarget.Group -> {
            orderManager.addTabToGroup(draggedTabId, target.groupId)
        }
        is DragTarget.InsertionPoint -> {
            if (target.inGroupId != null) {
                orderManager.addTabToGroup(draggedTabId, target.inGroupId, target.index)
            }
        }
        else -> {}
    }
    
    dragDropState.endDrag()
}
