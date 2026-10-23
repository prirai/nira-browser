@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.launch
import com.prirai.android.nira.browser.tabs.compose.DragLayer
import com.prirai.android.nira.browser.tabs.compose.DraggableItemType
import com.prirai.android.nira.browser.tabs.compose.DropTargetType
import com.prirai.android.nira.browser.tabs.compose.InsertionIndicator
import com.prirai.android.nira.browser.tabs.compose.draggableItem
import com.prirai.android.nira.browser.tabs.compose.dropTarget
import com.prirai.android.nira.browser.tabs.compose.rememberDragCoordinator

/**
 * List view for tab sheet
 * Refactored to use UnifiedItemBuilder and custom drag system
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TabSheetListView(
    viewModel: TabViewModel,
    orderManager: TabOrderManager,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState, Boolean) -> Unit = { _, _ -> },
    onGroupClick: (String) -> Unit,
    onGroupOptionsClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val expandedGroups by viewModel.expandedGroups.collectAsState()
    val selectedTabId by viewModel.selectedTabId.collectAsState()
    val currentOrder by viewModel.currentOrder.collectAsState()

    val scope = rememberCoroutineScope()
    val listState = rememberLazyGridState()

    // Create drag coordinator
    val coordinator = rememberDragCoordinator(
        scope = scope,
        viewModel = viewModel,
        orderManager = orderManager
    )

    // Menu state
    val menuState = rememberTabSheetMenuState()

    // Build + deduplicate unified items
    val uniqueItems = rememberUnifiedSheetItems(tabs, groups, expandedGroups, currentOrder)

    // Calculate position mapping for dividers
    // Maps UI item index to actual primary order position
    val positionMapping = remember(uniqueItems, currentOrder) {
        val mapping = mutableMapOf<Int, Int>()
        var orderPosition = 0

        uniqueItems.forEachIndexed { index, item ->
            when (item) {
                is UnifiedItem.GroupContainer -> {
                    // Group container counts as one position
                    mapping[index] = orderPosition
                    orderPosition++
                }

                is UnifiedItem.GroupHeader -> {
                    mapping[index] = orderPosition
                    orderPosition++
                }

                is UnifiedItem.GroupedTab -> {
                    // Grouped tabs don't occupy a position in primary order
                    mapping[index] = orderPosition - 1 // Use parent group's position
                }

                is UnifiedItem.SingleTab -> {
                    mapping[index] = orderPosition
                    orderPosition++
                }

                else -> {}
            }
        }
        mapping
    }

    // Use derivedStateOf to avoid unnecessary recomposition
    val isDragging by remember {
        derivedStateOf { coordinator.dragState.value.isDragging }
    }

    // Hover state manager for contextual drag feedback
    val hoverState = rememberTabSheetHoverState(coordinator, uniqueItems)
    val hoveredGroupId = hoverState.getHoveredGroupIdForUngroupedDrag()

    // Track if LazyColumn is currently scrolling to prevent position updates during scroll
    val isScrolling = listState.isScrollInProgress

    // Auto-scroll disabled - needs proper implementation to avoid conflicts
    // The drag system works fine without it for now


    // Pass scroll state to coordinator to prevent updates during scroll
    LaunchedEffect(isScrolling) {
        coordinator.setIsScrolling(isScrolling)
    }

    LaunchedEffect(selectedTabId, uniqueItems) {
        val tabId = selectedTabId ?: return@LaunchedEffect
        val targetIndex = uniqueItems.indexOfFirst { item ->
            when (item) {
                is UnifiedItem.SingleTab -> item.tab.id == tabId
                is UnifiedItem.GroupedTab -> item.tab.id == tabId
                is UnifiedItem.GroupContainer -> item.children.any { it.id == tabId }
                else -> false
            }
        }
        if (targetIndex >= 0) {
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { layoutCoordinates ->
                // Track scroll container bounds for auto-scroll
                val bounds = layoutCoordinates.boundsInRoot()
                coordinator.setScrollContainerBounds(bounds)
            }
    ) {
        // Static layer - using LazyVerticalGrid with 1 column (works better than LazyColumn for drag)
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),  // Always 8dp spacing to avoid layout shift
            userScrollEnabled = !isDragging  // Only disable scroll during active drag, not during touch
        ) {
            itemsIndexed(
                items = uniqueItems,
                key = { _, item -> item.id },
                span = { _, _ -> androidx.compose.foundation.lazy.grid.GridItemSpan(1) }
            ) { index, item ->
                val currentOrderPosition = positionMapping[index] ?: index
                val isLastItemInGroup = item is UnifiedItem.GroupedTab && item.isLastInGroup
                val isGroupHeader = item is UnifiedItem.GroupHeader || item is UnifiedItem.GroupContainer

                // Check if we should show divider before this item using hover state logic
                val previousItem = if (index > 0) uniqueItems.getOrNull(index - 1) else null
                val showDivider = hoverState.shouldShowDivider(index, item, previousItem)

                // Divider zone BEFORE item - always present, visible only during drag
                if (showDivider) {
                    val isHovering = isDragging && coordinator.isHoveringOver("divider_$currentOrderPosition")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-8).dp)  // Move up into the spacedBy gap
                            .height(8.dp)  // Match the spacedBy gap height
                            .then(
                                if (isDragging) {
                                    Modifier.dropTarget(
                                        id = "divider_$currentOrderPosition",
                                        type = DropTargetType.ROOT_POSITION,
                                        coordinator = coordinator,
                                        metadata = mapOf("position" to currentOrderPosition)
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isHovering) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .padding(horizontal = 8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }

                when (item) {
                    is UnifiedItem.GroupContainer -> {
                        // New unified group structure - header as parent with child tabs
                        GroupContainerListItem(
                            groupId = item.groupId,
                            title = item.title,
                            color = item.color,
                            isExpanded = item.isExpanded,
                            children = item.children,
                            selectedTabId = selectedTabId,
                            onHeaderClick = { onGroupClick(item.groupId) },
                            onOptionsClick = {
                                menuState.openGroupMenu(item.groupId, item.title)
                                onGroupOptionsClick(item.groupId)
                            },
                            onTabClick = onTabClick,
                            onTabClose = onTabClose,
                            onShowTabMenu = { tab, isInGroup ->
                                menuState.openTabMenu(tab, isInGroup)
                            },
                            isDragging = isDragging,
                            coordinator = coordinator,
                            hoverState = hoverState,
                            hoveredGroupId = hoveredGroupId
                        )
                    }

                    is UnifiedItem.GroupHeader -> {
                        TabSwipeWrapper(
                            isDragging = isDragging,
                            onDismiss = { viewModel.ungroupAll(item.groupId) },
                            onMenu = { menuState.openGroupMenu(item.groupId, item.title) },
                            dismissIcon = Icons.Default.Delete,
                            dismissIconLabel = stringResource(R.string.ungroup),
                        ) {
                            GroupHeaderRow(
                                title = item.title,
                                color = item.color,
                                tabCount = item.tabCount,
                                isExpanded = item.isExpanded,
                                onHeaderClick = { onGroupClick(item.groupId) },
                                trailing = {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = stringResource(R.string.drag_to_reorder),
                                        tint = Color(item.color).copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
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
                                    .groupHeaderFeedback(item.groupId, coordinator, hoverState)
                            )
                        }
                    }

                    is UnifiedItem.GroupedTab -> {
                        val group = groups.find { it.id == item.groupId }

                        TabSwipeWrapper(
                            isDragging = isDragging,
                            onDismiss = { onTabClose(item.tab.id) },
                            onMenu = { menuState.openTabMenu(item.tab, inGroup = true) },
                        ) {
                            TabListItem(
                                tab = item.tab,
                                isSelected = item.tab.id == selectedTabId,
                                isInGroup = true,
                                isLastInGroup = item.isLastInGroup,
                                groupId = item.groupId,
                                groupColor = group?.color,
                                onTabClick = { onTabClick(item.tab.id) },
                                onTabClose = { onTabClose(item.tab.id) },
                                onTabLongPress = {
                                    // Long press now only used for drag - menu via swipe right
                                },
                                isDragging = isDragging,
                                modifier = Modifier
                                    .draggableItem(
                                        itemType = DraggableItemType.Tab(
                                            item.tab.id,
                                            item.groupId
                                        ),
                                        coordinator = coordinator
                                    )
                                    .dropTarget(
                                        id = item.tab.id,
                                        type = DropTargetType.TAB,
                                        coordinator = coordinator,
                                        metadata = mapOf(
                                            "tabId" to item.tab.id,
                                            "groupId" to item.groupId
                                        )
                                    )
                                    .groupedTabFeedback(
                                        tabId = item.tab.id,
                                        groupId = item.groupId,
                                        coordinator = coordinator,
                                        hoverState = hoverState,
                                        hoveredGroupId = hoveredGroupId
                                    )
                            )
                        }
                    }

                    is UnifiedItem.SingleTab -> {
                        TabSwipeWrapper(
                            isDragging = isDragging,
                            onDismiss = { onTabClose(item.tab.id) },
                            onMenu = { menuState.openTabMenu(item.tab, inGroup = false) },
                        ) {
                            TabListItem(
                                tab = item.tab,
                                isSelected = item.tab.id == selectedTabId,
                                isInGroup = false,
                                isLastInGroup = false,
                                groupId = null,
                                groupColor = null,
                                onTabClick = { onTabClick(item.tab.id) },
                                onTabClose = { onTabClose(item.tab.id) },
                                onTabLongPress = {
                                    // Long press now only used for drag - menu via swipe right
                                },
                                isDragging = isDragging,
                                modifier = Modifier
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
                                    .ungroupedTabFeedback(item.tab.id, coordinator)
                            )
                        }
                    }

                    is UnifiedItem.GroupRow -> {
                        // GroupRow is not used in list view
                    }
                }

                // Divider zone AFTER last item during drag
                if (isDragging && index == uniqueItems.lastIndex) {
                    val afterLastPosition = currentOrderPosition + 1
                    val isHovering = coordinator.isHoveringOver("divider_$afterLastPosition")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)  // 8dp zone for drop at end
                            .dropTarget(
                                id = "divider_$afterLastPosition",
                                type = DropTargetType.ROOT_POSITION,
                                coordinator = coordinator,
                                metadata = mapOf("position" to afterLastPosition)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isHovering) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .padding(horizontal = 8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Insertion indicator layer
        InsertionIndicator(coordinator = coordinator)

        // Drag layer - show full item pills with proper width
        DragLayer(coordinator = coordinator) { draggedItem ->
            when (draggedItem) {
                is DraggableItemType.Tab -> {
                    val tab = tabs.find { it.id == draggedItem.tabId }
                    val item = uniqueItems.find {
                        when (it) {
                            is UnifiedItem.SingleTab -> it.tab.id == draggedItem.tabId
                            is UnifiedItem.GroupedTab -> it.tab.id == draggedItem.tabId
                            is UnifiedItem.GroupContainer -> it.children.any { tab -> tab.id == draggedItem.tabId }
                            else -> false
                        }
                    }
                    if (tab != null) {
                        when (item) {
                            is UnifiedItem.GroupContainer -> {
                                // Find the tab within the container
                                TabListItem(
                                    tab = tab,
                                    isSelected = false,
                                    isInGroup = true,
                                    isLastInGroup = false,
                                    groupId = item.groupId,
                                    groupColor = item.color,
                                    onTabClick = {},
                                    onTabClose = {},
                                    onTabLongPress = {},
                                    modifier = Modifier.width(350.dp)
                                )
                            }

                            is UnifiedItem.GroupedTab -> {
                                val group = groups.find { it.id == item.groupId }
                                TabListItem(
                                    tab = tab,
                                    isSelected = false,
                                    isInGroup = true,
                                    isLastInGroup = false,
                                    groupId = item.groupId,
                                    groupColor = group?.color,
                                    onTabClick = {},
                                    onTabClose = {},
                                    onTabLongPress = {},
                                    modifier = Modifier.width(350.dp)
                                )
                            }

                            else -> {
                                TabListItem(
                                    tab = tab,
                                    isSelected = false,
                                    isInGroup = false,
                                    isLastInGroup = false,
                                    groupId = null,
                                    groupColor = null,
                                    onTabClick = {},
                                    onTabClose = {},
                                    onTabLongPress = {},
                                    modifier = Modifier.width(350.dp)
                                )
                            }
                        }
                    }
                }

                is DraggableItemType.Group -> {
                    val item = uniqueItems.find {
                        (it is UnifiedItem.GroupHeader && it.groupId == draggedItem.groupId) ||
                                (it is UnifiedItem.GroupContainer && it.groupId == draggedItem.groupId)
                    }
                    when (item) {
                        is UnifiedItem.GroupContainer -> {
                            GroupHeaderRow(
                                title = item.title,
                                color = item.color,
                                tabCount = item.tabCount,
                                isExpanded = false,
                                onHeaderClick = {},
                                trailing = {},
                                modifier = Modifier.width(350.dp)
                            )
                        }

                        is UnifiedItem.GroupHeader -> {
                            GroupHeaderRow(
                                title = item.title,
                                color = item.color,
                                tabCount = item.tabCount,
                                isExpanded = false,
                                onHeaderClick = {},
                                trailing = {},
                                modifier = Modifier.width(350.dp)
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    // Show tab + group context menus
    TabSheetMenuHost(menuState = menuState, viewModel = viewModel, scope = scope)
}

/**
 * Individual tab list item
 */
@Composable
private fun TabListItem(
    tab: TabSessionState,
    isSelected: Boolean,
    isInGroup: Boolean,
    isLastInGroup: Boolean,
    groupId: String?,
    groupColor: Int?,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val effectiveIsInGroup = isInGroup && groupId != null
    val effectiveIsLastInGroup = effectiveIsInGroup && isLastInGroup

    // Background color - use group color for selected tabs in groups
    val backgroundColor = when {
        isSelected && effectiveIsInGroup && groupColor != null -> Color(groupColor).copy(alpha = 0.25f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        effectiveIsInGroup && groupColor != null -> Color(groupColor).copy(alpha = 0.05f)
        else -> MaterialTheme.colorScheme.surface
    }

    // Shape based on position in group
    val shape = when {
        !effectiveIsInGroup -> RoundedCornerShape(12.dp)
        effectiveIsLastInGroup -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
        else -> RoundedCornerShape(0.dp)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (effectiveIsInGroup) 16.dp else 8.dp,
                vertical = 2.dp
            ),
        shape = shape,
        color = backgroundColor,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        border = when {
            // Group color borders are functional identity; the selected state
            // speaks through its container color, never an accent stroke.
            effectiveIsInGroup && groupColor != null -> BorderStroke(
                if (isSelected) 2.dp else 1.dp,
                Color(groupColor).copy(alpha = if (isSelected) 0.9f else 0.2f)
            )
            else -> null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onTabClick() }
            ) {
                // Favicon - use centralized FaviconImage for consistent globe icon fallback
                FaviconImage(
                    tab = tab,
                    size = 24.dp,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                )

                // Title and URL
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = tab.content.title.ifEmpty { stringResource(R.string.tab_history_new_tab) },
                        style =
                            if (isSelected) {
                                MaterialTheme.typography.bodyLargeEmphasized
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Always show URL (spacing is always present, not just during drag)
                    if (tab.content.url.isNotEmpty()) {
                        Text(
                            text = tab.content.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Drag handle - drag is handled by draggableItem modifier on parent
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.drag_to_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
