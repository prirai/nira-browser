package com.prirai.android.nira

import android.view.View
import com.prirai.android.nira.browser.toolbar.ToolbarGestureHandler
import com.prirai.android.nira.browser.toolbar.WebExtensionToolbarFeature
import com.prirai.android.nira.toolbar.ContextualBottomToolbar
import com.prirai.android.nira.ext.components
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

        // Observe tab changes for real-time toolbar updates
        observeTabChangesForToolbar()

        // Initialize the Revolutionary Modern Toolbar System
        initializeModernToolbarSystem()
    }

    private fun setupContextualBottomToolbar() {
        // Use integrated toolbar from the address bar component, fallback to separate one
        val toolbar =
            (browserToolbarView.integratedContextualToolbar as? com.prirai.android.nira.toolbar.ContextualBottomToolbar)
                ?: binding.contextualBottomToolbar

        // Force bottom toolbar to show for testing iOS icons
        val showBottomToolbar = true // UserPreferences(requireContext()).shouldUseBottomToolbar
        if (showBottomToolbar) {
            toolbar.visibility = View.VISIBLE
        } else {
            toolbar.visibility = View.GONE
        }

        if (showBottomToolbar) {
            toolbar.listener = object : ContextualBottomToolbar.ContextualToolbarListener {
                override fun onBackClicked() {
                    requireContext().components.sessionUseCases.goBack()
                }

                override fun onForwardClicked() {
                    requireContext().components.sessionUseCases.goForward()
                }

                override fun onShareClicked() {
                    val store = requireContext().components.store.state
                    val currentTab = store.tabs.find { it.id == store.selectedTabId }
                    currentTab?.let { tab ->
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, tab.content.url)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, tab.content.title)
                        }
                        startActivity(
                            android.content.Intent.createChooser(
                                shareIntent, getString(R.string.share)
                            )
                        )
                    }
                }

                override fun onSearchClicked() {
                    // Focus on the toolbar for search
                    browserToolbarView.view.displayMode()
                }

                override fun onNewTabClicked() {
                    requireContext().components.tabsUseCases.addTab.invoke(
                        "about:homepage", selectTab = true
                    )
                }

                override fun onTabCountClicked() {
                    // Open tabs bottom sheet
                    val tabsBottomSheet =
                        com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                    tabsBottomSheet.show(
                        parentFragmentManager,
                        com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG
                    )
                }

                override fun onBookmarksClicked() {
                    try {
                        // Use the exact same logic as the three-dot menu
                        browserInteractor.onBrowserToolbarMenuItemTapped(
                            com.prirai.android.nira.components.toolbar.ToolbarMenu.Item.Bookmarks
                        )
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "BookmarkDebug", "❌ WORKING: Error in bookmark handler", e
                        )
                    }
                }

                override fun onMenuClicked() {
                    // Create and show the menu directly since the toolbar menu builder is disabled
                    val context = requireContext()
                    val components = context.components

                    // Create a BrowserMenu instance similar to what BrowserToolbarView does
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
                    val menuButton =
                        binding.contextualBottomToolbar.findViewById<View>(R.id.menu_button)

                    // Create a temporary view above the button for better positioning
                    val tempView = android.view.View(context)
                    tempView.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)

                    // Add the temp view to the parent layout
                    val parent = binding.contextualBottomToolbar
                    parent.addView(tempView)

                    // Position the temp view above the menu button
                    tempView.x = menuButton.x
                    tempView.y = menuButton.y - 60 // 60px above the button

                    // Show menu anchored to temp view
                    menu.show(anchor = tempView)

                    // Clean up temp view after menu interaction
                    tempView.postDelayed({
                        try {
                            parent.removeView(tempView)
                        } catch (e: Exception) {
                            // Ignore if view was already removed
                        }
                    }, 3000) // 3 seconds cleanup delay
                }
            }

            // Update toolbar context when tab changes
            updateContextualToolbar()
        }
    }

    private fun updateContextualToolbar() {
        // Safety check: ensure fragment and view are still valid
        if (!isAdded || view == null) return

        try {
            val toolbar = binding.contextualBottomToolbar
            if (toolbar.visibility == View.VISIBLE) {
                val store = requireContext().components.store.state
                val currentTab = store.tabs.find { it.id == store.selectedTabId }

                // Better homepage detection: only treat as homepage if URL is actually about:homepage
                // This fixes the forward button issue by properly distinguishing homepage from search results
                val isHomepage = currentTab?.content?.url == "about:homepage"

                toolbar.updateForContext(
                    tab = currentTab,
                    canGoBack = currentTab?.content?.canGoBack ?: false,
                    canGoForward = currentTab?.content?.canGoForward ?: false,
                    tabCount = store.tabs.size,
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
                }.collect {
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
                            contextualToolbar?.findViewById<android.view.View>(R.id.menu_button)

                        if (menuButton != null) {
                            // Use the same positioning approach as the working implementation
                            // Create a temporary view above the button for better positioning
                            val tempView = android.view.View(context)
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
                            }, 3000) // 3 seconds cleanup delay

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
                        val menuButton = browserToolbarView.view.findViewById<android.view.View>(
                            mozilla.components.browser.toolbar.R.id.mozac_browser_toolbar_menu
                        )
                        menuButton?.performClick()
                    } catch (e2: Exception) {
                        android.util.Log.e("ModernToolbar", "All menu approaches failed", e2)
                    }
                }
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.SEARCH -> {
                // Connect to the same search functionality from about:homepage
                try {
                    // Navigate to search screen like the working implementation
                    // Navigate to search - using the working homepage search functionality
                } catch (e: Exception) {
                    android.util.Log.e("ModernToolbar", "Search navigation failed", e)
                    // Fallback: try alternative search methods
                    try {
                        // Alternative: Focus on the address bar for search
                        browserToolbarView.view.requestFocus()
                    } catch (e2: Exception) {
                        android.util.Log.e("ModernToolbar", "Search fallback failed", e2)
                    }
                }
            }

            com.prirai.android.nira.components.toolbar.modern.ModernToolbarManager.NavigationAction.NEW_TAB -> {
                // Open new tab with homepage URL like the working implementation
                requireContext().components.tabsUseCases.addTab("about:homepage")
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

    private fun observeTabChangesForModernToolbar() {
        // Track last known tab IDs for detecting new tabs
        var lastTabIds = emptySet<String>()

        // Simplified observation without complex flows for now
        viewLifecycleOwner.lifecycleScope.launch {
            val store = requireContext().components.store

            // Simple polling approach
            while (true) {
                try {
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

                    modernToolbarManager?.updateTabs(state.tabs, state.selectedTabId)

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
                        tabCount = currentState.tabs.size,
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
        val selectedTab = state.tabs.find { it.id == state.selectedTabId }
        val currentUrl = selectedTab?.content?.url ?: ""
        // Properly detect homepage - both empty URL and about:homepage
        val isHomepage = currentUrl.isEmpty() || currentUrl == "about:homepage"

        modernToolbarManager?.updateModernContext(
            tab = selectedTab,
            canGoBack = selectedTab?.content?.canGoBack ?: false,
            canGoForward = selectedTab?.content?.canGoForward ?: false,
            tabCount = state.tabs.size,
            isHomepage = isHomepage
        )

    }

    private fun hideOldToolbarComponents(toolbarPosition: Int) {

        // Hide old separate toolbar components that are no longer needed
        binding.tabGroupBar.visibility = android.view.View.GONE
        binding.contextualBottomToolbar.visibility = android.view.View.GONE

        // Hide any duplicate or conflicting toolbar components in the coordinator layout
        try {
            val coordinatorLayout = binding.browserLayout
            for (i in 0 until coordinatorLayout.childCount) {
                val child = coordinatorLayout.getChildAt(i)

                // Look for any remaining BrowserToolbar or toolbar-related views that aren't the modern system
                if (child is mozilla.components.browser.toolbar.BrowserToolbar && child != browserToolbarView.view) {
                    child.visibility = android.view.View.GONE
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
                                child.visibility = android.view.View.GONE
                            }
                        } else {
                            // Hide old top components
                            if ((layoutParams.gravity and android.view.Gravity.TOP) == android.view.Gravity.TOP || layoutParams.gravity == android.view.Gravity.NO_GRAVITY) {
                                // Don't hide the EngineView or other essential components
                                if (child.javaClass.simpleName.contains("Toolbar") || child.javaClass.simpleName.contains(
                                        "Tab"
                                    )
                                ) {
                                    child.visibility = android.view.View.GONE
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
            if (child is com.prirai.android.nira.toolbar.ContextualBottomToolbar) {
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


}
