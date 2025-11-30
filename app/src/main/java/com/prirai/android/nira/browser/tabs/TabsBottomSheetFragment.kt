package com.prirai.android.nira.browser.tabs

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.BrowsingModeManager
import com.prirai.android.nira.components.toolbar.modern.TabIsland
import com.prirai.android.nira.components.toolbar.modern.TabIslandManager
import com.prirai.android.nira.databinding.FragmentTabsBottomSheetBinding
import com.prirai.android.nira.ext.components
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * Modern bottom sheet dialog for managing tabs with tab islands support
 * Shows tab islands in vertical layout similar to toolbar but stacked vertically
 * Supports drag-and-drop for organizing tabs into islands
 */
class TabsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentTabsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var browsingModeManager: BrowsingModeManager
    private lateinit var configuration: Configuration
    private lateinit var tabsAdapter: TabIslandsVerticalAdapter
    private lateinit var islandManager: TabIslandManager
    private var isInitializing = true
    private var itemTouchHelper: ItemTouchHelper? = null
    private var isUpdating = false

    companion object {
        const val TAG = "TabsBottomSheetFragment"
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

        islandManager = TabIslandManager.getInstance(requireContext())
        setupUI()
        setupTabsAdapter()
        setupDragAndDrop()
        setupStoreObserver()
        updateTabsDisplay()
    }

    override fun onStart() {
        super.onStart()

        // Reset flag to scroll to selected tab when modal reopens
        isInitializing = true

        // Configure bottom sheet behavior
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
        behavior.isDraggable = false

        bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
            val layoutParams = bottomSheet.layoutParams
            layoutParams.height = desiredHeight
            bottomSheet.layoutParams = layoutParams
        }

        bottomSheetDialog.setCancelable(true)
        bottomSheetDialog.setCanceledOnTouchOutside(true)
    }

    private fun setupUI() {
        browsingModeManager = (activity as BrowserActivity).browsingModeManager
        
        // Determine the correct profile from the currently selected tab
        val store = requireContext().components.store.state
        val selectedTab = store.tabs.find { it.id == store.selectedTabId }
        
        if (selectedTab != null) {
            if (selectedTab.content.private) {
                // Private tab selected
                browsingModeManager.mode = BrowsingMode.Private
            } else {
                // Normal tab - determine profile from contextId
                val contextId = selectedTab.contextId ?: "profile_default"
                val profileId = contextId.removePrefix("profile_")
                val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                val profile = profileManager.getAllProfiles().find { it.id == profileId } 
                    ?: com.prirai.android.nira.browser.profile.BrowserProfile.getDefaultProfile()
                browsingModeManager.currentProfile = profile
                browsingModeManager.mode = BrowsingMode.Normal
            }
        }
        
        configuration = Configuration(
            if (browsingModeManager.mode.isPrivate)
                BrowserTabType.PRIVATE
            else
                BrowserTabType.NORMAL
        )

        // Setup new tab button
        binding.newTabButton.apply {
            setOnClickListener { addNewTab() }
            contentDescription = getString(R.string.new_tab)
        }

        setupProfileChips()
        binding.dragHandle.contentDescription = getString(R.string.drag_handle_description)
    }
    
    private fun setupProfileChips() {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val profiles = profileManager.getAllProfiles()
        val isPrivateMode = browsingModeManager.mode.isPrivate
        val currentProfile = browsingModeManager.currentProfile
        
        binding.profileChipGroup.removeAllViews()
        
        // Remove any existing listeners to prevent duplicates
        binding.profileChipGroup.setOnCheckedStateChangeListener(null)
        
        var chipToCheck: com.google.android.material.chip.Chip? = null
        val profileIdMap = mutableMapOf<Int, String>()
        
        // Add profile chips
        profiles.forEach { profile ->
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_profile_chip, binding.profileChipGroup, false) as com.google.android.material.chip.Chip
            
            chip.text = "${profile.emoji} ${profile.name}"
            chip.tag = profile.id
            chip.id = View.generateViewId()
            profileIdMap[chip.id] = profile.id
            
            // Set chip long-press listener for editing
            chip.setOnLongClickListener {
                if (!isInitializing) {
                    showProfileEditDialog(profile)
                }
                true
            }
            
            binding.profileChipGroup.addView(chip)
            
            // Remember which chip to check
            if (!isPrivateMode && profile.id == currentProfile.id) {
                chipToCheck = chip
            }
        }
        
        // Add Private mode chip
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
        
        // Add "+" chip to create new profile
        val addChip = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_profile_chip, binding.profileChipGroup, false) as com.google.android.material.chip.Chip
        addChip.text = "+"
        addChip.isCheckable = false
        addChip.id = View.generateViewId()
        addChip.setOnClickListener {
            showProfileCreateDialog()
        }
        binding.profileChipGroup.addView(addChip)
        
        // Check the appropriate chip using ChipGroup's method
        chipToCheck?.let {
            binding.profileChipGroup.check(it.id)
        }
        
        // Set up listener for chip selection changes
        binding.profileChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (isInitializing || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            val selectedChipId = checkedIds.first()
            val selectedProfileId = profileIdMap[selectedChipId]
            
            when (selectedProfileId) {
                "private" -> {
                    // Switch to private mode
                    if (!browsingModeManager.mode.isPrivate) {
                        browsingModeManager.mode = BrowsingMode.Private
                        profileManager.setPrivateMode(true)
                        animateTabModeTransition(BrowserTabType.PRIVATE)
                    }
                }
                else -> {
                    // Switch to normal profile
                    val profile = profiles.find { it.id == selectedProfileId }
                    if (profile != null && (browsingModeManager.mode.isPrivate || profile.id != browsingModeManager.currentProfile.id)) {
                        browsingModeManager.currentProfile = profile
                        browsingModeManager.mode = BrowsingMode.Normal
                        profileManager.setActiveProfile(profile)
                        profileManager.setPrivateMode(false)
                        animateTabModeTransition(BrowserTabType.NORMAL)
                    }
                }
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
                        
                        // Refresh profile chips
                        setupProfileChips()
                        
                        // Switch to new profile
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
                        
                        // Refresh profile chips
                        setupProfileChips()
                        
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    },
                    onDelete = {
                        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                        
                        // Switch to default profile if deleting current
                        if (profile.id == browsingModeManager.currentProfile.id) {
                            browsingModeManager.currentProfile = com.prirai.android.nira.browser.profile.BrowserProfile.getDefaultProfile()
                        }
                        
                        profileManager.deleteProfile(profile.id)
                        
                        // Refresh profile chips
                        setupProfileChips()
                        updateTabsDisplay()
                        
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                    }
                )
            }
        }
        
        (binding.root as ViewGroup).addView(composeView)
    }

    private fun setupTabsAdapter() {
        tabsAdapter = TabIslandsVerticalAdapter(
            context = requireContext(),
            onTabClick = { tabId -> selectTab(tabId) },
            onTabClose = { tabId -> closeTab(tabId) },
            onIslandHeaderClick = { islandId -> toggleIslandExpanded(islandId) },
            onIslandLongPress = { islandId -> showIslandOptions(islandId) },
            onUngroupedTabLongPress = { tabId -> showUngroupedTabOptions(tabId) }
        )

        binding.tabsRecyclerView.apply {
            adapter = tabsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = true
            setHasFixedSize(false)
            contentDescription = getString(R.string.tabs_list_description)
        }
    }

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            private var draggedViewHolder: RecyclerView.ViewHolder? = null
            private var dropTargetPosition: Int = -1
            private var lastHighlightedPosition: Int = -1
            private var isDropOnDivider: Boolean = false
            private var isDividerAbove: Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition



                if (fromPosition == -1 || toPosition == -1) {
                    return false
                }

                // Get the items being moved
                val fromItem = tabsAdapter.getItemAt(fromPosition)
                val toItem = tabsAdapter.getItemAt(toPosition)

                // Only allow moving tabs (not headers or collapsed islands)
                if (fromItem !is TabIslandsVerticalAdapter.ListItem.TabInIsland &&
                    fromItem !is TabIslandsVerticalAdapter.ListItem.UngroupedTab
                ) {
                    return false
                }

                // More precise drop target validation - allow all valid handleDrop targets
                val canDrop = when (toItem) {
                    is TabIslandsVerticalAdapter.ListItem.ExpandedIslandHeader -> true
                    is TabIslandsVerticalAdapter.ListItem.CollapsedIsland -> true
                    is TabIslandsVerticalAdapter.ListItem.UngroupedHeader -> true
                    is TabIslandsVerticalAdapter.ListItem.UngroupedTab -> true
                    is TabIslandsVerticalAdapter.ListItem.TabInIsland -> true
                    is TabIslandsVerticalAdapter.ListItem.IslandBottomCap -> true
                    else -> false
                }

                if (canDrop) {
                    dropTargetPosition = toPosition
                    // Add haptic feedback and visual confirmation
                    target.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    target.itemView.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(100)
                        .start()
                    return true
                } else {
                    return false
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not implementing swipe
            }

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                val swipeFlags = 0
                return makeMovementFlags(dragFlags, swipeFlags)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                when (actionState) {
                    ItemTouchHelper.ACTION_STATE_DRAG -> {
                        draggedViewHolder = viewHolder
                        dropTargetPosition = -1
                        viewHolder?.itemView?.apply {
                            // Animate elevation and scale
                            animate()
                                .scaleX(1.05f)
                                .scaleY(1.05f)
                                .alpha(0.9f)
                                .setDuration(100)
                                .start()
                            elevation = 8f * resources.displayMetrics.density
                        }
                    }

                    ItemTouchHelper.ACTION_STATE_IDLE -> {
                        draggedViewHolder?.itemView?.apply {
                            // Reset view
                            animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .alpha(1.0f)
                                .setDuration(100)
                                .start()
                            elevation = 0f
                        }

                        // Handle drag-out-of-island (ungroup) special case
                        if (dropTargetPosition == -999) {
                            handleUngroupDrop(draggedViewHolder)
                            draggedViewHolder = null
                            dropTargetPosition = -1
                            return
                        }

                        // Simple approach: use the last highlighted target position
                        val targetPosition = when {
                            dropTargetPosition != -1 -> dropTargetPosition
                            lastHighlightedPosition != -1 -> lastHighlightedPosition
                            else -> findClosestValidDropTarget(draggedViewHolder)
                        }

                        // Handle drop if we have a valid target
                        if (targetPosition != -1) {
                            if (isDropOnDivider) {
                                handleDividerDrop(draggedViewHolder, targetPosition, isDividerAbove)
                            } else {
                                handleDrop(draggedViewHolder, targetPosition)
                            }
                        }

                        draggedViewHolder = null
                        dropTargetPosition = -1
                        lastHighlightedPosition = -1
                        isDropOnDivider = false
                        isDividerAbove = false

                        // Reset ungrouped header appearance
                        updateUngroupedHeaderDragState(binding.tabsRecyclerView, false)
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                // Reset all child view states
                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    child.background = null
                    child.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }

                // Reset dragged view
                viewHolder.itemView.apply {
                    alpha = 1.0f
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                    translationY = 0f
                    elevation = 0f
                }
            }

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                    // Highlight drop targets and track position
                    val result = highlightDropTargets(recyclerView, viewHolder, dY)
                    lastHighlightedPosition = result.position
                    isDropOnDivider = result.isDivider
                    isDividerAbove = result.isDividerAbove

                    // Set "UNGROUP" mode on ungrouped header during drag
                    updateUngroupedHeaderDragState(recyclerView, true)
                } else {
                    // Reset ungrouped header when not dragging
                    updateUngroupedHeaderDragState(recyclerView, false)
                }

                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(binding.tabsRecyclerView)
    }

    private fun updateUngroupedHeaderDragState(recyclerView: RecyclerView, isDragging: Boolean) {
        try {
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i) ?: continue
                val viewHolder = recyclerView.getChildViewHolder(child) ?: continue

                if (viewHolder is TabIslandsVerticalAdapter.UngroupedHeaderViewHolder) {
                    viewHolder.setDragMode(isDragging)
                    break
                }
            }
        } catch (e: Exception) {
            // Silently handle exception
        }
    }

    data class DropTargetResult(
        val position: Int,
        val isDivider: Boolean,
        val isDividerAbove: Boolean
    )

    private fun highlightDropTargets(
        recyclerView: RecyclerView,
        draggedViewHolder: RecyclerView.ViewHolder,
        dY: Float
    ): DropTargetResult {
        val draggedPosition = draggedViewHolder.bindingAdapterPosition
        if (draggedPosition == -1) return DropTargetResult(-1, false, false)

        val draggedItem = tabsAdapter.getItemAt(draggedPosition)
        if (draggedItem !is TabIslandsVerticalAdapter.ListItem.TabInIsland &&
            draggedItem !is TabIslandsVerticalAdapter.ListItem.UngroupedTab
        ) {
            return DropTargetResult(-1, false, false)
        }

        // Calculate drag position
        val itemView = draggedViewHolder.itemView
        val draggedCenterY = itemView.top + dY + itemView.height / 2f

        // Find and highlight potential drop targets
        var closestTarget: View? = null
        var closestDistance = Float.MAX_VALUE
        var highlightedPosition = -1
        var showTopDivider = false
        var showBottomDivider = false

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val viewHolder = recyclerView.getChildViewHolder(child) ?: continue
            val position = viewHolder.bindingAdapterPosition

            if (position == -1 || position >= tabsAdapter.itemCount || child == itemView) continue

            val item = try {
                tabsAdapter.getItemAt(position)
            } catch (e: Exception) {
                continue
            }

            // Calculate distance from dragged item to this child
            val childCenterY = child.top + child.height / 2f
            val distance = kotlin.math.abs(draggedCenterY - childCenterY)

            // Check if this item can be a drop target
            val canBeTarget = when (item) {
                is TabIslandsVerticalAdapter.ListItem.ExpandedIslandHeader -> true
                is TabIslandsVerticalAdapter.ListItem.CollapsedIsland -> true
                is TabIslandsVerticalAdapter.ListItem.UngroupedHeader -> {
                    // Only ungroup if coming from island
                    draggedItem is TabIslandsVerticalAdapter.ListItem.TabInIsland
                }

                is TabIslandsVerticalAdapter.ListItem.UngroupedTab -> {
                    // Allow any tab to group with ungrouped tabs
                    true
                }

                is TabIslandsVerticalAdapter.ListItem.TabInIsland -> true
                else -> false
            }

            // Reset background first
            try {
                child.background = null
            } catch (e: Exception) {
                // View might be in invalid state
            }

            if (canBeTarget && distance < child.height) {
                // Check for divider-based drop (70-30 split for ungrouped tabs)
                val isUngroupedTab = item is TabIslandsVerticalAdapter.ListItem.UngroupedTab

                if (isUngroupedTab) {
                    val relativeY = draggedCenterY - child.top
                    val topZone = child.height * 0.3f
                    val bottomZone = child.height * 0.7f

                    // Prioritize divider zones over center grouping zone
                    val dividerDistance = if (relativeY < topZone) {
                        relativeY // Distance to top edge
                    } else if (relativeY > bottomZone) {
                        child.height - relativeY // Distance to bottom edge
                    } else {
                        Float.MAX_VALUE // In grouping zone
                    }

                    if (dividerDistance < closestDistance) {
                        closestTarget = child
                        closestDistance = dividerDistance
                        highlightedPosition = position
                        showTopDivider = relativeY < topZone
                        showBottomDivider = relativeY > bottomZone
                    } else if (dividerDistance == Float.MAX_VALUE && distance < closestDistance) {
                        // Grouping zone (center)
                        closestTarget = child
                        closestDistance = distance
                        highlightedPosition = position
                        showTopDivider = false
                        showBottomDivider = false
                    }
                } else {
                    // Non-ungrouped items - use normal distance
                    if (distance < closestDistance) {
                        closestTarget = child
                        closestDistance = distance
                        highlightedPosition = position
                        showTopDivider = false
                        showBottomDivider = false
                    }
                }
            }


        }

        // Reset all backgrounds first
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            if (child != null && child != itemView) {
                try {
                    child.background = null
                    child.foreground = null
                    child.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        // Highlight the closest valid drop target
        closestTarget?.let { target ->
            try {
                if (showTopDivider || showBottomDivider) {
                    // Show divider line at top or bottom edge
                    val dividerColor = ContextCompat.getColor(
                        requireContext(),
                        android.R.color.holo_blue_light
                    )

                    // Create a drawable that shows a thick line at top or bottom
                    val dividerHeight = 8 // dp
                    val dividerHeightPx =
                        (dividerHeight * recyclerView.context.resources.displayMetrics.density).toInt()

                    val drawable = object : android.graphics.drawable.Drawable() {
                        override fun draw(canvas: Canvas) {
                            val paint = android.graphics.Paint().apply {
                                color = dividerColor
                                style = android.graphics.Paint.Style.FILL
                            }

                            if (showTopDivider) {
                                canvas.drawRect(
                                    0f,
                                    0f,
                                    bounds.width().toFloat(),
                                    dividerHeightPx.toFloat(),
                                    paint
                                )
                            } else if (showBottomDivider) {
                                canvas.drawRect(
                                    0f,
                                    bounds.height() - dividerHeightPx.toFloat(),
                                    bounds.width().toFloat(),
                                    bounds.height().toFloat(),
                                    paint
                                )
                            }
                        }

                        override fun setAlpha(alpha: Int) {}
                        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
                    }

                    target.foreground = drawable
                } else {
                    // Full highlight for grouping
                    val highlightColor = ContextCompat.getColor(
                        requireContext(),
                        android.R.color.holo_blue_light
                    )
                    target.setBackgroundColor(highlightColor)

                    // Add visual feedback to indicate successful drop zone
                    target.animate()
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(100)
                        .start()
                }
            } catch (e: Exception) {
                // Silently handle exception
            }
        }

        return DropTargetResult(highlightedPosition, showTopDivider || showBottomDivider, showTopDivider)
    }

    private fun handleDividerDrop(viewHolder: RecyclerView.ViewHolder?, adjacentPosition: Int, isAbove: Boolean) {
        if (viewHolder == null || adjacentPosition == -1) return

        val sourcePosition = viewHolder.bindingAdapterPosition
        if (sourcePosition == -1) return

        val sourceItem = tabsAdapter.getItemAt(sourcePosition)

        // Extract tab ID from source
        val tabId = when (sourceItem) {
            is TabIslandsVerticalAdapter.ListItem.TabInIsland -> sourceItem.tab.id
            is TabIslandsVerticalAdapter.ListItem.UngroupedTab -> sourceItem.tab.id
            else -> return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Remove from current island if in one
            val currentIsland = islandManager.getIslandForTab(tabId)
            if (currentIsland != null) {
                islandManager.removeTabFromIsland(tabId, currentIsland.id)
            }

            // For now, just ungroup the tab - positioning will be handled by the adapter's natural order
            // In the future, you could implement actual reordering logic here
            updateTabsDisplay()
        }
    }

    private fun findClosestValidDropTarget(draggedViewHolder: RecyclerView.ViewHolder?): Int {
        if (draggedViewHolder == null) return -1

        val recyclerView = binding.tabsRecyclerView
        val draggedView = draggedViewHolder.itemView
        val draggedCenterY = draggedView.top + draggedView.height / 2f
        val draggedPosition = draggedViewHolder.bindingAdapterPosition

        if (draggedPosition == -1 || draggedPosition >= tabsAdapter.itemCount) return -1

        var closestPosition = -1
        var closestDistance = Float.MAX_VALUE

        val draggedItem = try {
            tabsAdapter.getItemAt(draggedPosition)
        } catch (e: Exception) {
            return -1
        }

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val viewHolder = recyclerView.getChildViewHolder(child) ?: continue
            val position = viewHolder.bindingAdapterPosition

            if (position == -1 || position >= tabsAdapter.itemCount || child == draggedView) continue

            val item = try {
                tabsAdapter.getItemAt(position)
            } catch (e: Exception) {
                continue
            }

            // Check if this can be a valid drop target
            val canBeTarget = when (item) {
                is TabIslandsVerticalAdapter.ListItem.ExpandedIslandHeader -> true
                is TabIslandsVerticalAdapter.ListItem.CollapsedIsland -> true
                is TabIslandsVerticalAdapter.ListItem.UngroupedHeader -> {
                    // Allow ungrouping from islands
                    draggedItem is TabIslandsVerticalAdapter.ListItem.TabInIsland
                }

                is TabIslandsVerticalAdapter.ListItem.UngroupedTab -> {
                    // Allow any tab to group with ungrouped tabs
                    true
                }

                is TabIslandsVerticalAdapter.ListItem.TabInIsland -> true
                is TabIslandsVerticalAdapter.ListItem.IslandBottomCap -> true
                else -> false
            }

            if (canBeTarget) {
                val childCenterY = child.top + child.height / 2f
                val distance = kotlin.math.abs(draggedCenterY - childCenterY)

                if (distance < child.height && distance < closestDistance) {
                    closestDistance = distance
                    closestPosition = position
                }
            }
        }

        return closestPosition
    }

    private fun handleDrop(viewHolder: RecyclerView.ViewHolder?, targetPosition: Int) {
        if (viewHolder == null || targetPosition == -1) return

        val sourcePosition = viewHolder.bindingAdapterPosition
        if (sourcePosition == -1) return

        val sourceItem = tabsAdapter.getItemAt(sourcePosition)
        val targetItem = tabsAdapter.getItemAt(targetPosition)

        // Extract tab ID from source
        val tabId = when (sourceItem) {
            is TabIslandsVerticalAdapter.ListItem.TabInIsland -> sourceItem.tab.id
            is TabIslandsVerticalAdapter.ListItem.UngroupedTab -> sourceItem.tab.id
            else -> return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            when (targetItem) {
                is TabIslandsVerticalAdapter.ListItem.ExpandedIslandHeader -> {
                    // Add tab to island
                    islandManager.addTabToIsland(tabId, targetItem.island.id)
                    updateTabsDisplay()
                }

                is TabIslandsVerticalAdapter.ListItem.CollapsedIsland -> {
                    // Add tab to collapsed island
                    islandManager.addTabToIsland(tabId, targetItem.island.id)
                    updateTabsDisplay()
                }

                is TabIslandsVerticalAdapter.ListItem.UngroupedHeader -> {
                    // Remove tab from island
                    val currentIsland = islandManager.getIslandForTab(tabId)
                    if (currentIsland != null) {
                        islandManager.removeTabFromIsland(tabId, currentIsland.id)
                        updateTabsDisplay()
                    }
                }

                is TabIslandsVerticalAdapter.ListItem.UngroupedTab -> {
                    // Any tab dropped on ungrouped tab - create island
                    val targetTabId = targetItem.tab.id
                    val sourceTabId = when (sourceItem) {
                        is TabIslandsVerticalAdapter.ListItem.UngroupedTab -> sourceItem.tab.id
                        is TabIslandsVerticalAdapter.ListItem.TabInIsland -> sourceItem.tab.id
                        else -> return@launch
                    }

                    if (sourceTabId != targetTabId) {
                        // If source is in an island, remove it first
                        val currentIsland = islandManager.getIslandForTab(sourceTabId)
                        if (currentIsland != null) {
                            islandManager.removeTabFromIsland(sourceTabId, currentIsland.id)
                        }

                        // Create island with both tabs
                        islandManager.createIsland(listOf(sourceTabId, targetTabId))
                        updateTabsDisplay()
                    }
                }

                is TabIslandsVerticalAdapter.ListItem.TabInIsland -> {
                    // Handle dragging to tabs within islands
                    if (sourceItem is TabIslandsVerticalAdapter.ListItem.UngroupedTab) {
                        // Ungrouped tab dragged to grouped tab - add to that island
                        val targetIsland = islandManager.getIslandForTab(targetItem.tab.id)
                        if (targetIsland != null) {
                            islandManager.addTabToIsland(sourceItem.tab.id, targetIsland.id)
                            updateTabsDisplay()
                        }
                    } else if (sourceItem is TabIslandsVerticalAdapter.ListItem.TabInIsland) {
                        // Tab from one island dragged to another island
                        val sourceIsland = islandManager.getIslandForTab(sourceItem.tab.id)
                        val targetIsland = islandManager.getIslandForTab(targetItem.tab.id)

                        if (sourceIsland != null && targetIsland != null && sourceIsland.id != targetIsland.id) {
                            // Move tab between islands
                            islandManager.removeTabFromIsland(sourceItem.tab.id, sourceIsland.id)
                            islandManager.addTabToIsland(sourceItem.tab.id, targetIsland.id)
                            updateTabsDisplay()
                        }
                    }
                }

                is TabIslandsVerticalAdapter.ListItem.IslandBottomCap -> {
                    // Handle drop on island bottom cap - treat as drop on island
                    val currentIsland = islandManager.getIslandForTab(tabId)
                    if (currentIsland?.id != targetItem.island.id) {
                        if (currentIsland != null) {
                            islandManager.removeTabFromIsland(tabId, currentIsland.id)
                        }
                        islandManager.addTabToIsland(tabId, targetItem.island.id)
                        updateTabsDisplay()
                    }
                }

                else -> {
                    // Handle any other unknown cases
                    // No action needed for unsupported target types
                }
            }
        }
    }


    private fun handleUngroupDropSync(viewHolder: RecyclerView.ViewHolder?) {
        if (viewHolder == null) return

        val sourcePosition = viewHolder.bindingAdapterPosition
        if (sourcePosition == -1) return

        val sourceItem = tabsAdapter.getItemAt(sourcePosition)

        // Extract tab ID from source
        val tabId = when (sourceItem) {
            is TabIslandsVerticalAdapter.ListItem.TabInIsland -> sourceItem.tab.id
            else -> return
        }

        // Remove tab from its current island immediately
        val currentIsland = islandManager.getIslandForTab(tabId)
        if (currentIsland != null) {
            islandManager.removeTabFromIsland(tabId, currentIsland.id)
            viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            updateTabsDisplayImmediate()
        }
    }

    private fun handleUngroupDrop(viewHolder: RecyclerView.ViewHolder?) {
        viewLifecycleOwner.lifecycleScope.launch {
            handleUngroupDropSync(viewHolder)
        }
    }

    private fun animateItemMove(fromPosition: Int, toPosition: Int) {
        // Smooth animation for item movement
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 200
        animator.addUpdateListener {
            binding.tabsRecyclerView.invalidate()
        }
        animator.start()
    }

    private fun setupStoreObserver() {
        // Observe browser store state changes to auto-update tab list
        viewLifecycleOwner.lifecycleScope.launch {
            val store = requireContext().components.store
            var lastTabIds = emptySet<String>()
            var lastSelectedTabId: String? = null

            // Poll for state changes
            while (true) {
                try {
                    val state = store.state
                    val currentTabIds = state.tabs.map { it.id }.toSet()
                    val currentSelectedTabId = state.selectedTabId

                    // Update if tabs changed or selection changed
                    if (currentTabIds != lastTabIds || currentSelectedTabId != lastSelectedTabId) {
                        updateTabsDisplayImmediate()
                        lastTabIds = currentTabIds
                        lastSelectedTabId = currentSelectedTabId
                    }

                    kotlinx.coroutines.delay(100) // Poll every 100ms
                } catch (e: Exception) {
                    // Fragment might be destroyed
                    break
                }
            }
        }
    }

    private fun updateTabsDisplayImmediate() {
        if (!::tabsAdapter.isInitialized || isUpdating) return
        isUpdating = true

        val store = requireContext().components.store.state
        val metadata = com.prirai.android.nira.browser.profile.TabProfileMetadata.getInstance(requireContext())
        
        val tabs = if (configuration.browserTabType == BrowserTabType.NORMAL) {
            // Filter by current profile and non-private
            val currentProfileId = browsingModeManager.currentProfile.id
            store.tabs.filter { tab ->
                !tab.content.private && metadata.getTabProfile(tab.id) == currentProfileId
            }
        } else {
            // Private tabs
            store.tabs.filter { it.content.private }
        }

        // Get all islands from island manager
        val allIslands = islandManager.getAllIslands()

        // Filter islands that have tabs in current mode
        val islandsWithTabs = allIslands.filter { island ->
            island.tabIds.any { tabId -> tabs.any { it.id == tabId } }
        }

        // Get tabs not in any island
        val tabsInIslands = islandsWithTabs.flatMap { it.tabIds }.toSet()
        val ungroupedTabs = tabs.filter { it.id !in tabsInIslands }

        // Update immediately without animation
        tabsAdapter.updateData(
            islands = islandsWithTabs,
            ungroupedTabs = ungroupedTabs,
            allTabs = tabs,
            selectedTabId = store.selectedTabId
        )

        // Show/hide empty state
        if (tabs.isEmpty()) {
            binding.tabsRecyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
        } else {
            binding.tabsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
        }

        isUpdating = false
    }

    private fun updateTabsDisplay() {
        if (!::tabsAdapter.isInitialized) return

        val store = requireContext().components.store.state
        
        val tabs = if (configuration.browserTabType == BrowserTabType.NORMAL) {
            // Filter by current profile using contextId (Gecko-native)
            val currentProfileContextId = "profile_${browsingModeManager.currentProfile.id}"
            android.util.Log.d("TabsBottomSheet", "Filtering tabs for contextId=$currentProfileContextId")
            val filteredTabs = store.tabs.filter { tab ->
                !tab.content.private && tab.contextId == currentProfileContextId
            }
            android.util.Log.d("TabsBottomSheet", "Found ${filteredTabs.size} tabs: ${filteredTabs.map { "id=${it.id}, contextId=${it.contextId}" }}")
            filteredTabs
        } else {
            // Private tabs use contextId = "private"
            android.util.Log.d("TabsBottomSheet", "Filtering private tabs")
            val filteredTabs = store.tabs.filter { tab ->
                tab.content.private && tab.contextId == "private"
            }
            android.util.Log.d("TabsBottomSheet", "Found ${filteredTabs.size} private tabs")
            filteredTabs
        }

        // Get all islands from island manager
        val allIslands = islandManager.getAllIslands()

        // Filter islands that have tabs in current mode
        val islandsWithTabs = allIslands.filter { island ->
            island.tabIds.any { tabId -> tabs.any { it.id == tabId } }
        }

        // Get tabs not in any island
        val tabsInIslands = islandsWithTabs.flatMap { it.tabIds }.toSet()
        val ungroupedTabs = tabs.filter { it.id !in tabsInIslands }

        // Animate the update
        binding.tabsRecyclerView.animate()
            .alpha(0.95f)
            .setDuration(50)
            .withEndAction {
                // Check if binding is still valid (fragment might be detached during animation)
                if (_binding == null) {
                    android.util.Log.w("TabsBottomSheet", "Binding is null, skipping update")
                    return@withEndAction
                }
                
                tabsAdapter.updateData(
                    islands = islandsWithTabs,
                    ungroupedTabs = ungroupedTabs,
                    allTabs = tabs,
                    selectedTabId = store.selectedTabId
                )

                // Scroll to selected tab only on initial load
                if (isInitializing) {
                    store.selectedTabId?.let { selectedId ->
                        val position = tabsAdapter.findPositionOfTab(selectedId)
                        if (position != -1) {
                            binding.tabsRecyclerView.post {
                                (binding.tabsRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                                    position,
                                    100
                                )
                            }
                        }
                    }
                    isInitializing = false
                }

                binding.tabsRecyclerView.animate()
                    .alpha(1f)
                    .setDuration(50)
                    .start()
            }
            .start()

        // Show/hide empty state
        if (tabs.isEmpty()) {
            binding.tabsRecyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
        } else {
            binding.tabsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
        }

        // Update UI state after initialization
        if (isInitializing) {
            isInitializing = false
        }
    }

    private fun animateTabModeTransition(targetMode: BrowserTabType) {
        // Immediately switch mode without animation if fragment is detaching
        if (!isAdded || context == null) {
            return
        }
        
        binding.tabsRecyclerView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                // Double check fragment is still attached before proceeding
                if (isAdded && context != null && view != null) {
                    switchToTabMode(targetMode)
                    binding.tabsRecyclerView.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
            }
            .start()
    }

    private fun switchToTabMode(targetMode: BrowserTabType) {
        // Triple check - animation callbacks can fire after detachment
        if (!isAdded || context == null || view == null) return
        val ctx = context ?: return
        val store = ctx.components.store.state
        val targetTabs = if (targetMode == BrowserTabType.NORMAL) {
            // Filter by current profile using contextId
            val currentProfileContextId = "profile_${browsingModeManager.currentProfile.id}"
            store.tabs.filter { tab ->
                !tab.content.private && tab.contextId == currentProfileContextId
            }
        } else {
            // Private tabs use contextId = "private"
            store.tabs.filter { tab ->
                tab.content.private && tab.contextId == "private"
            }
        }

        browsingModeManager.mode = if (targetMode == BrowserTabType.NORMAL) {
            BrowsingMode.Normal
        } else {
            BrowsingMode.Private
        }
        configuration = Configuration(targetMode)

        if (targetTabs.isEmpty()) {
            addNewTab()
            return
        } else {
            val currentSelectedTabId = store.selectedTabId
            val currentSelectedTab = store.tabs.find { it.id == currentSelectedTabId }
            val targetTab = if (currentSelectedTab != null && targetTabs.contains(currentSelectedTab)) {
                currentSelectedTab
            } else {
                targetTabs.maxByOrNull { it.lastAccess } ?: targetTabs.last()
            }

            requireContext().components.tabsUseCases.selectTab(targetTab.id)
            updateTabsDisplay()
        }
    }

    private fun addNewTab() {
        val isPrivate = configuration.browserTabType == BrowserTabType.PRIVATE
        val homepage = "about:homepage"

        val newTabId = requireContext().components.tabsUseCases.addTab.invoke(
            homepage,
            selectTab = true,
            private = isPrivate
        )
        
        // Associate tab with current profile (unless private)
        if (!isPrivate) {
            val metadata = com.prirai.android.nira.browser.profile.TabProfileMetadata.getInstance(requireContext())
            metadata.setTabProfile(newTabId, browsingModeManager.currentProfile.id)
        }

        dismiss()
    }

    private fun selectTab(tabId: String) {
        val store = requireContext().components.store.state
        val tab = store.tabs.find { it.id == tabId } ?: return

        requireContext().components.tabsUseCases.selectTab(tab.id)

        if (tab.content.private && !browsingModeManager.mode.isPrivate) {
            browsingModeManager.mode = BrowsingMode.Private
        } else if (!tab.content.private && browsingModeManager.mode.isPrivate) {
            val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
            browsingModeManager.mode = BrowsingMode.Normal
        }

        if (tab.content.url == "about:homepage") {
            requireContext().components.sessionUseCases.reload(tab.id)
        } else {
            try {
                val navController = requireActivity().findNavController(R.id.container)
                if (navController.currentDestination?.id != R.id.browserFragment) {
                    if (!navController.popBackStack(R.id.browserFragment, false)) {
                        navController.navigate(R.id.browserFragment)
                    }
                }
            } catch (e: Exception) {
                requireContext().components.sessionUseCases.reload(tab.id)
            }
        }

        dismiss()
    }

    private fun closeTab(tabId: String) {
        val store = requireContext().components.store.state
        val tab = store.tabs.find { it.id == tabId } ?: return

        // Remove from island if it was in one
        val island = islandManager.getIslandForTab(tabId)
        if (island != null) {
            islandManager.removeTabFromIsland(tabId, island.id)
        }

        // Close the tab - store observer will handle UI update automatically
        requireContext().components.tabsUseCases.removeTab(tab.id)
    }

    private fun toggleIslandExpanded(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        // Animate the collapse/expand
        val wasCollapsed = island.isCollapsed
        // Use bottom sheet specific method that allows multiple expanded islands
        islandManager.toggleIslandCollapseBottomSheet(islandId)

        // Use smooth animation
        if (wasCollapsed) {
            // Expanding - animate items appearing
            updateTabsDisplay()
        } else {
            // Collapsing - animate items disappearing
            updateTabsDisplay()
        }
    }

    private fun showIslandOptions(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        val options = arrayOf(
            getString(R.string.tab_island_rename),
            getString(R.string.tab_island_change_color),
            getString(R.string.tab_island_ungroup),
            getString(R.string.tab_island_close_all)
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(if (island.name.isNotBlank()) island.name else getString(R.string.tab_island_name))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameIslandDialog(islandId)
                    1 -> showChangeColorDialog(islandId)
                    2 -> ungroupIsland(islandId)
                    3 -> closeAllTabsInIsland(islandId)
                }
            }
            .show()
    }

    private fun showRenameIslandDialog(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        val input = android.widget.EditText(requireContext()).apply {
            setText(island.name)
            hint = getString(R.string.tab_island_name)
            contentDescription = getString(R.string.tab_island_rename)
            selectAll()
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.tab_island_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString()
                islandManager.renameIsland(islandId, newName)
                updateTabsDisplay()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showChangeColorDialog(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        val colors = TabIsland.DEFAULT_COLORS
        val colorNames = arrayOf(
            "Red", "Green", "Blue", "Orange", "Light Green",
            "Yellow", "Grey", "Pink", "Purple", "Cyan", "Lime", "Deep Orange"
        )

        // Create a custom adapter to show colored circles
        val adapter = object : android.widget.ArrayAdapter<String>(
            requireContext(),
            android.R.layout.select_dialog_item,
            colorNames
        ) {
            override fun getView(
                position: Int,
                convertView: android.view.View?,
                parent: android.view.ViewGroup
            ): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)

                // Create colored indicator
                val colorCircle = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(colors[position])
                    setSize(48, 48)
                }

                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(colorCircle, null, null, null)
                textView.compoundDrawablePadding = 32

                return view
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.tab_island_choose_color)
            .setAdapter(adapter) { _, which ->
                islandManager.changeIslandColor(islandId, colors[which])
                updateTabsDisplay()

                // Trigger toolbar refresh by forcing a store state notification
                val store = requireContext().components.store.state
                val selectedTabId = store.selectedTabId
                if (selectedTabId != null) {
                    requireContext().components.tabsUseCases.selectTab(selectedTabId)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ungroupIsland(islandId: String) {
        islandManager.deleteIsland(islandId)
        updateTabsDisplay()
    }

    private fun closeAllTabsInIsland(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        // Close all tabs in the island
        island.tabIds.forEach { tabId ->
            val store = requireContext().components.store.state
            val tab = store.tabs.find { it.id == tabId }
            if (tab != null) {
                requireContext().components.tabsUseCases.removeTab(tab.id)
            }
        }

        // Island will be cleaned up automatically
        updateTabsDisplay()
    }

    private fun showUngroupedTabOptions(tabId: String) {
        val store = requireContext().components.store.state
        val tab = store.tabs.find { it.id == tabId } ?: return

        val options = arrayOf(
            getString(R.string.tab_island_create),
            getString(R.string.cancel)
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(tab.content.title.ifBlank { getString(R.string.tab_island_name) })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCreateIslandDialog(tabId)
                }
            }
            .show()
    }

    private fun showCreateIslandDialog(tabId: String) {
        val store = requireContext().components.store.state
        val ungroupedTabs = if (configuration.browserTabType == BrowserTabType.NORMAL) {
            store.tabs.filter { !it.content.private }
        } else {
            store.tabs.filter { it.content.private }
        }.filter { tab -> !islandManager.isTabInIsland(tab.id) }

        val tabNames = ungroupedTabs.map { tab ->
            tab.content.title.ifBlank { tab.content.url }
        }.toTypedArray()

        val selectedTabs = mutableSetOf(tabId)
        val checkedItems = BooleanArray(ungroupedTabs.size) { index ->
            ungroupedTabs[index].id == tabId
        }

        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.tab_island_name)
        }

        val dialogLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.tab_island_create)
            .setView(dialogLayout)
            .setMultiChoiceItems(tabNames, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedTabs.add(ungroupedTabs[which].id)
                } else {
                    selectedTabs.remove(ungroupedTabs[which].id)
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selectedTabs.isNotEmpty()) {
                    val name = input.text.toString()
                    islandManager.createIsland(
                        tabIds = selectedTabs.toList(),
                        name = name.ifBlank { null }
                    )
                    updateTabsDisplay()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        _binding = null
    }
}
