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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
    val listState = rememberLazyListState()

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

    // Build unified items
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

    val dragState by coordinator.dragState
    val isDragging = dragState.isDragging

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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(
                items = uniqueItems,
                key = { _, item -> item.id }
            ) { index, item ->
                // Show divider during drag
                if (isDragging && index == 0) {
                    val isHovering = coordinator.isHoveringOver("divider_0")
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .dropTarget(
                                id = "divider_0",
                                type = DropTargetType.ROOT_POSITION,
                                coordinator = coordinator,
                                metadata = mapOf("position" to 0)
                            ),
                        thickness = 2.dp,
                        color = if (isHovering)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }

                when (item) {
                    is UnifiedItem.GroupHeader -> {
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

                    is UnifiedItem.GroupedTab -> {
                        val group = groups.find { it.id == item.groupId }
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
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
                        )

                        SwipeToDismissBox(
                            state = dismissState,
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
                            },
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true
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
                        )

                        SwipeToDismissBox(
                            state = dismissState,
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
                            },
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true
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

                    is UnifiedItem.GroupRow -> {
                        // GroupRow is not used in list view
                    }
                }

                // Show divider after each item during drag
                if (isDragging) {
                    val isHovering = coordinator.isHoveringOver("divider_${index + 1}")
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .dropTarget(
                                id = "divider_${index + 1}",
                                type = DropTargetType.ROOT_POSITION,
                                coordinator = coordinator,
                                metadata = mapOf("position" to index + 1)
                            ),
                        thickness = 2.dp,
                        color = if (isHovering)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
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
                .clickable { onHeaderClick() }
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

    // Background color
    val backgroundColor = when {
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
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else if (effectiveIsInGroup && groupColor != null) {
            BorderStroke(1.dp, Color(groupColor).copy(alpha = 0.2f))
        } else null
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
                    .clickable { onTabClick() }
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
