package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val tabItems = currentOrder.primaryOrder.filterIsInstance<UnifiedTabOrder.OrderItem.SingleTab>()
                
                items(
                    items = tabItems,
                    key = { item -> item.tabId }
                ) { item ->
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
                                    orderManager = orderManager,
                                    modifier = Modifier.draggableHandle()
                                )
                            }
                            
                            // Add divider after tabs
                            Spacer(modifier = Modifier.width(8.dp))
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
    
    if (isSelected) {
        // Selected tab: show pill shape with background
        Card(
            modifier = modifier
                .width(120.dp)
                .height(56.dp)
                .dragFeedbackScale(isTarget, isDragging)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTabClick(tab.id) },
                        onLongPress = {
                            dragDropState.startDrag(tab.id)
                        }
                    )
                },
            shape = RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS),
            elevation = CardDefaults.cardElevation(
                defaultElevation = getTabElevation(isDragging)
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isTarget) getDragTargetColor(DragFeedback.Reorder) else MaterialTheme.colorScheme.primaryContainer
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
    } else {
        // Unfocused tab: minimal design - just content, no decoration
        Row(
            modifier = modifier
                .width(120.dp)
                .height(56.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTabClick(tab.id) },
                        onLongPress = {
                            dragDropState.startDrag(tab.id)
                        }
                    )
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = tab.content.title.takeIf { it.isNotBlank() } ?: tab.content.url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            IconButton(
                onClick = { onTabClose(tab.id) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}



