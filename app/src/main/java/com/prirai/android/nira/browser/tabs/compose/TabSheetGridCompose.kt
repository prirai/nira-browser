package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.launch
import com.prirai.android.nira.browser.tabs.compose.DragLayer
import com.prirai.android.nira.browser.tabs.compose.DraggableItemType
import com.prirai.android.nira.browser.tabs.compose.DropTargetType
import com.prirai.android.nira.browser.tabs.compose.InsertionIndicator
import com.prirai.android.nira.browser.tabs.compose.draggableItem
import com.prirai.android.nira.browser.tabs.compose.dragVisualFeedback
import com.prirai.android.nira.browser.tabs.compose.dropTarget
import com.prirai.android.nira.browser.tabs.compose.rememberDragCoordinator
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
    var menuTab by remember { mutableStateOf<TabSessionState?>(null) }
    var menuIsInGroup by remember { mutableStateOf(false) }
    var showTabMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var menuGroupId by remember { mutableStateOf<String?>(null) }
    var menuGroupName by remember { mutableStateOf<String?>(null) }

    // Build unified items using UnifiedItemBuilder
    val items = remember(tabs, groups, expandedGroups, currentOrder) {
        UnifiedItemBuilder.buildItems(
            order = currentOrder,
            tabs = tabs,
            groups = groups,
            expandedGroups = expandedGroups,
            viewMode = ViewMode.GRID
        )
    }

    // Deduplicate items
    val uniqueItems = remember(items) {
        UnifiedItemBuilder.deduplicateItems(items)
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
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = uniqueItems,
                key = { it.id },
                span = { item ->
                    // Group headers and rows span all columns
                    when (item) {
                        is UnifiedItem.GroupHeader -> androidx.compose.foundation.lazy.grid.GridItemSpan(3)
                        is UnifiedItem.GroupRow -> androidx.compose.foundation.lazy.grid.GridItemSpan(3)
                        else -> androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                    }
                }
            ) { item ->
                when (item) {
                    is UnifiedItem.GroupHeader -> {
                        GroupHeaderGridItem(
                            groupId = item.groupId,
                            title = item.title,
                            color = item.color,
                            tabCount = item.tabCount,
                            isExpanded = item.isExpanded,
                            onHeaderClick = { onGroupClick(item.groupId) },
                            onOptionsClick = {
                                menuGroupId = item.groupId
                                menuGroupName = item.title
                                showGroupMenu = true
                                onGroupOptionsClick(item.groupId)
                            },
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

                    is UnifiedItem.GroupRow -> {
                        val group = groups.find { it.id == item.groupId }
                        GroupTabsRow(
                            groupId = item.groupId,
                            tabs = item.tabs,
                            groupColor = group?.color ?: 0xFF2196F3.toInt(),
                            selectedTabId = selectedTabId,
                            onTabClick = onTabClick,
                            onTabClose = onTabClose,
                            onTabLongPress = { tab ->
                                // Long press now only used for drag - use tap/click for menu
                            },
                            modifier = Modifier.animateItem(),
                            coordinator = coordinator
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
                                .animateItem()
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
                                .dragVisualFeedback(item.tab.id, coordinator)
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
                        it is UnifiedItem.GroupHeader && it.groupId == draggedItem.groupId
                    } as? UnifiedItem.GroupHeader
                    if (item != null) {
                        GroupHeaderGridItem(
                            groupId = item.groupId,
                            title = item.title,
                            color = item.color,
                            tabCount = item.tabCount,
                            isExpanded = false,
                            onHeaderClick = {},
                            onOptionsClick = {},
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                    }
                }
            }
        }
    }

    // Show tab menu
    if (showTabMenu && menuTab != null) {
        TabContextMenu(
            tab = menuTab!!,
            isInGroup = menuIsInGroup,
            onDismiss = { showTabMenu = false },
            viewModel = viewModel,
            scope = scope
        )
    }

    // Show group menu
    if (showGroupMenu && menuGroupId != null) {
        GroupContextMenu(
            groupId = menuGroupId!!,
            groupName = menuGroupName ?: "Group",
            onDismiss = { showGroupMenu = false },
            viewModel = viewModel,
            scope = scope
        )
    }
}

/**
 * Group header item for grid view
 */
@Composable
private fun GroupHeaderGridItem(
    groupId: String,
    title: String,
    color: Int,
    tabCount: Int,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(color).copy(alpha = 0.1f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, Color(color).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onHeaderClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Expand/Collapse icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(color),
                    modifier = Modifier.size(20.dp)
                )

                // Group name
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(color),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Tab count badge
                Surface(
                    shape = CircleShape,
                    color = Color(color).copy(alpha = 0.2f),
                    border = BorderStroke(2.dp, Color(color))
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .defaultMinSize(minWidth = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabCount.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(color),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Options menu button
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Group options",
                    tint = Color(color)
                )
            }
        }
    }
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
    coordinator: DragCoordinator? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        color = Color(groupColor).copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color(groupColor).copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { tab ->
                TabGridItemCompact(
                    tab = tab,
                    isSelected = tab.id == selectedTabId,
                    groupColor = Color(groupColor),
                    onTabClick = { onTabClick(tab.id) },
                    onTabClose = { onTabClose(tab.id) },
                    onTabLongPress = { onTabLongPress(tab) },
                    modifier = Modifier
                        .weight(1f)
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
                                    .dragVisualFeedback(tab.id, coordinator)
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
    }
}

/**
 * Compact tab item for use in group rows
 */
@Composable
private fun TabGridItemCompact(
    tab: TabSessionState,
    isSelected: Boolean,
    groupColor: Color,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    Surface(
        modifier = modifier
            .aspectRatio(0.75f)
            .scale(scale.value),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 2.dp else 1.dp,
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, groupColor.copy(alpha = 0.3f))
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Make content area clickable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onTabClick() }
            ) {
                // Thumbnail
                if (tab.content.url.isNotEmpty()) {
                    AsyncImage(
                        model = tab.content.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Overlay with title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tab.content.title.ifEmpty { "New Tab" },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                scope.launch {
                                    scale.animateTo(0.8f, animationSpec = tween(100))
                                    onTabClose()
                                }
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
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
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    Surface(
        modifier = modifier
            .aspectRatio(0.75f)
            .scale(scale.value),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else if (groupColor != null) {
            BorderStroke(1.dp, Color(groupColor).copy(alpha = 0.3f))
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Make content area clickable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onTabClick() }
            ) {
                // Thumbnail or placeholder
                if (tab.content.url.isNotEmpty()) {
                    AsyncImage(
                        model = tab.content.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                // Favicon overlay (top-left)
                if (tab.content.icon != null) {
                    Surface(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                            .align(Alignment.TopStart),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 2.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Image(
                                bitmap = tab.content.icon!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Close button (top-right)
                IconButton(
                    onClick = {
                        scope.launch {
                            scale.animateTo(0.8f, animationSpec = tween(150))
                            onTabClose()
                        }
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Title overlay (bottom)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = tab.content.title.ifEmpty { "New Tab" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

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
            }
        }
    }
}
