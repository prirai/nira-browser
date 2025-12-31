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
                        // Duplicate the tab - would need implementation
                        onDismiss()
                }
            ))
            
            // Option 3: Pin/Unpin Tab (for all tabs)
            add(Material3BrowserMenu.MenuItem.Action(
                id = "pin",
                title = "Pin Tab",
                iconRes = R.drawable.ic_pin_outline,
                onClick = {
                    // Pin/unpin functionality - would need implementation
                    onDismiss()
                }
            ))
            

        }
        
        val menu = Material3BrowserMenu(context, menuItems)
        menu.show(anchorView, preferBottom = false)
    }
    

}
