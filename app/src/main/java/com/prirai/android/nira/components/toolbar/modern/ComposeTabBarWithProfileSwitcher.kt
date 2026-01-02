package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.browser.tabs.compose.TabBarCompose
import com.prirai.android.nira.browser.tabs.compose.TabOrderManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    init {
        clipToPadding = false
        clipChildren = false

        // Add compose view
        addView(composeView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))

        // Initialize managers
        groupManager = UnifiedTabGroupManager.getInstance(context)
        tabOrderManager = TabOrderManager.getInstance(context, groupManager!!)
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
            NiraTheme {
                TabBarWithProfileSwitcherContent()
            }
        }
    }

    @Composable
    private fun TabBarWithProfileSwitcherContent() {
        val profileManager = remember { ProfileManager.getInstance(context) }
        
        // Use mutableStateOf to make these reactive and update when changed
        var currentProfile by remember { mutableStateOf(profileManager.getActiveProfile()) }
        var isPrivateMode by remember { mutableStateOf(profileManager.isPrivateMode()) }

        // State for tabs and selected tab
        var tabs by remember { mutableStateOf(emptyList<TabSessionState>()) }
        var selectedTabId by remember { mutableStateOf<String?>(null) }

        // Collect store updates and also update profile state
        val store = context.components.store
        val lifecycleOwner = context as LifecycleOwner

        LaunchedEffect(Unit) {
            store.flowScoped(lifecycleOwner) { flow ->
                flow.collect { state ->
                    // Update profile state on each collection to catch profile switches
                    currentProfile = profileManager.getActiveProfile()
                    isPrivateMode = profileManager.isPrivateMode()
                    
                    val filteredTabs = state.tabs.filter { tab ->
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
                    tabs = filteredTabs
                    selectedTabId = state.selectedTabId
                }
            }
        }

        val orderManager = tabOrderManager ?: return

        Box(modifier = Modifier.fillMaxWidth()) {
            // Tab bar (background layer)
            TabBarCompose(
                tabs = tabs,
                orderManager = orderManager,
                selectedTabId = selectedTabId,
                onTabClick = { tabId ->
                    onTabSelected?.invoke(tabId)
                },
                onTabClose = { tabId ->
                    onTabClosed?.invoke(tabId)
                },
                onTabLongPress = { tab, isInGroup ->
                    // Show unified menu for tabs
                    // Note: We'll handle this at the parent level since we need View reference
                },
                onGroupLongPress = { groupId ->
                    // Show unified menu for groups
                    // Note: We'll handle this at the parent level since we need View reference
                }
            )
            
            // Floating profile icon (overlay layer - top-left corner)
            ProfileSwitcherFloatingIcon(
                currentProfile = currentProfile,
                isPrivateMode = isPrivateMode,
                onProfileClick = {
                    // Show profile switcher menu
                    showProfileSwitcherMenu()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }
    }

    @Composable
    private fun ProfileSwitcherFloatingIcon(
        currentProfile: BrowserProfile,
        isPrivateMode: Boolean,
        onProfileClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        // Floating circular profile icon with elevation
        Surface(
            modifier = modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileClick),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = if (isPrivateMode) "🕵️" else currentProfile.emoji,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    private fun showProfileSwitcherMenu() {
        // Simple profile switcher - could be enhanced with a proper dialog
        val profileManager = ProfileManager.getInstance(context)
        val profiles = profileManager.getAllProfiles()
        val currentProfile = profileManager.getActiveProfile()
        val isPrivate = profileManager.isPrivateMode()

        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Private mode
        options.add("🕵️ Private")
        actions.add {
            profileManager.setPrivateMode(true)
            onPrivateModeSelected?.invoke()
            setupComposeContent() // Refresh UI
        }

        // Profiles
        profiles.forEach { profile ->
            options.add("${profile.emoji} ${profile.name}")
            actions.add {
                profileManager.setActiveProfile(profile)
                profileManager.setPrivateMode(false)
                onProfileSelected?.invoke(profile)
                setupComposeContent() // Refresh UI
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Switch Profile")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which]()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun updateTabs(tabs: List<TabSessionState>, selectedId: String?) {
        // The compose content will automatically update via produceState
        setupComposeContent()
    }

    fun updateProfileIcon(profile: BrowserProfile) {
        setupComposeContent()
    }

    fun updateToPrivateMode() {
        setupComposeContent()
    }

    fun setProfileSwitcherVisible(visible: Boolean) {
        // For now, always visible. Could be enhanced to hide/show
    }
}