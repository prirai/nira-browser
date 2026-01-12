package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
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
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    // Add divider before each item (as drop target for reordering)
                    TabDivider(
                        id = "divider_$index",
                        coordinator = coordinator,
                        position = index,
                        modifier = Modifier.animateItem()
                    )

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
                                onGroupClick = { groupId ->
                                    viewModel.toggleGroupExpanded(groupId)
                                },
                                viewModel = viewModel,
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

                // Add final divider (for appending at end)
                item(key = "divider_end") {
                    TabDivider(
                        id = "divider_end",
                        coordinator = coordinator,
                        position = items.size,
                        modifier = Modifier.animateItem()
                    )
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
                            onGroupClick = null,
                            viewModel = viewModel,
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
 * Tab pill composable - transparent background, uses container backdrop
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
    Row(
        modifier = modifier
            .height(40.dp)
            .width(120.dp)
            .clickable { onTabClick(tab.id) }
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
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Divider that acts as a drop target for reordering
 */
@Composable
private fun TabDivider(
    id: String,
    coordinator: DragCoordinator,
    position: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(2.dp)
            .height(40.dp)
            .dropTarget(
                id = id,
                type = DropTargetType.ROOT_POSITION,
                coordinator = coordinator,
                metadata = mapOf("position" to position)
            )
    ) {
        // Visual divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )
    }
}

/**
 * Group pill composable - transparent background with dividers between tabs
 */
@Composable
private fun GroupPill(
    group: BarItem.Group,
    isSelected: Boolean,
    coordinator: DragCoordinator,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onGroupClick: ((String) -> Unit)? = null,
    viewModel: TabViewModel,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(group.isExpanded) }

    Surface(
        modifier = modifier
            .height(40.dp)
            .widthIn(min = 150.dp, max = if (expanded) 600.dp else 150.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(group.color).copy(alpha = 0.15f),
        border = BorderStroke(2.dp, Color(group.color))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = true,
                    onClick = {
                        expanded = !expanded
                        onGroupClick?.invoke(group.groupId)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Collapse/Expand icon
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = Color(group.color),
                modifier = Modifier.size(20.dp)
            )

            // Group name
            Text(
                text = group.groupName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(group.color),
                modifier = Modifier.weight(1f, fill = false)
            )

            // Tab count badge
            Surface(
                shape = CircleShape,
                color = Color(group.color).copy(alpha = 0.3f)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .defaultMinSize(minWidth = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${group.tabs.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(group.color),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (expanded) {
                // Show tab pills in group with dividers
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    group.tabs.forEachIndexed { index, tab ->
                        if (index > 0) {
                            // Divider between tabs
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(Color(group.color).copy(alpha = 0.3f))
                            )
                        }

                        // Tab pill without background
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .width(100.dp)
                                .clickable(
                                    onClick = { onTabClick(tab.id) }
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tab.content.icon?.let { icon ->
                                Image(
                                    bitmap = icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = tab.content.title.ifEmpty { "New Tab" },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color(group.color),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Divider before add button
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(Color(group.color).copy(alpha = 0.3f))
                    )

                    // Add tab button
                    IconButton(
                        onClick = {
                            // Create new tab and add to this group
                            val contextId = group.contextId ?: "profile_default"
                            viewModel.createNewTabInGroup(group.groupId, contextId)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add tab to group",
                            tint = Color(group.color),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
