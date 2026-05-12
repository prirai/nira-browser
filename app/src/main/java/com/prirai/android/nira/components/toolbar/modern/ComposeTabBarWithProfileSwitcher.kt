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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


/**
 * Compose-based tab bar with profile switcher pill.
 * Replaces the RecyclerView-based TabGroupWithProfileSwitcher.
 */
class ComposeTabBarWithProfileSwitcher @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private var nextInstanceId = 0
        private val activeInstances = mutableListOf<java.lang.ref.WeakReference<ComposeTabBarWithProfileSwitcher>>()
        fun liveCount(): Int = activeInstances.count { it.get() != null }
    }

    private val instanceId = nextInstanceId++

    init {
        android.util.Log.d("TabBarDebug", "CTBPS #$instanceId created (live=${liveCount()})")
        android.util.Log.d("TabBarDebug", "  stack=" + android.util.Log.getStackTraceString(Exception()))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Hide all other instances that are still attached
        activeInstances.removeAll { it.get() == null }
        activeInstances.forEach { ref ->
            val other = ref.get()
            if (other != null && other !== this) {
                other.visibility = android.view.View.GONE
                android.util.Log.w("TabBarDebug", "Hiding stale CTBPS #${other.instanceId}, keeping #${this.instanceId}")
            }
        }
        activeInstances.add(java.lang.ref.WeakReference(this))
        android.util.Log.d("TabBarDebug", "CTBPS #$instanceId attached, parent=$parent, live=${liveCount()}")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        android.util.Log.d("TabBarDebug", "CTBPS #$instanceId detached")
        activeInstances.removeAll { it.get() == null || it.get() === this }
    }
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
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _profileIconTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)

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
            viewModel.onTabRestore = { tab, position, groupId ->
                // Restore tab at original position
                val components = context.components
                val newTabId = components.tabsUseCases.addTab(
                    url = tab.content.url,
                    private = tab.content.private,
                    contextId = tab.contextId,
                    selectTab = false
                )
                if (groupId != null) {
                    coroutineScope.launch {
                        groupManager!!.addTabToGroup(newTabId, groupId)
                    }
                }
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

        android.util.Log.d("TabBarDebug", "setup #$instanceId: composeView parent=${composeView.parent}")
        android.util.Log.d("TabBarDebug", "setup #$instanceId: this.parent=${this.parent} this.isAttachedToWindow=${this.isAttachedToWindow}")
        setupComposeContent()
        post {
            android.util.Log.d("TabBarDebug", "after setup #$instanceId: childCount=$childCount, this.parent=${this.parent}")
        }
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

        // Observe profile icon trigger so that updateProfileIcon() forces re-evaluation
        val profileTrigger by _profileIconTrigger.collectAsState()

        // Get current profile and mode - recalculate when browserState or trigger changes
        val currentProfile = remember(browserState.value, profileTrigger) { profileManager.getActiveProfile() }
        val isPrivateMode = remember(browserState.value, profileTrigger) { profileManager.isPrivateMode() }

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
        val manager = groupManager ?: return

        // Collect groups directly from the manager (updated synchronously on IO)
        // rather than from the ViewModel (updated via 150ms-debounced observer).
        // This ensures the UI sees new groups immediately, preventing a stale
        // intermediate order that renders duplicate tab items.
        val rawGroups by manager.groupsState.collectAsState()
        android.util.Log.d("TabBarDebug", "rawGroups changed: size=${rawGroups.size} ids=${rawGroups.map { it.id.substring(0..7) }}")

        // Determine contextId for the active profile
        val profileId = if (isPrivateMode) "private" else currentProfile.id
        val profileContextId = when {
            isPrivateMode -> "private"
            profileId == "default" -> "profile_default"
            else -> "profile_$profileId"
        }

        // Filter groups to the current profile (mirrors refreshGroupsForProfile logic)
        val profileGroups = remember(rawGroups, profileContextId, tabs) {
            val filtered = rawGroups.filter { group ->
                when {
                    profileId == "default" -> group.contextId == "profile_default" || group.contextId == null
                    else -> group.contextId == profileContextId
                }
            }.filter { group ->
                group.tabIds.any { tabId -> tabs.any { it.id == tabId } }
            }
            android.util.Log.d("TabBarDebug", "profileGroups filtered: ${filtered.size} groups")
            filtered
        }

        // Update orderManager and viewModel with current tabs
        LaunchedEffect(tabs, selectedTabId, currentProfile, isPrivateMode, profileGroups) {
            android.util.Log.d("TabBarDebug", "LaunchedEffect fired: profileId=$profileId tabs=${tabs.size} profileGroups=${profileGroups.size}")
            // Rebuild order to include any new tabs/groups
            orderManager.rebuildOrderForProfile(profileId, tabs)
            android.util.Log.d("TabBarDebug", "rebuildOrderForProfile done, currentOrder=${orderManager.currentOrder.value?.primaryOrder?.size}")

            viewModel.loadTabsForProfile(profileId, tabs, selectedTabId)
            android.util.Log.d("TabBarDebug", "loadTabsForProfile done, viewModel groups=${viewModel.groups.value.size}")
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

        // Observe tab sheet dismissal timestamp; passed directly to TabBarCompose
        // so it can scroll to the active tab after each dismissal event.
        val autoScrollTrigger by com.prirai.android.nira.browser.tabs.compose.TabSheetStateManager.dismissedTimestamp.collectAsState()

        // Tab bar only - profile switching available via menu
        // Use key() to properly recreate composition on profile switch
        key(profileKey) {
            TabBarCompose(
                tabs = tabs,
                viewModel = viewModel,
                orderManager = orderManager,
                selectedTabId = selectedTabId,
                groups = profileGroups,
                onTabClick = { tabId: String ->
                    onTabSelected?.invoke(tabId)
                },
                onTabClose = { tabId: String ->
                    // Use ViewModel's closeTab with undo support
                    viewModel.closeTab(tabId, showUndo = true)
                    // Still invoke callback for legacy support
                    onTabClosed?.invoke(tabId)
                },
                modifier = Modifier.fillMaxWidth(),
                autoScrollTrigger = autoScrollTrigger
            )
        }
    }


    fun updateTabs(tabs: List<TabSessionState>, selectedId: String?) {
        // No-op: the composable already observes the store reactively via produceState.
        // Calling setupComposeContent() here restarts the entire composition on every
        // store emission (20+ times during page load), which can cause duplicate
        // composition instances to accumulate on the ComposeView.
    }

    fun updateProfileIcon(profile: BrowserProfile) {
        _profileIconTrigger.value++
    }

}
