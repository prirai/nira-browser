package com.prirai.android.nira.browser.tabs

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.NavGraphDirections
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.BrowsingModeManager
import com.prirai.android.nira.browser.tabgroups.TabGroupManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.databinding.FragmentTabsBottomSheetBinding
import com.prirai.android.nira.ext.components
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.lib.state.ext.flowScoped
import kotlin.random.Random
import androidx.core.graphics.toColorInt

class TabsBottomSheetFragment : BottomSheetDialogFragment() {

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

    override fun onStart() {
        super.onStart()

        val bottomSheetDialog = dialog as com.google.android.material.bottomsheet.BottomSheetDialog
        val behavior = bottomSheetDialog.behavior

        val screenHeight = resources.displayMetrics.heightPixels
        val desiredHeight = (screenHeight * 0.85).toInt()

        behavior.isFitToContents = false
        behavior.peekHeight = desiredHeight
        behavior.expandedOffset = screenHeight - desiredHeight
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isHideable = true
        behavior.isDraggable = true

        bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
            val layoutParams = bottomSheet.layoutParams
            layoutParams.height = desiredHeight
            bottomSheet.layoutParams = layoutParams
            
            // Apply window insets to handle gesture navigation
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                // The profile chip scroll view already has paddingBottom set to handle gesture area
                insets
            }
        }

        bottomSheetDialog.setCancelable(true)
        bottomSheetDialog.setCanceledOnTouchOutside(true)
    }

    private fun setupUI() {
        binding.newTabFab.setOnClickListener {
            addNewTab()
        }
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
                
                dismiss()
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
                // No menu for ungrouped tabs
                false
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
                    tab.contextId == expectedContextId
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
            
            for (group in allGroups) {
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
            }
            
            val groupedTabIds = allGroups.flatMap { it.tabIds }.toSet()
            
            filteredTabs.filter { it.id !in groupedTabIds }.forEach { tab ->
                tabItems.add(TabItem.SingleTab(tab))
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
        return try {
            when (colorString.lowercase()) {
                "blue" -> "#2196F3".toColorInt()
                "red" -> "#F44336".toColorInt()
                "green" -> "#4CAF50".toColorInt()
                "orange" -> "#FF9800".toColorInt()
                "purple" -> "#9C27B0".toColorInt()
                "pink" -> "#E91E63".toColorInt()
                "teal" -> "#009688".toColorInt()
                "yellow" -> "#FFC107".toColorInt()
                else -> "#2196F3".toColorInt()
            }
        } catch (e: Exception) {
            "#2196F3".toColorInt()
        }
    }

    private fun showGroupTabOptionsMenu(tabId: String, groupId: String, view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        popup.menu.add(0, 1, 0, "Remove from group")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    lifecycleScope.launch {
                        unifiedGroupManager.removeTabFromGroup(tabId)
                    }
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
        popup.menu.add(0, 3, 2, "Ungroup All Tabs")
        popup.menu.add(0, 4, 3, "Close All Tabs")
        
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
                3 -> { // Ungroup All Tabs
                    lifecycleScope.launch {
                        unifiedGroupManager.deleteGroup(groupId)
                        updateTabsDisplay()
                    }
                    true
                }
                4 -> { // Close All Tabs
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

    private fun showRenameGroupDialog(groupId: String) {
        lifecycleScope.launch {
            val group = unifiedGroupManager.getGroup(groupId) ?: return@launch
            
            val input = android.widget.EditText(requireContext())
            input.setText(group.name)
            input.hint = "Group name"
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rename Group")
                .setView(input)
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
        val colorNames = COLORS.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Color")
            .setItems(colorNames) { _, which ->
                lifecycleScope.launch {
                    val colorInt = getColorInt(COLORS[which])
                    unifiedGroupManager.changeGroupColor(groupId, colorInt)
                    updateTabsDisplay()
                }
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
            
            // Create tab with proper contextId
            requireContext().components.tabsUseCases.addTab(
                url = "about:blank",
                private = isPrivate,
                contextId = if (isPrivate) "private" else "profile_${currentProfile.id}",
                selectTab = true
            )
            
            // Navigate to homeFragment
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(this@TabsBottomSheetFragment)
                    .navigate(R.id.homeFragment)
                dismiss()
            } catch (e: Exception) {
                // Navigation failed, just dismiss
                dismiss()
            }
        }
    }

    private fun showProfileCreateDialog() {
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext())
        composeView.setContent {
            androidx.compose.material3.MaterialTheme {
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
        composeView.setContent {
            androidx.compose.material3.MaterialTheme {
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
