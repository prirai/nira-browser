package com.prirai.android.nira.browser.tabs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.BrowsingModeManager
import com.prirai.android.nira.browser.tabgroups.TabGroupManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
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
    private lateinit var tabsAdapter: TabsWithGroupsAdapter
    private lateinit var dragHelper: TabGroupDragHelper
    private var isInitializing = true

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

        setupUI()
        setupTabsAdapter()
        setupDragAndDrop()
        setupProfileChips()
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
            
            // Apply window insets for edge-to-edge
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                
                // Apply padding to profile chip bar to avoid navigation bar overlap
                binding.profileChipScrollView.setPadding(
                    binding.profileChipScrollView.paddingLeft,
                    binding.profileChipScrollView.paddingTop,
                    binding.profileChipScrollView.paddingRight,
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
                tabsAdapter.toggleGroup(groupId)
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
        dragHelper = TabGroupDragHelper(
            adapter = tabsAdapter,
            groupManager = unifiedGroupManager,
            scope = lifecycleScope,
            onUpdate = { updateTabsDisplay() }
        )
        dragHelper.attachToRecyclerView(binding.tabsRecyclerView)
    }

    private fun setupProfileChips() {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val profiles = profileManager.getAllProfiles()
        val isPrivateMode = browsingModeManager.mode.isPrivate
        val currentProfile = browsingModeManager.currentProfile

        binding.profileChipGroup.removeAllViews()
        binding.profileChipGroup.setOnCheckedStateChangeListener(null)

        var chipToCheck: com.google.android.material.chip.Chip? = null
        val profileIdMap = mutableMapOf<Int, String>()

        profiles.forEach { profile ->
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_profile_chip, binding.profileChipGroup, false) as com.google.android.material.chip.Chip

            chip.text = "${profile.emoji} ${profile.name}"
            chip.tag = profile.id
            chip.id = View.generateViewId()
            profileIdMap[chip.id] = profile.id

            chip.setOnLongClickListener {
                if (!isInitializing) {
                    showProfileEditDialog(profile)
                }
                true
            }

            binding.profileChipGroup.addView(chip)

            if (!isPrivateMode && profile.id == currentProfile.id) {
                chipToCheck = chip
            }
        }

        val privateChip = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_profile_chip, binding.profileChipGroup, false) as com.google.android.material.chip.Chip
        privateChip.text = "🕵️ Private"
        privateChip.tag = "private"
        privateChip.id = View.generateViewId()
        profileIdMap[privateChip.id] = "private"
        binding.profileChipGroup.addView(privateChip)

        if (isPrivateMode) {
            chipToCheck = privateChip
        }

        val addChip = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_profile_chip, binding.profileChipGroup, false) as com.google.android.material.chip.Chip
        addChip.text = "+"
        addChip.isCheckable = false
        addChip.id = View.generateViewId()
        addChip.setOnClickListener {
            showProfileCreateDialog()
        }
        binding.profileChipGroup.addView(addChip)

        chipToCheck?.let {
            binding.profileChipGroup.check(it.id)
        }

        binding.profileChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isInitializing || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val selectedChipId = checkedIds.first()
            when (val selectedProfileId = profileIdMap[selectedChipId]) {
                "private" -> {
                    if (!browsingModeManager.mode.isPrivate) {
                        browsingModeManager.mode = BrowsingMode.Private
                        profileManager.setPrivateMode(true)
                        updateTabsDisplay()
                    }
                }
                else -> {
                    val profile = profiles.find { it.id == selectedProfileId }
                    if (profile != null && (browsingModeManager.mode.isPrivate || profile.id != browsingModeManager.currentProfile.id)) {
                        browsingModeManager.currentProfile = profile
                        browsingModeManager.mode = BrowsingMode.Normal
                        profileManager.setActiveProfile(profile)
                        profileManager.setPrivateMode(false)
                        updateTabsDisplay()
                    }
                }
            }
        }
    }

    private fun setupStoreObserver() {
        requireContext().components.store.flowScoped(viewLifecycleOwner) { flow ->
            flow.map { state -> state.tabs to state.selectedTabId }
                .distinctUntilChanged()
                .collect { (_, _) ->
                    if (isAdded && context != null) {
                        updateTabsDisplay()
                    }
                }
        }
    }

    private fun setupUnifiedGroupObserver() {
        // Listen to group state changes from UnifiedTabGroupManager
        lifecycleScope.launch {
            unifiedGroupManager.groupsState.collect { _ ->
                if (isAdded && context != null) {
                    updateTabsDisplay()
                }
            }
        }
        
        // Also listen to group events for fine-grained updates
        lifecycleScope.launch {
            unifiedGroupManager.groupEvents.collect { event ->
                if (isAdded && context != null) {
                    updateTabsDisplay()
                }
            }
        }
    }

    private fun updateTabsDisplay() {
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
                            tabsAdapter.expandGroup(group.id)
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

            tabsAdapter.updateItems(tabItems, store.selectedTabId)

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

                        setupProfileChips()

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

                        setupProfileChips()

                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onDelete = {
                        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())

                        if (profile.id == browsingModeManager.currentProfile.id) {
                            browsingModeManager.currentProfile = com.prirai.android.nira.browser.profile.BrowserProfile.getDefaultProfile()
                        }

                        profileManager.deleteProfile(profile.id)

                        setupProfileChips()
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
}
