package com.prirai.android.nira.browser.tabs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import android.view.animation.PathInterpolator
import android.transition.TransitionManager
import android.transition.ChangeBounds
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.BrowsingModeManager
import com.prirai.android.nira.browser.tabgroups.TabGroupManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabs.compose.TabViewModel
import com.prirai.android.nira.browser.tabs.compose.TabSheetListView
import com.prirai.android.nira.browser.tabs.compose.TabSheetGridView
import com.prirai.android.nira.databinding.FragmentTabsBottomSheetBinding
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.lib.state.ext.flowScoped
import androidx.core.content.edit

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
 */
class TabsBottomSheetFragment : DialogFragment() {

    private var _binding: FragmentTabsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var browsingModeManager: BrowsingModeManager
    private lateinit var tabGroupManager: TabGroupManager
    private lateinit var unifiedGroupManager: UnifiedTabGroupManager

    private var composeOrderManager: com.prirai.android.nira.browser.tabs.compose.TabOrderManager? = null
    private var tabViewModel: TabViewModel? = null

    // Compose state management
    private val profileStateTrigger = mutableStateOf(0) // Triggers recomposition on profile/mode changes
    private val showMenuState = mutableStateOf(false) // Controls context menu visibility
    private val menuTabState = mutableStateOf<TabSessionState?>(null) // Currently selected tab for menu
    private val menuIsInGroupState = mutableStateOf(false) // Whether selected tab is in a group

    private var isInitializing = true
    private var isGridView = false
    private val viewPrefs by lazy {
        requireContext().getSharedPreferences("tabs_view_prefs", android.content.Context.MODE_PRIVATE)
    }

    companion object {
        const val TAG = "TabsBottomSheet"
        fun newInstance() = TabsBottomSheetFragment()
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
        tabGroupManager = activity.tabGroupManager
        unifiedGroupManager = UnifiedTabGroupManager.getInstance(requireContext())

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
                val profileManager =
                    com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
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

            composeOrderManager = com.prirai.android.nira.browser.tabs.compose.TabOrderManager.getInstance(
                requireContext(),
                unifiedGroupManager
            )
            setupComposeTabViews()
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
                        viewPrefs.edit { putBoolean("is_grid_view", false) }
                        handleSwitchToListView()
                    }
                }

                binding.gridViewButton.id -> {
                    if (!isGridView) {
                        isGridView = true
                        viewPrefs.edit { putBoolean("is_grid_view", true) }
                        handleSwitchToGridView()
                    }
                }
            }
        }
    }

    private fun handleSwitchToListView() {
            setupComposeTabViews()
    }

    private fun handleSwitchToGridView() {
            setupComposeTabViews()
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
                updateTabsDisplay()
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
                updateTabsDisplay()
                // Trigger Compose recomposition (it will automatically refresh via LaunchedEffect)
                profileStateTrigger.value++
            }
        }
    }

    private fun updateGridDisplay() {
        if (!isAdded || context == null) return

        lifecycleScope.launch {
            val store = requireContext().components.store.state
            val isPrivateMode = browsingModeManager.mode.isPrivate
            val currentProfile = browsingModeManager.currentProfile

            val filteredTabs = store.tabs.filter { tab ->
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

            val allGroups = unifiedGroupManager.getAllGroups()
            val gridItems = mutableListOf<TabGridItem>()

            val tabToGroupMap = mutableMapOf<String, com.prirai.android.nira.browser.tabgroups.TabGroupData>()
            allGroups.forEach { group ->
                group.tabIds.forEach { tabId ->
                    tabToGroupMap[tabId] = group
                }
            }

            val processedGroups = mutableSetOf<String>()
            val processedTabs = mutableSetOf<String>()

            filteredTabs.forEach { tab ->
                if (processedTabs.contains(tab.id)) return@forEach

                val group = tabToGroupMap[tab.id]

                if (group != null && !processedGroups.contains(group.id)) {
                    processedGroups.add(group.id)

                    val groupTabs = filteredTabs.filter { it.id in group.tabIds }

                    if (groupTabs.isNotEmpty()) {
                        gridItems.add(
                            TabGridItem.GroupHeader(
                                groupId = group.id,
                                name = group.name.ifBlank { "" },
                                color = group.color,
                                tabs = groupTabs
                            )
                        )
                    }

                    group.tabIds.forEach { processedTabs.add(it) }
                } else if (group == null) {
                    gridItems.add(TabGridItem.Tab(tab, null))
                    processedTabs.add(tab.id)
                }
            }

            if (filteredTabs.isEmpty()) {
                binding.tabsGridRecyclerView.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.VISIBLE
            } else {
                binding.tabsGridRecyclerView.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.GONE
            }
        }
    }

    private fun updateTabsDisplay() {
        if (isGridView) {
            updateGridDisplay()
            return
        }
        return
    }

    private fun showMoveToProfileDialog(tabIds: List<String>, isGroup: Boolean = false) {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val profiles = profileManager.getAllProfiles()

        val items = profiles.map { "${it.emoji} ${it.name}" }.toMutableList()
        items.add("🕵️ Private")

        val tabWord = if (isGroup) "group" else if (tabIds.size > 1) "tabs" else "tab"

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

                updateTabsDisplay()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                        val profileManager =
                            com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                        val newProfile = profileManager.createProfile(name, color, emoji)

                        setupMergedProfileButtons()

                        browsingModeManager.currentProfile = newProfile
                        browsingModeManager.mode = BrowsingMode.Normal
                        updateTabsDisplay()

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
                        val profileManager =
                            com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                        val updatedProfile = profile.copy(name = name, color = color, emoji = emoji)
                        profileManager.updateProfile(updatedProfile)

                        if (profile.id == browsingModeManager.currentProfile.id) {
                            browsingModeManager.currentProfile = updatedProfile
                        }

                        setupMergedProfileButtons()

                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onDelete = {
                        val profileManager =
                            com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())

                        if (profile.id == browsingModeManager.currentProfile.id) {
                            browsingModeManager.currentProfile =
                                com.prirai.android.nira.browser.profile.BrowserProfile.getDefaultProfile()
                        }

                        profileManager.deleteProfile(profile.id)

                        setupMergedProfileButtons()
                        updateTabsDisplay()

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
                // Read AMOLED preference
                val prefs = com.prirai.android.nira.preferences.UserPreferences(requireContext())
                val isAmoledActive = prefs.amoledMode
                
                com.prirai.android.nira.ui.theme.NiraTheme(amoledMode = isAmoledActive) {
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
        // Menu state
        val showMenu by showMenuState
        val menuTab by menuTabState
        val menuIsInGroup by menuIsInGroupState

        // Update profile button visibility based on menu state - use SideEffect for immediate execution
        androidx.compose.runtime.SideEffect {
            binding.profileButtonContainer.visibility = if (showMenu) View.GONE else View.VISIBLE
        }

        // Create ViewModel once and store it
        val viewModel = remember {
            TabViewModel(requireContext(), unifiedGroupManager).also {
                tabViewModel = it
                // Set up callbacks for tab operations
                it.onTabRemove = { tabId ->
                    // Immediately remove tab from store
                    requireContext().components.tabsUseCases.removeTab(tabId)
                }
                it.onTabRestore = { tab, position ->
                    // Restore tab at original position
                    val components = requireContext().components
                    components.tabsUseCases.addTab(
                        url = tab.content.url,
                        private = tab.content.private,
                        contextId = tab.contextId,
                        selectTab = false
                    )
                }
            }
        }

        // Set up global snackbar manager scope
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            com.prirai.android.nira.browser.tabs.compose.GlobalSnackbarManager.getInstance().coroutineScope = scope
        }

        // Get snackbar host state from global manager
        val snackbarHostState =
            com.prirai.android.nira.browser.tabs.compose.GlobalSnackbarManager.getInstance().snackbarHostState

        // Load tabs for current profile - trigger recomposition on profile/mode changes
        val trigger by profileStateTrigger
        LaunchedEffect(trigger) {
            val store = requireContext().components.store
            val browsingMode = browsingModeManager.mode
            val currentProfile = browsingModeManager.currentProfile
            val isPrivateMode = browsingMode.isPrivate
            val profileId = if (isPrivateMode) "private" else currentProfile.id

            // Helper to filter tabs
            fun filterTabs(tabs: List<TabSessionState>) = tabs.filter { tab ->
                val tabIsPrivate = tab.content.private
                if (tabIsPrivate != isPrivateMode) {
                    false
                } else if (isPrivateMode) {
                    tab.contextId == "private"
                } else {
                    val expectedContextId = "profile_${currentProfile.id}"
                    // Default profile shows null contextId tabs, other profiles don't
                    if (currentProfile.id == "default") {
                        (tab.contextId == expectedContextId) || (tab.contextId == null)
                    } else {
                        tab.contextId == expectedContextId
                    }
                }
            }

            // Collect store updates
            launch {
                store.flowScoped(viewLifecycleOwner) { flow ->
                    flow.collect { state ->
                        val filteredTabs = filterTabs(state.tabs)
                        viewModel.loadTabsForProfile(profileId, filteredTabs, state.selectedTabId)
                    }
                }
            }

            // Listen to group events and trigger refresh
            launch {
                unifiedGroupManager.groupEvents.collect { event ->
                    // When groups change, force a refresh with current store state
                    val state = store.state
                    val filteredTabs = filterTabs(state.tabs)
                    viewModel.loadTabsForProfile(profileId, filteredTabs, state.selectedTabId)
                }
            }
        }

        // Use Scaffold for proper snackbar positioning
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 80.dp) // Above bottom buttons
                )
            }
        ) { paddingValues ->
            // Wrapper Box to hold tabs and menu overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Choose view based on grid mode
                if (isGridView) {
                    TabSheetGridView(
                        viewModel = viewModel,
                        orderManager = composeOrderManager!!,
                        onTabClick = ::handleTabClickCompose,
                        onTabClose = ::handleTabCloseCompose,
                        onTabLongPress = ::handleTabLongPressCompose,
                        onGroupClick = { groupId ->
                            viewModel.toggleGroupExpanded(groupId)
                        },
                        onGroupOptionsClick = { groupId ->
                            // Show group options menu - could be enhanced
                        }
                    )
                } else {
                    TabSheetListView(
                        viewModel = viewModel,
                        orderManager = composeOrderManager!!,
                        onTabClick = ::handleTabClickCompose,
                        onTabClose = ::handleTabCloseCompose,
                        onTabLongPress = ::handleTabLongPressCompose,
                        onGroupClick = { groupId ->
                            viewModel.toggleGroupExpanded(groupId)
                        },
                        onGroupOptionsClick = { groupId ->
                            // Show group options menu - could be enhanced
                        }
                    )
                }

                // Menu overlay - shows at bottom when tab is long-pressed
                if (showMenu && menuTab != null) {
                    // Clickable background to dismiss menu when clicking outside
                    Box(
                        modifier = Modifier
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
                        isInGroup = menuIsInGroup,
                        onDismiss = {
                            showMenuState.value = false
                            menuTabState.value = null
                        },
                        onMoveToProfile = {
                            showMoveToProfileDialog(listOf(menuTab!!.id))
                            showMenuState.value = false
                            menuTabState.value = null
                        },
                        onRemoveFromGroup = {
                            lifecycleScope.launch {
                                tabViewModel?.removeTabFromGroup(menuTab!!.id)
                            }
                            showMenuState.value = false
                            menuTabState.value = null
                        },
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .fillMaxWidth()
                    )
                }
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
                    val profileManager =
                        com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
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
        // Use ViewModel's closeTab with undo support
        // The actual deletion will be handled by the onTabDeleteConfirmed callback
        tabViewModel?.closeTab(tabId, showUndo = true)
    }

    private fun handleTabLongPressCompose(tab: TabSessionState, isInGroup: Boolean) {
        // Show menu in place of profile switcher
        menuTabState.value = tab
        menuIsInGroupState.value = isInGroup
        showMenuState.value = true
    }

    @Composable
    private fun TabContextMenu(
        tab: TabSessionState,
        isInGroup: Boolean,
        onDismiss: () -> Unit,
        onMoveToProfile: () -> Unit,
        onRemoveFromGroup: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
            modifier = modifier
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // Tab title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = tab.content.title.ifEmpty { "Tab" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "Close menu"
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Menu options
                    if (isInGroup) {
                        MenuOption(
                            icon = androidx.compose.material.icons.Icons.Default.Clear,
                            text = "Remove from Group",
                            onClick = onRemoveFromGroup
                        )
                    }

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

    @Composable
    private fun MenuOption(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        text: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
