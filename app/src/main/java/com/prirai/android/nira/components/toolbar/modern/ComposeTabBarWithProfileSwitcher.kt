package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.browser.tabs.compose.TabBarCompose
import com.prirai.android.nira.browser.tabs.compose.TabViewModel
import com.prirai.android.nira.browser.tabs.compose.TabOrderManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.lib.state.ext.flowScoped


/**
 * Compose-based tab bar with profile switcher pill.
 * Replaces the RecyclerView-based TabGroupWithProfileSwitcher.
 */
class ComposeTabBarWithProfileSwitcher @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
    }

    private var onTabSelected: ((String) -> Unit)? = null
    private var onTabClosed: ((String) -> Unit)? = null
    private var onProfileSelected: ((BrowserProfile) -> Unit)? = null
    private var onPrivateModeSelected: (() -> Unit)? = null

    private var tabOrderManager: TabOrderManager? = null
    private var groupManager: UnifiedTabGroupManager? = null
    private var tabViewModel: TabViewModel? = null

    init {
        clipToPadding = false
        clipChildren = false

        // Add compose view
        addView(
            composeView, LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        // Initialize managers
        groupManager = UnifiedTabGroupManager.getInstance(context)
        tabOrderManager = TabOrderManager.getInstance(context, groupManager!!)
        tabViewModel = TabViewModel(context, groupManager!!).also { viewModel ->
            // Set up callbacks for tab operations
            viewModel.onTabRemove = { tabId ->
                // Immediately remove tab from store
                context.components.tabsUseCases.removeTab(tabId)
            }
            viewModel.onTabRestore = { tab, position ->
                // Restore tab at original position
                val components = context.components
                components.tabsUseCases.addTab(
                    url = tab.content.url,
                    private = tab.content.private,
                    contextId = tab.contextId,
                    selectTab = false
                )
            }
        }
    }

    fun setup(
        onTabSelected: (String) -> Unit,
        onTabClosed: (String) -> Unit,
        onProfileSelected: (BrowserProfile) -> Unit,
        onPrivateModeSelected: (() -> Unit)? = null
    ) {
        this.onTabSelected = onTabSelected
        this.onTabClosed = onTabClosed
        this.onProfileSelected = onProfileSelected
        this.onPrivateModeSelected = onPrivateModeSelected

        setupComposeContent()
    }

    private fun setupComposeContent() {
        composeView.setContent {
            // Read AMOLED preference
            val prefs = com.prirai.android.nira.preferences.UserPreferences(context)
            val isAmoledActive = prefs.amoledMode
            
            NiraTheme(amoledMode = isAmoledActive) {
                TabBarWithProfileSwitcherContent()
            }
        }
    }

    @Composable
    private fun TabBarWithProfileSwitcherContent() {
        val profileManager = remember { ProfileManager.getInstance(context) }

        // State for tabs and selected tab - observe store state changes
        val store = context.components.store
        val lifecycleOwner = context as LifecycleOwner

        // Use produceState to observe store state changes reactively
        val browserState = produceState(initialValue = store.state, store) {
            store.flowScoped(lifecycleOwner) { flow ->
                flow.collect { state ->
                    value = state
                }
            }
        }

        // Get current profile and mode - recalculate when browserState changes
        val currentProfile = remember(browserState.value) { profileManager.getActiveProfile() }
        val isPrivateMode = remember(browserState.value) { profileManager.isPrivateMode() }

        // Filter tabs based on current profile - this will update when browserState changes
        val tabs = remember(browserState.value.tabs, currentProfile, isPrivateMode) {
            browserState.value.tabs.filter { tab ->
                val tabIsPrivate = tab.content.private
                if (tabIsPrivate != isPrivateMode) {
                    false
                } else if (isPrivateMode) {
                    tab.contextId == "private"
                } else {
                    val expectedContextId = "profile_${currentProfile.id}"
                    (tab.contextId == expectedContextId) || (tab.contextId == null)
                }
            }
        }

        val selectedTabId = browserState.value.selectedTabId

        val orderManager = tabOrderManager ?: return
        val viewModel = tabViewModel ?: return

        // Update orderManager and viewModel with current tabs
        LaunchedEffect(tabs, selectedTabId, currentProfile, isPrivateMode) {
            val profileId = if (isPrivateMode) "private" else currentProfile.id

            // Rebuild order to include any new tabs
            orderManager.rebuildOrderForProfile(profileId, tabs)

            viewModel.loadTabsForProfile(profileId, tabs, selectedTabId)
        }

        // Set up global snackbar manager scope
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            com.prirai.android.nira.browser.tabs.compose.GlobalSnackbarManager.getInstance().coroutineScope = scope
        }

        // Observe duplicate tab events
        LaunchedEffect(viewModel) {
            viewModel.duplicateTabEvent.collect { request ->
                android.util.Log.d(
                    "TabBar",
                    "Received duplicate tab request: ${request.url}, group: ${request.groupId}"
                )
                // Create the new tab
                val components = context.components
                val newTabId = components.tabsUseCases.addTab(
                    url = request.url,
                    private = isPrivateMode,
                    contextId = if (isPrivateMode) "private" else "profile_${currentProfile.id}"
                )
                android.util.Log.d("TabBar", "Created new tab: $newTabId")

                // If original tab was in a group, add the new tab to the same group
                if (request.groupId != null) {
                    kotlinx.coroutines.delay(100) // Small delay to ensure tab is in store
                    val groupManager =
                        com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager.getInstance(context)
                    groupManager.addTabToGroup(request.groupId, newTabId)
                    android.util.Log.d("TabBar", "Added tab to group: ${request.groupId}")
                }
            }
        }

        // Create a stable key for the tab bar based on profile
        val profileKey = remember(currentProfile, isPrivateMode) {
            if (isPrivateMode) "private" else "profile_${currentProfile.id}"
        }

        // Tab bar only - profile switching available via menu
        // Use key() to properly recreate composition on profile switch
        key(profileKey) {
            TabBarCompose(
                tabs = tabs,
                viewModel = viewModel,
                orderManager = orderManager,
                selectedTabId = selectedTabId,
                onTabClick = { tabId: String ->
                    onTabSelected?.invoke(tabId)
                },
                onTabClose = { tabId: String ->
                    // Use ViewModel's closeTab with undo support
                    viewModel.closeTab(tabId, showUndo = true)
                    // Still invoke callback for legacy support
                    onTabClosed?.invoke(tabId)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }


    fun updateTabs(tabs: List<TabSessionState>, selectedId: String?) {
        // The compose content will automatically update via produceState
        setupComposeContent()
    }

    fun updateProfileIcon(profile: BrowserProfile) {
        setupComposeContent()
    }

}
