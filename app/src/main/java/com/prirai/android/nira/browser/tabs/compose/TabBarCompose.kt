package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import kotlin.math.abs

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
                    when (item) {
                        is BarItem.SingleTab -> {
                            var offsetY by remember { mutableStateOf(0f) }
                            var showMenu by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .draggableItem(
                                        itemType = DraggableItemType.Tab(item.tab.id),
                                        coordinator = coordinator
                                    )
                                    .dragVisualFeedback(item.tab.id, coordinator)
                                    .pointerInput(item.tab.id) {
                                        detectVerticalDragGestures(
                                            onDragEnd = {
                                                if (abs(offsetY) > 50) {
                                                    if (offsetY < 0) {
                                                        // Swipe up - delete
                                                        onTabClose(item.tab.id)
                                                    } else {
                                                        // Swipe down - menu
                                                        showMenu = true
                                                    }
                                                }
                                                offsetY = 0f
                                            },
                                            onDragCancel = { offsetY = 0f },
                                            onVerticalDrag = { _, dragAmount ->
                                                offsetY += dragAmount
                                            }
                                        )
                                    }
                            ) {
                                TabPill(
                                    tab = item.tab,
                                    isSelected = item.tab.id == selectedTabId,
                                    coordinator = coordinator,
                                    onTabClick = onTabClick,
                                    onTabClose = onTabClose,
                                    isDragging = false
                                )

                                // Swipe feedback
                                if (abs(offsetY) > 10) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                if (offsetY < 0)
                                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                else
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ),
                                        contentAlignment = if (offsetY < 0) Alignment.TopCenter else Alignment.BottomCenter
                                    ) {
                                        Icon(
                                            imageVector = if (offsetY < 0) Icons.Default.Delete else Icons.Default.MoreVert,
                                            contentDescription = null,
                                            tint = if (offsetY < 0)
                                                MaterialTheme.colorScheme.onErrorContainer
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            if (showMenu) {
                                ShowTabMenu(
                                    tab = item.tab,
                                    isInGroup = false,
                                    onDismiss = { showMenu = false },
                                    viewModel = viewModel,
                                    scope = scope
                                )
                            }
                        }

                        is BarItem.Group -> {
                            var offsetY by remember { mutableStateOf(0f) }
                            var showMenu by remember { mutableStateOf(false) }

                            Box(
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
                                    .pointerInput(item.groupId) {
                                        detectVerticalDragGestures(
                                            onDragEnd = {
                                                if (abs(offsetY) > 50) {
                                                    if (offsetY < 0) {
                                                        // Swipe up - ungroup all
                                                        viewModel.ungroupAll(item.groupId)
                                                    } else {
                                                        // Swipe down - menu
                                                        showMenu = true
                                                    }
                                                }
                                                offsetY = 0f
                                            },
                                            onDragCancel = { offsetY = 0f },
                                            onVerticalDrag = { _, dragAmount ->
                                                offsetY += dragAmount
                                            }
                                        )
                                    }
                            ) {
                                GroupPill(
                                    group = item,
                                    isSelected = selectedTabId in item.tabIds,
                                    selectedTabId = selectedTabId,
                                    coordinator = coordinator,
                                    onTabClick = onTabClick,
                                    onTabClose = onTabClose,
                                    onGroupClick = { groupId ->
                                        viewModel.toggleGroupExpanded(groupId)
                                    },
                                    viewModel = viewModel,
                                    isDragging = false
                                )

                                // Swipe feedback
                                if (abs(offsetY) > 10) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                if (offsetY < 0)
                                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                else
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ),
                                        contentAlignment = if (offsetY < 0) Alignment.TopCenter else Alignment.BottomCenter
                                    ) {
                                        Icon(
                                            imageVector = if (offsetY < 0) Icons.Default.Close else Icons.Default.MoreVert,
                                            contentDescription = null,
                                            tint = if (offsetY < 0)
                                                MaterialTheme.colorScheme.onErrorContainer
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            if (showMenu) {
                                ShowGroupMenu(
                                    groupId = item.groupId,
                                    groupName = item.groupName,
                                    onDismiss = { showMenu = false },
                                    viewModel = viewModel,
                                    scope = scope
                                )
                            }
                        }
                    }

                    // Add divider after each item (except the last one)
                    if (index < items.size - 1) {
                        TabDivider(
                            id = "divider_${index + 1}",
                            coordinator = coordinator,
                            position = index + 1,
                            modifier = Modifier.animateItem()
                        )
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
                        // Glass-morphic dragged tab with pill shape
                        Surface(
                            modifier = Modifier
                                .height(40.dp)
                                .width(120.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp
                        ) {
                            TabPill(
                                tab = tab,
                                isSelected = false,
                                coordinator = coordinator,
                                onTabClick = {},
                                onTabClose = {},
                                isDragging = true
                            )
                        }
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
                            selectedTabId = null,
                            coordinator = coordinator,
                            onTabClick = {},
                            onTabClose = {},
                            onGroupClick = null,
                            viewModel = viewModel,
                            isDragging = true
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
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .width(120.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
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

            // Title - use onSurface color when dragging for better contrast
            Text(
                text = tab.content.title.ifEmpty { "New Tab" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isDragging) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
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
            .width(8.dp)
            .height(40.dp)
            .dropTarget(
                id = id,
                type = DropTargetType.ROOT_POSITION,
                coordinator = coordinator,
                metadata = mapOf("position" to position)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Visual divider
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )
    }
}

/**
 * Reusable tab context menu wrapper - used by tab bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowTabMenu(
    tab: TabSessionState,
    isInGroup: Boolean,
    onDismiss: () -> Unit,
    viewModel: TabViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier
) {
    com.prirai.android.nira.browser.tabs.compose.TabContextMenu(
        tab = tab,
        isInGroup = isInGroup,
        onDismiss = onDismiss,
        viewModel = viewModel,
        scope = scope,
        modifier = modifier
    )
}

/**
 * Reusable group context menu wrapper - used by tab bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowGroupMenu(
    groupId: String,
    groupName: String,
    onDismiss: () -> Unit,
    viewModel: TabViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier
) {
    com.prirai.android.nira.browser.tabs.compose.GroupContextMenu(
        groupId = groupId,
        groupName = groupName,
        onDismiss = onDismiss,
        viewModel = viewModel,
        scope = scope,
        modifier = modifier
    )
}

/**
 * Group pill composable - transparent background with dividers between tabs
 */
@Composable
private fun GroupPill(
    group: BarItem.Group,
    isSelected: Boolean,
    selectedTabId: String?,
    coordinator: DragCoordinator,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onGroupClick: ((String) -> Unit)? = null,
    viewModel: TabViewModel,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(group.isExpanded) }

    Surface(
        modifier = modifier
            .height(40.dp)
            .wrapContentWidth()
            .then(
                if (isDragging) Modifier else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color(group.color).copy(alpha = 0.15f),
        border = BorderStroke(2.dp, Color(group.color))
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxHeight()
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

            // Group name - use onSurface color when dragging, no ellipsis
            Text(
                text = group.groupName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = if (isDragging) MaterialTheme.colorScheme.onSurface else Color(group.color)
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

                        // Tab pill with background when selected - use group color
                        val isTabSelected = selectedTabId == tab.id
                        Surface(
                            modifier = Modifier
                                .height(32.dp)
                                .width(100.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isTabSelected) Color(group.color).copy(alpha = 0.2f) else Color.Transparent,
                            border = if (isTabSelected) BorderStroke(
                                1.5.dp,
                                Color(group.color)
                            ) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
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
                                    fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
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
