package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.launch

sealed class ListItem {
    data class Tab(
        val tab: TabSessionState,
    ) : ListItem()
}

@Composable
fun TabSheetListView(
    viewModel: TabViewModel,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState) -> Unit = { _ -> },
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsState()
    val selectedTabId by viewModel.selectedTabId.collectAsState()
    val currentOrder by viewModel.currentOrder.collectAsState()
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dragDropState = rememberAdvancedDragDropState()
    
    // Build list items from tabs using UnifiedTabOrder
    val listItems = remember(tabs, currentOrder) {
        buildListItemsFromOrder(tabs, currentOrder)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(
            items = listItems,
            key = { _, item ->
                when (item) {
                    is ListItem.Tab -> "tab_${item.tab.id}"
                }
            }
        ) { index, item ->
            when (item) {
                is ListItem.Tab -> {
                    TabListItem(
                        tab = item.tab,
                        isSelected = item.tab.id == selectedTabId,
                        index = index,
                        dragDropState = dragDropState,
                        onTabClick = { onTabClick(item.tab.id) },
                        onTabClose = { onTabClose(item.tab.id) },
                        onTabLongPress = { onTabLongPress(item.tab) },
                        onDragEnd = { operation ->
                            scope.launch {
                                handleDragOperation(
                                    viewModel,
                                    operation,
                                    item.tab.id,
                                    tabs
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
private fun TabListItem(
    tab: TabSessionState,
    isSelected: Boolean,
    index: Int,
    dragDropState: AdvancedDragDropState,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit = {},
    onDragEnd: (DragOperation) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = dragDropState.getTargetScale(tab.id),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    val isBeingDragged = dragDropState.isDragging && dragDropState.draggedItemId == tab.id
    
    // Determine border color
    val borderColor = when {
        tab.contextId == null -> Color(0xFFFF9800) // Orange for default profile
        else -> Color.Transparent
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .scale(scale)
            .draggableItem(tab.id, dragDropState)
            .advancedDraggable(
                id = tab.id,
                index = index,
                dragDropState = dragDropState,
                onDragEnd = onDragEnd
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            )
            .then(
                Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp))
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
    currentOrder: UnifiedTabOrder?
): List<ListItem> {
    val items = mutableListOf<ListItem>()
    val addedTabIds = mutableSetOf<String>()
    
    // Simply render all tabs in their order
    if (currentOrder != null) {
        currentOrder.primaryOrder.forEach { item ->
            if (item is UnifiedTabOrder.OrderItem.SingleTab) {
                val tab = tabs.find { it.id == item.tabId }
                if (tab != null && tab.id !in addedTabIds) {
                    items.add(ListItem.Tab(tab))
                    addedTabIds.add(tab.id)
                }
            }
        }
    }
    
    // Add any remaining tabs
    tabs.forEach { tab ->
        if (tab.id !in addedTabIds) {
            items.add(ListItem.Tab(tab))
            addedTabIds.add(tab.id)
        }
    }
    
    return items
}

private suspend fun handleDragOperation(
    viewModel: TabViewModel,
    operation: DragOperation,
    draggedId: String,
    tabs: List<TabSessionState>
) {
    if (operation is DragOperation.Reorder) {
        val draggedTab = tabs.find { it.id == draggedId } ?: return
        
        // Simple reorder in flat list
        val currentIndex = tabs.indexOf(draggedTab)
        if (currentIndex != -1 && currentIndex != operation.targetIndex) {
            viewModel.reorderTabs(currentIndex, operation.targetIndex)
        }
    }
}
