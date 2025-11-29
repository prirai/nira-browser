package com.prirai.android.nira.customtabs

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.ColorRes
import mozilla.components.browser.menu.BrowserMenuBuilder
import mozilla.components.browser.menu.item.BrowserMenuCategory
import mozilla.components.browser.menu.item.BrowserMenuDivider
import mozilla.components.browser.menu.item.BrowserMenuImageText
import mozilla.components.browser.menu.item.SimpleBrowserMenuItem
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.state.CustomTabSessionState
import mozilla.components.browser.state.store.BrowserStore
import com.prirai.android.nira.R
import mozilla.components.ui.icons.R as IconsR

/**
 * Builds the toolbar menu for custom tabs with options like "Open in Nira".
 */
class CustomTabToolbarMenu(
    private val context: Context,
    private val store: BrowserStore,
    private val sessionId: String?,
    private val onItemTapped: (Item) -> Unit = {}
) {

    sealed class Item {
        object FindInPage : Item()
        object Share : Item()
        object OpenInBrowser : Item()
        data class RequestDesktop(val checked: Boolean) : Item()
    }

    val menuBuilder by lazy {
        BrowserMenuBuilder(menuItems)
    }

    private val session: CustomTabSessionState?
        get() = sessionId?.let { store.state.findCustomTab(it) }

    @ColorRes
    private fun primaryTextColor(): Int {
        return android.R.color.primary_text_dark
    }

    private val menuItems by lazy {
        listOf(
            // "Powered by Nira" header
            BrowserMenuCategory(
                label = context.getString(R.string.powered_by_nira).uppercase(),
                textSize = 12f,
                textColorResource = primaryTextColor(),
                textStyle = Typeface.NORMAL,
            ),
            BrowserMenuDivider(),
            
            // Menu items
            BrowserMenuImageText(
                label = context.getString(R.string.browser_menu_find_in_page),
                imageResource = IconsR.drawable.mozac_ic_search_24,
                iconTintColorResource = primaryTextColor(),
            ) {
                onItemTapped(Item.FindInPage)
            },
            BrowserMenuImageText(
                label = context.getString(R.string.share),
                imageResource = IconsR.drawable.mozac_ic_share_android_24,
                iconTintColorResource = primaryTextColor(),
            ) {
                onItemTapped(Item.Share)
            },
            BrowserMenuDivider(),
            SimpleBrowserMenuItem(
                label = context.getString(R.string.browser_menu_open_in_nira),
                textColorResource = primaryTextColor(),
            ) {
                onItemTapped(Item.OpenInBrowser)
            }
        )
    }
}
