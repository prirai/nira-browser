package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.browser.tabs.compose.TabBarComposeWithMenus
import com.prirai.android.nira.browser.tabs.compose.TabViewModel
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
    private var tabViewModel: TabViewModel? = null

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
        tabViewModel = TabViewModel(context, groupManager!!)
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
        
        // Derive reactive state from browserState
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
        
        // Update viewModel with current tabs
        LaunchedEffect(tabs, selectedTabId, currentProfile, isPrivateMode) {
            val profileId = if (isPrivateMode) "private" else currentProfile.id
            viewModel.loadTabsForProfile(profileId, tabs, selectedTabId)
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Tab bar (background layer)
            TabBarComposeWithMenus(
                tabs = tabs,
                orderManager = orderManager,
                viewModel = viewModel,
                selectedTabId = selectedTabId,
                onTabClick = { tabId ->
                    onTabSelected?.invoke(tabId)
                },
                onTabClose = { tabId ->
                    onTabClosed?.invoke(tabId)
                }
            )
            
            // Floating profile icon (overlay layer - top-left corner)
            ProfileSwitcherFloatingIcon(
                currentProfile = currentProfile,
                isPrivateMode = isPrivateMode,
                onProfileLongClick = {
                    // Show profile switcher menu on long press
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
        onProfileLongClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val scope = rememberCoroutineScope()
        var isExpanded by remember { mutableStateOf(false) }
        
        // Auto-collapse after 3 seconds when expanded
        LaunchedEffect(isExpanded) {
            if (isExpanded) {
                kotlinx.coroutines.delay(3000)
                isExpanded = false
            }
        }
        
        // Floating profile icon with expand/collapse animation
        androidx.compose.animation.AnimatedVisibility(
            visible = isExpanded,
            modifier = modifier,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
        ) {
            // Expanded: Show icon + name
            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { onProfileLongClick() }
                        )
                    },
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 8.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isPrivateMode) "🕵️" else currentProfile.emoji,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (isPrivateMode) "Private" else currentProfile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        // Collapsed icon (always visible)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isExpanded,
            modifier = modifier
        ) {
            // Collapsed: Show icon only - tap to expand, long-press for menu
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { isExpanded = true },
                            onLongPress = { onProfileLongClick() }
                        )
                    },
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