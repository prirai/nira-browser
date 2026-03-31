package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
        tonalElevation = 2.dp
    ) {
        // IntrinsicSize.Min lets the left accent strip fill the container's full height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // 4dp colored left strip matching group color
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Color(color),
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )

            Column(modifier = Modifier.weight(1f)) {
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Child tabs wrapped in extension-list style Surface — shown when expanded
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        children.forEachIndexed { index, tab ->
                            val isLastInGroup = index == children.size - 1

                            // Drag insertion indicator
                            if (isDragging && hoverState.isDraggingGroupedTab() && coordinator.isHoveringOver("divider_${tab.id}")) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .padding(horizontal = 16.dp)
                                        .background(Color(color), RoundedCornerShape(2.dp))
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
                                    onShowTabMenu = { onShowTabMenu(tab, true) },
                                    isDragging = isDragging,
                                    coordinator = coordinator,
                                    hoverState = hoverState,
                                    hoveredGroupId = hoveredGroupId
                                )
                            }

                            if (index < children.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 72.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
            } // end Column
        } // end Row IntrinsicSize
    }
}


/**
 * Grouped tab item within the container — wraps [GroupedTabRow] with swipe-to-dismiss
 * and drag-and-drop modifiers.
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
        // Inline tab row that lives inside the shared group Surface container
        GroupedTabRow(
            tab = tab,
            isSelected = isSelected,
            groupColor = groupColor,
            onTabClick = onTabClick,
            onTabClose = onTabClose,
            modifier = Modifier
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
 * A single tab row rendered inside the grouped Surface container.
 * Has no own background Surface — relies on the parent surfaceContainerHigh container.
 * Selected state is shown via a subtle background tint on the row.
 */
@Composable
private fun GroupedTabRow(
    tab: TabSessionState,
    isSelected: Boolean,
    groupColor: Int,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .clickable { onTabClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 40dp rounded favicon container
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                FaviconImage(tab = tab, size = 22.dp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title + URL
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = tab.content.title.ifEmpty { "New Tab" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (tab.content.url.isNotEmpty()) {
                Text(
                    text = tab.content.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Close button
        IconButton(
            onClick = onTabClose,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close tab",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
        tonalElevation = 2.dp
    ) {
        // IntrinsicSize.Min lets the left accent strip fill the container's full height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // 4dp colored left strip matching group color
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Color(color),
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )

            Column(modifier = Modifier.weight(1f)) {
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Child tabs grid — shown when expanded
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
            } // end Column
        } // end Row IntrinsicSize
    }
}

/**
 * 2-column grid of tabs within a group, wrapped in the extension-list style Surface container.
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        val tabChunks = tabs.chunked(2)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabChunks.forEach { rowTabs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTabs.forEach { tab ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
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
                    // Pad to maintain the 2-column grid when the last row has only 1 tab
                    if (rowTabs.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
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
