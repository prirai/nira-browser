package com.prirai.android.nira.browser.tabs.modern

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.BrowsingModeManager
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.state.TabSessionState

/**
 * Modern, simplified tab sheet - completely rebuilt from ground up.
 * 
 * Key improvements:
 * - Simple architecture (TabManager + TabSheet + TabBar)
 * - Clean Compose UI
 * - Better performance
 * - Intuitive UX
 * - Maintainable code (~300 lines vs 1810)
 */
class ModernTabSheet : DialogFragment() {
    
    private lateinit var tabManager: TabManager
    private lateinit var browsingModeManager: BrowsingModeManager
    private lateinit var unifiedGroupManager: UnifiedTabGroupManager
    private var isGridView by mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
        
        val activity = requireActivity() as BrowserActivity
        browsingModeManager = activity.browsingModeManager
        unifiedGroupManager = UnifiedTabGroupManager.getInstance(requireContext())
        
        tabManager = TabManager(
            context = requireContext(),
            store = activity.components.store,
            groupManager = unifiedGroupManager
        )
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(
                    colorScheme = if (com.prirai.android.nira.theme.ThemeManager.isDarkMode(requireContext())) {
                        darkColorScheme()
                    } else {
                        lightColorScheme()
                    }
                ) {
                    ModernTabSheetContent()
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Edge-to-edge support
            val isDark = com.prirai.android.nira.theme.ThemeManager.isDarkMode(requireContext())
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
            }
        }
        
        // Load current profile
        loadCurrentProfile()
    }
    
    private fun loadCurrentProfile() {
        val activity = requireActivity() as BrowserActivity
        val profileManager = ProfileManager.getInstance(requireContext())
        val currentProfile = profileManager.getActiveProfile()
        val isPrivate = browsingModeManager.mode == BrowsingMode.Private
        
        val store = activity.components.store
        val tabs = if (isPrivate) {
            store.state.privateTabs
        } else {
            store.state.tabs.filter { 
                val contextId = it.contextId ?: "profile_default"
                contextId == "profile_${currentProfile.id}"
            }
        }
        
        tabManager.loadProfile(currentProfile.id, isPrivate)
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ModernTabSheetContent() {
        var showTabMenu by remember { mutableStateOf<TabSessionState?>(null) }
        var showGroupMenu by remember { mutableStateOf<com.prirai.android.nira.browser.tabgroups.TabGroupData?>(null) }
        var showAddToGroupDialog by remember { mutableStateOf<TabSessionState?>(null) }
        
        val state by tabManager.state.collectAsState()
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Tabs") },
                    actions = {
                        // View mode toggle
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.List else Icons.Default.Home,
                                contentDescription = "Toggle view"
                            )
                        }
                        
                        // Search
                        IconButton(onClick = { showTabSearch() }) {
                            Icon(Icons.Default.Search, "Search tabs")
                        }
                        
                        // Close
                        IconButton(onClick = { dismiss() }) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { addNewTab() }
                ) {
                    Icon(Icons.Default.Add, "New tab")
                }
            },
            bottomBar = {
                ProfileBar()
            }
        ) { padding ->
            TabSheet(
                tabManager = tabManager,
                isGridView = isGridView,
                onTabClick = { tabId ->
                    tabManager.selectTab(tabId)
                    dismiss()
                },
                onTabClose = { tabId ->
                    tabManager.closeTab(tabId)
                },
                onTabLongPress = { tab ->
                    showTabMenu = tab
                },
                onGroupLongPress = { group ->
                    showGroupMenu = group
                },
                modifier = Modifier.padding(padding)
            )
        }
        
        // Tab context menu
        showTabMenu?.let { tab ->
            TabContextMenu(
                tab = tab,
                onDismiss = { showTabMenu = null },
                onCloseTab = {
                    tabManager.closeTab(tab.id)
                    showTabMenu = null
                },
                onAddToGroup = {
                    showAddToGroupDialog = tab
                    showTabMenu = null
                },
                onRemoveFromGroup = {
                    tabManager.removeTabFromGroup(tab.id)
                    showTabMenu = null
                }
            )
        }
        
        // Group options menu
        showGroupMenu?.let { group ->
            GroupOptionsDialog(
                group = group,
                onDismiss = { showGroupMenu = null },
                onRename = { newName ->
                    tabManager.renameGroup(group.id, newName)
                    showGroupMenu = null
                },
                onRecolor = { newColor ->
                    tabManager.changeGroupColor(group.id, newColor)
                    showGroupMenu = null
                },
                onUngroup = {
                    tabManager.ungroupTabs(group.id)
                    showGroupMenu = null
                },
                onCloseAll = {
                    tabManager.closeGroup(group.id)
                    showGroupMenu = null
                }
            )
        }
        
        // Add to group dialog
        showAddToGroupDialog?.let { tab ->
            val profileManager = ProfileManager.getInstance(requireContext())
            val currentProfile = profileManager.getActiveProfile()
            val isPrivate = browsingModeManager.mode == BrowsingMode.Private
            val contextId = if (isPrivate) "private" else "profile_${currentProfile.id}"
            
            AddToGroupDialog(
                tab = tab,
                existingGroups = state.groups,
                onDismiss = { showAddToGroupDialog = null },
                onAddToExistingGroup = { groupId ->
                    tabManager.addTabToGroup(tab.id, groupId)
                    showAddToGroupDialog = null
                },
                onCreateNewGroup = { name, color ->
                    tabManager.createGroup(name, color, listOf(tab.id), contextId)
                    showAddToGroupDialog = null
                }
            )
        }
    }
    
    @Composable
    private fun ProfileBar() {
        val activity = requireActivity() as BrowserActivity
        val profileManager = ProfileManager.getInstance(requireContext())
        val profiles = profileManager.getAllProfiles()
        val currentProfile = profileManager.getActiveProfile()
        val isPrivate = browsingModeManager.mode == BrowsingMode.Private
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp
        ) {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEach { profile ->
                    Button(
                        onClick = {
                            if (!isPrivate && profile.id != currentProfile.id) {
                                profileManager.setActiveProfile(profile)
                                tabManager.loadProfile(profile.id, false)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isPrivate && profile.id == currentProfile.id) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Text("${profile.emoji} ${profile.name}")
                    }
                }
                
                // Private button
                Button(
                    onClick = {
                        if (!isPrivate) {
                            browsingModeManager.mode = BrowsingMode.Private
                            tabManager.loadProfile("private", true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPrivate) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text("🕵️ Private")
                }
                
                // Add profile button
                IconButton(onClick = { showProfileCreateDialog() }) {
                    Icon(Icons.Default.Add, "Add profile")
                }
            }
        }
    }
    
    @Composable
    private fun TabContextMenu(
        tab: TabSessionState,
        onDismiss: () -> Unit,
        onCloseTab: () -> Unit,
        onAddToGroup: () -> Unit,
        onRemoveFromGroup: () -> Unit
    ) {
        val state by tabManager.state.collectAsState()
        val isInGroup = state.groups.any { tab.id in it.tabIds }
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Tab Actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onCloseTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Close Tab")
                    }
                    
                    if (isInGroup) {
                        TextButton(
                            onClick = onRemoveFromGroup,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Remove from Group")
                        }
                    } else {
                        TextButton(
                            onClick = onAddToGroup,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add to Group")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
    
    private fun addNewTab() {
        val activity = requireActivity() as BrowserActivity
        val profileManager = ProfileManager.getInstance(requireContext())
        val currentProfile = profileManager.getActiveProfile()
        val isPrivate = browsingModeManager.mode == BrowsingMode.Private
        
        val contextId = if (isPrivate) "private" else "profile_${currentProfile.id}"
        
        activity.components.tabsUseCases.addTab(
            url = "about:blank",
            private = isPrivate,
            contextId = contextId,
            selectTab = true
        )
        
        dismiss()
    }
    
    private fun showTabSearch() {
        // TODO: Implement tab search
    }
    
    private fun showProfileCreateDialog() {
        // TODO: Implement profile creation
    }
}
