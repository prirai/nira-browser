package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState
import com.prirai.android.nira.browser.tabgroups.TabGroupData
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
    groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoScrollTrigger: Long = 0L
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

    // Build items from order, using the latest groups to prevent duplicate
    // rendering during the race between order rebuild and group creation.
    val items = remember(order, tabs, groups) {
        buildBarItems(order, tabs, groups)
    }
    
    // Track initial load to prevent animated transitions showing both states
    var                     isInitialLoad by remember { mutableStateOf(false) }

    // Scroll to selected tab once on initial composition (e.g. app open).
    // Waits for order to finish loading, then scrolls instantly (no animation).
    // Centers the selected tab in the viewport when possible.
    var hasInitialScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(order, selectedTabId, listState.layoutInfo.viewportSize) {
        if (!hasInitialScrolled && order != null && selectedTabId != null) {
            val selectedIndex = order!!.primaryOrder.indexOfFirst { item ->
                when (item) {
                    is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId == selectedTabId
                    is UnifiedTabOrder.OrderItem.TabGroup -> selectedTabId in item.tabIds
                }
            }
            if (selectedIndex >= 0) {
                // Wait for layout to be ready
                val viewportSize = listState.layoutInfo.viewportSize.width
                if (viewportSize > 0) {
                    // Calculate offset to center the selected tab
                    // Negative offset moves the item towards the center
                    val averageItemSize = 180 // Approximate tab width in pixels (150dp + spacing)
                    val scrollOffset = -(viewportSize / 2 - averageItemSize / 2)
                    
                    listState.scrollToItem(selectedIndex, scrollOffset.coerceAtLeast(-viewportSize))
                    hasInitialScrolled = true
                }
            }
        }
    }

    // Auto-scroll to selected tab when explicitly triggered (tab sheet dismissed,
    // new tab created, etc.). Only fires when autoScrollTrigger changes, so it
    // never interrupts the user while they are manually scrolling the tab bar.
    // Centers the selected tab in the viewport when possible.
    LaunchedEffect(autoScrollTrigger, order) {
        if (autoScrollTrigger > 0L && System.currentTimeMillis() - autoScrollTrigger < 2000L) {
            val currentOrder = order ?: return@LaunchedEffect
            val currentTabId = selectedTabId ?: return@LaunchedEffect
            val selectedIndex = currentOrder.primaryOrder.indexOfFirst { item ->
                when (item) {
                    is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId == currentTabId
                    is UnifiedTabOrder.OrderItem.TabGroup -> currentTabId in item.tabIds
                }
            }
            if (selectedIndex >= 0) {
                // Calculate offset to center the selected tab
                val viewportSize = listState.layoutInfo.viewportSize.width
                if (viewportSize > 0) {
                    val averageItemSize = 180 // Approximate tab width in pixels
                    val scrollOffset = -(viewportSize / 2 - averageItemSize / 2)
                    
                    listState.animateScrollToItem(selectedIndex, scrollOffset.coerceAtLeast(-viewportSize))
                }
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    when (item) {
                        is BarItem.SingleTab -> {
                            var offsetY by remember { mutableStateOf(0f) }
                            var showMenu by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .then(if (!isInitialLoad) Modifier.animateItem() else Modifier)
                                    .draggableItem(
                                        itemType = DraggableItemType.Tab(item.tab.id),
                                        coordinator = coordinator
                                    )
                                    .dropTarget(
                                        id = item.tab.id,
                                        type = DropTargetType.TAB,
                                        coordinator = coordinator,
                                        metadata = mapOf(
                                            "tabId" to item.tab.id,
                                            "contextId" to (item.tab.contextId ?: "")
                                        )
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

                    // Add invisible divider after each item for drag-and-drop (except the last one)
                    if (index < items.size - 1) {
                        TabDivider(
                            id = "divider_${index + 1}",
                            coordinator = coordinator,
                            position = index + 1
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
                                .height(32.dp)
                                .width(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
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
 * Build bar items from order, using live group data as the source of truth
 * to prevent duplicate rendering during the race between order rebuild and
 * group creation by the middleware's fire-and-forget coroutine.
 *
 * If the order is stale (missing a new group), we:
 * 1. Filter out SingleTab items whose tabs belong to a known group
 * 2. Synthesize Group items from the live groups data at the position of
 *    the group's first tab in the current order
 */
private fun buildBarItems(order: UnifiedTabOrder?, tabs: List<TabSessionState>, groups: List<TabGroupData>): List<BarItem> {
    android.util.Log.d("TabBarDebug", "buildBarItems: order=${order?.primaryOrder?.size ?: null} groups=${groups.size} tabs=${tabs.size}")

    if (order == null) {
        android.util.Log.d("TabBarDebug", "  -> order is null, returning ${tabs.size} SingleTab items")
        return tabs.map { BarItem.SingleTab(it) }
    }

    if (groups.isEmpty()) {
        // No groups — render all tabs individually
        val result = order.primaryOrder.mapNotNull { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab ->
                    tabs.find { it.id == item.tabId }?.let { BarItem.SingleTab(it) }
                else -> null
            }
        }
        android.util.Log.d("TabBarDebug", "  -> no groups, returning ${result.size} SingleTab items from order")
        return result
    }

    // Build a map of tab ID -> group for fast lookup
    val tabIdToGroup = groups.associateBy { it.id }.flatMap { (groupId, group) ->
        group.tabIds.map { tabId -> tabId to group }
    }.toMap()

    // Build set of all tab IDs that belong to any group
    val tabIdsInGroups = tabIdToGroup.keys

    // Index each tab's position in the current order for placement
    val tabOrderIndex = mutableMapOf<String, Int>()
    order.primaryOrder.forEachIndexed { index, item ->
        when (item) {
            is UnifiedTabOrder.OrderItem.SingleTab -> tabOrderIndex[item.tabId] = index
            is UnifiedTabOrder.OrderItem.TabGroup -> item.tabIds.forEach { tabOrderIndex[it] = index }
        }
    }

    // Deduplicate: keep each group once, keyed by groupId
    val seenGroups = mutableSetOf<String>()
    val result = mutableListOf<BarItem>()

    // Walk the order to build items, replacing grouped tabs with their group
    order.primaryOrder.forEach { item ->
        when (item) {
            is UnifiedTabOrder.OrderItem.SingleTab -> {
                val tab = tabs.find { it.id == item.tabId } ?: return@forEach
                val group = tabIdToGroup[item.tabId]
                if (group != null) {
                    // This tab belongs to a group — emit the group if not already done
                    if (group.id !in seenGroups) {
                        seenGroups.add(group.id)
                        val groupTabs = group.tabIds.mapNotNull { tid ->
                            tabs.find { it.id == tid }
                        }
                        if (groupTabs.isNotEmpty()) {
                            result.add(
                                BarItem.Group(
                                    groupId = group.id,
                                    groupName = group.name,
                                    color = group.color,
                                    contextId = groupTabs.first().contextId,
                                    tabs = groupTabs,
                                    tabIds = group.tabIds,
                                    isExpanded = item@ order.primaryOrder
                                        .filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>()
                                        .find { it.groupId == group.id }
                                        ?.isExpanded ?: true
                                )
                            )
                            android.util.Log.d("TabBarDebug", "  -> SYNTHESIZED Group from live data: ${group.id} (${groupTabs.size} tabs)")
                        }
                    }
                    // Skip individual tab — it's part of the group above
                } else {
                    result.add(BarItem.SingleTab(tab))
                }
            }

            is UnifiedTabOrder.OrderItem.TabGroup -> {
                val groupTabs = item.tabIds.mapNotNull { tabId ->
                    tabs.find { it.id == tabId }
                }
                if (groupTabs.isNotEmpty()) {
                    if (item.groupId !in seenGroups) {
                        seenGroups.add(item.groupId)
                        result.add(
                            BarItem.Group(
                                groupId = item.groupId,
                                groupName = item.groupName,
                                color = item.color,
                                contextId = groupTabs.first().contextId,
                                tabs = groupTabs,
                                tabIds = item.tabIds,
                                isExpanded = item.isExpanded
                            )
                        )
                    }
                    android.util.Log.d("TabBarDebug", "  -> ORDER Group: ${item.groupId} (${groupTabs.size} tabs) expanded=${item.isExpanded}")
                }
            }
        }
    }

    // Handle groups whose tabs aren't in the order at all yet (brand-new group
    // created by middleware before the order rebuild). Place at the end,
    // sorted by the group's creation order.
    val newGroups = groups.filter { it.id !in seenGroups }
    if (newGroups.isNotEmpty()) {
        android.util.Log.d("TabBarDebug", "  -> APPENDING ${newGroups.size} brand-new groups from live data")
    }
    newGroups
        .sortedBy { it.createdAt }
        .forEach { group ->
            val groupTabs = group.tabIds.mapNotNull { tid -> tabs.find { it.id == tid } }
            if (groupTabs.isNotEmpty()) {
                result.add(
                    BarItem.Group(
                        groupId = group.id,
                        groupName = group.name,
                        color = group.color,
                        contextId = groupTabs.first().contextId,
                        tabs = groupTabs,
                        tabIds = group.tabIds,
                        isExpanded = true
                    )
                )
            }
        }

    val resultSummary = result.joinToString(",") { it.toString() }
    android.util.Log.d("TabBarDebug", "  => RESULT: ${result.size} items - [$resultSummary]")
    return result
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
 * Tab pill composable - matches grouped tab pill appearance
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
            .height(32.dp)
            .width(100.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onTabClick(tab.id) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Favicon
            FaviconImage(
                tab = tab,
                size = 14.dp,
                modifier = Modifier
            )

            // Title
            Text(
                text = getTabDisplayTitle(tab),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isDragging) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Invisible divider that acts as a drop target for reordering
 * Used for drag-and-drop but not visually rendered
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
            .height(32.dp)
            .width(4.dp)
            .dropTarget(
                id = id,
                type = DropTargetType.ROOT_POSITION,
                coordinator = coordinator,
                metadata = mapOf("position" to position)
            )
    ) {
        // No visual divider - just an invisible drop target
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
 * Group pill composable - solid background with dividers between tabs
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
    // Get expanded state from centralized group manager
    val groupData = viewModel.getGroup(group.groupId)
    val expanded = groupData?.let { !it.isCollapsed } ?: true
    
    var menuTab by remember { mutableStateOf<TabSessionState?>(null) }
    var showTabMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .then(
                if (expanded) Modifier.wrapContentHeight().defaultMinSize(minHeight = 32.dp)
                else Modifier.height(32.dp)
            )
            .wrapContentWidth()
            .then(
                if (isDragging) Modifier else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        color = Color(group.color).copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .then(if (expanded) Modifier.wrapContentHeight() else Modifier.fillMaxHeight())
                .clickable(
                    enabled = true,
                    onClick = {
                        viewModel.toggleGroupExpanded(group.groupId)
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

                        // Wrap SwipeableTabPill with draggable and drop target for individual tab reordering
                        val isTabSelected = selectedTabId == tab.id
                        Box(
                            modifier = Modifier
                                .draggableItem(
                                    itemType = DraggableItemType.Tab(tab.id),
                                    coordinator = coordinator
                                )
                                .dropTarget(
                                    id = tab.id,
                                    type = DropTargetType.TAB,
                                    coordinator = coordinator,
                                    metadata = mapOf(
                                        "tabId" to tab.id,
                                        "groupId" to group.groupId,
                                        "isInGroup" to true
                                    )
                                )
                        ) {
                            SwipeableTabPill(
                                tab = tab,
                                isSelected = isTabSelected,
                                groupColor = group.color,
                                onTabClick = { onTabClick(tab.id) },
                                onTabClose = { onTabClose(tab.id) },
                                onShowMenu = {
                                    menuTab = tab
                                    showTabMenu = true
                                },
                                modifier = Modifier,
                                swipeThreshold = 40f
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

    // Show tab menu when triggered
    if (showTabMenu && menuTab != null) {
        ShowTabMenu(
            tab = menuTab!!,
            isInGroup = true,
            onDismiss = {
                showTabMenu = false
                menuTab = null
            },
            viewModel = viewModel,
            scope = rememberCoroutineScope()
        )
    }
}
