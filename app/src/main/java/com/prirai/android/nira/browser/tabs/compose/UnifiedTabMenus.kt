package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import android.view.View
import com.prirai.android.nira.R
import com.prirai.android.nira.components.menu.Material3BrowserMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * DEPRECATED: Old menu system using Material3BrowserMenu (View-based).
 * This file is kept for reference but is NO LONGER USED.
 * 
 * The new menu system uses TabMenuCompose.kt with ModalBottomSheet (Compose-based).
 * See: TabMenuCompose.kt - TabContextMenu() and GroupContextMenu()
 * 
 * DO NOT USE THIS FILE. It will be removed in a future update.
 */
@Deprecated(
    message = "Use TabMenuCompose.kt instead",
    replaceWith = ReplaceWith("TabContextMenu", "com.prirai.android.nira.browser.tabs.compose")
)
object UnifiedTabMenus {
    
    /**
     * Show menu for an ungrouped tab.
     * Available in: Tab Bar, List View, Grid View
     */
    fun showUngroupedTabMenu(
        context: Context,
        anchorView: View,
        tab: TabSessionState,
        viewModel: TabViewModel,
        scope: CoroutineScope,
        onMoveToProfile: (String) -> Unit = {},
        onShare: (String) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        val menuItems = buildList {
            // New Tab
            add(Material3BrowserMenu.MenuItem.Action(
                id = "new_tab",
                title = "New Tab",
                iconRes = R.drawable.mozac_ic_tab_new_24,
                onClick = {
                    scope.launch {
                        viewModel.createNewTab()
                        onDismiss()
                    }
                }
            ))
            
            // Duplicate Tab
            add(Material3BrowserMenu.MenuItem.Action(
                id = "duplicate",
                title = "Duplicate Tab",
                iconRes = R.drawable.control_point_duplicate_24px,
                onClick = {
                    scope.launch {
                        viewModel.duplicateTab(tab.id)
                        onDismiss()
                    }
                }
            ))
            
            // Add divider
            add(Material3BrowserMenu.MenuItem.Divider)
            
            // Add to Group
            add(Material3BrowserMenu.MenuItem.Action(
                id = "add_to_group",
                title = "Add to Island",
                iconRes = R.drawable.ic_tab_group,
                onClick = {
                    scope.launch {
                        viewModel.showAddToGroupDialog(tab.id)
                        onDismiss()
                    }
                }
            ))
            
            // Move to Profile
            add(Material3BrowserMenu.MenuItem.Action(
                id = "move_to_profile",
                title = "Move to Profile",
                iconRes = R.drawable.ic_profile,
                onClick = {
                    onMoveToProfile(tab.id)
                    onDismiss()
                }
            ))
            
            // Add divider
            add(Material3BrowserMenu.MenuItem.Divider)
            
            // Share
            add(Material3BrowserMenu.MenuItem.Action(
                id = "share",
                title = "Share",
                iconRes = R.drawable.ic_share,
                onClick = {
                    onShare(tab.id)
                    onDismiss()
                }
            ))
            
            // Select Tabs (multi-select mode)
            add(Material3BrowserMenu.MenuItem.Action(
                id = "select_tabs",
                title = "Select Tabs",
                iconRes = R.drawable.ic_check_circle,
                onClick = {
                    scope.launch {
                        viewModel.enterMultiSelectMode()
                        onDismiss()
                    }
                }
            ))
            
            // Add divider
            add(Material3BrowserMenu.MenuItem.Divider)
            
            // Close Tab
            add(Material3BrowserMenu.MenuItem.Action(
                id = "close",
                title = "Close Tab",
                iconRes = R.drawable.ic_close_small,
                onClick = {
                    scope.launch {
                        viewModel.closeTab(tab.id)
                        onDismiss()
                    }
                }
            ))
        }
        
        val menu = Material3BrowserMenu(context, menuItems)
        menu.show(anchorView, preferBottom = false)
    }
    
    /**
     * Show menu for a grouped tab.
     * Available in: Tab Bar, List View, Grid View
     * Same as ungrouped + "Remove from Group" option
     */
    fun showGroupedTabMenu(
        context: Context,
        anchorView: View,
        tab: TabSessionState,
        groupId: String,
        viewModel: TabViewModel,
        scope: CoroutineScope,
        onMoveToProfile: (String) -> Unit = {},
        onShare: (String) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        val menuItems = buildList {
            // New Tab
            add(Material3BrowserMenu.MenuItem.Action(
                id = "new_tab",
                title = "New Tab",
                iconRes = R.drawable.mozac_ic_tab_new_24,
                onClick = {
                    scope.launch {
                        viewModel.createNewTab()
                        onDismiss()
                    }
                }
            ))
            
            // Duplicate Tab
            add(Material3BrowserMenu.MenuItem.Action(
                id = "duplicate",
                title = "Duplicate Tab",
                iconRes = R.drawable.control_point_duplicate_24px,
                onClick = {
                    scope.launch {
                        viewModel.duplicateTab(tab.id)
                        onDismiss()
                    }
                }
            ))
            
            // Add divider
            add(Material3BrowserMenu.MenuItem.Divider)
            
            // Remove from Group (specific to grouped tabs)
            add(Material3BrowserMenu.MenuItem.Action(
                id = "remove_from_group",
                title = "Remove from Island",
                iconRes = R.drawable.ungroup_24px,
                onClick = {
                    scope.launch {
                        viewModel.removeTabFromGroup(tab.id)
                        onDismiss()
                    }
                }
            ))
            
            // Move to Profile
            add(Material3BrowserMenu.MenuItem.Action(
                id = "move_to_profile",
                title = "Move to Profile",
                iconRes = R.drawable.ic_profile,
                onClick = {
                    onMoveToProfile(tab.id)
                    onDismiss()
                }
            ))
            
            // Add divider
            add(Material3BrowserMenu.MenuItem.Divider)
            
            // Share
            add(Material3BrowserMenu.MenuItem.Action(
                id = "share",
                title = "Share",
                iconRes = R.drawable.ic_share,
                onClick = {
                    onShare(tab.id)
                    onDismiss()
                }
            ))
            
            // Select Tabs (multi-select mode)
            add(Material3BrowserMenu.MenuItem.Action(
                id = "select_tabs",
                title = "Select Tabs",
                iconRes = R.drawable.ic_check_circle,
                onClick = {
                    scope.launch {
                        viewModel.enterMultiSelectMode()
                        onDismiss()
                    }
                }
            ))
            
            // Add divider
            add(Material3BrowserMenu.MenuItem.Divider)
            
            // Close Tab
            add(Material3BrowserMenu.MenuItem.Action(
                id = "close",
                title = "Close Tab",
                iconRes = R.drawable.ic_close_small,
                onClick = {
                    scope.launch {
                        viewModel.closeTab(tab.id)
                        onDismiss()
                    }
                }
            ))
        }
        
        val menu = Material3BrowserMenu(context, menuItems)
        menu.show(anchorView, preferBottom = false)
    }
    
    /**
     * Show menu for a tab group container.
     * Available in: Tab Bar, List View, Grid View
     */
    fun showGroupMenu(
        context: Context,
        anchorView: View,
        groupId: String,
        groupName: String,
        viewModel: TabViewModel,
        scope: CoroutineScope,
        onRename: (String) -> Unit = {},
        onChangeColor: (String) -> Unit = {},
        onMoveToProfile: (String) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        val menuItems = listOf(
            // Rename Group
            Material3BrowserMenu.MenuItem.Action(
                id = "rename",
                title = "Rename Island",
                iconRes = R.drawable.ic_edit,
                onClick = {
                    onRename(groupId)
                    onDismiss()
                }
            ),
            
            // Change Color
            Material3BrowserMenu.MenuItem.Action(
                id = "change_color",
                title = "Change Color",
                iconRes = R.drawable.ic_palette,
                onClick = {
                    onChangeColor(groupId)
                    onDismiss()
                }
            ),
            
            // Add divider
            Material3BrowserMenu.MenuItem.Divider,
            
            // Move Group to Profile
            Material3BrowserMenu.MenuItem.Action(
                id = "move_to_profile",
                title = "Move Island to Profile",
                iconRes = R.drawable.ic_profile,
                onClick = {
                    onMoveToProfile(groupId)
                    onDismiss()
                }
            ),
            
            // Add divider
            Material3BrowserMenu.MenuItem.Divider,
            
            // Ungroup All Tabs
            Material3BrowserMenu.MenuItem.Action(
                id = "ungroup",
                title = "Ungroup All Tabs",
                iconRes = R.drawable.ungroup_24px,
                onClick = {
                    scope.launch {
                        viewModel.ungroupAllTabs(groupId)
                        onDismiss()
                    }
                }
            ),
            
            // Close All Tabs in Group
            Material3BrowserMenu.MenuItem.Action(
                id = "close_all",
                title = "Close All Tabs",
                iconRes = R.drawable.ic_close_small,
                onClick = {
                    scope.launch {
                        viewModel.closeAllTabsInGroup(groupId)
                        onDismiss()
                    }
                }
            )
        )
        
        val menu = Material3BrowserMenu(context, menuItems)
        menu.show(anchorView, preferBottom = false)
    }
}
