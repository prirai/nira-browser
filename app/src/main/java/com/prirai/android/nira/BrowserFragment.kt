package com.prirai.android.nira

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.prirai.android.nira.browser.toolbar.ToolbarGestureHandler
import com.prirai.android.nira.browser.toolbar.WebExtensionToolbarFeature
import com.prirai.android.nira.components.toolbar.ToolbarMenu
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.nav
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.state.SessionState
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.feature.tabs.WindowFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper

/**
 * Fragment used for browsing the web within the main app.
 */
@ExperimentalCoroutinesApi
@Suppress("TooManyFunctions", "LargeClass")
class BrowserFragment : BaseBrowserFragment(), UserInteractionHandler {

    private val windowFeature = ViewBoundFeatureWrapper<WindowFeature>()
    private val webExtToolbarFeature = ViewBoundFeatureWrapper<WebExtensionToolbarFeature>()

    // Track last tab IDs for auto-grouping detection
    private var lastTabIds = setOf<String>()

    @Suppress("LongMethod")
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
    }

    private fun observeTabChangesForToolbar() {
        // Observe browser state changes to update toolbar in real-time
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flowScoped { flow ->
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

        // Also observe tab selection changes
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flowScoped { flow ->
                flow.distinctUntilChangedBy { it.selectedTabId }.collect { state ->
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
            
            // For TOP toolbar mode, add bottom components to the activity's root container
            if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal) {
                val bottomContainer = unifiedToolbar?.getBottomComponentsContainer()
                
                bottomContainer?.let { container ->
                    // Add to activity's root container at bottom
                    val activity = requireActivity() as? com.prirai.android.nira.BrowserActivity
                    val rootContainer = activity?.findViewById<ViewGroup>(com.prirai.android.nira.R.id.rootContainer)
                    
                    rootContainer?.let {
                        // Set the NavHostFragment to have weight=1 so it doesn't take all space
                        val navHost = it.getChildAt(it.childCount - 1)
                        (navHost.layoutParams as? android.widget.LinearLayout.LayoutParams)?.weight = 1f
                        
                        val layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            weight = 0f
                        }
                        
                        it.addView(container, layoutParams)
                        container.visibility = View.VISIBLE
                    }
                }
            }
            
            // Set tab selection listener
            unifiedToolbar?.setOnTabSelectedListener { tabId ->
                requireContext().components.tabsUseCases.selectTab(tabId)
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
                    // Show the browser menu (not settings).
                    // Creates a BrowserMenu instance with all standard menu items
                    // (forward, reload, share, bookmarks, settings, etc.)
                    val menuToolbar = com.prirai.android.nira.components.toolbar.BrowserMenu(
                        context = requireContext(),
                        store = requireContext().components.store,
                        onItemTapped = { item ->
                            // Handle menu item selection via the browser interactor
                            browserInteractor.onBrowserToolbarMenuItemTapped(item)
                        },
                        lifecycleOwner = viewLifecycleOwner,
                        isPinningSupported = requireContext().components.webAppUseCases.isPinningSupported(),
                        // Reverse item order for top toolbar (menu drops down), normal order for bottom toolbar (menu pops up)
                        shouldReverseItems = com.prirai.android.nira.preferences.UserPreferences(requireContext()).toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal
                    )
                    // Show menu aligned to the right using custom anchor (same as home page)
                    val prefs = com.prirai.android.nira.preferences.UserPreferences(requireContext())
                    val isBottomToolbar = prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal
                    
                    val decorView = requireActivity().window.decorView as ViewGroup
                    val anchorView = android.view.View(requireContext()).apply {
                        id = android.view.View.generateViewId()
                        layoutParams = android.widget.FrameLayout.LayoutParams(10, 10).apply {
                            gravity = if (isBottomToolbar) {
                                android.view.Gravity.BOTTOM or android.view.Gravity.END
                            } else {
                                android.view.Gravity.TOP or android.view.Gravity.END
                            }
                            
                            if (isBottomToolbar) {
                                bottomMargin = 80
                            } else {
                                topMargin = 80
                            }
                            rightMargin = 20
                        }
                    }
                    
                    decorView.addView(anchorView)
                    anchorView.post {
                        menuToolbar.menuBuilder.build(requireContext()).show(anchor = anchorView)
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
                    val newTabIds = currentTabIds - lastTabIds

                    lastTabIds = currentTabIds

                    // Tabs are updated automatically via store observation in UnifiedToolbar

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

    // Legacy method removed - no longer needed with UnifiedToolbar

    // Legacy method removed - no longer needed with UnifiedToolbar

    private fun applySimpleScrollBehaviorFix() {
        val prefs = UserPreferences(requireContext())

        // Only apply for bottom toolbar position
        if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal && prefs.hideBarWhileScrolling) {


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


}
