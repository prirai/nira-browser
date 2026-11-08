package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import kotlinx.coroutines.CoroutineScope
import mozilla.components.browser.state.state.TabSessionState

/**
 * The context-menu state both tab sheet views (list and grid) share: which tab or
 * group a menu is open for, and whether it is showing. Kept as one object instead
 * of five loose `remember` booleans duplicated per view.
 */
@androidx.compose.runtime.Stable
class TabSheetMenuState {
    var menuTab by mutableStateOf<TabSessionState?>(null)
    var menuIsInGroup by mutableStateOf(false)
    var showTabMenu by mutableStateOf(false)
    var menuGroupId by mutableStateOf<String?>(null)
    var menuGroupName by mutableStateOf<String?>(null)
    var showGroupMenu by mutableStateOf(false)

    fun openTabMenu(tab: TabSessionState, inGroup: Boolean) {
        menuTab = tab
        menuIsInGroup = inGroup
        showTabMenu = true
    }

    fun openGroupMenu(groupId: String, groupName: String) {
        menuGroupId = groupId
        menuGroupName = groupName
        showGroupMenu = true
    }
}

@Composable
fun rememberTabSheetMenuState(): TabSheetMenuState = remember { TabSheetMenuState() }

/**
 * Renders the tab and group context menus for a sheet view. One host instead of
 * the same two `if` blocks duplicated at the bottom of every view.
 */
@Composable
fun TabSheetMenuHost(
    menuState: TabSheetMenuState,
    viewModel: TabViewModel,
    scope: CoroutineScope,
) {
    if (menuState.showTabMenu && menuState.menuTab != null) {
        TabContextMenu(
            tab = menuState.menuTab!!,
            isInGroup = menuState.menuIsInGroup,
            onDismiss = { menuState.showTabMenu = false },
            viewModel = viewModel,
            scope = scope,
        )
    }
    if (menuState.showGroupMenu && menuState.menuGroupId != null) {
        GroupContextMenu(
            groupId = menuState.menuGroupId!!,
            groupName = menuState.menuGroupName ?: stringResource(R.string.group),
            onDismiss = { menuState.showGroupMenu = false },
            viewModel = viewModel,
            scope = scope,
        )
    }
}

/**
 * The unified item list every tab sheet view renders: build + dedup, in one
 * place. Keys stay stable across view modes so remembered item state (drag
 * offset, animateItem) travels with the tab.
 */
@Composable
fun rememberUnifiedSheetItems(
    tabs: List<TabSessionState>,
    groups: List<TabGroupData>,
    expandedGroups: Set<String>,
    currentOrder: UnifiedTabOrder?,
): List<UnifiedItem> {
    val items = remember(tabs, groups, expandedGroups, currentOrder) {
        UnifiedItemBuilder.buildItems(
            order = currentOrder,
            tabs = tabs,
            groups = groups,
            expandedGroups = expandedGroups,
        )
    }
    return remember(items) { UnifiedItemBuilder.deduplicateItems(items) }
}

/**
 * The group header row both sheet views render: Surface with the group tint,
 * expand/collapse indicator, emphasized name, count badge, and a view-specific
 * [trailing] affordance (the list view shows a drag handle, the grid view an
 * options button).
 */
@Composable
fun GroupHeaderRow(
    title: String,
    color: Int,
    tabCount: Int,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        // Neutral M3 container; the group color lives on the name, badge
        // and expand icon - not on the container fill or a border.
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
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
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(if (isExpanded) R.string.collapse else R.string.expand),
                    tint = Color(color),
                    modifier = Modifier.size(24.dp)
                )

                // Group name
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
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
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = Color(color)
                        )
                    }
                }
            }

            trailing()
        }
    }
}

/**
 * The swipe affordance every list-shaped tab row shares: swipe toward end
 * (left) triggers [onDismiss], swipe toward start (right) opens the menu via
 * [onMenu]. Both directions are disabled while a drag is in flight so the
 * drag system owns the gesture.
 *
 * [dismissIcon] and [dismissIconLabel] let the group header carry "Ungroup"
 * where tab rows carry the delete glyph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwipeWrapper(
    isDragging: Boolean,
    onDismiss: () -> Unit,
    onMenu: () -> Unit,
    dismissIcon: ImageVector = Icons.Default.Delete,
    dismissIconLabel: String = stringResource(R.string.delete),
    content: @Composable () -> Unit,
) {
    @Suppress("DEPRECATION")
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (isDragging) {
                false
            } else {
                when (dismissValue) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        onDismiss()
                        false
                    }

                    SwipeToDismissBoxValue.StartToEnd -> {
                        onMenu()
                        false
                    }

                    else -> false
                }
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isDragging,
        enableDismissFromEndToStart = !isDragging,
        backgroundContent = { TabSwipeBackground(dismissState, dismissIcon, dismissIconLabel) },
    ) {
        content()
    }
}

/**
 * The swipe background: errorContainer with the destructive glyph when
 * swiping toward the end, primaryContainer with the menu glyph when swiping
 * toward the start.
 */
@Composable
private fun TabSwipeBackground(
    dismissState: SwipeToDismissBoxState,
    dismissIcon: ImageVector,
    dismissIconLabel: String,
) {
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
            Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        when (dismissDirection) {
            SwipeToDismissBoxValue.EndToStart -> {
                Icon(
                    imageVector = dismissIcon,
                    contentDescription = dismissIconLabel,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            SwipeToDismissBoxValue.StartToEnd -> {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            else -> {}
        }
    }
}
