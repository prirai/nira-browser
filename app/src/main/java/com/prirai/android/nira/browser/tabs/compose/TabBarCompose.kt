package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Horizontal tab bar with Chromium-style drag & drop support
 * Uses two-layer rendering: static layer + floating drag layer
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBarCompose(
    tabs: List<TabSessionState>,
    viewModel: TabViewModel,
    orderManager: TabOrderManager,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val order by orderManager.currentOrder.collectAsState()
    val listState = rememberLazyListState()

    // Create drag coordinator
    val coordinator = rememberDragCoordinator(
        scope = scope,
        viewModel = viewModel,
        orderManager = orderManager
    )

    // Build items from order
    val items = remember(order, tabs) {
        buildBarItems(order, tabs)
    }

    // Auto-scroll to selected tab
    LaunchedEffect(selectedTabId, order) {
        val currentOrder = order
        if (selectedTabId != null && currentOrder != null) {
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

    Box(modifier = modifier.fillMaxWidth()) {
        // Static layer - main tab bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp
        ) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(items, key = { it.id }) { item ->
                    when (item) {
                        is BarItem.SingleTab -> {
                            TabPill(
                                tab = item.tab,
                                isSelected = item.tab.id == selectedTabId,
                                coordinator = coordinator,
                                onTabClick = onTabClick,
                                onTabClose = onTabClose,
                                modifier = Modifier
                                    .animateItem()
                                    .draggableItem(
                                        itemType = DraggableItemType.Tab(item.tab.id),
                                        coordinator = coordinator
                                    )
                                    .dropTarget(
                                        id = item.tab.id,
                                        type = DropTargetType.TAB,
                                        coordinator = coordinator,
                                        metadata = mapOf("tabId" to item.tab.id)
                                    )
                                    .dragVisualFeedback(item.tab.id, coordinator)
                            )
                        }

                        is BarItem.Group -> {
                            GroupPill(
                                group = item,
                                isSelected = selectedTabId in item.tabIds,
                                coordinator = coordinator,
                                onTabClick = onTabClick,
                                onTabClose = onTabClose,
                                modifier = Modifier
                                    .animateItem()
                                    .draggableItem(
                                        itemType = DraggableItemType.Group(item.groupId),
                                        coordinator = coordinator
                                    )
                                    .dropTarget(
                                        id = item.groupId,
                                        type = DropTargetType.GROUP_HEADER,
                                        coordinator = coordinator,
                                        metadata = mapOf<String, Any>(
                                            "groupId" to item.groupId,
                                            "contextId" to (item.contextId ?: "")
                                        )
                                    )
                                    .dragVisualFeedback(item.groupId, coordinator)
                            )
                        }
                    }
                }
            }
        }

        // Drag layer - floating item that follows pointer
        DragLayer(coordinator = coordinator) { draggedItem ->
            when (draggedItem) {
                is DraggableItemType.Tab -> {
                    val tab = tabs.find { it.id == draggedItem.tabId }
                    if (tab != null) {
                        TabPill(
                            tab = tab,
                            isSelected = false,
                            coordinator = coordinator,
                            onTabClick = {},
                            onTabClose = {},
                            modifier = Modifier
                        )
                    }
                }

                is DraggableItemType.Group -> {
                    val item = items.find {
                        it is BarItem.Group && it.groupId == draggedItem.groupId
                    } as? BarItem.Group
                    if (item != null) {
                        GroupPill(
                            group = item,
                            isSelected = false,
                            coordinator = coordinator,
                            onTabClick = {},
                            onTabClose = {},
                            modifier = Modifier
                        )
                    }
                }
            }
        }
    }
}

/**
 * Build bar items from order
 */
private fun buildBarItems(order: UnifiedTabOrder?, tabs: List<TabSessionState>): List<BarItem> {
    if (order == null) return tabs.map { BarItem.SingleTab(it) }

    return order.primaryOrder.mapNotNull { orderItem ->
        when (orderItem) {
            is UnifiedTabOrder.OrderItem.SingleTab -> {
                tabs.find { it.id == orderItem.tabId }?.let { BarItem.SingleTab(it) }
            }

            is UnifiedTabOrder.OrderItem.TabGroup -> {
                val groupTabs = orderItem.tabIds.mapNotNull { tabId ->
                    tabs.find { it.id == tabId }
                }
                if (groupTabs.isNotEmpty()) {
                    BarItem.Group(
                        groupId = orderItem.groupId,
                        groupName = orderItem.groupName,
                        color = orderItem.color,
                        contextId = groupTabs.first().contextId,
                        tabs = groupTabs,
                        tabIds = orderItem.tabIds,
                        isExpanded = orderItem.isExpanded
                    )
                } else null
            }
        }
    }
}

/**
 * Sealed class for bar items
 */
sealed class BarItem {
    abstract val id: String

    data class SingleTab(val tab: TabSessionState) : BarItem() {
        override val id = tab.id
    }

    data class Group(
        val groupId: String,
        val groupName: String,
        val color: Int,
        val contextId: String?,
        val tabs: List<TabSessionState>,
        val tabIds: List<String>,
        val isExpanded: Boolean
    ) : BarItem() {
        override val id = groupId
    }
}

/**
 * Tab pill composable
 */
@Composable
private fun TabPill(
    tab: TabSessionState,
    isSelected: Boolean,
    coordinator: DragCoordinator,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .widthIn(min = 120.dp, max = 200.dp)
            .clickable { onTabClick(tab.id) },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Favicon
            tab.content.icon?.let { icon ->
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Title
            Text(
                text = tab.content.title.ifEmpty { "New Tab" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Close button
            IconButton(
                onClick = { onTabClose(tab.id) },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Group pill composable
 */
@Composable
private fun GroupPill(
    group: BarItem.Group,
    isSelected: Boolean,
    coordinator: DragCoordinator,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = group.isExpanded

    Surface(
        modifier = modifier
            .height(40.dp)
            .widthIn(min = if (expanded) 240.dp else 120.dp, max = 400.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(group.color).copy(alpha = 0.2f),
        border = BorderStroke(2.dp, Color(group.color))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Group name
            Text(
                text = group.groupName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(group.color),
                modifier = Modifier.weight(1f, fill = false)
            )

            // Tab count
            Text(
                text = "${group.tabs.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                Spacer(Modifier.width(4.dp))

                // Show tab favicons in group
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    group.tabs.take(3).forEach { tab ->
                        tab.content.icon?.let { icon ->
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onTabClick(tab.id) }
                            )
                        }
                    }
                    if (group.tabs.size > 3) {
                        Text(
                            text = "+${group.tabs.size - 3}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(group.color)
                        )
                    }
                }
            }
        }
    }
}
