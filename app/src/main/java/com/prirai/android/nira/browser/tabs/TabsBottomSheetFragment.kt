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
import com.prirai.android.nira.browser.tabgroups.TabGroupManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabs.compose.TabViewModel
import com.prirai.android.nira.browser.tabs.compose.TabSheetListView
import com.prirai.android.nira.browser.tabs.compose.TabSheetGridView
import com.prirai.android.nira.databinding.FragmentTabsBottomSheetBinding
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.lib.state.ext.flowScoped

class TabsBottomSheetFragment : DialogFragment() {

    private var _binding: FragmentTabsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var browsingModeManager: BrowsingModeManager
    private lateinit var tabGroupManager: TabGroupManager
    private lateinit var unifiedGroupManager: UnifiedTabGroupManager
    
    // Old RecyclerView adapters (only used when not using Compose)
    private var tabsAdapter: TabsWithGroupsAdapter? = null
    private var tabsGridAdapter: TabsGridAdapter? = null
    private var dragHelper: TabGroupDragHelper? = null
    private var gridDragHelper: TabGridDragHelper? = null
    
    // New flat adapter system (only used when not using Compose)
    private var flatTabsAdapter: com.prirai.android.nira.browser.tabs.dragdrop.FlatTabsAdapter? = null
    private var unifiedDragHelper: com.prirai.android.nira.browser.tabs.dragdrop.UnifiedDragHelper? = null
    private lateinit var tabOrderPersistence: com.prirai.android.nira.browser.tabs.dragdrop.TabOrderPersistence
    private val collapsedGroupIds = mutableSetOf<String>()
    private var useNewDragSystem = true // Feature flag
    
    // Compose tab system
    private var useComposeTabSystem = true // Feature flag to enable Compose
    private var composeOrderManager: com.prirai.android.nira.browser.tabs.compose.TabOrderManager? = null
    private var tabViewModel: com.prirai.android.nira.browser.tabs.compose.TabViewModel? = null
    
    // Mutable state for Compose to observe profile/mode changes
    private val profileStateTrigger = mutableStateOf(0)
    
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
        tabGroupManager = activity.tabGroupManager
        unifiedGroupManager = UnifiedTabGroupManager.getInstance(requireContext())
        tabOrderPersistence = com.prirai.android.nira.browser.tabs.dragdrop.TabOrderPersistence(requireContext())

        setupUI()
        
        if (useComposeTabSystem) {
            // Initialize Compose tab system
            composeOrderManager = com.prirai.android.nira.browser.tabs.compose.TabOrderManager(requireContext(), unifiedGroupManager)
            setupComposeTabViews()
        } else if (useNewDragSystem) {
            setupFlatTabsAdapter()
            setupNewDragAndDrop()
            // Grid view is needed for both systems
            setupGridView()
        } else {
            setupTabsAdapter()
            setupDragAndDrop()
            // Grid view is needed for both systems
            setupGridView()
        }
        
        setupMergedProfileButtons()
        setupStoreObserver()
        setupUnifiedGroupObserver()
        
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

    private fun setupTabsAdapter() {
        tabsAdapter = TabsWithGroupsAdapter(
            context = requireContext(),
            onTabClick = { tabId ->
                requireContext().components.tabsUseCases.selectTab(tabId)
                
                // Navigate to browser if we're currently on home
                try {
                    val navController = NavHostFragment.findNavController(this)
                    if (navController.currentDestination?.id == R.id.homeFragment) {
                        // Navigate using activity's openToBrowser method for proper handling
                        (requireActivity() as BrowserActivity).openToBrowser(
                            from = com.prirai.android.nira.BrowserDirection.FromHome,
                            customTabSessionId = tabId
                        )
                    }
                } catch (e: Exception) {
                    // Ignore navigation errors
                }
                
                // Post dismiss to avoid touch event recycling crash
                view?.post { dismiss() }
            },
            onTabClose = { tabId ->
                requireContext().components.tabsUseCases.removeTab(tabId)
            },
            onGroupClick = { groupId ->
                tabsAdapter!!.toggleGroup(groupId)
            },
            onGroupMoreClick = { groupId, view ->
                showGroupOptionsMenu(groupId, view)
            },
            onTabLongPress = { tabId, view ->
                showUngroupedTabOptionsMenu(tabId, view)
                true
            },
            onGroupTabLongPress = { tabId, groupId, view ->
                showGroupTabOptionsMenu(tabId, groupId, view)
                true
            }
        )

        binding.tabsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tabsAdapter
        }
    }

    private fun setupDragAndDrop() {
        // List view drag helper
        dragHelper = TabGroupDragHelper(
            adapter = tabsAdapter!!,
            groupManager = unifiedGroupManager,
            scope = lifecycleScope,
            onUpdate = { updateTabsDisplay() }
        )
        dragHelper!!.attachToRecyclerView(binding.tabsRecyclerView)
        
        // Grid view drag helper
        gridDragHelper = TabGridDragHelper(
            adapter = tabsGridAdapter!!,
            groupManager = unifiedGroupManager,
            scope = lifecycleScope,
            onUpdate = { updateGridDisplay() }
        )
        gridDragHelper!!.attachToRecyclerView(binding.tabsGridRecyclerView)
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
    
    private fun setupGridView() {
        // Load saved view mode
        isGridView = viewPrefs.getBoolean("is_grid_view", false)
        
        // Setup grid adapter with ThumbnailLoader
        val thumbnailLoader = mozilla.components.browser.thumbnails.loader.ThumbnailLoader(
            requireContext().components.thumbnailStorage
        )
        tabsGridAdapter = TabsGridAdapter(
            thumbnailLoader = thumbnailLoader,
            onTabClick = { tabId ->
                requireContext().components.tabsUseCases.selectTab(tabId)
                
                // Navigate to browser if we're currently on home
                try {
                    val navController = NavHostFragment.findNavController(this)
                    if (navController.currentDestination?.id == R.id.homeFragment) {
                        (requireActivity() as BrowserActivity).openToBrowser(
                            from = com.prirai.android.nira.BrowserDirection.FromHome,
                            customTabSessionId = tabId
                        )
                    }
                } catch (e: Exception) {
                    // Ignore navigation errors
                }
                
                view?.post { dismiss() }
            },
            onTabClose = { tabId ->
                requireContext().components.tabsUseCases.removeTab(tabId)
            },
            onGroupMoreClick = { groupId, view ->
                showGroupOptionsMenu(groupId, view)
            },
            onTabLongPress = { tabId, view ->
                showUngroupedTabOptionsMenu(tabId, view)
                true
            }
        )
        
        binding.tabsGridRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (tabsGridAdapter!!.getItemViewType(position)) {
                            0 -> 3 // Group takes full width (3 columns)
                            else -> 1 // Individual tabs take 1/3 width
                        }
                    }
                }
            }
            adapter = tabsGridAdapter
        }
        
        // Setup view mode switcher
        binding.viewModeSwitcher.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            
            when (checkedId) {
                R.id.listViewButton -> handleSwitchToListView()
                R.id.gridViewButton -> handleSwitchToGridView()
            }
        }
        
        // Apply initial view mode
        if (isGridView) {
            binding.gridViewButton.isChecked = true
            binding.tabsRecyclerView.visibility = View.GONE
            binding.tabsGridRecyclerView.visibility = View.VISIBLE
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
                        gridItems.add(TabGridItem.GroupHeader(
                            groupId = group.id,
                            name = group.name.ifBlank { "" },
                            color = group.color,
                            tabs = groupTabs
                        ))
                    }
                    
                    group.tabIds.forEach { processedTabs.add(it) }
                } else if (group == null) {
                    gridItems.add(TabGridItem.Tab(tab, null))
                    processedTabs.add(tab.id)
                }
            }

            tabsGridAdapter?.updateItems(gridItems, store.selectedTabId)
            
            if (filteredTabs.isEmpty()) {
                binding.tabsGridRecyclerView.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.VISIBLE
            } else {
                binding.tabsGridRecyclerView.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.GONE
            }
        }
    }

    private fun setupStoreObserver() {
        requireContext().components.store.flowScoped(viewLifecycleOwner) { flow ->
            flow.map { state -> state.tabs to state.selectedTabId }
                .distinctUntilChanged()
                .collect { (_, _) ->
                    if (isAdded && context != null) {
                        if (useComposeTabSystem) {
                            // Compose system handles updates automatically via produceState
                        } else if (useNewDragSystem) {
                            updateFlatTabsDisplay()
                        } else {
                            updateTabsDisplay()
                        }
                    }
                }
        }
    }

    private fun setupUnifiedGroupObserver() {
        // Listen to group state changes from UnifiedTabGroupManager
        lifecycleScope.launch {
            unifiedGroupManager.groupsState.collect { _ ->
                if (isAdded && context != null) {
                    if (useComposeTabSystem) {
                        // Compose system handles updates automatically
                    } else if (useNewDragSystem) {
                        updateFlatTabsDisplay()
                    } else {
                        updateTabsDisplay()
                    }
                }
            }
        }
        
        // Also listen to group events for fine-grained updates
        lifecycleScope.launch {
            unifiedGroupManager.groupEvents.collect { event ->
                if (isAdded && context != null) {
                    if (useComposeTabSystem) {
                        // Compose system handles updates automatically
                    } else if (useNewDragSystem) {
                        updateFlatTabsDisplay()
                    } else {
                        updateTabsDisplay()
                    }
                }
            }
        }
    }

    private fun updateTabsDisplay() {
        if (isGridView) {
            updateGridDisplay()
            return
        }
        
        // If using new drag system, delegate to new method
        if (useNewDragSystem) {
            updateFlatTabsDisplay()
            return
        }
        
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
                    // Include tabs with matching contextId OR guest tabs (null contextId)
                    (tab.contextId == expectedContextId) || (tab.contextId == null)
                }
            }

            // Use UnifiedTabGroupManager instead of old managers
            val allGroups = unifiedGroupManager.getAllGroups()
            
            val tabItems = mutableListOf<TabItem>()
            
            // Find group containing selected tab to auto-expand it
            val selectedTabId = store.selectedTabId
            val groupWithSelectedTab = allGroups.find { group ->
                group.tabIds.contains(selectedTabId)
            }
            
            // Create a map of tab ID to group for quick lookup
            val tabToGroupMap = mutableMapOf<String, com.prirai.android.nira.browser.tabgroups.TabGroupData>()
            allGroups.forEach { group ->
                group.tabIds.forEach { tabId ->
                    tabToGroupMap[tabId] = group
                }
            }
            
            val processedGroups = mutableSetOf<String>()
            val processedTabs = mutableSetOf<String>()
            
            // Process tabs in their original order (like tab bar)
            // Groups appear where their first tab is located
            filteredTabs.forEach { tab ->
                // Skip if already processed
                if (processedTabs.contains(tab.id)) {
                    return@forEach
                }
                
                val group = tabToGroupMap[tab.id]
                
                if (group != null && !processedGroups.contains(group.id)) {
                    // First tab of a group - add the entire group here
                    processedGroups.add(group.id)
                    
                    val groupTabs = filteredTabs.filter { it.id in group.tabIds }
                    
                    if (groupTabs.isNotEmpty()) {
                        tabItems.add(TabItem.Group(
                            groupId = group.id,
                            name = group.name.ifBlank { "" },
                            color = group.color,
                            tabs = groupTabs
                        ))
                        
                        // Auto-expand group containing selected tab
                        if (group.id == groupWithSelectedTab?.id) {
                            tabsAdapter!!.expandGroup(group.id)
                        }
                    }
                    
                    // Mark all group tabs as processed
                    group.tabIds.forEach { processedTabs.add(it) }
                } else if (group == null) {
                    // Tab is not in any group - add it in its original position
                    tabItems.add(TabItem.SingleTab(tab))
                    processedTabs.add(tab.id)
                }
            }

            tabsAdapter!!.updateItems(tabItems, store.selectedTabId)

            if (filteredTabs.isEmpty()) {
                binding.tabsRecyclerView.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.VISIBLE
            } else {
                binding.tabsRecyclerView.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.GONE
            }
        }
    }

    private fun parseColor(colorString: String): Int {
        return com.prirai.android.nira.theme.ColorConstants.TabGroups.parseColor(colorString)
    }

    private fun showUngroupedTabOptionsMenu(tabId: String, view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        popup.menu.add(0, 1, 0, "Move to Profile")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showMoveToProfileDialog(listOf(tabId))
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    private fun showGroupTabOptionsMenu(tabId: String, groupId: String, view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        popup.menu.add(0, 1, 0, "Remove from group")
        popup.menu.add(0, 2, 1, "Move to Profile")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    lifecycleScope.launch {
                        unifiedGroupManager.removeTabFromGroup(tabId)
                    }
                    true
                }
                2 -> {
                    showMoveToProfileDialog(listOf(tabId))
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    private fun showGroupOptionsMenu(groupId: String, view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        
        // Add menu items to match tab bar options
        popup.menu.add(0, 1, 0, "Rename Island")
        popup.menu.add(0, 2, 1, "Change Color")
        popup.menu.add(0, 3, 2, "Move Group to Profile")
        popup.menu.add(0, 4, 3, "Ungroup All Tabs")
        popup.menu.add(0, 5, 4, "Close All Tabs")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { // Rename Island
                    showRenameGroupDialog(groupId)
                    true
                }
                2 -> { // Change Color
                    showChangeGroupColorDialog(groupId)
                    true
                }
                3 -> { // Move Group to Profile
                    lifecycleScope.launch {
                        val group = unifiedGroupManager.getGroup(groupId)
                        group?.tabIds?.let { tabIds ->
                            showMoveToProfileDialog(tabIds, isGroup = true)
                        }
                    }
                    true
                }
                4 -> { // Ungroup All Tabs
                    lifecycleScope.launch {
                        unifiedGroupManager.deleteGroup(groupId)
                        updateTabsDisplay()
                    }
                    true
                }
                5 -> { // Close All Tabs
                    lifecycleScope.launch {
                        val group = unifiedGroupManager.getGroup(groupId)
                        group?.tabIds?.forEach { tabId ->
                            requireContext().components.tabsUseCases.removeTab(tabId)
                        }
                        // Group will be auto-deleted when tabs are removed
                        updateTabsDisplay()
                    }
                    true
                }
                else -> false
            }
        }
        
        popup.show()
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

    private fun showRenameGroupDialog(groupId: String) {
        lifecycleScope.launch {
            val group = unifiedGroupManager.getGroup(groupId) ?: return@launch
            
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edittext, null)
            val inputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout)
            val input = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_text)
            
            inputLayout.hint = "Group name"
            input.setText(group.name)
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rename Group")
                .setView(dialogView)
                .setPositiveButton("Rename") { _, _ ->
                    lifecycleScope.launch {
                        unifiedGroupManager.renameGroup(groupId, input.text.toString())
                        updateTabsDisplay()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showChangeGroupColorDialog(groupId: String) {
        lifecycleScope.launch {
            val group = unifiedGroupManager.getGroup(groupId) ?: return@launch
            val currentColor = group.color
            
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_color_picker, null)
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.colorRecyclerView)
            
            var selectedColorIndex = COLORS.indexOfFirst { getColorInt(it) == currentColor }.takeIf { it >= 0 } ?: 0
            
            val colorAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color_chip, parent, false)
                    return object : RecyclerView.ViewHolder(view) {}
                }
                
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val card = holder.itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorCard)
                    val colorView = holder.itemView.findViewById<View>(R.id.colorView)
                    
                    val colorInt = getColorInt(COLORS[position])
                    colorView.setBackgroundColor(colorInt)
                    card.isChecked = position == selectedColorIndex
                    
                    card.setOnClickListener {
                        val oldSelection = selectedColorIndex
                        selectedColorIndex = position
                        notifyItemChanged(oldSelection)
                        notifyItemChanged(selectedColorIndex)
                    }
                }
                
                override fun getItemCount() = COLORS.size
            }
            
            recyclerView.adapter = colorAdapter
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Choose Color")
                .setView(dialogView)
                .setPositiveButton("Apply") { _, _ ->
                    lifecycleScope.launch {
                        val colorInt = getColorInt(COLORS[selectedColorIndex])
                        unifiedGroupManager.changeGroupColor(groupId, colorInt)
                        updateTabsDisplay()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
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
    
    private fun setupFlatTabsAdapter() {
        flatTabsAdapter = com.prirai.android.nira.browser.tabs.dragdrop.FlatTabsAdapter(
            onTabClick = { tabId ->
                requireContext().components.tabsUseCases.selectTab(tabId)
                
                try {
                    val navController = NavHostFragment.findNavController(this)
                    if (navController.currentDestination?.id == R.id.homeFragment) {
                        (requireActivity() as BrowserActivity).openToBrowser(
                            from = com.prirai.android.nira.BrowserDirection.FromHome,
                            customTabSessionId = tabId
                        )
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                
                view?.post { dismiss() }
            },
            onTabClose = { tabId ->
                requireContext().components.tabsUseCases.removeTab(tabId)
            },
            onTabLongPress = { tabId, view ->
                showUngroupedTabOptionsMenu(tabId, view)
                true
            },
            onGroupedTabLongPress = { tabId, groupId, view ->
                showGroupedTabOptionsMenu(tabId, groupId, view)
                true
            },
            onGroupHeaderClick = { groupId ->
                toggleGroupExpansion(groupId)
            },
            onGroupMoreClick = { groupId, view ->
                showGroupOptionsMenu(groupId, view)
            }
        )
        
        binding.tabsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = flatTabsAdapter
        }
    }
    
    private fun setupNewDragAndDrop() {
        unifiedDragHelper = com.prirai.android.nira.browser.tabs.dragdrop.UnifiedDragHelper(
            adapter = flatTabsAdapter!!,
            groupManager = unifiedGroupManager,
            scope = lifecycleScope,
            onUpdate = { updateFlatTabsDisplay() },
            getCurrentFlatList = { flatTabsAdapter!!.currentList },
            onOrderChanged = { saveCurrentTabOrder() },
            isGridView = false,
            spanCount = 1
        )
        unifiedDragHelper!!.attachToRecyclerView(binding.tabsRecyclerView)
    }
    
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
        
        // Capture current browsing mode and profile based on trigger
        // This ensures we get fresh values on each profile switch
        val browsingMode = remember(trigger) { browsingModeManager.mode }
        val currentProfile = remember(trigger) { browsingModeManager.currentProfile }
        
        // Create ViewModel once and store it
        val viewModel = remember {
            TabViewModel(requireContext(), unifiedGroupManager).also {
                tabViewModel = it
            }
        }
        
        // Simple approach: Load tabs directly when profile changes, then observe store updates
        LaunchedEffect(browsingMode, currentProfile, trigger) {
            val store = requireContext().components.store
            val isPrivateMode = browsingMode.isPrivate
            val activeProfile = currentProfile
            val profileId = if (isPrivateMode) "private" else activeProfile.id
            
            // Collect tabs from store and update ViewModel
            store.flowScoped(viewLifecycleOwner) { flow ->
                flow.collect { state ->
                    // Filter tabs based on mode and profile
                    val filteredTabs = state.tabs.filter { tab ->
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
                    
                    // Update ViewModel with filtered tabs
                    viewModel.loadTabsForProfile(profileId, filteredTabs, state.selectedTabId)
                }
            }
        }
        
        // Observe group changes and trigger refresh
        LaunchedEffect(Unit) {
            unifiedGroupManager.groupEvents.collect { event ->
                // When groups change, force a refresh with current store state
                val store = requireContext().components.store
                val state = store.state
                val isPrivateMode = browsingMode.isPrivate
                val activeProfile = currentProfile
                
                val filteredTabs = state.tabs.filter { tab ->
                    val tabIsPrivate = tab.content.private
                    if (tabIsPrivate != isPrivateMode) {
                        false
                    } else if (isPrivateMode) {
                        tab.contextId == "private"
                    } else {
                        val expectedContextId = "profile_${activeProfile.id}"
                        if (activeProfile.id == "default") {
                            (tab.contextId == expectedContextId) || (tab.contextId == null)
                        } else {
                            tab.contextId == expectedContextId
                        }
                    }
                }
                
                viewModel.forceRefresh(filteredTabs, state.selectedTabId)
            }
        }
        
        // Choose view based on grid mode
        if (isGridView) {
            TabSheetGridView(
                viewModel = viewModel,
                onTabClick = ::handleTabClickCompose,
                onTabClose = ::handleTabCloseCompose,
                onGroupClick = { groupId ->
                    viewModel.toggleGroupExpanded(groupId)
                },
                onGroupOptionsClick = { groupId ->
                    showGroupOptionsDialog(groupId, viewModel)
                }
            )
        } else {
            TabSheetListView(
                viewModel = viewModel,
                onTabClick = ::handleTabClickCompose,
                onTabClose = ::handleTabCloseCompose,
                onGroupClick = { groupId ->
                    viewModel.toggleGroupExpanded(groupId)
                },
                onGroupOptionsClick = { groupId ->
                    showGroupOptionsDialog(groupId, viewModel)
                }
            )
        }
    }
    
    private fun handleTabClickCompose(tabId: String) {
        lifecycleScope.launch {
            requireContext().components.tabsUseCases.selectTab(tabId)
            dismiss()
        }
    }
    
    private fun handleTabCloseCompose(tabId: String) {
        lifecycleScope.launch {
            requireContext().components.tabsUseCases.removeTab(tabId)
        }
    }
    
    private fun showGroupOptionsDialog(groupId: String, viewModel: TabViewModel) {
        lifecycleScope.launch {
            val group = unifiedGroupManager.getGroup(groupId) ?: return@launch
            
            val options = arrayOf("Rename Group", "Change Color", "Ungroup All")
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(group.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showRenameGroupDialog(groupId, group.name, viewModel)
                        1 -> showChangeGroupColorDialogCompose(groupId, viewModel)
                        2 -> {
                            viewModel.ungroupAll(groupId)
                        }
                    }
                }
                .show()
        }
    }
    
    private fun showRenameGroupDialog(groupId: String, currentName: String, viewModel: TabViewModel) {
        val input = android.widget.EditText(requireContext())
        input.setText(currentName)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Group")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank()) {
                    viewModel.renameGroup(groupId, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showChangeGroupColorDialogCompose(groupId: String, viewModel: TabViewModel) {
        lifecycleScope.launch {
            val group = unifiedGroupManager.getGroup(groupId) ?: return@launch
            val currentColor = group.color
            
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_color_picker, null)
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.colorRecyclerView)
            
            var selectedColorIndex = COLORS.indexOfFirst { getColorInt(it) == currentColor }.takeIf { it >= 0 } ?: 0
            
            val colorAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color_chip, parent, false)
                    return object : RecyclerView.ViewHolder(view) {}
                }
                
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val card = holder.itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorCard)
                    val colorView = holder.itemView.findViewById<View>(R.id.colorView)
                    
                    val colorInt = getColorInt(COLORS[position])
                    colorView.setBackgroundColor(colorInt)
                    card.isChecked = position == selectedColorIndex
                    
                    card.setOnClickListener {
                        val oldSelection = selectedColorIndex
                        selectedColorIndex = position
                        notifyItemChanged(oldSelection)
                        notifyItemChanged(selectedColorIndex)
                    }
                }
                
                override fun getItemCount() = COLORS.size
            }
            
            recyclerView.adapter = colorAdapter
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Choose Color")
                .setView(dialogView)
                .setPositiveButton("Apply") { _, _ ->
                    val colorInt = getColorInt(COLORS[selectedColorIndex])
                    viewModel.changeGroupColor(groupId, colorInt)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun toggleGroupExpansion(groupId: String) {
        if (collapsedGroupIds.contains(groupId)) {
            collapsedGroupIds.remove(groupId)
        } else {
            collapsedGroupIds.add(groupId)
        }
        updateFlatTabsDisplay()
    }
    
    private fun updateFlatTabsDisplay() {
        if (!isAdded || context == null || flatTabsAdapter == null) return
        
        lifecycleScope.launch {
            val store = requireContext().components.store.state
            val isPrivateMode = browsingModeManager.mode.isPrivate
            val currentProfile = browsingModeManager.currentProfile
            
            // Filter tabs
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
            
            // Get groups
            val allGroups = unifiedGroupManager.getAllGroups()
            
            // Load custom order
            val profileKey = if (isPrivateMode) "private" else currentProfile.id
            val customOrder = tabOrderPersistence.loadOrder(profileKey)
            
            // Build flat list with custom order
            val builder = com.prirai.android.nira.browser.tabs.dragdrop.TabListBuilder(collapsedGroupIds)
            val flatList = builder.buildList(filteredTabs, allGroups, customOrder)
            
            // Update adapter
            flatTabsAdapter?.updateItems(flatList, store.selectedTabId)
            
            // Show/hide empty state
            if (filteredTabs.isEmpty()) {
                binding.tabsRecyclerView.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.VISIBLE
            } else {
                binding.tabsRecyclerView.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.GONE
            }
        }
    }
    
    /**
     * Save current tab order for persistence
     */
    private fun saveCurrentTabOrder() {
        lifecycleScope.launch {
            val currentList = flatTabsAdapter!!.currentList
            val tabIds = currentList.mapNotNull { item ->
                when (item) {
                    is com.prirai.android.nira.browser.tabs.dragdrop.TabListItem.UngroupedTab -> item.tab.id
                    is com.prirai.android.nira.browser.tabs.dragdrop.TabListItem.GroupedTab -> item.tab.id
                    else -> null
                }
            }
            
            val isPrivateMode = browsingModeManager.mode.isPrivate
            val currentProfile = browsingModeManager.currentProfile
            val profileKey = if (isPrivateMode) "private" else currentProfile.id
            
            tabOrderPersistence.saveOrder(profileKey, tabIds)
        }
    }
    
    private fun showGroupedTabOptionsMenu(tabId: String, groupId: String, view: View) {
        // Alias for the existing method
        showGroupTabOptionsMenu(tabId, groupId, view)
    }
}
