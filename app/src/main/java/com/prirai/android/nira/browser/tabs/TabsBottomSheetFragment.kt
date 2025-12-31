package com.prirai.android.nira.browser.tabs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import android.view.animation.PathInterpolator
import android.transition.TransitionManager
import android.transition.ChangeBounds
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.BrowsingModeManager
import com.prirai.android.nira.browser.tabs.compose.TabViewModel
import com.prirai.android.nira.browser.tabs.compose.TabSheetListView
import com.prirai.android.nira.browser.tabs.compose.TabSheetGridView
import com.prirai.android.nira.databinding.FragmentTabsBottomSheetBinding
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.browser.state.state.TabSessionState

/**
 * Modern Compose-based tab switcher displayed as a fullscreen bottom sheet.
 * 
 * Features:
 * - List and grid view modes
 * - Tab groups with drag-and-drop
 * - Multi-profile support
 * - Private browsing mode
 * - Tab search
 * - Context menus for tab operations
 * 
 * Architecture:
 * - UI: Jetpack Compose (TabSheetListView / TabSheetGridView)
 * - State: TabViewModel with StateFlow
 * - Groups: UnifiedTabGroupManager
 * - Tabs: Mozilla Components Store
 * 
 * @see docs/TAB_ARCHITECTURE.md for detailed architecture documentation
 */
class TabsBottomSheetFragment : DialogFragment() {

    private var _binding: FragmentTabsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var browsingModeManager: BrowsingModeManager


    // Modern Compose tab system (ACTIVE)
    private var useComposeTabSystem = true // Set to false to use legacy RecyclerView (not recommended)
    private var composeOrderManager: com.prirai.android.nira.browser.tabs.compose.TabOrderManager? = null
    private var tabViewModel: com.prirai.android.nira.browser.tabs.compose.TabViewModel? = null
    
    // Compose state management
    private val profileStateTrigger = mutableStateOf(0) // Triggers recomposition on profile/mode changes
    private val showMenuState = mutableStateOf(false) // Controls context menu visibility
    private val menuTabState = mutableStateOf<TabSessionState?>(null) // Currently selected tab for menu


    
    private var isInitializing = true
    private var isGridView = false
    private val viewPrefs by lazy { 
        requireContext().getSharedPreferences("tabs_view_prefs", android.content.Context.MODE_PRIVATE) 
    }

    companion object {
        const val TAG = "TabsBottomSheet"
        fun newInstance() = TabsBottomSheetFragment()
        
        private val COLORS = arrayOf("blue", "red", "green", "orange", "purple", "pink", "teal", "yellow")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = (activity as BrowserActivity)
        browsingModeManager = activity.browsingModeManager


        // Switch to the correct profile based on the selected tab's context
        val store = requireContext().components.store
        val selectedTab = store.state.tabs.find { it.id == store.state.selectedTabId }
        if (selectedTab != null) {
            val isPrivate = selectedTab.content.private
            val tabContextId = selectedTab.contextId
            
            // Switch to correct browsing mode
            if (isPrivate && browsingModeManager.mode != BrowsingMode.Private) {
                browsingModeManager.mode = BrowsingMode.Private
            } else if (!isPrivate && browsingModeManager.mode != BrowsingMode.Normal) {
                browsingModeManager.mode = BrowsingMode.Normal
            }
            
            // Switch to correct profile for normal mode
            if (!isPrivate) {
                val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                when {
                    tabContextId == null || tabContextId == "profile_default" -> {
                        val defaultProfile = profileManager.getAllProfiles().find { it.id == "default" }
                        if (defaultProfile != null && browsingModeManager.currentProfile.id != "default") {
                            profileManager.setActiveProfile(defaultProfile)
                            browsingModeManager.currentProfile = defaultProfile
                        }
                    }
                    tabContextId.startsWith("profile_") -> {
                        val profileId = tabContextId.removePrefix("profile_")
                        val targetProfile = profileManager.getAllProfiles().find { it.id == profileId }
                        if (targetProfile != null && browsingModeManager.currentProfile.id != profileId) {
                            profileManager.setActiveProfile(targetProfile)
                            browsingModeManager.currentProfile = targetProfile
                        }
                    }
                }
            }
        }

        setupUI()
        
        if (useComposeTabSystem) {
            // Initialize Compose tab system
            composeOrderManager = com.prirai.android.nira.browser.tabs.compose.TabOrderManager.getInstance(requireContext())
            setupComposeTabViews()
        }
        
        setupMergedProfileButtons()

        
        isInitializing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use fullscreen dialog style
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
    }

    override fun onStart() {
        super.onStart()
        
        // Make dialog fullscreen
        dialog?.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Preserve status bar appearance (don't change icons to black)
            val isDark = com.prirai.android.nira.theme.ThemeManager.isDarkMode(requireContext())
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
            }
            
            // Apply window insets for edge-to-edge
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                
                // Apply padding to profile button container to avoid navigation bar overlap
                binding.profileButtonContainer.setPadding(
                    binding.profileButtonContainer.paddingLeft,
                    binding.profileButtonContainer.paddingTop,
                    binding.profileButtonContainer.paddingRight,
                    systemBars.bottom + 16 // Add extra padding for navigation bar
                )
                
                insets
            }
        }
    }

    private fun setupUI() {
        // Apply Material 3 theming
        com.prirai.android.nira.theme.ThemeManager.isDarkMode(requireContext())
        val isAmoled = com.prirai.android.nira.theme.ThemeManager.isAmoledActive(requireContext())
        
        if (isAmoled) {
            dialog?.window?.decorView?.setBackgroundColor(Color.BLACK)
        }
        
        binding.newTabFab.setOnClickListener {
            addNewTab()
            // Post dismiss to avoid touch event recycling crash
            view?.post { dismiss() }
        }
        
        binding.searchTabFab.setOnClickListener {
            showTabSearch()
        }
        
        // Setup view mode switcher
        setupViewModeSwitcher()
    }
    
    private fun setupViewModeSwitcher() {
        // Restore saved view preference
        isGridView = viewPrefs.getBoolean("is_grid_view", false)
        
        if (isGridView) {
            binding.viewModeSwitcher.check(binding.gridViewButton.id)
        } else {
            binding.viewModeSwitcher.check(binding.listViewButton.id)
        }
        
        binding.viewModeSwitcher.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            
            when (checkedId) {
                binding.listViewButton.id -> {
                    if (isGridView) {
                        isGridView = false
                        viewPrefs.edit().putBoolean("is_grid_view", false).apply()
                        handleSwitchToListView()
                    }
                }
                binding.gridViewButton.id -> {
                    if (!isGridView) {
                        isGridView = true
                        viewPrefs.edit().putBoolean("is_grid_view", true).apply()
                        handleSwitchToGridView()
                    }
                }
            }
        }
    }
    
    private fun handleSwitchToListView() {
        if (useComposeTabSystem) {
            // Recreate compose view with list view
            setupComposeTabViews()
        } else {
            binding.tabsRecyclerView.visibility = View.VISIBLE
            binding.tabsGridRecyclerView.visibility = View.GONE
        }
    }
    
    private fun handleSwitchToGridView() {
        if (useComposeTabSystem) {
            // Recreate compose view with grid view
            setupComposeTabViews()
        } else {
            binding.tabsRecyclerView.visibility = View.GONE
            binding.tabsGridRecyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun showTabSearch() {
        val searchFragment = TabSearchFragment.newInstance()
        searchFragment.show(parentFragmentManager, TabSearchFragment.TAG)
    }




    
    private fun setupMergedProfileButtons() {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val profiles = profileManager.getAllProfiles()
        val isPrivateMode = browsingModeManager.mode.isPrivate
        val currentProfile = browsingModeManager.currentProfile

        binding.profileButtonGroup.removeAllViews()

        val buttons = mutableListOf<MergedProfileButton>()
        
        // Add profile buttons
        profiles.forEachIndexed { index, profile ->
            val button = MergedProfileButton(requireContext()).apply {
                id = View.generateViewId()
                tag = profile.id
                setText(profile.emoji, profile.name)
                setActive(!isPrivateMode && profile.id == currentProfile.id, animated = false)
                
                val position = when {
                    profiles.size == 1 -> MergedProfileButton.Position.ONLY
                    index == 0 -> MergedProfileButton.Position.FIRST
                    index == profiles.size - 1 -> MergedProfileButton.Position.MIDDLE
                    else -> MergedProfileButton.Position.MIDDLE
                }
                setPosition(position)
                
                val heightPx = (48 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    heightPx
                ).apply {
                    marginEnd = 1 // 1dp for merged appearance
                }
                
                setOnClickListener {
                    switchToProfile(profile.id, false)
                }
                
                setOnLongClickListener {
                    showProfileEditDialog(profile)
                    true
                }
            }
            buttons.add(button)
            binding.profileButtonGroup.addView(button)
        }
        
        // Add private button
        val privateButton = MergedProfileButton(requireContext()).apply {
            id = View.generateViewId()
            tag = "private"
            setText("🕵️", "Private")
            setActive(isPrivateMode, animated = false)
            setPosition(MergedProfileButton.Position.MIDDLE)
            
            val heightPx = (48 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightPx
            ).apply {
                marginEnd = 1
            }
            
            setOnClickListener {
                switchToProfile("private", true)
            }
        }
        binding.profileButtonGroup.addView(privateButton)
        
        // Add plus button
        val plusButton = MergedProfileButton(requireContext()).apply {
            id = View.generateViewId()
            setText("+", "")
            setActive(false, animated = false)
            setPosition(MergedProfileButton.Position.LAST)
            
            val heightPx = (48 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightPx
            )
            
            setOnClickListener {
                showProfileCreateDialog()
            }
        }
        binding.profileButtonGroup.addView(plusButton)
    }

    private fun switchToProfile(profileId: String, isPrivate: Boolean) {
        if (isInitializing) return
        
        // Animate all buttons
        val transition = ChangeBounds().apply {
            duration = 300
            interpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
        }
        TransitionManager.beginDelayedTransition(binding.profileButtonGroup, transition)
        
        for (i in 0 until binding.profileButtonGroup.childCount) {
            val child = binding.profileButtonGroup.getChildAt(i) as? MergedProfileButton
            child?.let {
                val buttonTag = it.tag as? String
                val shouldBeActive = if (isPrivate) {
                    buttonTag == "private"
                } else {
                    buttonTag == profileId
                }
                it.setActive(shouldBeActive, animated = true)
            }
        }
        
        // Update browsing mode and tabs
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        if (isPrivate) {
            if (!browsingModeManager.mode.isPrivate) {
                browsingModeManager.mode = BrowsingMode.Private
                profileManager.setPrivateMode(true)
                // Trigger Compose recomposition (it will automatically refresh via LaunchedEffect)
                profileStateTrigger.value++
            }
        } else {
            val profiles = profileManager.getAllProfiles()
            val profile = profiles.find { it.id == profileId }
            if (profile != null && (browsingModeManager.mode.isPrivate || profile.id != browsingModeManager.currentProfile.id)) {
                browsingModeManager.currentProfile = profile
                browsingModeManager.mode = BrowsingMode.Normal
                profileManager.setActiveProfile(profile)
                profileManager.setPrivateMode(false)
                // Trigger Compose recomposition (it will automatically refresh via LaunchedEffect)
                profileStateTrigger.value++
            }
        }
    }
    







    
    private fun showMoveToProfileDialog(tabIds: List<String>) {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val profiles = profileManager.getAllProfiles()
        
        val items = profiles.map { "${it.emoji} ${it.name}" }.toMutableList()
        items.add("🕵️ Private")
        
        val tabWord = if (tabIds.size > 1) "tabs" else "tab"
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Move $tabWord to Profile")
            .setItems(items.toTypedArray()) { _, which ->
                val targetProfileId = if (which == items.size - 1) {
                    "private"
                } else {
                    profiles[which].id
                }
                
                // Migrate tabs
                val migratedCount = profileManager.migrateTabsToProfile(tabIds, targetProfileId)
                
                // Show confirmation
                android.widget.Toast.makeText(
                    requireContext(),
                    "Moved $migratedCount $tabWord to ${items[which]}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
                // No updateTabsDisplay() needed as Compose handles it via state flow
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    
    private fun getColorInt(colorName: String): Int {
        return when (colorName) {
            "blue" -> 0xFF2196F3.toInt()
            "green" -> 0xFF4CAF50.toInt()
            "red" -> 0xFFF44336.toInt()
            "orange" -> 0xFFFF9800.toInt()
            "purple" -> 0xFF9C27B0.toInt()
            "pink" -> 0xFFE91E63.toInt()
            "cyan" -> 0xFF00BCD4.toInt()
            "yellow" -> 0xFFFFEB3B.toInt()
            else -> 0xFF2196F3.toInt()
        }
    }

    private fun addNewTab() {
        lifecycleScope.launch {
            val isPrivate = browsingModeManager.mode.isPrivate
            val currentProfile = browsingModeManager.currentProfile
            val contextId = if (isPrivate) "private" else "profile_${currentProfile.id}"
            
            requireContext().components.tabsUseCases.addTab(
                url = "about:homepage",
                private = isPrivate,
                contextId = contextId,
                selectTab = true
            )
            
            try {
                NavHostFragment.findNavController(this@TabsBottomSheetFragment)
                    .navigate(R.id.homeFragment)
                // Post dismiss to avoid touch event recycling crash
                view?.post { dismiss() }
            } catch (e: Exception) {
                view?.post { dismiss() }
            }
        }
    }

    private fun showProfileCreateDialog() {
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext())
        val userPreferences = com.prirai.android.nira.preferences.UserPreferences(requireContext())
        val themeChoice =
            com.prirai.android.nira.settings.ThemeChoice.entries.toTypedArray()[userPreferences.appThemeChoice]
        val isDark = when (themeChoice) {
            com.prirai.android.nira.settings.ThemeChoice.DARK -> true
            com.prirai.android.nira.settings.ThemeChoice.LIGHT -> false
            com.prirai.android.nira.settings.ThemeChoice.SYSTEM -> 
                requireContext().resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        
        composeView.setContent {
            com.prirai.android.nira.ui.theme.NiraTheme(
                darkTheme = isDark,
                isPrivateMode = browsingModeManager.mode == BrowsingMode.Private,
                amoledMode = userPreferences.amoledMode,
                dynamicColor = userPreferences.dynamicColors
            ) {
                com.prirai.android.nira.browser.profile.ProfileCreateDialog(
                    onDismiss = {
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onConfirm = { name, color, emoji ->
                        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                        val newProfile = profileManager.createProfile(name, color, emoji)

                        setupMergedProfileButtons()

                        browsingModeManager.currentProfile = newProfile
                        browsingModeManager.mode = BrowsingMode.Normal

                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    }
                )
            }
        }

        (binding.root as ViewGroup).addView(composeView)
    }

    private fun showProfileEditDialog(profile: com.prirai.android.nira.browser.profile.BrowserProfile) {
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext())
        val userPreferences = com.prirai.android.nira.preferences.UserPreferences(requireContext())
        val themeChoice =
            com.prirai.android.nira.settings.ThemeChoice.entries.toTypedArray()[userPreferences.appThemeChoice]
        val isDark = when (themeChoice) {
            com.prirai.android.nira.settings.ThemeChoice.DARK -> true
            com.prirai.android.nira.settings.ThemeChoice.LIGHT -> false
            com.prirai.android.nira.settings.ThemeChoice.SYSTEM -> 
                requireContext().resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        
        composeView.setContent {
            com.prirai.android.nira.ui.theme.NiraTheme(
                darkTheme = isDark,
                isPrivateMode = browsingModeManager.mode == BrowsingMode.Private,
                amoledMode = userPreferences.amoledMode,
                dynamicColor = userPreferences.dynamicColors
            ) {
                com.prirai.android.nira.browser.profile.ProfileEditDialog(
                    profile = profile,
                    onDismiss = {
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onConfirm = { name, color, emoji ->
                        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                        val updatedProfile = profile.copy(name = name, color = color, emoji = emoji)
                        profileManager.updateProfile(updatedProfile)

                        if (profile.id == browsingModeManager.currentProfile.id) {
                            browsingModeManager.currentProfile = updatedProfile
                        }

                        setupMergedProfileButtons()

                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onDelete = {
                        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())

                        if (profile.id == browsingModeManager.currentProfile.id) {
                            browsingModeManager.currentProfile = com.prirai.android.nira.browser.profile.BrowserProfile.getDefaultProfile()
                        }

                        profileManager.deleteProfile(profile.id)

                        setupMergedProfileButtons()

                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    }
                )
            }
        }

        (binding.root as ViewGroup).addView(composeView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // ============ NEW FLAT ADAPTER SYSTEM ============
    

    
    private fun setupComposeTabViews() {
        // Hide RecyclerView
        binding.tabsRecyclerView.visibility = View.GONE
        binding.tabsGridRecyclerView.visibility = View.GONE
        
        // Find parent container
        val container = binding.tabsRecyclerView.parent as? ViewGroup ?: return
        
        // Remove existing compose view if any
        container.findViewWithTag<androidx.compose.ui.platform.ComposeView>("tabs_compose")?.let {
            container.removeView(it)
        }
        
        // Add ComposeView
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            tag = "tabs_compose"
            setContent {
                com.prirai.android.nira.ui.theme.NiraTheme {
                    ComposeTabContent()
                }
            }
        }
        
        // Add to container with same layout params as RecyclerView
        val layoutParams = binding.tabsRecyclerView.layoutParams
        composeView.layoutParams = layoutParams
        container.addView(composeView)
    }
    
    @Composable
    private fun ComposeTabContent() {
        // Trigger recomposition when profile/mode changes
        val trigger by profileStateTrigger
        
        // Menu state
        val showMenu by showMenuState
        val menuTab by menuTabState

        val browsingMode = remember(trigger) { browsingModeManager.mode }
        val currentProfile = remember(trigger) { browsingModeManager.currentProfile }
        
        // Create ViewModel once and store it
        // Create ViewModel once and store it
        val viewModel = remember {
            TabViewModel(requireContext()).also {
                tabViewModel = it
            }
        }
        
        // Produce state that updates when store changes OR groups change
        val tabsState by produceState(
            initialValue = emptyList<mozilla.components.browser.state.state.TabSessionState>(),
            browsingMode, currentProfile, trigger
        ) {
            val store = requireContext().components.store
            val isPrivateMode = browsingMode.isPrivate
            val activeProfile = currentProfile
            val profileId = if (isPrivateMode) "private" else activeProfile.id
            
            // Helper to filter tabs
            fun filterTabs(tabs: List<mozilla.components.browser.state.state.TabSessionState>) = tabs.filter { tab ->
                val tabIsPrivate = tab.content.private
                if (tabIsPrivate != isPrivateMode) {
                    false
                } else if (isPrivateMode) {
                    tab.contextId == "private"
                } else {
                    val expectedContextId = "profile_${activeProfile.id}"
                    // Default profile shows null contextId tabs, other profiles don't
                    if (activeProfile.id == "default") {
                        (tab.contextId == expectedContextId) || (tab.contextId == null)
                    } else {
                        tab.contextId == expectedContextId
                    }
                }
            }
            
            // Collect both store updates AND group events
            launch {
                store.flowScoped(viewLifecycleOwner) { flow ->
                    flow.collect { state ->
                        val filteredTabs = filterTabs(state.tabs)
                        value = filteredTabs
                        viewModel.loadTabsForProfile(profileId, filteredTabs, state.selectedTabId)
                    }
                }
            }
            
            // Listen to group events and trigger refresh

        }
        
        // Wrapper Box to hold tabs and menu overlay
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
        ) {
            // Choose view based on grid mode
            // Choose view based on grid mode
            if (isGridView) {
                TabSheetGridView(
                    viewModel = viewModel,
                    onTabClick = ::handleTabClickCompose,
                    onTabClose = ::handleTabCloseCompose,
                    onTabLongPress = ::handleTabLongPressCompose,
                )
            } else {
                TabSheetListView(
                    viewModel = viewModel,
                    onTabClick = ::handleTabClickCompose,
                    onTabClose = ::handleTabCloseCompose,
                    onTabLongPress = ::handleTabLongPressCompose,
                )
            }
            
            // Menu overlay - shows at bottom when tab is long-pressed
            if (showMenu && menuTab != null) {
                // Clickable background to dismiss menu when clicking outside
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            showMenuState.value = false
                            menuTabState.value = null
                        }
                )
                
                TabContextMenu(
                    tab = menuTab!!,
                    onDismiss = {
                        showMenuState.value = false
                        menuTabState.value = null
                    },
                    onMoveToProfile = {
                        showMoveToProfileDialog(listOf(menuTab!!.id))
                        showMenuState.value = false
                        menuTabState.value = null
                    },
                    modifier = androidx.compose.ui.Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }
        }
    }
    
    private fun handleTabClickCompose(tabId: String) {
        lifecycleScope.launch {
            // Get the tab to check its context
            val store = requireContext().components.store
            val tab = store.state.tabs.find { it.id == tabId }
            
            if (tab != null) {
                // Switch profile/mode if needed based on tab's context
                val isPrivate = tab.content.private
                val tabContextId = tab.contextId
                
                if (isPrivate) {
                    // Switch to private mode
                    if (browsingModeManager.mode != BrowsingMode.Private) {
                        browsingModeManager.mode = BrowsingMode.Private
                    }
                } else {
                    // Switch to normal mode and determine profile
                    if (browsingModeManager.mode != BrowsingMode.Normal) {
                        browsingModeManager.mode = BrowsingMode.Normal
                    }
                    
                    // Switch profile if needed
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                    when {
                        tabContextId == null || tabContextId == "profile_default" -> {
                            // Default profile
                            val defaultProfile = profileManager.getAllProfiles().find { it.id == "default" }
                            if (defaultProfile != null && browsingModeManager.currentProfile.id != "default") {
                                profileManager.setActiveProfile(defaultProfile)
                                browsingModeManager.currentProfile = defaultProfile
                            }
                        }
                        tabContextId.startsWith("profile_") -> {
                            // Other profile
                            val profileId = tabContextId.removePrefix("profile_")
                            val targetProfile = profileManager.getAllProfiles().find { it.id == profileId }
                            if (targetProfile != null && browsingModeManager.currentProfile.id != profileId) {
                                profileManager.setActiveProfile(targetProfile)
                                browsingModeManager.currentProfile = targetProfile
                            }
                        }
                    }
                }
            }
            
            requireContext().components.tabsUseCases.selectTab(tabId)
            dismiss()
        }
    }
    
    private fun handleTabCloseCompose(tabId: String) {
        lifecycleScope.launch {
            requireContext().components.tabsUseCases.removeTab(tabId)
        }
    }
    
    private fun handleTabLongPressCompose(tab: TabSessionState) {
        // Show menu in place of profile switcher
        menuTabState.value = tab
        showMenuState.value = true
    }
    
    private fun showUngroupedTabMenuDialog(tabId: String, tabTitle: String) {
        val options = arrayOf("Move to Profile", "Duplicate Tab", "Pin Tab")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(tabTitle.ifEmpty { "Tab" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showMoveToProfileDialog(listOf(tabId))
                    1 -> {
                        android.widget.Toast.makeText(requireContext(), "Duplicate tab coming soon", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        android.widget.Toast.makeText(requireContext(), "Pin tab coming soon", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
    @androidx.compose.runtime.Composable
    private fun TabContextMenu(
        tab: TabSessionState,
        onDismiss: () -> Unit,
        onMoveToProfile: () -> Unit,
        modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
            modifier = modifier
        ) {
            androidx.compose.material3.Surface(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.padding(8.dp)
                ) {
                    // Tab title
                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        androidx.compose.material3.Text(
                            text = tab.content.title.ifEmpty { "Tab" },
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = androidx.compose.ui.Modifier.weight(1f)
                        )
                        
                        androidx.compose.material3.IconButton(onClick = onDismiss) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "Close menu"
                            )
                        }
                    }
                    
                    androidx.compose.material3.HorizontalDivider(
                        modifier = androidx.compose.ui.Modifier.padding(vertical = 4.dp)
                    )
                    
                    // Menu options

                    
                    MenuOption(
                        icon = androidx.compose.material.icons.Icons.Default.AccountCircle,
                        text = "Move to Profile",
                        onClick = onMoveToProfile
                    )
                    
                    MenuOption(
                        icon = androidx.compose.material.icons.Icons.Default.Close,
                        text = "Close Tab",
                        onClick = {
                            handleTabCloseCompose(tab.id)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
    
    @androidx.compose.runtime.Composable
    private fun MenuOption(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        text: String,
        onClick: () -> Unit
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier.size(24.dp),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
            
            androidx.compose.material3.Text(
                text = text,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
            )
        }
    }
}
