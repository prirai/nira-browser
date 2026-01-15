package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
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
 * - Helper components: TabMenuHeader, TabMenuItem
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
 * - New Tab, Duplicate, Add to Island, Pin, Share, Select, Close, Close Others
 *
 * Options for grouped tabs:
 * - New Tab, Duplicate, Remove from Island, Pin, Share, Select, Close, Close Others
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            TabMenuHeader(tab)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.mozac_ic_tab_new_24), "New Tab") },
                text = "New Tab",
                onClick = {
                    scope.launch {
                        viewModel.createNewTab()
                        onDismiss()
                    }
                }
            )

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.control_point_duplicate_24px), "Duplicate") },
                text = "Duplicate Tab",
                onClick = {
                    scope.launch {
                        viewModel.duplicateTab(tab.id)
                        onDismiss()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (isInGroup) {
                TabMenuItem(
                    icon = { Icon(painterResource(R.drawable.ungroup_24px), "Remove from Island") },
                    text = "Remove from Island",
                    onClick = {
                        scope.launch {
                            viewModel.removeTabFromGroup(tab.id)
                            onDismiss()
                        }
                    }
                )
            }

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_pin_outline), "Pin Tab") },
                text = "Pin Tab",
                onClick = {
                    scope.launch {
                        viewModel.togglePinTab(tab.id)
                        onDismiss()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ios_share_24), "Share") },
                text = "Share Tab",
                onClick = {
                    onDismiss()
                    // Share functionality will be handled by the caller
                }
            )

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_select_all_24), "Select Tabs") },
                text = "Select Tabs",
                onClick = {
                    scope.launch {
                        viewModel.enterMultiSelectMode()
                        onDismiss()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            TabMenuItem(
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_close_small),
                        "Close Tab",
                        modifier = Modifier.size(32.dp)
                    )
                },
                text = "Close Tab",
                onClick = {
                    scope.launch {
                        viewModel.closeTab(tab.id)
                        onDismiss()
                    }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Inline rename field at the top
            var editedName by remember { mutableStateOf(groupName) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        bottomStart = 12.dp,
                        topEnd = 0.dp,
                        bottomEnd = 0.dp
                    )
                )

                // Accept button (rounded right end)
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(
                        topStart = 0.dp,
                        bottomStart = 0.dp,
                        topEnd = 12.dp,
                        bottomEnd = 12.dp
                    ),
                    color = if (editedName.isNotBlank() && editedName != groupName) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    onClick = {
                        if (editedName.isNotBlank() && editedName != groupName) {
                            scope.launch {
                                viewModel.renameGroup(groupId, editedName)
                                onDismiss()
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm rename",
                            tint = if (editedName.isNotBlank() && editedName != groupName) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_palette), "Change Color") },
                text = "Change Color",
                onClick = {
                    showColorPicker = true
                }
            )

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_expand_more), "Collapse") },
                text = "Collapse/Expand",
                onClick = {
                    scope.launch {
                        viewModel.toggleGroupExpanded(groupId)
                        onDismiss()
                    }
                }
            )

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_pin_outline), "Pin Island") },
                text = "Pin Island",
                onClick = {
                    scope.launch {
                        viewModel.pinGroup(groupId)
                        onDismiss()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ungroup_24px), "Ungroup") },
                text = "Ungroup All Tabs",
                onClick = {
                    scope.launch {
                        viewModel.ungroupAllTabs(groupId)
                        onDismiss()
                    }
                }
            )

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_close_small), "Close All") },
                text = "Close All Tabs",
                onClick = {
                    scope.launch {
                        viewModel.closeAllTabsInGroup(groupId)
                        onDismiss()
                    }
                }
            )

            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_profile), "Move to Profile") },
                text = "Move to Profile",
                onClick = {
                    showProfilePicker = true
                }
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

    if (showProfilePicker) {
        ProfilePickerDialog(
            onConfirm = { profileId ->
                scope.launch {
                    viewModel.moveGroupToProfile(groupId, profileId)
                    showProfilePicker = false
                    onDismiss()
                }
            },
            onDismiss = {
                showProfilePicker = false
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
                text = tab.content.title.ifEmpty { "New Tab" },
                style = MaterialTheme.typography.titleSmall,
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

/**
 * Reusable menu item component with icon and text.
 * Provides consistent styling across all menu items.
 */
@Composable
private fun TabMenuItem(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
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
