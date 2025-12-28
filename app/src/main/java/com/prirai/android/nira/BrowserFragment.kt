package com.prirai.android.nira

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.browser.toolbar.ToolbarGestureHandler
import com.prirai.android.nira.browser.toolbar.WebExtensionToolbarFeature
import com.prirai.android.nira.components.toolbar.ToolbarMenu
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.nav
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.feature.tabs.WindowFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged

/**
 * Fragment used for browsing the web within the main app.
 */
@ExperimentalCoroutinesApi
class BrowserFragment : BaseBrowserFragment(), UserInteractionHandler {

    private val windowFeature = ViewBoundFeatureWrapper<WindowFeature>()
    private val webExtToolbarFeature = ViewBoundFeatureWrapper<WebExtensionToolbarFeature>()

    // Track last tab IDs for auto-grouping detection
    private var lastTabIds = setOf<String>()

    // Toolbar icon for fullscreen toggle

    override fun initializeUI(view: View, tab: SessionState) {
        super.initializeUI(view, tab)

        val context = requireContext()
        val components = context.components

        // Only add gesture handler if unifiedToolbar has a browser toolbar
        unifiedToolbar?.getBrowserToolbar()?.let { toolbar ->
            binding.gestureLayout.addGestureListener(
                ToolbarGestureHandler(
                    activity = requireActivity(),
                    contentLayout = binding.browserLayout,
                    tabPreview = binding.tabPreview,
                    toolbarLayout = toolbar,
                    store = components.store,
                    selectTabUseCase = components.tabsUseCases.selectTab
                )
            )
        }

        thumbnailsFeature.set(
            feature = BrowserThumbnails(context, binding.engineView, components.store),
            owner = this,
            view = view
        )

        // Setup web extension toolbar feature using unifiedToolbar's browser toolbar
        unifiedToolbar?.getBrowserToolbar()?.let { toolbar ->
            if (UserPreferences(requireContext()).barAddonsList.isNotEmpty()) {
                webExtToolbarFeature.set(
                    feature = WebExtensionToolbarFeature(
                        toolbar,
                        components.store,
                        UserPreferences(requireContext()).barAddonsList.split(","),
                    ), owner = this, view = view
                )
            } else if (UserPreferences(requireContext()).showAddonsInBar) {
                webExtToolbarFeature.set(
                    feature = WebExtensionToolbarFeature(
                        toolbar, components.store, showAllExtensions = true
                    ), owner = this, view = view
                )
            }
        }

        windowFeature.set(
            feature = WindowFeature(
                store = components.store, tabsUseCases = components.tabsUseCases
            ), owner = this, view = view
        )

        // Tab groups and contextual toolbar handled by UnifiedToolbar

        observeTabChangesForToolbar()
    }

    override fun onResume() {
        super.onResume()
        // Contextual toolbar updates handled automatically by UnifiedToolbar

        // Ensure tab preview is hidden (fixes doubled content issue after returning from settings)
        binding.tabPreview.visibility = View.GONE

        // Reset EngineView's dynamic toolbar configuration to prevent rendering artifacts
        // No clipping needed - we use margins instead
        binding.engineView.setDynamicToolbarMaxHeight(0)
        
        // Apply AMOLED mode background if enabled
        applyThemeColors()
        
        // Update toolbar styling for private mode
        updateToolbarForPrivateMode()
    }
    
    private fun updateToolbarForPrivateMode() {
        val selectedTab = requireContext().components.store.state.tabs.find { 
            it.id == requireContext().components.store.state.selectedTabId 
        }
        val isPrivate = selectedTab?.content?.private == true
        
        // Use the UnifiedToolbar instance from this fragment
        _unifiedToolbar?.applyPrivateMode(isPrivate)
        
        // Also update status bar color
        updateStatusBarForPrivateMode(isPrivate)
    }
    
    private fun updateStatusBarForPrivateMode(isPrivate: Boolean) {
        val window = requireActivity().window
        if (isPrivate) {
            val purpleColor = android.graphics.Color.parseColor("#6200EE")
            window.statusBarColor = purpleColor
        } else {
            // Reset to theme color
            val typedValue = android.util.TypedValue()
            requireContext().theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurface, 
                typedValue, 
                true
            )
            window.statusBarColor = typedValue.data
        }
    }
    
    private fun applyThemeColors() {
        val bgColor = com.prirai.android.nira.theme.ThemeManager.getBackgroundColor(requireContext())
        
        // Apply to all background views
        binding.gestureLayout.setBackgroundColor(bgColor)
        binding.browserWindow.setBackgroundColor(bgColor)
        binding.browserLayout.setBackgroundColor(bgColor)
        binding.swipeRefresh.setBackgroundColor(bgColor)
    }

    private fun observeTabChangesForToolbar() {
        // Safety check: ensure fragment is attached before accessing context
        if (!isAdded) return
        
        val lruManager = com.prirai.android.nira.browser.tabs.TabLRUManager.getInstance(requireContext())
        
        // Observe browser state changes to update toolbar in real-time
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flowScoped(viewLifecycleOwner) { flow ->
                flow.mapNotNull { state ->
                    // Safety check: ensure fragment is still attached
                    if (!isAdded) return@mapNotNull null
                    val currentTab = state.tabs.find { it.id == state.selectedTabId }
                    currentTab
                }.ifAnyChanged { tab ->
                    arrayOf(
                        tab.content.loading,
                        tab.content.canGoBack,
                        tab.content.canGoForward,
                        tab.content.url,
                        tab.id
                    )
                }.collect { tab ->
                    // Toolbar updates handled automatically by UnifiedToolbar
                }
            }
        }

        // Track tab removals for LRU
        viewLifecycleOwner.lifecycleScope.launch {
            // Safety check before accessing context
            if (!isAdded) return@launch
            
            var lastTabIds = requireContext().components.store.state.tabs.map { it.id }.toSet()
            requireContext().components.store.flowScoped(viewLifecycleOwner) { flow ->
                flow.mapNotNull { state -> 
                    if (!isAdded) return@mapNotNull null
                    state.tabs.map { it.id }.toSet() 
                }.distinctUntilChangedBy { it.hashCode() }
                .collect { currentTabIds: Set<String> ->
                    val removedTabIds = lastTabIds - currentTabIds
                    removedTabIds.forEach { tabId: String ->
                        lruManager.onTabClosed(tabId)
                    }
                }
            }
        }

        // Also observe tab selection changes
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flowScoped(viewLifecycleOwner) { flow ->
                flow.mapNotNull { state ->
                    // Safety check: ensure fragment is still attached
                    if (!isAdded) return@mapNotNull null
                    state
                }.distinctUntilChangedBy { it.selectedTabId }.collect { state ->
                    // Safety check before accessing context
                    if (!isAdded || context == null) return@collect
                    
                    val fragmentContext = requireContext()
                    
                    // Track tab selection in LRU manager
                    state.selectedTabId?.let { tabId ->
                        lruManager.onTabSelected(tabId)
                    }
                    
                    // Update toolbar profile context based on selected tab
                    val selectedTab = state.tabs.find { it.id == state.selectedTabId }
                    selectedTab?.let { tab ->
                        // Get the profile for this tab
                        val tabContextId = tab.contextId
                        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(fragmentContext)
                        
                        // Extract profile ID from contextId (format: "profile_X")
                        val profileId = tabContextId?.removePrefix("profile_")
                        
                        if (profileId != null) {
                            val allProfiles = profileManager.getAllProfiles()
                            val profile = allProfiles.find { it.id == profileId }
                            val currentProfile = profileManager.getActiveProfile()
                            
                            if (profile != null && currentProfile.id != profileId) {
                                // Switch to this profile context in the toolbar
                                profileManager.setActiveProfile(profile)
                                unifiedToolbar?.getTabGroupBar()?.updateProfileIcon(profile)
                            }
                        }
                    }
                    
                    // Navigate to ComposeHomeFragment when about:homepage tab is selected
                    if (isAdded && view != null) {
                        // Toolbar updates handled automatically by UnifiedToolbar

                        // Navigate to ComposeHomeFragment when about:homepage tab is selected
                        // Only do this when switching TO a homepage tab, not when the current tab's URL changes
                        val selectedTab = state.tabs.find { it.id == state.selectedTabId }
                        val navController = try {
                            androidx.navigation.fragment.NavHostFragment.findNavController(this@BrowserFragment)
                        } catch (e: Exception) {
                            null
                        }
                        
                        // Only navigate to home if:
                        // 1. URL is about:homepage
                        // 2. Tab is not loading (stable state)
                        // 3. We're not already on the home fragment
                        // 4. Navigation controller is available
                        if (selectedTab?.content?.url == "about:homepage" &&
                            selectedTab.content.loading == false &&
                            navController != null &&
                            navController.currentDestination?.id != R.id.homeFragment) {
                            try {
                                navController.navigate(R.id.homeFragment)
                            } catch (e: Exception) {
                                // Navigation failed (e.g., already navigating)
                            }
                        }
                    }
                }
            }
        }
    }


    override fun initializeUnifiedToolbar(view: View, tab: SessionState) {
        val prefs = UserPreferences(requireContext())

        try {
            // Create UnifiedToolbar using the interactor from base class
            _unifiedToolbar = com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar.create(
                context = requireContext(),
                parent = binding.browserLayout,
                lifecycleOwner = viewLifecycleOwner,
                interactor = browserInteractor,
                customTabSession = null,
                store = requireContext().components.store
            )

            // Set engine view for scroll behavior
            unifiedToolbar?.setEngineView(binding.engineView)

            // Add layout listener to update web content positioning once toolbar is measured
            // Only for the main toolbar view, not bottom components to avoid jank
            unifiedToolbar?.getToolbarView()?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                requestWebContentPositionUpdate()
            }

            // For TOP toolbar mode, add bottom components directly to fragment layout
            if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal) {
                val bottomContainer = unifiedToolbar?.getBottomComponentsContainer()
                
                bottomContainer?.let { container ->
                    // Add directly to browserLayout (CoordinatorLayout) instead of rootContainer
                    val coordinatorLayout = binding.browserLayout as? androidx.coordinatorlayout.widget.CoordinatorLayout
                    
                    coordinatorLayout?.let {
                        val layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
                            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = android.view.Gravity.BOTTOM
                            // IMPORTANT: Add scroll behavior for hide-on-scroll functionality
                            // Bottom components should hide when scrolling down, show when scrolling up
                            behavior = mozilla.components.ui.widgets.behavior.EngineViewScrollingBehavior(
                                context,
                                null,
                                mozilla.components.ui.widgets.behavior.ViewPosition.BOTTOM
                            )
                        }
                        
                        // Apply Material 3 background with elevation
                        val elevationDp = 3f * resources.displayMetrics.density
                        val elevatedColor = com.google.android.material.elevation.ElevationOverlayProvider(requireContext())
                            .compositeOverlayWithThemeSurfaceColorIfNeeded(elevationDp)
                        container.setBackgroundColor(elevatedColor)
                        
                        // Set high elevation to ensure it stays above engine view
                        container.elevation = 16f * resources.displayMetrics.density
                        
                        // Apply window insets to avoid navigation bar
                        // CRITICAL: Only apply bottom padding for navigation bar, NO extra margins
                        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                            val navigationBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                            // Only bottom padding, no margins
                            view.setPadding(0, 0, 0, navigationBars.bottom)
                            insets
                        }
                        
                        it.addView(container, layoutParams)
                        container.visibility = View.VISIBLE
                        
                        // Disable animation/transition to prevent stutter
                        container.layoutTransition = null
                        
                        // Use hardware layer for better scroll performance
                        container.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        
                        // Request insets to be applied
                        androidx.core.view.ViewCompat.requestApplyInsets(container)
                    }
                }
            }
            
            // Set tab selection listener
            unifiedToolbar?.setOnTabSelectedListener { tabId ->
                requireContext().components.tabsUseCases.selectTab(tabId)
            }
            
            // Set expansion state listener to update web content positioning
            unifiedToolbar?.setOnExpansionStateChangedListener { expanded ->
                requestWebContentPositionUpdate()
            }
            
            // Set toolbar offset listener for smooth margin adjustment (top toolbar only)
            unifiedToolbar?.setOnToolbarOffsetChangedListener { currentOffset, totalHeight ->
                adjustWebContentMarginsForToolbarOffset(currentOffset, totalHeight)
                // For bottom toolbar mode, also adjust fullscreen button
                if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal) {
                    adjustFullscreenButtonPosition(currentOffset, totalHeight)
                }
            }
            
            // For TOP toolbar mode, track bottom components translation to move fullscreen button
            if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal) {
                unifiedToolbar?.getBottomComponentsContainer()?.let { container ->
                    container.viewTreeObserver.addOnPreDrawListener {
                        val fullscreenButton = view?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
                            R.id.fullscreenToggleButton
                        )
                        fullscreenButton?.translationY = container.translationY
                        true
                    }
                }
            }

            // Set contextual toolbar listener
            val contextualListener = object : com.prirai.android.nira.toolbar.ContextualBottomToolbar.ContextualToolbarListener {
                override fun onBackClicked() {
                    requireContext().components.sessionUseCases.goBack()
                }

                override fun onForwardClicked() {
                    requireContext().components.sessionUseCases.goForward()
                }

                override fun onBookmarksClicked() {
                    val bookmarksBottomSheet = com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment.newInstance()
                    bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                }

                override fun onShareClicked() {
                    val state = requireContext().components.store.state
                    val selectedTab = state.tabs.find { it.id == state.selectedTabId }
                    val currentUrl = selectedTab?.content?.url
                    if (!currentUrl.isNullOrBlank()) {
                        val shareIntent = android.content.Intent()
                        shareIntent.action = android.content.Intent.ACTION_SEND
                        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, currentUrl)
                        shareIntent.type = "text/plain"
                        startActivity(android.content.Intent.createChooser(shareIntent, "Share"))
                    }
                }

                override fun onTabCountClicked() {
                    try {
                        val tabsBottomSheet =
                            com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                        tabsBottomSheet.show(
                            parentFragmentManager,
                            com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG
                        )
                    } catch (e: Exception) {
                        // Silently handle tab sheet opening failure
                    }
                }

                override fun onMenuClicked() {
                    // Show custom Material 3 menu
                    // Use try-catch to handle cases where view might not be ready
                    try {
                        if (view != null && isAdded) {
                            showBrowserMenu()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BrowserFragment", "Error showing menu", e)
                    }
                }

                override fun onSearchClicked() {
                    val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = null)
                    nav(R.id.browserFragment, directions)
                }

                override fun onNewTabClicked() {
                    requireContext().components.tabsUseCases.addTab.invoke("about:homepage")
                }
            }

            unifiedToolbar?.setContextualToolbarListener(contextualListener)

            // Start observing tab changes for real-time updates
            observeTabChangesForModernToolbar()

            // Initialize with current tab state
            initializeModernToolbarWithCurrentState()
            
            // Setup floating fullscreen toggle button
            setupFloatingFullscreenButton()

        } catch (e: Exception) {
            // Silently handle toolbar initialization failure and apply simple fix
            applySimpleScrollBehaviorFix()
        }
    }

    // Legacy method - no longer used with UnifiedToolbar
    // Kept for potential backward compatibility


    private fun handleNewTabInIsland(islandId: String) {
        // Navigate to Compose home fragment
        try {
            androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.homeFragment)
        } catch (e: Exception) {
            // Fallback: Create a new tab and automatically add it to the specified island
            val store = requireContext().components.store
            val state = store.state
            val selectedTab = state.tabs.find { it.id == state.selectedTabId }

            // Create new tab with current tab as parent to enable auto-grouping
            val newTabId = requireContext().components.tabsUseCases.addTab.invoke(
                url = "about:homepage",
                selectTab = true,
                parentId = selectedTab?.id
            )

            // Manually add to island since we're creating from plus button
            if (newTabId != null) {
                val islandManager =
                    com.prirai.android.nira.components.toolbar.modern.TabIslandManager.getInstance(requireContext())
                islandManager.addTabToIsland(newTabId, islandId)
            }
        }
    }

    private fun observeTabChangesForModernToolbar() {
        // Track last known tab IDs for detecting new tabs
        var lastTabIds = emptySet<String>()

        // Use proper flow-based observation instead of polling
        viewLifecycleOwner.lifecycleScope.launch {
            val store = requireContext().components.store

            store.flowScoped(viewLifecycleOwner) { flow ->
                flow.mapNotNull { state ->
                    // Check if fragment is still attached before proceeding
                    if (!isAdded) return@mapNotNull null

                    val activity = activity as? BrowserActivity ?: return@mapNotNull null
                    val isPrivateMode = activity.browsingModeManager.mode.isPrivate
                    val profileManager =
                        com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                    val currentProfile = profileManager.getActiveProfile()
                    val currentProfileContextId = if (isPrivateMode) {
                        "private"
                    } else {
                        "profile_${currentProfile.id}"
                    }

                    val filteredTabs = state.tabs.filter { tab ->
                        // Match private mode first
                        if (tab.content.private != isPrivateMode) {
                            false
                        } else {
                            // Include tabs with matching contextId OR guest tabs (null contextId)
                            (tab.contextId == currentProfileContextId) || (tab.contextId == null)
                        }
                    }

                    // Return state data for change detection
                    state to filteredTabs
                }.distinctUntilChangedBy { (state, filteredTabs) ->
                    // Detect changes in:
                    // 1. Tab list (additions/removals)
                    // 2. Selected tab
                    // 3. Tab content (title, url, icon) for any visible tab
                    Triple(
                        filteredTabs.map { it.id },
                        state.selectedTabId,
                        filteredTabs.joinToString("|") { tab ->
                            // Include title, url, icon hash, and loading state
                            "${tab.id}:${tab.content.title}:${tab.content.url}:${tab.content.icon?.hashCode()}:${tab.content.loading}"
                        }
                    )
                }.collect { (state, filteredTabs) ->
                    if (!isAdded) return@collect

                    // Detect new tabs for auto-grouping
                    val currentTabIds = state.tabs.map { it.id }.toSet()
                    currentTabIds - lastTabIds
                    
                    // Update toolbar with current context
                    val currentSelectedTab = state.tabs.find { it.id == state.selectedTabId }
                    val currentUrl = currentSelectedTab?.content?.url ?: ""
                    // Properly detect homepage - both empty URL and about:homepage
                    val currentIsHomepage = currentUrl.isEmpty() || currentUrl == "about:homepage"

                    unifiedToolbar?.updateContextualToolbar(
                        tab = currentSelectedTab,
                        canGoBack = currentSelectedTab?.content?.canGoBack ?: false,
                        canGoForward = currentSelectedTab?.content?.canGoForward ?: false,
                        tabCount = filteredTabs.size,  // Use filtered tabs count
                        isHomepage = currentIsHomepage
                    )
                }
            }
        }
    }

    private fun initializeModernToolbarWithCurrentState() {
        // Initialize the modern toolbar with the current tab state immediately
        val state = requireContext().components.store.state

        // Apply same filtering logic as in observeTabChangesForModernToolbar
        val activity = activity as? BrowserActivity
        val isPrivateMode = activity?.browsingModeManager?.mode?.isPrivate ?: false
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val currentProfile = profileManager.getActiveProfile()
        val currentProfileContextId = if (isPrivateMode) {
            "private"
        } else {
            "profile_${currentProfile.id}"
        }

        val filteredTabs = state.tabs.filter { tab ->
            // Match private mode first
            if (tab.content.private != isPrivateMode) {
                false
            } else {
                // Include tabs with matching contextId OR guest tabs (null contextId)
                (tab.contextId == currentProfileContextId) || (tab.contextId == null)
            }
        }

        val selectedTab = state.tabs.find { it.id == state.selectedTabId }
        val currentUrl = selectedTab?.content?.url ?: ""
        // Properly detect homepage - both empty URL and about:homepage
        val isHomepage = currentUrl.isEmpty() || currentUrl == "about:homepage"

        unifiedToolbar?.updateContextualToolbar(
            tab = selectedTab,
            canGoBack = selectedTab?.content?.canGoBack ?: false,
            canGoForward = selectedTab?.content?.canGoForward ?: false,
            tabCount = filteredTabs.size,  // Use filtered tabs count
            isHomepage = isHomepage
        )

    }

    private var lastTopMargin = -1
    
    /**
     * Adjust web content top margin smoothly as toolbar offset changes
     * Only needed for top toolbar position - provides smooth margin animation
     */
    private fun adjustWebContentMarginsForToolbarOffset(currentOffset: Int, totalHeight: Int) {
        // CRITICAL: Check if view is still attached to prevent crash when switching tabs
        if (!isAdded || view == null) {
            return
        }
        
        val prefs = UserPreferences(requireContext())
        if (prefs.toolbarPosition != com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal) {
            return // Bottom toolbar doesn't need margin adjustment
        }
        
        // For top toolbar: adjust top margin based on toolbar scroll position
        // Do NOT use translationY as it causes black bars when bottom components hide
        val swipeRefreshParams = binding.swipeRefresh.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams ?: return
        
        // Only adjust top margin based on the address bar height (not bottom components)
        val addressBarHeight = resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)
        
        // Calculate how much of the address bar is visible
        val visibleAddressBarHeight = addressBarHeight - currentOffset
        val newTopMargin = maxOf(0, visibleAddressBarHeight)
        
        // OPTIMIZATION: Only update layout if margin actually changed significantly (> 1px)
        // This prevents excessive layout passes and eliminates stutter
        if (kotlin.math.abs(newTopMargin - lastTopMargin) > 1) {
            swipeRefreshParams.topMargin = newTopMargin
            lastTopMargin = newTopMargin
            
            // CRITICAL: Ensure bottom margin is ALWAYS 0 for top toolbar
            // The bottom components float above the engine view, not pushing it up
            swipeRefreshParams.bottomMargin = 0
            
            binding.swipeRefresh.layoutParams = swipeRefreshParams
        }
    }

    /**
     * Setup floating fullscreen toggle button above toolbars
     */
    private fun setupFloatingFullscreenButton() {
        val fullscreenButton = view?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fullscreenToggleButton
        )
        
        fullscreenButton?.let { button ->
            // Position button to attach to bottom toolbars (U-shape)
            positionFullscreenButtonAboveToolbars()
            
            // Set click listener
            button.setOnClickListener {
                toggleManualFullscreen()
            }
            
            // Listen to toolbar position changes to reposition button
            unifiedToolbar?.getToolbarView()?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                positionFullscreenButtonAboveToolbars()
            }
            
            // Apply rounded top corners only (U-shape attachment)
            applyUShapeToButton(button)
        }
    }
    
    /**
     * Apply U-shape styling to button (rounded top, flat bottom for attachment)
     */
    private fun applyUShapeToButton(button: com.google.android.material.floatingactionbutton.FloatingActionButton) {
        // Create shape with only top corners rounded - using medium corners for more compact look
        val cornerRadius = 16f * resources.displayMetrics.density // Medium corner radius
        val shapeModel = com.google.android.material.shape.ShapeAppearanceModel.builder()
            .setTopLeftCorner(com.google.android.material.shape.CornerFamily.ROUNDED, cornerRadius)
            .setTopRightCorner(com.google.android.material.shape.CornerFamily.ROUNDED, cornerRadius)
            .setBottomLeftCorner(com.google.android.material.shape.CornerFamily.ROUNDED, 0f)
            .setBottomRightCorner(com.google.android.material.shape.CornerFamily.ROUNDED, 0f)
            .build()
        
        button.shapeAppearanceModel = shapeModel
    }
    
    private fun positionFullscreenButtonAboveToolbars() {
        val fullscreenButton = view?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fullscreenToggleButton
        ) ?: return
        
        val prefs = UserPreferences(requireContext())
        val layoutParams = fullscreenButton.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        
        // Get navigation bar height
        val windowInsets = androidx.core.view.ViewCompat.getRootWindowInsets(fullscreenButton)
        val navBarHeight = windowInsets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        
        val bottomMargin = if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal) {
            // BOTTOM toolbar: sum all components + nav bar
            val tabGroupBar = unifiedToolbar?.getTabGroupBar()
            val browserToolbar = unifiedToolbar?.getBrowserToolbar()
            val contextualToolbar = unifiedToolbar?.getContextualToolbar()
            
            var totalHeight = navBarHeight
            contextualToolbar?.let { if (prefs.showContextualToolbar && it.isVisible) totalHeight += it.height }
            browserToolbar?.let { totalHeight += it.asView()?.height ?: 0 }
            tabGroupBar?.let { if (prefs.showTabGroupBar && it.isVisible) totalHeight += it.height }
            
            if (totalHeight > navBarHeight) totalHeight else (160 * resources.displayMetrics.density).toInt() + navBarHeight
        } else {
            // TOP toolbar: Calculate total bottom height (bottom components + nav bar)
            // This is the INITIAL position when toolbars are visible
            val tabGroupBar = unifiedToolbar?.getTabGroupBar()
            val contextualToolbar = unifiedToolbar?.getContextualToolbar()
            
            var totalHeight = navBarHeight  // Always include nav bar
            contextualToolbar?.let { if (prefs.showContextualToolbar && it.isVisible) totalHeight += it.height }
            tabGroupBar?.let { if (prefs.showTabGroupBar && it.isVisible) totalHeight += it.height }
            
            // Return full height including nav bar
            totalHeight
        }
        
        layoutParams.bottomMargin = bottomMargin
        fullscreenButton.layoutParams = layoutParams
    }
    
    private fun adjustFullscreenButtonPosition(currentOffset: Int, totalHeight: Int) {
        val fullscreenButton = view?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fullscreenToggleButton
        ) ?: return
        
        val prefs = UserPreferences(requireContext())
        
        if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal) {
            // BOTTOM mode: button follows toolbar translation
            // As toolbar scrolls down (hides), offset is positive, button moves down
            fullscreenButton.translationY = currentOffset.toFloat()
        } else {
            // TOP mode: Bottom components have scroll behavior and hide downward
            // Button should move down with them (same translationY as container)
            val bottomComponentsContainer = unifiedToolbar?.getBottomComponentsContainer()
            if (bottomComponentsContainer != null) {
                // EngineViewScrollingBehavior uses translationY to hide/show
                // Positive translationY means moved down (hidden)
                fullscreenButton.translationY = bottomComponentsContainer.translationY
            } else {
                fullscreenButton.translationY = 0f
            }
        }
    }
    
    private fun getBottomComponentsBaseHeight(): Int {
        val prefs = UserPreferences(requireContext())
        val windowInsets = androidx.core.view.ViewCompat.getRootWindowInsets(view?.findViewById(R.id.fullscreenToggleButton) ?: return 0)
        val navBarHeight = windowInsets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        
        val tabGroupBar = unifiedToolbar?.getTabGroupBar()
        val contextualToolbar = unifiedToolbar?.getContextualToolbar()
        
        var totalHeight = navBarHeight
        contextualToolbar?.let { if (prefs.showContextualToolbar && it.isVisible) totalHeight += it.height }
        tabGroupBar?.let { if (prefs.showTabGroupBar && it.isVisible) totalHeight += it.height }
        
        return totalHeight
    }

    /**
     * Update the fullscreen toggle button icon and visibility
     */
    override fun updateToolbarFullscreenIcon(isFullscreen: Boolean) {
        val fullscreenButton = view?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fullscreenToggleButton
        ) ?: return
        
        if (isFullscreen) {
            // Hide the button when in fullscreen (floating FAB takes over)
            fullscreenButton.animate()
                .alpha(0f)
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(200)
                .withEndAction {
                    fullscreenButton.visibility = View.GONE
                }
                .start()
        } else {
            // Show the button with enter fullscreen icon
            val icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_fullscreen)
            fullscreenButton.setImageDrawable(icon)
            fullscreenButton.visibility = View.VISIBLE
            fullscreenButton.alpha = 0f
            fullscreenButton.scaleX = 0f
            fullscreenButton.scaleY = 0f
            fullscreenButton.animate()
                .alpha(0.5f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()
        }
    }

    // Legacy method removed - no longer needed with UnifiedToolbar

    // Legacy method removed - no longer needed with UnifiedToolbar

    private fun applySimpleScrollBehaviorFix() {
        val prefs = UserPreferences(requireContext())

        // Only apply for bottom toolbar position
        if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal) {


            // The modern toolbar system handles dynamic height automatically
        }
    }

    /**
     * Override fullscreen handling to properly hide/show modern toolbar system.
     * Based on Mozilla's implementation in Fenix BaseBrowserFragment.
     *
     * Note: Swipe-to-fullscreen gestures are handled by the web content (e.g., YouTube player).
     * If swipe gestures don't work consistently, it's typically due to the video player's
     * internal state management, not the browser. The browser correctly responds to fullscreen
     * API calls from the web content.
     */
    override fun onFullScreenModeChanged(inFullScreen: Boolean) {
        if (inFullScreen) {
            // Entering fullscreen - completely hide and collapse all toolbars
            expandBrowserViewForFullscreen()
        } else {
            // Exiting fullscreen - restore toolbars
            collapseBrowserViewFromFullscreen()
        }
    }

    /**
     * Expands the browser view to full screen by hiding all toolbar elements.
     * Follows Mozilla's pattern from Fenix.
     */
    private fun expandBrowserViewForFullscreen() {
        // Hide unified toolbar
        unifiedToolbar?.apply {
            collapse()
            isVisible = false
        }

        // No need to touch swipeRefresh layout params - BaseBrowserFragment handles it
    }

    /**
     * Collapses the browser view from fullscreen by restoring toolbar elements.
     * Follows Mozilla's pattern from Fenix.
     */
    private fun collapseBrowserViewFromFullscreen() {
        if (!webAppToolbarShouldBeVisible) return

        // Show and expand unified toolbar
        unifiedToolbar?.apply {
            isVisible = true
            post {
                requestApplyInsets()
                expand()
                requestLayout()
        }

    }
        // BaseBrowserFragment will call initializeEngineView automatically
}
    private fun showBrowserMenu() {
        val store = requireContext().components.store
        val selectedTab = store.state.tabs.find { it.id == store.state.selectedTabId }
        
        val menuItems = mutableListOf<com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem>()
        
        // History & Bookmarks Pill Row
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.PillRow(
                title1 = getString(R.string.action_history),
                icon1 = R.drawable.ic_baseline_history,
                onClick1 = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.History
                    )
                },
                title2 = getString(R.string.action_bookmarks),
                icon2 = R.drawable.ic_baseline_bookmark,
                onClick2 = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.Bookmarks
                    )
                }
            )
        )
        
        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)
        
        // Print & PDF Pill Row
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.PillRow(
                title1 = getString(R.string.action_print),
                icon1 = R.drawable.ic_baseline_print,
                onClick1 = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.Print
                    )
                },
                title2 = getString(R.string.save_as_pdf),
                icon2 = R.drawable.ic_baseline_pdf,
                onClick2 = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.PDF
                    )
                }
            )
        )
        
        // Add to Homescreen/Install (conditional)
        if (requireContext().components.webAppUseCases.isPinningSupported()) {
            if (requireContext().components.webAppUseCases.isInstallable()) {
                menuItems.add(
                    com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                        id = "install_webapp",
                        title = getString(R.string.install_web_app),
                        iconRes = R.drawable.ic_round_smartphone,
                        onClick = {
                            browserInteractor.onBrowserToolbarMenuItemTapped(
                                ToolbarMenu.Item.InstallWebApp
                            )
                        }
                    )
                )
            } else {
                menuItems.add(
                    com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                        id = "add_to_homescreen",
                        title = getString(R.string.action_add_to_homescreen),
                        iconRes = R.drawable.ic_round_smartphone,
                        onClick = {
                            browserInteractor.onBrowserToolbarMenuItemTapped(
                                ToolbarMenu.Item.AddToHomeScreen
                            )
                        }
                    )
                )
            }
        }
        
        // Open in App (conditional)
        selectedTab?.let { tab ->
            if (requireContext().components.appLinksUseCases.appLinkRedirect(tab.content.url).hasExternalApp()) {
                menuItems.add(
                    com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                        id = "open_in_app",
                        title = getString(R.string.mozac_feature_contextmenu_open_link_in_external_app),
                        iconRes = R.drawable.ic_baseline_open_in_new,
                        onClick = {
                            browserInteractor.onBrowserToolbarMenuItemTapped(
                                ToolbarMenu.Item.OpenInApp
                            )
                        }
                    )
                )
            }
        }
        
        // Desktop Mode Toggle
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Toggle(
                id = "desktop_mode",
                title = getString(R.string.desktop_mode),
                iconRes = R.drawable.ic_desktop,
                isChecked = selectedTab?.content?.desktopMode ?: false,
                onToggle = { checked ->
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.RequestDesktop(checked)
                    )
                }
            )
        )
        
        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)
        
        // Find in Page
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "find_in_page",
                title = getString(R.string.mozac_feature_findindpage_input),
                iconRes = R.drawable.mozac_ic_search_24,
                onClick = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.FindInPage
                    )
                }
            )
        )
        
        // Add to Bookmarks
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "add_to_bookmarks",
                title = "Add to Bookmarks",
                iconRes = R.drawable.ic_baseline_bookmark_add,
                onClick = {
                    selectedTab?.let { tab ->
                        val title = tab.content.title.ifEmpty { tab.content.url }
                        val url = tab.content.url
                        
                        val dialog = com.prirai.android.nira.browser.bookmark.ui.AddBookmarkSiteDialog(
                            requireActivity(), 
                            title, 
                            url
                        )
                        dialog.setOnClickListener { _, _ ->
                            // Save changes to persistent storage
                            com.prirai.android.nira.browser.bookmark.repository.BookmarkManager.getInstance(requireContext()).save()
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Bookmark added",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        dialog.show()
                    }
                }
            )
        )
        
        // Add to Favorites (Shortcuts)
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "add_to_favorites",
                title = "Add to Favorites",
                iconRes = R.drawable.ic_baseline_star_24,
                onClick = {
                    selectedTab?.let { tab ->
                        val title = tab.content.title.ifEmpty { tab.content.url }
                        val url = tab.content.url
                        
                        val dialog = com.prirai.android.nira.browser.shortcuts.AddShortcutDialogFragment.newInstance(url, title)
                        dialog.show(parentFragmentManager, com.prirai.android.nira.browser.shortcuts.AddShortcutDialogFragment.TAG)
                    }
                }
            )
        )
        
        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)
        
        // Extensions
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "extensions",
                title = getString(R.string.extensions),
                iconRes = R.drawable.mozac_ic_extension_24,
                onClick = {
                    val extensionsBottomSheet = com.prirai.android.nira.addons.ExtensionsBottomSheetFragment.newInstance()
                    extensionsBottomSheet.show(parentFragmentManager, com.prirai.android.nira.addons.ExtensionsBottomSheetFragment.TAG)
                }
            )
        )
        
        // Settings
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "settings",
                title = getString(R.string.settings),
                iconRes = R.drawable.ic_round_settings,
                onClick = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.Settings
                    )
                }
            )
        )
        
        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)
        
        // New Tab & Private Tab above toolbar
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "new_tab",
                title = getString(R.string.mozac_browser_menu_new_tab),
                iconRes = R.drawable.mozac_ic_tab_new_24,
                onClick = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.NewTab
                    )
                }
            )
        )
        
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "new_private_tab",
                title = getString(R.string.mozac_browser_menu_new_private_tab),
                iconRes = R.drawable.ic_incognito,
                onClick = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.NewPrivateTab
                    )
                }
            )
        )
        
        menuItems.add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)
        
        // Toolbar Row at bottom
        menuItems.add(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.ToolbarRow(
                onBackClick = {
                    requireContext().components.sessionUseCases.goBack()
                },
                onForwardClick = {
                    requireContext().components.sessionUseCases.goForward()
                },
                onReloadClick = {
                    requireContext().components.sessionUseCases.reload()
                },
                onShareClick = {
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        ToolbarMenu.Item.Share
                    )
                },
                backEnabled = selectedTab?.content?.canGoBack ?: false,
                forwardEnabled = selectedTab?.content?.canGoForward ?: false
            )
        )
        
        // Find anchor for menu
        // When contextual toolbar is enabled, prioritize menu button from it, not from address bar
        val prefs = UserPreferences(requireContext())
        val menuButton = view?.findViewById<android.widget.ImageButton>(R.id.menu_button)
        val toolbarView = unifiedToolbar?.getToolbarView()
        
        // Use menu button if contextual toolbar is showing, otherwise use toolbar view
        val anchor = if (prefs.showContextualToolbar && menuButton != null) {
            menuButton
        } else {
            toolbarView ?: menuButton ?: view
        }
        
        // Determine menu position: prefer bottom UNLESS toolbar is at top AND contextual toolbar is disabled
        val preferBottom = prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal ||
                          prefs.showContextualToolbar
        
        anchor?.let {
            com.prirai.android.nira.components.menu.Material3BrowserMenu(
                requireContext(),
                menuItems
            ).show(it, preferBottom)
        }
    }

    override fun onBackPressed(): Boolean {
        // First, try the default back handling (reader mode, find in page, fullscreen, etc.)
        val handled = super.onBackPressed()
        
        if (!handled) {
            // If nothing handled the back press, check if we can go back in browser history
            val store = requireContext().components.store
            val selectedTab = store.state.tabs.find { it.id == store.state.selectedTabId }
            
            // If we can't go back in history, navigate to home fragment
            if (selectedTab != null && !selectedTab.content.canGoBack) {
                try {
                    val navController = androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    // Only navigate if we're not already on home fragment
                    if (navController.currentDestination?.id != R.id.homeFragment) {
                        // Signal to home fragment to prevent auto-navigation back
                        com.prirai.android.nira.browser.home.ComposeHomeFragment.navigateToHome()
                        navController.navigate(R.id.homeFragment)
                        return true
                    }
                } catch (e: Exception) {
                    // Navigation failed, let system handle it
                }
            }
        }
        
        return handled
    }
}
