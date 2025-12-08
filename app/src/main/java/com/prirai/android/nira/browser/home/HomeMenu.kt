package com.prirai.android.nira.browser.home

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.R
import com.prirai.android.nira.components.toolbar.ThemedBrowserMenuImageText
import com.prirai.android.nira.preferences.UserPreferences
import mozilla.components.browser.menu.BrowserMenuBuilder
import mozilla.components.browser.menu.BrowserMenuHighlight
import mozilla.components.browser.menu.ext.getHighlight
import mozilla.components.browser.menu.item.BrowserMenuDivider

class HomeMenu(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
    private val onItemTapped: (Item) -> Unit = {},
    private val onMenuBuilderChanged: (BrowserMenuBuilder) -> Unit = {},
    private val onHighlightPresent: (BrowserMenuHighlight) -> Unit = {}
) {
    sealed class Item {
        data object NewTab : Item()
        data object NewPrivateTab : Item()
        data object AddonsManager : Item()
        data object Settings : Item()
        data object History : Item()
        data object Bookmarks : Item()
    }

    private val shouldUseBottomToolbar = UserPreferences(context).shouldUseBottomToolbar

    private val coreMenuItems by lazy {

        val newTabItem = ThemedBrowserMenuImageText(
            context.getString(R.string.mozac_browser_menu_new_tab),
            R.drawable.mozac_ic_tab_new_24
        ) {
            onItemTapped.invoke(Item.NewTab)
        }

        val newPrivateTabItem = ThemedBrowserMenuImageText(
            context.getString(R.string.mozac_browser_menu_new_private_tab),
            R.drawable.ic_incognito
        ) {
            onItemTapped.invoke(Item.NewPrivateTab)
        }

        val bookmarksIcon = R.drawable.ic_baseline_bookmark

        val bookmarksItem = ThemedBrowserMenuImageText(
            context.getString(R.string.action_bookmarks),
            bookmarksIcon
        ) {
            onItemTapped.invoke(Item.Bookmarks)
        }

        val historyItem = ThemedBrowserMenuImageText(
            context.getString(R.string.action_history),
            R.drawable.ic_baseline_history
        ) {
            onItemTapped.invoke(Item.History)
        }

        val addons = ThemedBrowserMenuImageText(
            context.getString(R.string.mozac_browser_menu_extensions),
            R.drawable.mozac_ic_extension_24
        ) {
            onItemTapped.invoke(Item.AddonsManager)
        }

        val settingsItem = ThemedBrowserMenuImageText(
            context.getString(R.string.settings),
            R.drawable.ic_round_settings
        ) {
            onItemTapped.invoke(Item.Settings)
        }

        val menuItems = listOfNotNull(
            newTabItem,
            newPrivateTabItem,
            BrowserMenuDivider(),
            historyItem,
            bookmarksItem,
            BrowserMenuDivider(),
            settingsItem,
            addons
        ).also { items ->
            items.getHighlight()?.let { onHighlightPresent(it) }
        }

        if (shouldUseBottomToolbar) {
            menuItems.reversed()
        } else {
            menuItems
        }
    }

    init {
        onMenuBuilderChanged(BrowserMenuBuilder(coreMenuItems))
    }
}
