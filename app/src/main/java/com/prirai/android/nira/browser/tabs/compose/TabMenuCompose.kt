@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.rememberNiraSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Unified Tab Menu System
 *
 * This file provides a comprehensive, reusable menu system for tabs and groups.
 * It includes:
 * - TabContextMenu: Menu for individual tabs (grouped or ungrouped)
 * - GroupContextMenu: Menu for group containers
 * - UnifiedTabMenu: Smart entry point that shows appropriate menu based on context
 * - Reusable dialogs: RenameGroupDialog, ColorPickerDialog, ProfilePickerDialog
 * - Helper components: TabMenuHeader, MenuCardGroup
 *
 * Usage:
 * - For tabs: Call TabContextMenu with isInGroup parameter
 * - For groups: Call GroupContextMenu
 * - For automatic detection: Call UnifiedTabMenu (recommended)
 */

// ============================================================================
// UNIFIED MENU ENTRY POINT
// ============================================================================

/**
 * Unified menu that automatically determines context and shows appropriate menu.
 * This is the recommended entry point for showing menus.
 *
 * @param menuType The type of menu to show (Tab or Group)
 * @param onDismiss Callback when menu is dismissed
 * @param viewModel The TabViewModel for actions
 * @param scope CoroutineScope for launching actions
 */
@Composable
fun UnifiedTabMenu(
    menuType: TabMenuType,
    onDismiss: () -> Unit,
    viewModel: TabViewModel,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    when (menuType) {
        is TabMenuType.Tab -> {
            TabContextMenu(
                tab = menuType.tab,
                isInGroup = menuType.isInGroup,
                onDismiss = onDismiss,
                viewModel = viewModel,
                scope = scope,
                modifier = modifier
            )
        }

        is TabMenuType.Group -> {
            GroupContextMenu(
                groupId = menuType.groupId,
                groupName = menuType.groupName,
                onDismiss = onDismiss,
                viewModel = viewModel,
                scope = scope,
                modifier = modifier
            )
        }
    }
}

/**
 * Sealed class representing the type of menu to show.
 * Provides type-safe menu context.
 */
sealed class TabMenuType {
    /**
     * Menu for an individual tab
     * @param tab The tab session state
     * @param isInGroup Whether the tab is currently in a group
     */
    data class Tab(
        val tab: TabSessionState,
        val isInGroup: Boolean
    ) : TabMenuType()

    /**
     * Menu for a group container
     * @param groupId The ID of the group
     * @param groupName The display name of the group
     */
    data class Group(
        val groupId: String,
        val groupName: String
    ) : TabMenuType()
}

// ============================================================================
// TAB CONTEXT MENU
// ============================================================================

/**
 * Context menu for individual tabs.
 * Shows different options based on whether the tab is in a group.
 *
 * Options for ungrouped tabs:
 * - Duplicate, Close
 *
 * Options for grouped tabs:
 * - Duplicate, Remove from Island, Close
 *
 * @param tab The tab to show menu for
 * @param isInGroup Whether this tab is currently in a group
 * @param onDismiss Callback when menu is dismissed
 * @param viewModel The TabViewModel for performing actions
 * @param scope CoroutineScope for launching coroutines
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabContextMenu(
    tab: TabSessionState,
    isInGroup: Boolean,
    onDismiss: () -> Unit,
    viewModel: TabViewModel,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    var showProfilePicker by remember { mutableStateOf(false) }

    if (showProfilePicker) {
        ProfilePickerDialog(
            currentContextId = tab.contextId,
            onConfirm = { profileId ->
                viewModel.moveTabToProfile(tab.id, profileId)
            },
            onDismiss = {
                showProfilePicker = false
                onDismiss()
            }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberNiraSheetState(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            TabMenuHeader(tab)

            MenuCardGroup(
                buildList {
                    add(
                        MenuEntry(
                            icon = { MenuIcon(painterResource(R.drawable.control_point_duplicate_24px)) },
                            text = stringResource(R.string.duplicate_tab),
                            onClick = {
                                scope.launch {
                                    viewModel.duplicateTab(tab.id)
                                    onDismiss()
                                }
                            }
                        )
                    )
                    if (isInGroup) {
                        add(
                            MenuEntry(
                                icon = { MenuIcon(painterResource(R.drawable.ungroup_24px)) },
                                text = stringResource(R.string.remove_from_island),
                                onClick = {
                                    scope.launch {
                                        viewModel.removeTabFromGroup(tab.id)
                                        onDismiss()
                                    }
                                }
                            )
                        )
                    }
                    add(
                        MenuEntry(
                            icon = { MenuIcon(painterResource(R.drawable.ic_profile)) },
                            text = stringResource(R.string.move_to_different_profile),
                            onClick = { showProfilePicker = true }
                        )
                    )
                }
            )
        }
    }
}

// ============================================================================
// GROUP CONTEXT MENU
// ============================================================================

/**
 * Context menu for group containers.
 * Provides group-specific operations like rename, color change, ungroup, etc.
 *
 * Available options:
 * - New Tab in Island: Creates a new tab in this group
 * - Rename Island: Shows dialog to rename the group
 * - Change Color: Shows color picker to change group color
 * - Collapse/Expand: Toggles group expansion state
 * - Pin Island: Pins/unpins the group
 * - Ungroup All Tabs: Removes all tabs from the group
 * - Close All Tabs: Closes all tabs in the group
 * - Move to Profile: Shows profile picker to move group
 *
 * @param groupId The ID of the group
 * @param groupName The display name of the group
 * @param onDismiss Callback when menu is dismissed
 * @param viewModel The TabViewModel for performing actions
 * @param scope CoroutineScope for launching coroutines
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupContextMenu(
    groupId: String,
    groupName: String,
    onDismiss: () -> Unit,
    viewModel: TabViewModel,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showProfilePicker by remember { mutableStateOf(false) }

    val groups by viewModel.groups.collectAsState()
    val currentGroup = groups.find { it.id == groupId }
    val groupAccentColor = currentGroup?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    if (showProfilePicker) {
        ProfilePickerDialog(
            currentContextId = currentGroup?.contextId,
            onConfirm = { profileId ->
                viewModel.moveGroupToProfile(groupId, profileId)
            },
            onDismiss = {
                showProfilePicker = false
                onDismiss()
            }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberNiraSheetState(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Inline rename field at the top
            var editedName by remember { mutableStateOf(groupName) }

            val canSave = editedName.isNotBlank() && editedName != groupName
            val saveName = {
                if (canSave) {
                    scope.launch {
                        viewModel.renameGroup(groupId, editedName)
                        onDismiss()
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = stringResource(R.string.change_color)) {
                            showColorPicker = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(groupAccentColor, CircleShape)
                    )
                }
                TextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.group)) },
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { saveName() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                FilledIconButton(
                    onClick = { saveName() },
                    enabled = canSave,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.confirm_rename))
                }
            }

            MenuCardGroup(
                listOf(
                    MenuEntry(
                        icon = { MenuIcon(painterResource(R.drawable.ic_pin_outline)) },
                        text = stringResource(R.string.pin_island),
                        onClick = {
                            scope.launch {
                                viewModel.pinGroup(groupId)
                                onDismiss()
                            }
                        }
                    ),
                    MenuEntry(
                        icon = { MenuIcon(painterResource(R.drawable.ic_profile)) },
                        text = stringResource(R.string.move_to_different_profile),
                        onClick = { showProfilePicker = true }
                    ),
                )
            )
            Spacer(Modifier.height(12.dp))
            MenuCardGroup(
                listOf(
                    MenuEntry(
                        icon = { MenuIcon(painterResource(R.drawable.ungroup_24px)) },
                        text = stringResource(R.string.ungroup_all_tabs),
                        onClick = {
                            scope.launch {
                                viewModel.ungroupAllTabs(groupId)
                                onDismiss()
                            }
                        }
                    ),
                    MenuEntry(
                        icon = { MenuIcon(Icons.Default.Close, MaterialTheme.colorScheme.error) },
                        text = stringResource(R.string.close_all_tabs),
                        destructive = true,
                        onClick = {
                            scope.launch {
                                viewModel.closeAllTabsInGroup(groupId)
                                onDismiss()
                            }
                        }
                    ),
                )
            )
        }
    }

    // Show dialogs
    if (showRenameDialog && currentGroup != null) {
        RenameGroupDialog(
            currentName = currentGroup.name,
            onConfirm = { newName ->
                scope.launch {
                    viewModel.renameGroup(groupId, newName)
                    showRenameDialog = false
                    onDismiss()
                }
            },
            onDismiss = {
                showRenameDialog = false
                onDismiss()
            }
        )
    }

    if (showColorPicker && currentGroup != null) {
        ColorPickerDialog(
            currentColor = currentGroup.color,
            onConfirm = { newColor ->
                scope.launch {
                    viewModel.changeGroupColor(groupId, newColor)
                    showColorPicker = false
                    onDismiss()
                }
            },
            onDismiss = {
                showColorPicker = false
                onDismiss()
            }
        )
    }
}

// ============================================================================
// REUSABLE MENU COMPONENTS
// ============================================================================

/**
 * Header component for tab menus showing favicon, title, and URL.
 */
@Composable
private fun TabMenuHeader(tab: TabSessionState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FaviconImage(
            tab = tab,
            size = 24.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tab.content.title.ifEmpty { stringResource(R.string.tab_history_new_tab) },
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1
            )
            Text(
                text = tab.content.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** One action in a menu card. */
internal data class MenuEntry(
    val icon: @Composable () -> Unit,
    val text: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

@Composable
internal fun MenuIcon(
    painter: androidx.compose.ui.graphics.painter.Painter,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(painter, null, tint = tint, modifier = Modifier.size(24.dp))
}

@Composable
internal fun MenuIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(imageVector, null, tint = tint, modifier = Modifier.size(24.dp))
}

/**
 * Entries stacked in one rounded card with dividers, the same list idiom the
 * Downloads, History and Bookmarks windows use ([NiraCardRow]).
 */
@Composable
internal fun MenuCardGroup(entries: List<MenuEntry>) {
    entries.forEachIndexed { index, entry ->
        NiraCardRow(
            title = entry.text,
            isTopRow = index == 0,
            isBottomRow = index == entries.lastIndex,
            showDivider = index != entries.lastIndex,
            onClick = entry.onClick,
            titleColor = if (entry.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            leading = entry.icon,
            minRowHeight = 56.dp,
            dividerIndent = 52.dp,
        )
    }
}

// ============================================================================
// NOTE: Dialog components have been moved to TabMenuDialogs.kt
// ============================================================================

/**
 * The following dialog components are now in TabMenuDialogs.kt:
 * - RenameGroupDialog
 * - ColorPickerDialog
 * - ProfilePickerDialog
 *
 * They are still imported and available for use in this file.
 * This separation improves code organization and reusability.
 */
