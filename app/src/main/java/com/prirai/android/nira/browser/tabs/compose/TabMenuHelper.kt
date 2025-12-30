package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import android.view.View
import com.prirai.android.nira.R
import com.prirai.android.nira.components.menu.Material3BrowserMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import com.prirai.android.nira.browser.tabs.compose.TabViewModel

object TabMenuHelper {
    
    fun showTabMenu(
        context: Context,
        anchorView: View,
        tab: TabSessionState,
        isInGroup: Boolean,
        viewModel: TabViewModel,
        scope: CoroutineScope,
        onMoveToProfile: (String) -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        val menuItems = buildList {
            // Option 1: Move to Profile (for all tabs)
            add(Material3BrowserMenu.MenuItem.Action(
                id = "move_to_profile",
                title = "Move to Profile",
                iconRes = R.drawable.ic_profile,
                onClick = {
                    onMoveToProfile(tab.id)
                }
            ))
            
            // Option 2: Duplicate Tab (for all tabs)
            add(Material3BrowserMenu.MenuItem.Action(
                id = "duplicate",
                title = "Duplicate Tab",
                iconRes = R.drawable.control_point_duplicate_24px,
                onClick = {
                    scope.launch {
                        // Duplicate the tab - would need implementation
                        onDismiss()
                    }
                }
            ))
            
            // Option 3: Pin/Unpin Tab (for all tabs)
            add(Material3BrowserMenu.MenuItem.Action(
                id = "pin",
                title = "Pin Tab",
                iconRes = R.drawable.ic_pin_outline,
                onClick = {
                    scope.launch {
                        // Pin/unpin functionality - would need implementation
                        onDismiss()
                    }
                }
            ))
            
            // Option 4: Remove from Group (only for grouped tabs)
            if (isInGroup) {
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
            }
        }
        
        val menu = Material3BrowserMenu(context, menuItems)
        menu.show(anchorView, preferBottom = false)
    }
    
    fun showGroupMenu(
        context: Context,
        anchorView: View,
        groupId: String,
        viewModel: TabViewModel,
        scope: CoroutineScope,
        onRename: (String) -> Unit,
        onChangeColor: (String) -> Unit,
        onMoveToProfile: (String) -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        val menuItems = listOf(
            Material3BrowserMenu.MenuItem.Action(
                id = "rename",
                title = "Rename Island",
                iconRes = R.drawable.ic_edit,
                onClick = {
                    onRename(groupId)
                }
            ),
            Material3BrowserMenu.MenuItem.Action(
                id = "change_color",
                title = "Change Color",
                iconRes = R.drawable.ic_palette,
                onClick = {
                    onChangeColor(groupId)
                }
            ),
            Material3BrowserMenu.MenuItem.Action(
                id = "move_to_profile",
                title = "Move Group to Profile",
                iconRes = R.drawable.ic_profile,
                onClick = {
                    onMoveToProfile(groupId)
                }
            ),
            Material3BrowserMenu.MenuItem.Action(
                id = "ungroup",
                title = "Ungroup All Tabs",
                iconRes = R.drawable.ungroup_24px,
                onClick = {
                    scope.launch {
                        // Ungroup all tabs - would need proper implementation
                        onDismiss()
                    }
                }
            ),
            Material3BrowserMenu.MenuItem.Action(
                id = "close_all",
                title = "Close All Tabs",
                iconRes = R.drawable.ic_close_small,
                onClick = {
                    scope.launch {
                        // Close all tabs in group - would need proper implementation
                        onDismiss()
                    }
                }
            )
        )
        
        val menu = Material3BrowserMenu(context, menuItems)
        menu.show(anchorView, preferBottom = false)
    }
}
