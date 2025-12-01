package com.prirai.android.nira.components.toolbar.modern

import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.preferences.UserPreferences
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.browser.state.state.SessionState

/**
 * Revolutionary toolbar manager that orchestrates all modern toolbar components
 * for a seamless, beautiful user experience.
 */
class ModernToolbarManager(
    private val container: CoordinatorLayout,
    private val toolbarPosition: ToolbarPosition,
    private val fragment: Fragment,
    private val lifecycleOwner: LifecycleOwner
) {

    var modernToolbarSystem: ModernToolbarSystem? = null
        private set
    private var tabGroupWithSwitcher: TabGroupWithProfileSwitcher? = null
    private var modernContextualToolbar: com.prirai.android.nira.toolbar.ContextualBottomToolbar? =
        null
    private var browserToolbar: BrowserToolbar? = null

    // Controllers and callbacks
    private var onTabSelected: ((String) -> Unit)? = null
    private var onTabClosed: ((String) -> Unit)? = null
    private var onNavigationAction: ((NavigationAction) -> Unit)? = null
    private var onNewTabInIsland: ((String) -> Unit)? = null

    fun initialize(
        browserToolbarInstance: BrowserToolbar,
        onTabSelected: (String) -> Unit,
        onTabClosed: (String) -> Unit,
        onNavigationAction: (NavigationAction) -> Unit,
        onNewTabInIsland: ((String) -> Unit)? = null
    ) {
        this.browserToolbar = browserToolbarInstance
        this.onTabSelected = onTabSelected
        this.onTabClosed = onTabClosed
        this.onNavigationAction = onNavigationAction
        this.onNewTabInIsland = onNewTabInIsland

        when (toolbarPosition) {
            ToolbarPosition.TOP -> initializeTopToolbar()
            ToolbarPosition.BOTTOM -> initializeModernBottomToolbar()
        }

    }

    private fun initializeTopToolbar() {
        // Create TWO modern toolbar systems for hybrid layout:
        // 1. Top system: Address bar only
        // 2. Bottom system: Tab bar + contextual toolbar

        // TOP SYSTEM: Address bar only
        val topToolbarSystem = ModernToolbarSystem(container.context).apply {
            id = generateViewId()
            setToolbarPosition(ModernToolbarSystem.ToolbarPosition.TOP)
        }

        val topParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP
            behavior = ModernScrollBehavior(container.context)
        }

        container.addView(topToolbarSystem, topParams)

        // Add only browser toolbar to top
        browserToolbar?.let { toolbar ->
            (toolbar.parent as? ViewGroup)?.removeView(toolbar)
            topToolbarSystem.addComponent(toolbar, ModernToolbarSystem.ComponentType.ADDRESS_BAR)
        }

        // Connect engine view to top system for proper scroll behavior
        findEngineView(container)?.let { engineView ->
            topToolbarSystem.setEngineView(engineView)
        }

        // BOTTOM SYSTEM: Tab group + contextual toolbar
        modernToolbarSystem = ModernToolbarSystem(container.context).apply {
            id = generateViewId()
            setToolbarPosition(ModernToolbarSystem.ToolbarPosition.BOTTOM)
        }

        val bottomParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            behavior = ModernScrollBehavior(container.context)
        }

        container.addView(modernToolbarSystem, bottomParams)

        // Add tab group and contextual toolbar to bottom
        createEnhancedTabGroupView()
        createModernContextualToolbar()

        // Configure scroll behavior for both systems
        updateScrollBehavior()
    }

    private fun initializeModernBottomToolbar() {
        // Create the revolutionary unified toolbar system
        modernToolbarSystem = ModernToolbarSystem(container.context).apply {
            id = generateViewId()
            setToolbarPosition(ModernToolbarSystem.ToolbarPosition.BOTTOM)
        }

        // Setup proper CoordinatorLayout params with our custom behavior
        val params = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            behavior = ModernScrollBehavior(container.context)
        }

        // Add to container
        container.addView(modernToolbarSystem, params)

        // Create and add components
        createEnhancedTabGroupView()
        addBrowserToolbar()
        createModernContextualToolbar()

        // Configure scroll behavior
        updateScrollBehavior()
    }

    private fun createEnhancedTabGroupView() {
        tabGroupWithSwitcher = TabGroupWithProfileSwitcher(container.context).apply {
            setup(
                onTabSelected = { tabId ->
                    this@ModernToolbarManager.onTabSelected?.invoke(tabId)
                },
                onTabClosed = { tabId ->
                    this@ModernToolbarManager.onTabClosed?.invoke(tabId)
                },
                onNewTabInIsland = { islandId ->
                    this@ModernToolbarManager.onNewTabInIsland?.invoke(islandId)
                },
                onProfileSelected = { profile ->
                    // Switch to the selected profile
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(container.context)
                    profileManager.setActiveProfile(profile)
                    profileManager.setPrivateMode(false)
                    
                    // Update browsing mode manager
                    (container.context as? android.app.Activity)?.let { activity ->
                        (activity as? com.prirai.android.nira.BrowserActivity)?.let { browserActivity ->
                            browserActivity.browsingModeManager.currentProfile = profile
                            browserActivity.browsingModeManager.mode = com.prirai.android.nira.browser.BrowsingMode.Normal
                        }
                    }
                }
            )
            
            // Update profile icon
            val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(container.context)
            updateProfileIcon(profileManager.getActiveProfile())
        }

        modernToolbarSystem?.addComponent(
            tabGroupWithSwitcher!!,
            ModernToolbarSystem.ComponentType.TAB_GROUP
        )
    }

    private fun addBrowserToolbar() {
        browserToolbar?.let { toolbar ->
            // Remove from existing parent first
            val oldParent = toolbar.parent as? ViewGroup
            oldParent?.removeView(toolbar)

            // Hide the old toolbar container to prevent black bar at top when using bottom toolbar
            if (toolbarPosition == ToolbarPosition.BOTTOM) {
                oldParent?.visibility = android.view.View.GONE
                oldParent?.layoutParams?.height = 0
            }

            modernToolbarSystem?.addComponent(
                toolbar,
                ModernToolbarSystem.ComponentType.ADDRESS_BAR
            )
        }
    }

    private fun createModernContextualToolbar() {
        // Check user preference - only create if enabled
        val prefs = UserPreferences(container.context)
        if (!prefs.showContextualToolbar) {
            // Don't create the contextual toolbar if user has disabled it
            return
        }
        
        // COMPLETE migration: Original theming + working functionality
        modernContextualToolbar =
            com.prirai.android.nira.toolbar.ContextualBottomToolbar(container.context).apply {
                // Restore the listener to make buttons actually work
                listener = object :
                    com.prirai.android.nira.toolbar.ContextualBottomToolbar.ContextualToolbarListener {
                    override fun onBackClicked() {
                        onNavigationAction?.invoke(NavigationAction.BACK)
                    }

                    override fun onForwardClicked() {
                        onNavigationAction?.invoke(NavigationAction.FORWARD)
                    }

                    override fun onShareClicked() {
                        onNavigationAction?.invoke(NavigationAction.SHARE)
                    }

                    override fun onSearchClicked() {
                        onNavigationAction?.invoke(NavigationAction.SEARCH)
                    }

                    override fun onNewTabClicked() {
                        onNavigationAction?.invoke(NavigationAction.NEW_TAB)
                    }

                    override fun onTabCountClicked() {
                        onNavigationAction?.invoke(NavigationAction.TAB_COUNT)
                    }

                    override fun onMenuClicked() {
                        onNavigationAction?.invoke(NavigationAction.MENU)
                    }

                    override fun onBookmarksClicked() {
                        onNavigationAction?.invoke(NavigationAction.BOOKMARKS)
                    }
                }
            }

        modernToolbarSystem?.addComponent(
            modernContextualToolbar!!,
            ModernToolbarSystem.ComponentType.CONTEXTUAL
        )
    }

    fun updateTabs(tabs: List<SessionState>, selectedTabId: String?) {
        tabGroupWithSwitcher?.updateTabs(tabs, selectedTabId)
    }

    fun recordTabParent(childTabId: String, parentTabId: String) {
        tabGroupWithSwitcher?.tabGroupView?.recordTabParent(childTabId, parentTabId)
    }

    fun autoGroupNewTab(newTabId: String) {
        tabGroupWithSwitcher?.tabGroupView?.autoGroupNewTab(newTabId)
    }

    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        // The original ContextualBottomToolbar handles its own navigation state
    }

    fun updateLoadingState(isLoading: Boolean) {
        // The original ContextualBottomToolbar handles loading state automatically
    }

    fun updateModernContext(
        tab: mozilla.components.browser.state.state.TabSessionState?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        tabCount: Int,
        isHomepage: Boolean = false
    ) {
        // Use the original ContextualBottomToolbar's updateForContext method
        modernContextualToolbar?.updateForContext(tab, canGoBack, canGoForward, tabCount, isHomepage)
    }

    private fun updateScrollBehavior() {
        val prefs = UserPreferences(container.context)
        val behavior =
            (modernToolbarSystem?.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? ModernScrollBehavior

        if (prefs.hideBarWhileScrolling) {
            modernToolbarSystem?.enableScrolling()
            behavior?.setScrollingEnabled(true)
        } else {
            modernToolbarSystem?.disableScrolling()
            behavior?.setScrollingEnabled(false)
        }
    }

    fun expand() {
        modernToolbarSystem?.expand()
    }

    fun collapse() {
        modernToolbarSystem?.collapse()
    }

    fun enableScrolling() {
        modernToolbarSystem?.enableScrolling()
    }

    fun disableScrolling() {
        modernToolbarSystem?.disableScrolling()
    }

    private fun generateViewId(): Int {
        return android.view.View.generateViewId()
    }

    private fun findEngineView(coordinatorLayout: CoordinatorLayout): mozilla.components.concept.engine.EngineView? {
        for (i in 0 until coordinatorLayout.childCount) {
            val child = coordinatorLayout.getChildAt(i)

            // Direct EngineView
            if (child is mozilla.components.concept.engine.EngineView) {
                return child
            }

            // EngineView in nested layouts
            if (child is ViewGroup) {
                val engineView = searchForEngineView(child)
                if (engineView != null) return engineView
            }
        }
        return null
    }

    private fun searchForEngineView(view: android.view.View): mozilla.components.concept.engine.EngineView? {
        if (view is mozilla.components.concept.engine.EngineView) return view

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val engineView = searchForEngineView(view.getChildAt(i))
                if (engineView != null) return engineView
            }
        }
        return null
    }

    enum class NavigationAction {
        BACK, FORWARD, REFRESH, BOOKMARK, SHARE, TAB_COUNT, MENU,
        SEARCH, NEW_TAB, BOOKMARKS
    }
}
