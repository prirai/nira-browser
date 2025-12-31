package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.launch

sealed class GridItem {
    data class Tab(
        val tab: TabSessionState,
    ) : GridItem()
}

@Composable
fun TabSheetGridView(
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
    val gridState = rememberLazyGridState()
    val dragDropState = rememberTabDragDropState()
    
    // Build grid items from tabs and groups using unified order
    val gridItems = remember(tabs, currentOrder) {
        buildGridItems(tabs, currentOrder)
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
            items = gridItems,
            key = { item ->
                when (item) {
                    is GridItem.Tab -> "tab_${item.tab.id}"
                }
            },
            span = { 
                androidx.compose.foundation.lazy.grid.GridItemSpan(1)
            }
        ) { item ->
            when (item) {
                is GridItem.Tab -> {
                    TabGridItem(
                        tab = item.tab,
                        isSelected = item.tab.id == selectedTabId,
                        dragDropState = dragDropState,
                        onTabClick = { onTabClick(item.tab.id) },
                        onTabClose = { onTabClose(item.tab.id) },
                        onTabLongPress = { onTabLongPress(item.tab) },
                        onDragEnd = { draggedId, hoveredId ->
                            scope.launch {
                                handleGridDragEnd(
                                    viewModel,
                                    draggedId,
                                    hoveredId,
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
private fun TabGridItemUnified(
    tab: TabSessionState,
    isSelected: Boolean,
    dragDropState: TabDragDropState,
    onDragEnd: (String, String?) -> Unit,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    modifier: Modifier = Modifier,
    compactWidth: androidx.compose.ui.unit.Dp? = null
) {
    val isHovered = dragDropState.isHovered(tab.id)
    
    val m = modifier
        .then(if (compactWidth != null) Modifier.width(compactWidth) else Modifier.fillMaxWidth())
        .height(if (compactWidth != null) 150.dp else 180.dp)
        .scale(if (isHovered) 1.05f else 1f)
        .draggableTab(
            uiItemId = "tab_${tab.id}",
            logicalId = tab.id,
            dragDropState = dragDropState,
            onDragEnd = onDragEnd
        )

    Card(
        modifier = m,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.clickable(onClick = onTabClick)) {
            // Thumbnail at top - takes most of the space
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .zIndex(0f)) {
                ThumbnailImageView(tab = tab, modifier = Modifier.fillMaxSize())
            }

            // Divider between thumbnail and footer
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

            // Title and close button at bottom
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
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onTabClose, modifier = Modifier.size(32.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabGridItem(
    tab: TabSessionState,
    isSelected: Boolean,
    dragDropState: TabDragDropState,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit = {},
    onDragEnd: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Use unified material3 expressive grid item for all tabs
    TabGridItemUnified(
        tab = tab,
        isSelected = isSelected,
        dragDropState = dragDropState,
        onTabClick = onTabClick,
        onTabClose = onTabClose,
        onDragEnd = onDragEnd,
        modifier = modifier,
        compactWidth = null
    )
}

private fun buildGridItems(
    tabs: List<TabSessionState>,
    order: UnifiedTabOrder?
): List<GridItem> {
    val items = mutableListOf<GridItem>()
    val addedTabIds = mutableSetOf<String>()
    
    // Simply render all tabs in order
    if (order != null) {
        order.primaryOrder.forEach { orderItem ->
            if (orderItem is UnifiedTabOrder.OrderItem.SingleTab) {
                val tab = tabs.find { it.id == orderItem.tabId }
                if (tab != null && tab.id !in addedTabIds) {
                    items.add(GridItem.Tab(tab))
                    addedTabIds.add(tab.id)
                }
            }
        }
    }
    
    // Add any remaining tabs
    tabs.forEach { tab ->
        if (tab.id !in addedTabIds) {
            items.add(GridItem.Tab(tab))
            addedTabIds.add(tab.id)
        }
    }
    
    return items
}

private suspend fun handleGridDragEnd(
    viewModel: TabViewModel,
    draggedId: String,
    hoveredId: String?,
    tabs: List<TabSessionState>
) {
    if (hoveredId == null) return
    
    // Simple reorder
    val draggedTab = tabs.find { it.id == draggedId }
    val hoveredTab = tabs.find { it.id == hoveredId }
    
    if (draggedTab != null && hoveredTab != null && draggedId != hoveredId) {
        val draggedIndex = tabs.indexOf(draggedTab)
        val hoveredIndex = tabs.indexOf(hoveredTab)
        if (draggedIndex != -1 && hoveredIndex != -1) {
            viewModel.reorderTabs(draggedIndex, hoveredIndex)
        }
    }
}
