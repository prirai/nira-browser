package com.prirai.android.nira.components.toolbar

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.BrowserAnimator
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.history.HistoryActivity
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

            is ToolbarMenu.Item.Settings -> {
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

            is ToolbarMenu.Item.InstallWebApp -> {
                currentSession?.let { session ->
                    // Get current profile as default
                    val currentProfileId = activity.getCurrentProfileId()
                    
                    // Show Material 3 dialog with profile selection
                    val dialog = com.prirai.android.nira.webapp.WebAppInstallDialog(
                        context = activity,
                        lifecycleOwner = activity,
                        appName = session.content.title ?: session.content.url,
                        appUrl = session.content.url,
                        defaultProfileId = currentProfileId
                    ) { selectedProfileId ->
                        MainScope().launch {
                            // Load icon for installation
                            val icon = activity.components.icons.loadIcon(
                                mozilla.components.browser.icons.IconRequest(
                                    url = session.content.url
                                )
                            ).await()?.bitmap
                            
                            // Install with selected profile
                            val installed = com.prirai.android.nira.webapp.WebAppInstaller.installPwa(
                                activity,
                                session,
                                null,
                                icon,
                                selectedProfileId
                            )
                            
                            if (installed) {
                                android.widget.Toast.makeText(
                                    activity,
                                    activity.getString(R.string.app_installed),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                android.widget.Toast.makeText(
                                    activity,
                                    activity.getString(R.string.web_app_already_installed),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    
                    dialog.show()
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
                currentSession?.let { session ->
                    MainScope().launch {
                        // Get icon
                        val icon = activity.components.icons.loadIcon(
                            mozilla.components.browser.icons.IconRequest(
                                url = session.content.url
                            )
                        ).await()?.bitmap
                        
                        // Add regular shortcut using our custom installer
                        com.prirai.android.nira.webapp.WebAppInstaller.addToHomescreen(
                            activity,
                            session,
                            icon
                        )
                        
                        android.widget.Toast.makeText(
                            activity,
                            "Shortcut added",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
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
                val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(activity)
                val currentProfile = profileManager.getActiveProfile()
                val contextId = "profile_${currentProfile.id}"
                
                activity.components.tabsUseCases.addTab.invoke(
                    "about:homepage",
                    selectTab = true,
                    contextId = contextId
                )
            }

            is ToolbarMenu.Item.NewPrivateTab -> {
                activity.browsingModeManager.mode = com.prirai.android.nira.browser.BrowsingMode.Private
                
                activity.components.tabsUseCases.addTab.invoke(
                    "about:homepage",
                    selectTab = true,
                    private = true,
                    contextId = "private"
                )
            }

            is ToolbarMenu.Item.Security -> {
                activity.showSslDialog()
            }
        }
    }
}
