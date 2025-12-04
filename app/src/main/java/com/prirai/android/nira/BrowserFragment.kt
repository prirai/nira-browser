package com.prirai.android.nira

import android.view.View
import androidx.core.view.isVisible
import com.prirai.android.nira.browser.toolbar.ToolbarGestureHandler
import com.prirai.android.nira.browser.toolbar.WebExtensionToolbarFeature
import com.prirai.android.nira.toolbar.ContextualBottomToolbar
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

    // Revolutionary Modern Toolbar System
    private var modernToolbarManager: com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager? =
        null


    @Suppress("LongMethod")
    override fun initializeUI(view: View, tab: SessionState) {
        super.initializeUI(view, tab)

        val context = requireContext()
        val components = context.components

        binding.gestureLayout.addGestureListener(
            ToolbarGestureHandler(
                activity = requireActivity(),
                contentLayout = binding.browserLayout,
                tabPreview = binding.tabPreview,
                toolbarLayout = browserToolbarView.view,
                store = components.store,
                selectTabUseCase = components.tabsUseCases.selectTab
            )
        )

        thumbnailsFeature.set(
            feature = BrowserThumbnails(context, binding.engineView, components.store),
            owner = this,
            view = view
        )

        if (UserPreferences(requireContext()).barAddonsList.isNotEmpty()) {
            webExtToolbarFeature.set(
                feature = WebExtensionToolbarFeature(
                    browserToolbarView.view,
                    components.store,
                    UserPreferences(requireContext()).barAddonsList.split(","),
                ), owner = this, view = view
            )
        } else if (UserPreferences(requireContext()).showAddonsInBar) {
            webExtToolbarFeature.set(
                feature = WebExtensionToolbarFeature(
                    browserToolbarView.view, components.store, showAllExtensions = true
                ), owner = this, view = view
            )
        }

        windowFeature.set(
            feature = WindowFeature(
                store = components.store, tabsUseCases = components.tabsUseCases
            ), owner = this, view = view
        )

        // Setup contextual bottom toolbar
        setupContextualBottomToolbar()

        // Tab groups are now handled by the modern toolbar system

        observeTabChangesForToolbar()

        initializeModernToolbarSystem()
    }

    private fun setupContextualBottomToolbar() {
        // Contextual toolbar is managed by ModernToolbarManager
    }

    private fun updateContextualToolbar() {
        if (!isAdded || view == null) return

        try {
            val toolbar = binding.contextualBottomToolbar
            if (toolbar.isVisible) {
                val store = requireContext().components.store.state
                val currentTab = store.tabs.find { it.id == store.selectedTabId }

                val isHomepage = currentTab?.content?.url == "about:homepage"
                
                // Apply same filtering logic for tab count
                val activity = activity as? BrowserActivity
                val isPrivateMode = activity?.browsingModeManager?.mode?.isPrivate ?: false
                val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                val currentProfile = profileManager.getActiveProfile()
                val currentProfileContextId = if (isPrivateMode) {
                    "private"
                } else {
                    "profile_${currentProfile.id}"
                }
                
                val filteredTabsCount = store.tabs.count { tab ->
                    if (tab.content.private != isPrivateMode) {
                        false
                    } else {
                        (tab.contextId == currentProfileContextId) || (tab.contextId == null)
                    }
                }

                toolbar.updateForContext(
                    tab = currentTab,
                    canGoBack = currentTab?.content?.canGoBack ?: false,
                    canGoForward = currentTab?.content?.canGoForward ?: false,
                    tabCount = filteredTabsCount,
                    isHomepage = isHomepage
                )
            }
        } catch (e: Exception) {
            // Ignore errors if fragment is being destroyed
        }
    }


    override fun onResume() {
        super.onResume()
        updateContextualToolbar()
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
                    // Update toolbar when navigation state changes (only if fragment is still attached)
                    if (isAdded && view != null) {
                        updateContextualToolbar()
                    }
                }
            }
        }

        // Also observe tab selection changes
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flowScoped { flow ->
                flow.distinctUntilChangedBy { it.selectedTabId }.collect {
                    // Update toolbar when tab selection changes (only if fragment is still attached)
                    if (isAdded && view != null) {
                        updateContextualToolbar()
                    }
                }
            }
        }
    }


    private fun initializeModernToolbarSystem() {
        val prefs = UserPreferences(requireContext())

        try {

            modernToolbarManager =
                com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager(
                    container = binding.browserLayout,
                    toolbarPosition = if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal) {
                        com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM
                    } else {
                        com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP
                    },
                    fragment = this,
                    lifecycleOwner = viewLifecycleOwner
                )

            // Initialize with our existing browser toolbar and callbacks
            modernToolbarManager?.initialize(
                browserToolbarInstance = browserToolbarView.view,
                onTabSelected = { tabId ->
                    requireContext().components.tabsUseCases.selectTab(tabId)
                },
                onTabClosed = { tabId ->
                    requireContext().components.tabsUseCases.removeTab(tabId)
                },
                onNavigationAction = { action ->
                    handleNavigationAction(action)
                },
                onNewTabInIsland = { islandId ->
                    handleNewTabInIsland(islandId)
                })

            // Hide old separate toolbar components for both top and bottom positions when using modern toolbar
            hideOldToolbarComponents(prefs.toolbarPosition)

            // Start observing tab changes for real-time updates
            observeTabChangesForModernToolbar()

            // Initialize with current tab state
            initializeModernToolbarWithCurrentState()


        } catch (e: Exception) {
            // Fallback to the simple fix
            applySimpleScrollBehaviorFix()
        }
    }

    private fun handleNavigationAction(action: com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction) {
        when (action) {
            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.BACK -> {
                requireContext().components.sessionUseCases.goBack()
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.FORWARD -> {
                requireContext().components.sessionUseCases.goForward()
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.REFRESH -> {
                requireContext().components.sessionUseCases.reload()
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.BOOKMARK -> {
                // TODO: Implement bookmark functionality
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.SHARE -> {
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

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.TAB_COUNT -> {
                // Use the existing TabsBottomSheetFragment - exactly like the working implementation
                try {
                    val tabsBottomSheet =
                        com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                    tabsBottomSheet.show(
                        parentFragmentManager,
                        com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG
                    )
                } catch (e: Exception) {
                    android.util.Log.e("ModernToolbar", "Failed to open tabs bottom sheet", e)
                }
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.MENU -> {
                // Use the EXACT same working approach from the original contextual toolbar
                try {
                    val context = requireContext()
                    val components = context.components

                    // Create a BrowserMenu instance exactly like the working implementation
                    val browserMenu =
                        com.prirai.android.nira.components.toolbar.BrowserMenu(
                            context = context,
                            store = components.store,
                            onItemTapped = { item ->
                                browserInteractor.onBrowserToolbarMenuItemTapped(item)
                            },
                            lifecycleOwner = viewLifecycleOwner,
                            isPinningSupported = components.webAppUseCases.isPinningSupported(),
                            shouldReverseItems = false
                        )

                    // Build and show the menu
                    val menu = browserMenu.menuBuilder.build(context)

                    // Get the modern toolbar system directly from the manager
                    val modernToolbarSystem = modernToolbarManager?.modernToolbarSystem

                    if (modernToolbarSystem != null) {
                        // Find the contextual toolbar within the modern system
                        val contextualToolbar =
                            findContextualToolbarInModernSystem(modernToolbarSystem)
                        val menuButton =
                            contextualToolbar?.findViewById<View>(R.id.menu_button)

                        if (menuButton != null) {
                            // Use the same positioning approach as the working implementation
                            // Create a temporary view above the button for better positioning
                            val tempView = View(context)
                            tempView.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)

                            // Add the temp view to the contextual toolbar parent
                            contextualToolbar.addView(tempView)

                            // Position the temp view above the menu button (same as working implementation)
                            tempView.x = menuButton.x
                            tempView.y = menuButton.y - 60 // 60px above the button

                            // Show menu anchored to temp view
                            menu.show(anchor = tempView)

                            // Clean up temp view after menu interaction
                            tempView.postDelayed({
                                try {
                                    contextualToolbar.removeView(tempView)
                                } catch (e: Exception) {
                                    // Ignore if view was already removed
                                }
                            }, 12000) // 12 seconds cleanup delay

                        } else {
                            // Fallback: show menu anchored to the modern toolbar system itself
                            menu.show(anchor = modernToolbarSystem)
                        }
                    } else {
                        android.util.Log.w(
                            "ModernToolbar", "Modern toolbar system not found, using fallback"
                        )
                        // Ultimate fallback: show menu anchored to the browser layout
                        menu.show(anchor = binding.browserLayout)
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ModernToolbar", "Failed to open menu via modern approach", e
                    )
                    // Final fallback: try the original button click approach
                    try {
                        val menuButton = browserToolbarView.view.findViewById<View>(
                            mozilla.components.browser.toolbar.R.id.mozac_browser_toolbar_menu
                        )
                        menuButton?.performClick()
                    } catch (e2: Exception) {
                        android.util.Log.e("ModernToolbar", "All menu approaches failed", e2)
                    }
                }
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.SEARCH -> {
                // Navigate to search dialog
                val directions = NavGraphDirections.actionGlobalSearchDialog(
                    sessionId = null
                )
                nav(R.id.browserFragment, directions)
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.NEW_TAB -> {
                // Create a new tab with the correct private mode and contextId
                val activity = (requireActivity() as BrowserActivity)
                val isPrivate = activity.browsingModeManager.mode.isPrivate
                val currentProfile = activity.browsingModeManager.currentProfile
                val contextId = if (isPrivate) "private" else "profile_${currentProfile.id}"
                
                requireContext().components.tabsUseCases.addTab(
                    url = "about:homepage",
                    private = isPrivate,
                    contextId = contextId,
                    selectTab = true
                )
                
                // Navigate to Compose home fragment
                try {
                    androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.homeFragment)
                } catch (e: Exception) {
                    android.util.Log.e("BrowserFragment", "Failed to navigate to home", e)
                }
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.BOOKMARKS -> {
                try {
                    // Use the EXACT same logic as the working contextual toolbar bookmark button
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        com.prirai.android.nira.components.toolbar.ToolbarMenu.Item.Bookmarks
                    )
                } catch (e: Exception) {
                }
            }
        }
    }

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

        // Simplified observation without complex flows for now
        viewLifecycleOwner.lifecycleScope.launch {
            val store = requireContext().components.store

            // Simple polling approach
            while (true) {
                try {
                    // Check if fragment is still attached before proceeding
                    if (!isAdded) {
                        break // Exit loop if fragment is detached
                    }
                    
                    val state = store.state

                    // Detect new tabs for auto-grouping
                    val currentTabIds = state.tabs.map { it.id }.toSet()
                    val newTabIds = currentTabIds - lastTabIds

                    // Auto-group new tabs with their parent
                    newTabIds.forEach { newTabId ->
                        val newTab = state.tabs.find { it.id == newTabId }
                        val parentId = newTab?.parentId
                        if (parentId != null) {
                            // Record parent-child relationship for island auto-grouping
                            modernToolbarManager?.recordTabParent(newTabId, parentId)
                            modernToolbarManager?.autoGroupNewTab(newTabId)
                        }
                    }

                    lastTabIds = currentTabIds

                    // Filter tabs by browsing mode AND profile before passing to toolbar
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

                    modernToolbarManager?.updateTabs(filteredTabs, state.selectedTabId)

                    // Update navigation state
                    val selectedTab = state.tabs.find { it.id == state.selectedTabId }
                    selectedTab?.let { tab ->
                        modernToolbarManager?.updateNavigationState(
                            canGoBack = tab.content.canGoBack,
                            canGoForward = tab.content.canGoForward
                        )
                        modernToolbarManager?.updateLoadingState(tab.content.loading)
                    }

                    // Update modern toolbar with current context
                    val currentState = store.state
                    val currentSelectedTab =
                        currentState.tabs.find { it.id == currentState.selectedTabId }
                    val currentUrl = currentSelectedTab?.content?.url ?: ""
                    // Properly detect homepage - both empty URL and about:homepage
                    val currentIsHomepage = currentUrl.isEmpty() || currentUrl == "about:homepage"

                    modernToolbarManager?.updateModernContext(
                        tab = currentSelectedTab,
                        canGoBack = currentSelectedTab?.content?.canGoBack ?: false,
                        canGoForward = currentSelectedTab?.content?.canGoForward ?: false,
                        tabCount = filteredTabs.size,  // Use filtered tabs count
                        isHomepage = currentIsHomepage
                    )

                    kotlinx.coroutines.delay(500) // Update every 500ms
                } catch (e: Exception) {
                    android.util.Log.e("ModernToolbar", "Error observing tabs", e)
                    kotlinx.coroutines.delay(1000)
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

        modernToolbarManager?.updateModernContext(
            tab = selectedTab,
            canGoBack = selectedTab?.content?.canGoBack ?: false,
            canGoForward = selectedTab?.content?.canGoForward ?: false,
            tabCount = filteredTabs.size,  // Use filtered tabs count
            isHomepage = isHomepage
        )

    }

    private fun hideOldToolbarComponents(toolbarPosition: Int) {

        // Hide old separate toolbar components that are no longer needed
        binding.tabGroupBar.visibility = View.GONE
        binding.contextualBottomToolbar.visibility = View.GONE

        // Hide any duplicate or conflicting toolbar components in the coordinator layout
        try {
            val coordinatorLayout = binding.browserLayout
            for (i in 0 until coordinatorLayout.childCount) {
                val child = coordinatorLayout.getChildAt(i)

                // Look for any remaining BrowserToolbar or toolbar-related views that aren't the modern system
                if (child is mozilla.components.browser.toolbar.BrowserToolbar && child != browserToolbarView.view) {
                    child.visibility = View.GONE
                }

                // Check for views with the opposite gravity that might conflict
                val layoutParams = child.layoutParams
                if (layoutParams is androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
                    val isModernToolbar =
                        child is com.prirai.android.nira.components.toolbar.modern.ModernToolbarSystem

                    if (!isModernToolbar) {
                        if (toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal) {
                            // Hide old bottom components
                            if ((layoutParams.gravity and android.view.Gravity.BOTTOM) == android.view.Gravity.BOTTOM) {
                                child.visibility = View.GONE
                            }
                        } else {
                            // Hide old top components
                            if ((layoutParams.gravity and android.view.Gravity.TOP) == android.view.Gravity.TOP || layoutParams.gravity == android.view.Gravity.NO_GRAVITY) {
                                // Don't hide the EngineView or other essential components
                                if (child.javaClass.simpleName.contains("Toolbar") || child.javaClass.simpleName.contains(
                                        "Tab"
                                    )
                                ) {
                                    child.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ModernToolbar", "Error hiding old components", e)
        }

    }

    private fun findContextualToolbarInModernSystem(modernToolbarSystem: com.prirai.android.nira.components.toolbar.modern.ModernToolbarSystem): android.view.ViewGroup? {
        // Search through the modern toolbar system's children to find the contextual toolbar
        for (i in 0 until modernToolbarSystem.childCount) {
            val child = modernToolbarSystem.getChildAt(i)
            if (child is ContextualBottomToolbar) {
                return child
            }
        }
        return null
    }

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
        // Hide modern toolbar systems
        modernToolbarManager?.modernToolbarSystem?.apply {
            collapse()
            isVisible = false
        }
        
        modernToolbarManager?.topToolbarSystem?.apply {
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
        
        // Show and expand modern toolbar systems
        modernToolbarManager?.modernToolbarSystem?.apply {
            isVisible = true
            post {
                requestApplyInsets()
                expand()
                requestLayout()
            }
        }
        
        modernToolbarManager?.topToolbarSystem?.apply {
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
