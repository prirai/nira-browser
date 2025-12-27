package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.browser.state.state.TabSessionState

/**
 * Helper functions to integrate Compose tab views into existing fragments
 */

/**
 * Create a ComposeView for the tab bar
 */
fun Fragment.createTabBarComposeView(
    tabs: List<TabSessionState>,
    orderManager: TabOrderManager,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit
): ComposeView {
    return ComposeView(requireContext()).apply {
        setContent {
            NiraTheme {
                TabBarCompose(
                    tabs = tabs,
                    orderManager = orderManager,
                    selectedTabId = selectedTabId,
                    onTabClick = onTabClick,
                    onTabClose = onTabClose
                )
            }
        }
    }
}

/**
 * Create a ComposeView for the tab list
 */
fun Fragment.createTabSheetListComposeView(
    tabs: List<TabSessionState>,
    orderManager: TabOrderManager,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit
): ComposeView {
    return ComposeView(requireContext()).apply {
        setContent {
            NiraTheme {
                TabSheetListCompose(
                    tabs = tabs,
                    orderManager = orderManager,
                    selectedTabId = selectedTabId,
                    onTabClick = onTabClick,
                    onTabClose = onTabClose
                )
            }
        }
    }
}

/**
 * Create a ComposeView for the tab grid
 */
fun Fragment.createTabSheetGridComposeView(
    tabs: List<TabSessionState>,
    orderManager: TabOrderManager,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    columns: Int = 3
): ComposeView {
    return ComposeView(requireContext()).apply {
        setContent {
            NiraTheme {
                TabSheetGridCompose(
                    tabs = tabs,
                    orderManager = orderManager,
                    selectedTabId = selectedTabId,
                    onTabClick = onTabClick,
                    onTabClose = onTabClose,
                    columns = columns
                )
            }
        }
    }
}

/**
 * Composable wrapper for easy integration - NOT CURRENTLY USED
 * Main implementation is in TabsBottomSheetFragment.ComposeTabContent()
 */
/*
@Composable
fun TabManagementScreen(
    tabs: List<TabSessionState>,
    selectedTabId: String?,
    profileId: String,
    viewType: TabViewType = TabViewType.LIST,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit
) {
    // Would need UnifiedTabGroupManager instance passed in
}
*/

enum class TabViewType {
    BAR,    // Horizontal tab bar
    LIST,   // Vertical list
    GRID    // 2D grid
}

/**
 * Migration helper to convert existing tab positions to UnifiedTabOrder
 * NOTE: Not used - kept for reference
 */
/*
suspend fun migrateToUnifiedOrder(
    context: android.content.Context,
    profileId: String,
    tabs: List<TabSessionState>
): UnifiedTabOrder {
    // Would need UnifiedTabGroupManager instance
    val primaryOrder = tabs.map { tab ->
        UnifiedTabOrder.OrderItem.SingleTab(tab.id)
    }
    return UnifiedTabOrder(profileId, primaryOrder)
}
*/
