package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var menuTab by remember { mutableStateOf<TabSessionState?>(null) }
    var menuIsInGroup by remember { mutableStateOf(false) }
    var showTabMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var menuGroupId by remember { mutableStateOf<String?>(null) }
    var menuGroupName by remember { mutableStateOf<String?>(null) }

    // Build unified items - only when NOT dragging to prevent scroll jumps
    val items = remember(tabs, groups, expandedGroups, currentOrder) {
        UnifiedItemBuilder.buildItems(
            order = currentOrder,
            tabs = tabs,
            groups = groups,
            expandedGroups = expandedGroups,
            viewMode = ViewMode.LIST
        )
    }

    // Deduplicate items
    val uniqueItems = remember(items) {
        UnifiedItemBuilder.deduplicateItems(items)
    }

    // Calculate position mapping for dividers
    // Maps UI item index to actual primary order position
    val positionMapping = remember(uniqueItems, currentOrder) {
        val mapping = mutableMapOf<Int, Int>()
        var orderPosition = 0

        uniqueItems.forEachIndexed { index, item ->
            when (item) {
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

    // Track if LazyColumn is currently scrolling to prevent position updates during scroll
    val isScrolling = listState.isScrollInProgress

    // Auto-scroll disabled - needs proper implementation to avoid conflicts
    // The drag system works fine without it for now


    // Pass scroll state to coordinator to prevent updates during scroll
    LaunchedEffect(isScrolling) {
        coordinator.setIsScrolling(isScrolling)
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
            userScrollEnabled = !isDragging  // Only disable scroll during active drag, not during touch
        ) {
            itemsIndexed(
                items = uniqueItems,
                key = { _, item -> item.id },
                span = { _, _ -> androidx.compose.foundation.lazy.grid.GridItemSpan(1) }
            ) { index, item ->
                val nextOrderPosition = positionMapping[index]?.plus(1) ?: (index + 1)
                val isLastItemInGroup = item is UnifiedItem.GroupedTab && item.isLastInGroup
                val isGroupHeader = item is UnifiedItem.GroupHeader

                // Show divider during drag
                if (isDragging && index == 0) {
                    val isHovering = coordinator.isHoveringOver("divider_0")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .dropTarget(
                                id = "divider_0",
                                type = DropTargetType.ROOT_POSITION,
                                coordinator = coordinator,
                                metadata = mapOf("position" to 0)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        HorizontalDivider(
                            thickness = if (isHovering) 3.dp else 2.dp,
                            color = if (isHovering)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    }
                }

                when (item) {
                    is UnifiedItem.GroupHeader -> {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                // Disable swipe during drag to prevent conflicts
                                if (isDragging) {
                                    false
                                } else {
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            // Swipe left - ungroup all tabs
                                            viewModel.ungroupAll(item.groupId)
                                            true
                                        }

                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            // Swipe right - show menu
                                            menuGroupId = item.groupId
                                            menuGroupName = item.title
                                            showGroupMenu = true
                                            false
                                        }

                                        else -> false
                                    }
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = !isDragging,
                            enableDismissFromEndToStart = !isDragging,
                            backgroundContent = {
                                val color = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                        Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Ungroup",
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }

                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Menu",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        ) {
                            GroupHeaderItem(
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

                    is UnifiedItem.GroupedTab -> {
                        val group = groups.find { it.id == item.groupId }
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                // Disable swipe during drag to prevent conflicts
                                if (isDragging) {
                                    false
                                } else {
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            onTabClose(item.tab.id)
                                            true
                                        }

                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            menuTab = item.tab
                                            menuIsInGroup = true
                                            showTabMenu = true
                                            false
                                        }

                                        else -> false
                                    }
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = !isDragging,
                            enableDismissFromEndToStart = !isDragging,
                            backgroundContent = {
                                val color = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                        Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }

                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Menu",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        else -> {}
                                    }
                                }
                            }
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
                                    .dragVisualFeedback(item.tab.id, coordinator)
                            )
                        }
                    }

                    is UnifiedItem.SingleTab -> {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                // Disable swipe during drag to prevent conflicts
                                if (isDragging) {
                                    false
                                } else {
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            onTabClose(item.tab.id)
                                            true
                                        }

                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            menuTab = item.tab
                                            menuIsInGroup = false
                                            showTabMenu = true
                                            false
                                        }

                                        else -> false
                                    }
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = !isDragging,
                            enableDismissFromEndToStart = !isDragging,
                            backgroundContent = {
                                val color = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                        Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }

                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Menu",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        else -> {}
                                    }
                                }
                            }
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
                                    .dragVisualFeedback(item.tab.id, coordinator)
                            )
                        }
                    }

                    is UnifiedItem.GroupRow -> {
                        // GroupRow is not used in list view
                    }
                }

                // Show divider after each item during drag (only after root-level items or last item in group, but NOT after group headers)
                if (isDragging && !isGroupHeader && (item !is UnifiedItem.GroupedTab || isLastItemInGroup)) {
                    val isHovering = coordinator.isHoveringOver("divider_$nextOrderPosition")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .dropTarget(
                                id = "divider_$nextOrderPosition",
                                type = DropTargetType.ROOT_POSITION,
                                coordinator = coordinator,
                                metadata = mapOf("position" to nextOrderPosition)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        HorizontalDivider(
                            thickness = if (isHovering) 3.dp else 2.dp,
                            color = if (isHovering)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
                    val item = uniqueItems.find {
                        when (it) {
                            is UnifiedItem.SingleTab -> it.tab.id == draggedItem.tabId
                            is UnifiedItem.GroupedTab -> it.tab.id == draggedItem.tabId
                            else -> false
                        }
                    }
                    if (tab != null) {
                        when (item) {
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
                                    modifier = Modifier
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
                                    modifier = Modifier
                                )
                            }
                        }
                    }
                }

                is DraggableItemType.Group -> {
                    val item = uniqueItems.find {
                        it is UnifiedItem.GroupHeader && it.groupId == draggedItem.groupId
                    } as? UnifiedItem.GroupHeader
                    if (item != null) {
                        GroupHeaderItem(
                            groupId = item.groupId,
                            title = item.title,
                            color = item.color,
                            tabCount = item.tabCount,
                            isExpanded = false,
                            onHeaderClick = {},
                            onOptionsClick = {},
                            modifier = Modifier
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
 * Group header item
 */
@Composable
private fun GroupHeaderItem(
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
    val scale = remember { Animatable(1f) }

    // Swipe state for horizontal drag (show menu)
    val offsetX = remember { Animatable(0f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .scale(scale.value),
        shape = RoundedCornerShape(12.dp),
        color = Color(color).copy(alpha = 0.1f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, Color(color).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onHeaderClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Expand/Collapse icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(color),
                    modifier = Modifier.size(24.dp)
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
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .defaultMinSize(minWidth = 24.dp),
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

            // Drag handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = Color(color).copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
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
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    // Swipe state for horizontal drag
    val offsetX = remember { Animatable(0f) }

    val effectiveIsInGroup = isInGroup && groupId != null
    val effectiveIsLastInGroup = effectiveIsInGroup && isLastInGroup

    // Background color - use group color for selected tabs in groups
    val backgroundColor = when {
        isSelected && effectiveIsInGroup && groupColor != null -> Color(groupColor).copy(alpha = 0.2f)
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
            )
            .scale(scale.value),
        shape = shape,
        color = backgroundColor,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        border = when {
            isSelected && effectiveIsInGroup && groupColor != null -> BorderStroke(2.dp, Color(groupColor))
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            effectiveIsInGroup && groupColor != null -> BorderStroke(1.dp, Color(groupColor).copy(alpha = 0.2f))
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
                // Favicon
                if (tab.content.icon != null) {
                    Image(
                        bitmap = tab.content.icon!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Title and URL
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = tab.content.title.ifEmpty { "New Tab" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
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

            // Drag handle - drag is handled by draggableItem modifier on parent
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Long press to drag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
