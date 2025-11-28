package com.prirai.android.nira.components.toolbar

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.BrowserAnimator
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.history.HistoryActivity
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.activity.SettingsActivity
import com.prirai.android.nira.ssl.showSslDialog
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession.LoadUrlFlags


interface BrowserToolbarMenuController {
    fun handleToolbarItemInteraction(item: ToolbarMenu.Item)
}

class DefaultBrowserToolbarMenuController(
    private val store: BrowserStore,
    private val activity: BrowserActivity,
    private val navController: NavController,
    private val findInPageLauncher: () -> Unit,
    private val browserAnimator: BrowserAnimator,
    private val customTabSessionId: String?
) : BrowserToolbarMenuController {

    private val currentSession
        get() = store.state.findCustomTabOrSelectedTab(customTabSessionId)

    @Suppress("ComplexMethod", "LongMethod")
    override fun handleToolbarItemInteraction(item: ToolbarMenu.Item) {
        val sessionUseCases = activity.components.sessionUseCases

        when (item) {
            is ToolbarMenu.Item.Back -> {
                currentSession?.let {
                    sessionUseCases.goBack.invoke(it.id)
                }
            }

            is ToolbarMenu.Item.Forward -> {
                currentSession?.let {
                    sessionUseCases.goForward.invoke(it.id)
                }
            }

            is ToolbarMenu.Item.Reload -> {
                val flags = if (item.bypassCache) {
                    LoadUrlFlags.select(LoadUrlFlags.BYPASS_CACHE)
                } else {
                    LoadUrlFlags.none()
                }

                currentSession?.let {
                    sessionUseCases.reload.invoke(it.id, flags = flags)
                }
            }

            is ToolbarMenu.Item.Stop -> currentSession?.let {
                sessionUseCases.stopLoading.invoke(
                    it.id
                )
            }

            is ToolbarMenu.Item.Settings -> browserAnimator.captureEngineViewAndDrawStatically {
                val settings = Intent(activity, SettingsActivity::class.java)
                settings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                activity.startActivity(settings)
            }

            is ToolbarMenu.Item.RequestDesktop -> {
                currentSession?.let {
                    sessionUseCases.requestDesktopSite.invoke(
                        item.isChecked,
                        it.id
                    )
                }
            }

            is ToolbarMenu.Item.ForceDarkMode -> {
                currentSession?.let { session ->
                    val userPrefs = activity.getSharedPreferences("scw_preferences", android.content.Context.MODE_PRIVATE)
                    val siteDarkMode = activity.getSharedPreferences("site_dark_mode_override", android.content.Context.MODE_PRIVATE)
                    val currentUrl = session.content.url
                    
                    val globalWebTheme = userPrefs.getInt("web_theme_choice", com.prirai.android.nira.settings.ThemeChoice.SYSTEM.ordinal)
                    
                    if (item.isChecked) {
                        // User wants dark mode for this site
                        if (globalWebTheme == com.prirai.android.nira.settings.ThemeChoice.LIGHT.ordinal) {
                            // Global is light, save site override as "dark"
                            siteDarkMode.edit().putString(currentUrl, "dark").apply()
                        } else {
                            // Global is dark or system, remove any override
                            siteDarkMode.edit().remove(currentUrl).apply()
                        }
                    } else {
                        // User wants light mode for this site
                        if (globalWebTheme == com.prirai.android.nira.settings.ThemeChoice.DARK.ordinal) {
                            // Global is dark, save site override as "light"
                            siteDarkMode.edit().putString(currentUrl, "light").apply()
                        } else {
                            // Global is light or system, remove any override
                            siteDarkMode.edit().remove(currentUrl).apply()
                        }
                    }
                    
                    // Update engine settings with the new color scheme
                    val newColorScheme = activity.components.darkEnabled(currentUrl)
                    activity.components.engine.settings.preferredColorScheme = newColorScheme
                    
                    // Reload to apply the changes
                    sessionUseCases.reload.invoke(
                        session.id,
                        LoadUrlFlags.select(LoadUrlFlags.BYPASS_CACHE)
                    )
                }
            }

            is ToolbarMenu.Item.InstallWebApp -> {
                MainScope().launch {
                    with(activity.components.webAppUseCases) {
                        addToHomescreen()
                        android.widget.Toast.makeText(
                            activity,
                            activity.getString(R.string.app_installed),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            is ToolbarMenu.Item.Print -> {
                store.state.selectedTab?.let {
                    store.dispatch(EngineAction.PrintContentAction(it.id))
                }
            }

            is ToolbarMenu.Item.PDF -> {
                activity.components.sessionUseCases.saveToPdf.invoke()
            }

            is ToolbarMenu.Item.AddToHomeScreen -> {
                MainScope().launch {
                    with(activity.components.webAppUseCases) {
                        addToHomescreen()
                    }
                }
            }

            is ToolbarMenu.Item.Share -> {
                MainScope().launch {
                    activity.components.store.state.selectedTab?.content?.let {
                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.type = "text/plain"
                        if (it.title != "") {
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT, it.title)
                        }
                        shareIntent.putExtra(Intent.EXTRA_TEXT, it.url)
                        ContextCompat.startActivity(
                            activity,
                            Intent.createChooser(
                                shareIntent,
                                activity.resources.getString(R.string.mozac_selection_context_menu_share)
                            ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            null
                        )
                    }
                }
            }

            is ToolbarMenu.Item.FindInPage -> {
                findInPageLauncher()
            }

            is ToolbarMenu.Item.OpenInApp -> {
                val appLinksUseCases = activity.components.appLinksUseCases
                val getRedirect = appLinksUseCases.appLinkRedirect

                currentSession?.let {
                    val redirect = getRedirect.invoke(it.content.url)
                    redirect.appIntent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    appLinksUseCases.openAppLink.invoke(redirect.appIntent)
                }
            }

            is ToolbarMenu.Item.Bookmarks -> {
                val bookmarksBottomSheet =
                    com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment.newInstance()
                bookmarksBottomSheet.show(activity.supportFragmentManager, "BookmarksBottomSheet")
            }

            is ToolbarMenu.Item.History -> browserAnimator.captureEngineViewAndDrawStatically {
                val settings = Intent(activity, HistoryActivity::class.java)
                settings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                activity.startActivity(settings)
            }

            is ToolbarMenu.Item.NewTab -> {
                activity.components.tabsUseCases.addTab.invoke(
                    "about:homepage",
                    selectTab = true
                )
            }

            is ToolbarMenu.Item.NewPrivateTab -> {
                // Switch to private mode first
                activity.browsingModeManager.mode = com.prirai.android.nira.browser.BrowsingMode.Private
                
                activity.components.tabsUseCases.addTab.invoke(
                    "about:homepage",
                    selectTab = true, private = true
                )
            }

            is ToolbarMenu.Item.Security -> {
                activity.showSslDialog()
            }
        }
    }
}
