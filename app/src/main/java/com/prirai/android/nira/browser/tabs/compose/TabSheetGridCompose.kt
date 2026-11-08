@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.ui.components.animateNiraGridItem
import mozilla.components.browser.state.state.TabSessionState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot

/**
 * Grid view for tab sheet
 * Refactored to use UnifiedItemBuilder and custom drag system
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TabSheetGridView(
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
    val gridState = rememberLazyGridState()

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

    val dragState by coordinator.dragState
    val isDragging = dragState.isDragging
    // Hover state manager for contextual drag feedback
    val hoverState = rememberTabSheetHoverState(coordinator, uniqueItems)
    val hoveredGroupId = hoverState.getHoveredGroupIdForUngroupedDragGrid()
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
            gridState.scrollToItem(targetIndex)
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
        // Static layer
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = uniqueItems,
                key = { it.id },
                span = { item ->
                    // Group containers, headers and rows span all columns
                    @Suppress("DEPRECATION")
                    when (item) {
                        is UnifiedItem.GroupContainer -> androidx.compose.foundation.lazy.grid.GridItemSpan(3)
                        is UnifiedItem.GroupHeader -> androidx.compose.foundation.lazy.grid.GridItemSpan(3)
                        is UnifiedItem.GroupRow -> androidx.compose.foundation.lazy.grid.GridItemSpan(3)
                        else -> androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                    }
                }
            ) { item ->
                @Suppress("DEPRECATION")
                when (item) {
                    is UnifiedItem.GroupContainer -> {
                        // New unified group structure - header as parent with child tabs
                        GroupContainerGridItem(
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
                            onTabLongPress = { tab -> onTabLongPress(tab, false) },
                            coordinator = coordinator,
                            hoverState = hoverState,
                            hoveredGroupId = hoveredGroupId,
                            modifier = animateNiraGridItem()
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
                                    IconButton(
                                        onClick = {
                                            menuState.openGroupMenu(item.groupId, item.title)
                                            onGroupOptionsClick(item.groupId)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.more_options),
                                            tint = Color(item.color)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .then(animateNiraGridItem())
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
                                    .groupHeaderFeedback(item.groupId, coordinator, hoverState, draggedScale = 0.85f)
                            )
                        }
                    }

                    is UnifiedItem.GroupRow -> {
                        val group = groups.find { it.id == item.groupId }
                        // Check if this group row is being hovered (for group container enlargement)
                        val isGroupHovered = hoveredGroupId == item.groupId

                        GroupTabsRow(
                            groupId = item.groupId,
                            tabs = item.tabs,
                            groupColor = group?.color ?: MaterialTheme.colorScheme.primary.toArgb(),
                            selectedTabId = selectedTabId,
                            onTabClick = onTabClick,
                            onTabClose = onTabClose,
                            onTabLongPress = { tab ->
                                // Long press now only used for drag - use tap/click for menu
                            },
                            modifier = animateNiraGridItem(),
                            coordinator = coordinator,
                            isGroupHovered = isGroupHovered,
                            hoverState = hoverState
                        )
                    }

                    is UnifiedItem.GroupedTab -> {
                        // GroupedTab items should not appear in grid view
                        // They should be in GroupRow instead
                        // But handle it just in case
                        val group = groups.find { it.id == item.groupId }
                        TabGridItem(
                            tab = item.tab,
                            isSelected = item.tab.id == selectedTabId,
                            groupColor = group?.color,
                            onTabClick = { onTabClick(item.tab.id) },
                            onTabClose = { onTabClose(item.tab.id) },
                            onTabLongPress = {
                                // Long press now only used for drag - use tap/click for menu
                            },
                            modifier = Modifier
                                .then(animateNiraGridItem())
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
                                    metadata = mapOf("tabId" to item.tab.id)
                                )
                                .groupedTabFeedback(
                                    tabId = item.tab.id,
                                    groupId = item.groupId,
                                    coordinator = coordinator,
                                    hoverState = hoverState,
                                    hoveredGroupId = hoveredGroupId,
                                    draggedScale = 0.85f
                                )
                        )
                    }

                    is UnifiedItem.SingleTab -> {
                        TabGridItem(
                            tab = item.tab,
                            isSelected = item.tab.id == selectedTabId,
                            groupColor = null,
                            onTabClick = { onTabClick(item.tab.id) },
                            onTabClose = { onTabClose(item.tab.id) },
                            onTabLongPress = {
                                // Long press now only used for drag - use tap/click for menu
                            },
                            modifier = Modifier
                                .then(animateNiraGridItem())
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
                                .ungroupedTabFeedback(item.tab.id, coordinator, draggedScale = 0.85f)
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    // Insertion indicator layer
    InsertionIndicator(coordinator = coordinator)

    // Drag layer
    DragLayer(coordinator = coordinator) { draggedItem ->
        when (draggedItem) {
            is DraggableItemType.Tab -> {
                val tab = tabs.find { it.id == draggedItem.tabId }
                if (tab != null) {
                    TabGridItem(
                        tab = tab,
                        isSelected = false,
                        groupColor = null,
                        onTabClick = {},
                        onTabClose = {},
                        onTabLongPress = {},
                        modifier = Modifier.width(180.dp)
                    )
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
                            modifier = Modifier.fillMaxWidth(0.9f)
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
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    // Show tab + group context menus
    TabSheetMenuHost(menuState = menuState, viewModel = viewModel, scope = scope)
}

/**
 * Row of tabs within a group (full width in grid)
 */
@Composable
fun GroupTabsRow(
    groupId: String,
    tabs: List<TabSessionState>,
    groupColor: Int,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState) -> Unit,
    modifier: Modifier = Modifier,
    coordinator: DragCoordinator? = null,
    isGroupHovered: Boolean = false,
    hoverState: TabSheetHoverState? = null
) {
    // Animate scale for the entire group row when hovered, on the theme's spatial spring
    val scale by animateFloatAsState(
        targetValue = if (isGroupHovered) 1.05f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "groupRowScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        color = Color(groupColor).copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color(groupColor).copy(alpha = 0.2f))
    ) {
        // Scrollable horizontal row with fixed-size tabs at 80% scale
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            contentPadding = PaddingValues(0.dp)
        ) {
            items(
                items = tabs,
                key = { it.id }
            ) { tab ->
                // Fixed width box at 80% scale for consistent sizing
                Box(
                    modifier = Modifier
                        .width(140.dp) // Fixed width for each tab
                        .scale(0.8f)
                        .then(
                            if (coordinator != null) {
                                Modifier
                                    .draggableItem(
                                        itemType = DraggableItemType.Tab(tab.id, groupId),
                                        coordinator = coordinator
                                    )
                                    .dropTarget(
                                        id = tab.id,
                                        type = DropTargetType.TAB,
                                        coordinator = coordinator,
                                        metadata = mapOf("tabId" to tab.id)
                                    )
                                    .then(
                                        if (hoverState != null && coordinator != null) {
                                            Modifier.groupRowTabFeedback(
                                                tabId = tab.id,
                                                coordinator = coordinator,
                                                hoverState = hoverState
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    TabGridItem(
                        tab = tab,
                        isSelected = tab.id == selectedTabId,
                        groupColor = groupColor,
                        onTabClick = { onTabClick(tab.id) },
                        onTabClose = { onTabClose(tab.id) },
                        onTabLongPress = { onTabLongPress(tab) }
                    )
                }
            }
        }
    }
}


/**
 * Full-size tab grid item
 */
@Composable
private fun TabGridItem(
    tab: TabSessionState,
    isSelected: Boolean,
    groupColor: Int?,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use the reusable TabGridCard component
    TabGridCard(
        tab = tab,
        isSelected = isSelected,
        groupColor = groupColor,
        onTabClick = onTabClick,
        onTabClose = onTabClose,
        modifier = modifier
    )
}
