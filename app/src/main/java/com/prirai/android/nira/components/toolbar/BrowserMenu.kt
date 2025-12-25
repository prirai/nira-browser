package com.prirai.android.nira.components.toolbar

import android.content.Context
import android.content.Intent
import androidx.annotation.ColorRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.R
import com.prirai.android.nira.addons.AddonsActivity
import com.prirai.android.nira.ext.components
import mozilla.components.browser.menu.WebExtensionBrowserMenuBuilder
import mozilla.components.browser.menu.item.BrowserMenuDivider
import mozilla.components.browser.menu.item.BrowserMenuItemToolbar
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore

class BrowserMenu(
    private val context: Context,
    private val store: BrowserStore,
    shouldReverseItems: Boolean,
    private val onItemTapped: (ToolbarMenu.Item) -> Unit = {},
    private val lifecycleOwner: LifecycleOwner,
    val isPinningSupported: Boolean
) : ToolbarMenu {

    private val selectedSession: TabSessionState? get() = store.state.selectedTab

    override val menuBuilder by lazy {
        WebExtensionBrowserMenuBuilder(
            menuItems,
            endOfMenuAlwaysVisible = !shouldReverseItems,
            store = store,
            style = WebExtensionBrowserMenuBuilder.Style(
                webExtIconTintColorResource = primaryTextColor()
            ),
            onAddonsManagerTapped = {
                val intent = Intent(context, AddonsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            },
            appendExtensionSubMenuAtStart = !shouldReverseItems
        )
    }

    override val menuToolbar by lazy {
        val back = BrowserMenuItemToolbar.TwoStateButton(
            primaryImageResource = R.drawable.ic_ios_back,
            primaryContentDescription = null.toString(),
            primaryImageTintResource = primaryTextColor(),
            isInPrimaryState = {
                selectedSession?.content?.canGoBack ?: true
            },
            secondaryImageTintResource = R.color.secondary_icon,
            disableInSecondaryState = true,
            longClickListener = { onItemTapped.invoke(ToolbarMenu.Item.Back(viewHistory = true)) }
        ) {
            onItemTapped.invoke(ToolbarMenu.Item.Back(viewHistory = false))
        }

        val forward = BrowserMenuItemToolbar.TwoStateButton(
            primaryImageResource = R.drawable.ic_ios_forward,
            primaryContentDescription = context.getString(R.string.forward),
            primaryImageTintResource = primaryTextColor(),
            isInPrimaryState = {
                selectedSession?.content?.canGoForward ?: true
            },
            secondaryImageTintResource = R.color.secondary_icon,
            disableInSecondaryState = true,
            longClickListener = { onItemTapped.invoke(ToolbarMenu.Item.Forward(viewHistory = true)) }
        ) {
            onItemTapped.invoke(ToolbarMenu.Item.Forward(viewHistory = false))
        }

        val refresh = BrowserMenuItemToolbar.TwoStateButton(
            primaryImageResource = mozilla.components.ui.icons.R.drawable.mozac_ic_arrow_clockwise_24,
            primaryContentDescription = context.getString(R.string.reload),
            primaryImageTintResource = primaryTextColor(),
            isInPrimaryState = {
                selectedSession?.content?.loading == false
            },
            secondaryImageResource = mozilla.components.ui.icons.R.drawable.mozac_ic_stop,
            secondaryContentDescription = context.getString(R.string.stop),
            secondaryImageTintResource = primaryTextColor(),
            disableInSecondaryState = false,
            longClickListener = { onItemTapped.invoke(ToolbarMenu.Item.Reload(bypassCache = true)) }
        ) {
            if (selectedSession?.content?.loading == true) {
                onItemTapped.invoke(ToolbarMenu.Item.Stop)
            } else {
                onItemTapped.invoke(ToolbarMenu.Item.Reload(bypassCache = false))
            }
        }

        val share = BrowserMenuItemToolbar.Button(
            imageResource = R.drawable.ios_share_24,
            contentDescription = context.getString(R.string.mozac_selection_context_menu_share),
            iconTintColorResource = primaryTextColor(),
            listener = {
                onItemTapped.invoke(ToolbarMenu.Item.Share)
            }
        )

        BrowserMenuItemToolbar(listOf(back, forward, share, refresh))
    }

    val externalAppItem = ThemedBrowserMenuImageText(
        context.getString(R.string.mozac_feature_contextmenu_open_link_in_external_app),
        R.drawable.ic_baseline_open_in_new
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.OpenInApp)
    }.apply {
        visible = {
            context.components.store.state.selectedTab?.let {
                context.components.appLinksUseCases.appLinkRedirect(it.content.url).hasExternalApp()
            } ?: false
        }
    }

    private fun canAddToHomescreen(): Boolean =
        selectedSession != null && isPinningSupported

    private val menuItems by lazy {
        val menuItems = listOfNotNull(
            settings,
            findInPage,
            BrowserMenuDivider(),
            historyItem,
            bookmarksItem,
            BrowserMenuDivider(),
            printItem,
            saveAsPdfItem,
            installWebApp,
            addToHomescreen.apply { visible = ::canAddToHomescreen },
            externalAppItem,
            desktopMode,
            BrowserMenuDivider(),
            newPrivateTabItem,
            newTabItem,
            BrowserMenuDivider(),
            menuToolbar
        )

        if (shouldReverseItems) {
            menuItems.reversed()
        } else {
            menuItems
        }
    }

    private val settings = ThemedBrowserMenuImageText(
        label = context.getString(R.string.settings),
        imageResource = R.drawable.ic_round_settings
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.Settings)
    }

    private val desktopMode = BrowserMenuImageCheckbox(
        imageResource = R.drawable.ic_desktop,
        label = context.getString(R.string.desktop_mode),
        initialState = {
            selectedSession?.content?.desktopMode ?: false
        }
    ) { checked ->
        onItemTapped.invoke(ToolbarMenu.Item.RequestDesktop(checked))
    }

    private val installWebApp = ThemedBrowserMenuImageText(
        label = context.getString(R.string.install_web_app),
        imageResource = R.drawable.ic_round_smartphone
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.InstallWebApp)
    }.apply {
        visible = {
            context.components.webAppUseCases.isInstallable()
        }
    }

    private val addToHomescreen = ThemedBrowserMenuImageText(
        label = context.getString(R.string.action_add_to_homescreen),
        imageResource = R.drawable.ic_round_smartphone
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.AddToHomeScreen)
    }

    private val findInPage = ThemedBrowserMenuImageText(
        label = context.getString(R.string.mozac_feature_findindpage_input),
        imageResource = R.drawable.mozac_ic_search_24
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.FindInPage)
    }

    val historyItem = ThemedBrowserMenuImageText(
        context.getString(R.string.action_history),
        R.drawable.ic_baseline_history
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.History)
    }

    val printItem = ThemedBrowserMenuImageText(
        context.getString(R.string.action_print),
        R.drawable.ic_baseline_print
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.Print)
    }

    val saveAsPdfItem = ThemedBrowserMenuImageText(
        context.getString(R.string.save_as_pdf),
        R.drawable.ic_baseline_pdf
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.PDF)
    }

    val newTabItem = ThemedBrowserMenuImageText(
        context.getString(R.string.mozac_browser_menu_new_tab),
        R.drawable.mozac_ic_tab_new_24
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.NewTab)
    }

    val newPrivateTabItem = ThemedBrowserMenuImageText(
        context.getString(R.string.mozac_browser_menu_new_private_tab),
        R.drawable.ic_incognito
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.NewPrivateTab)
    }

    val bookmarksItem = ThemedBrowserMenuImageText(
        context.getString(R.string.action_bookmarks),
        R.drawable.ic_baseline_bookmark
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.Bookmarks)
    }

    val securityItem = ThemedBrowserMenuImageText(
        label = "Security",
        imageResource = R.drawable.ic_baseline_lock
    ) {
        onItemTapped.invoke(ToolbarMenu.Item.Security)
    }

    @ColorRes
    @VisibleForTesting
    internal fun primaryTextColor(): Int {
        // Use Material 3 onSurface color for menu items
        // Resolve the color from theme attribute at runtime
        val typedValue = android.util.TypedValue()
        val resolved = context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnSurface,
            typedValue,
            true
        )

        return if (resolved && typedValue.resourceId != 0) {
            typedValue.resourceId
        } else {
            // Fallback to primary_icon if Material 3 color not available
            R.color.primary_icon
        }
    }
}
