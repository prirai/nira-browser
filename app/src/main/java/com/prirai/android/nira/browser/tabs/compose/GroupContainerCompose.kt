package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState

/**
 * Group container for list view - container has the background with integrated header.
 * The parent Surface contains everything with group styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupContainerListItem(
    groupId: String,
    title: String,
    color: Int,
    isExpanded: Boolean,
    children: List<TabSessionState>,
    selectedTabId: String?,
    onHeaderClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onShowTabMenu: (TabSessionState, Boolean) -> Unit,  // Callback to show unified menu
    isDragging: Boolean,
    coordinator: DragCoordinator,
    hoverState: TabSheetHoverState,
    hoveredGroupId: String?,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1f) }

    // Apply zoom effect when dragging ungrouped tab over this container
    LaunchedEffect(hoveredGroupId) {
        if (hoveredGroupId == groupId) {
            scale.animateTo(1.05f, spring(stiffness = Spring.StiffnessMediumLow))
        } else {
            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    // Single container with group styling
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .scale(scale.value)
            .then(
                // Only make group draggable when collapsed to avoid conflict with child tab dragging
                if (!isExpanded) {
                    Modifier.draggableItem(
                        itemType = DraggableItemType.Group(groupId),
                        coordinator = coordinator
                    )
                } else {
                    Modifier
                }
            )
            .dropTarget(
                id = groupId,
                type = DropTargetType.GROUP_HEADER,
                coordinator = coordinator,
                metadata = mapOf(
                    "groupId" to groupId,
                    "contextId" to (children.firstOrNull()?.contextId ?: "")
                )
            ),
        shape = RoundedCornerShape(12.dp),
        color = Color(color).copy(alpha = 0.1f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, Color(color).copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header content integrated into container
            // Use pointerInput instead of clickable to avoid interfering with drag gestures
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onHeaderClick() }
                        )
                    }
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
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                                text = children.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(color),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Options button
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color(color).copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Child tabs - shown when expanded
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    children.forEachIndexed { index, tab ->
                        val isLastInGroup = index == children.size - 1

                        // Show divider before tab when dragging grouped tab for reordering
                        if (isDragging && hoverState.isDraggingGroupedTab() && coordinator.isHoveringOver("divider_${tab.id}")) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .padding(horizontal = 16.dp)
                                    .background(
                                        Color(color),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }

                        Box(
                            modifier = Modifier.dropTarget(
                                id = "divider_${tab.id}",
                                type = DropTargetType.TAB,
                                coordinator = coordinator,
                                metadata = mapOf(
                                    "tabId" to tab.id,
                                    "groupId" to groupId,
                                    "isInGroup" to true,
                                    "insertBefore" to true
                                )
                            )
                        ) {
                            GroupedTabListItem(
                                tab = tab,
                                groupId = groupId,
                                groupColor = color,
                                isSelected = tab.id == selectedTabId,
                                isLastInGroup = isLastInGroup,
                                onTabClick = { onTabClick(tab.id) },
                                onTabClose = { onTabClose(tab.id) },
                                onShowTabMenu = { onShowTabMenu(tab, true) },  // Use unified menu system
                                isDragging = isDragging,
                                coordinator = coordinator,
                                hoverState = hoverState,
                                hoveredGroupId = hoveredGroupId
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * Grouped tab item within the container - uses TabListCard for consistency
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupedTabListItem(
    tab: TabSessionState,
    groupId: String,
    groupColor: Int,
    isSelected: Boolean,
    isLastInGroup: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onShowTabMenu: () -> Unit,  // Changed from onTabLongPress to onShowTabMenu
    isDragging: Boolean,
    coordinator: DragCoordinator,
    hoverState: TabSheetHoverState,
    hoveredGroupId: String?
) {
    // Swipe to dismiss support
    @Suppress("DEPRECATION")
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (isDragging) {
                false
            } else {
                when (dismissValue) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        onTabClose()
                        true
                    }

                    SwipeToDismissBoxValue.StartToEnd -> {
                        onShowTabMenu()  // Use unified menu system
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
            val dismissDirection = dismissState.dismissDirection
            val bgColor = when (dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    Alignment.CenterEnd else Alignment.CenterStart
            ) {
                when (dismissDirection) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Close",
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
        // Use the reusable TabListCard component with drag/drop modifiers
        TabListCard(
            tab = tab,
            isSelected = isSelected,
            groupColor = groupColor,
            onTabClick = onTabClick,
            onTabClose = onTabClose,
            showDragHandle = false, // No drag handle for grouped tabs (container shows it)
            modifier = Modifier
                .padding(horizontal = 0.dp, vertical = 2.dp) // Remove horizontal padding (SwipeToDismissBox handles it)
                .draggableItem(
                    itemType = DraggableItemType.Tab(tab.id, groupId),
                    coordinator = coordinator
                )
                .dropTarget(
                    id = tab.id,
                    type = DropTargetType.TAB,
                    coordinator = coordinator,
                    metadata = mapOf(
                        "tabId" to tab.id,
                        "groupId" to groupId,
                        "isInGroup" to true
                    )
                )
        )
    }
}

/**
 * Group container for grid view - container has the background with integrated header.
 * The parent Surface contains everything with group styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupContainerGridItem(
    groupId: String,
    title: String,
    color: Int,
    isExpanded: Boolean,
    children: List<TabSessionState>,
    selectedTabId: String?,
    onHeaderClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState) -> Unit,
    coordinator: DragCoordinator,
    hoverState: TabSheetHoverState,
    hoveredGroupId: String?,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1f) }

    // Single container with group styling
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .scale(scale.value)
            .then(
                // Only make group draggable when collapsed to avoid conflict with child tab dragging
                if (!isExpanded) {
                    Modifier.draggableItem(
                        itemType = DraggableItemType.Group(groupId),
                        coordinator = coordinator
                    )
                } else {
                    Modifier
                }
            )
            .dropTarget(
                id = groupId,
                type = DropTargetType.GROUP_HEADER,
                coordinator = coordinator,
                metadata = mapOf("groupId" to groupId)
            )
            .groupHeaderFeedback(groupId, coordinator, hoverState, draggedScale = 0.85f),
        shape = RoundedCornerShape(12.dp),
        color = Color(color).copy(alpha = 0.1f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, Color(color).copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header content integrated into container
            // Use pointerInput instead of clickable to avoid interfering with drag gestures
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onHeaderClick() }
                        )
                    }
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
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                                text = children.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(color),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Options button
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color(color).copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Child tabs row - shown when expanded
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                GroupTabsGridRow(
                    groupId = groupId,
                    tabs = children,
                    groupColor = color,
                    selectedTabId = selectedTabId,
                    onTabClick = onTabClick,
                    onTabClose = onTabClose,
                    onTabLongPress = { tab -> onTabLongPress(tab) },
                    coordinator = coordinator,
                    hoverState = hoverState,
                    hoveredGroupId = hoveredGroupId
                )
            }
        }
    }
}

/**
 * Horizontal scrollable row of tabs within a group for grid view
 */
@Composable
private fun GroupTabsGridRow(
    groupId: String,
    tabs: List<TabSessionState>,
    groupColor: Int,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState) -> Unit,
    coordinator: DragCoordinator,
    hoverState: TabSheetHoverState,
    hoveredGroupId: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = tabs.size,
                key = { index -> tabs[index].id }
            ) { index ->
                val tab = tabs[index]
                Box(
                    modifier = Modifier
                        .width(140.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .draggableItem(
                                itemType = DraggableItemType.Tab(tab.id, groupId),
                                coordinator = coordinator
                            )
                            .dropTarget(
                                id = tab.id,
                                type = DropTargetType.TAB,
                                coordinator = coordinator,
                                metadata = mapOf(
                                    "tabId" to tab.id,
                                    "groupId" to groupId
                                )
                            )
                            .groupedTabFeedback(
                                tabId = tab.id,
                                groupId = groupId,
                                coordinator = coordinator,
                                hoverState = hoverState,
                                hoveredGroupId = hoveredGroupId,
                                draggedScale = 0.85f
                            )
                    ) {
                        GroupedTabGridItem(
                            tab = tab,
                            groupColor = groupColor,
                            isSelected = tab.id == selectedTabId,
                            onTabClick = { onTabClick(tab.id) },
                            onTabClose = { onTabClose(tab.id) },
                            onTabLongPress = { onTabLongPress(tab) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual tab item in grid group row
 */
@Composable
private fun GroupedTabGridItem(
    tab: TabSessionState,
    groupColor: Int,
    isSelected: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit
) {
    // Use the reusable TabGridCard component
    TabGridCard(
        tab = tab,
        isSelected = isSelected,
        groupColor = groupColor,
        onTabClick = onTabClick,
        onTabClose = onTabClose
    )
}
