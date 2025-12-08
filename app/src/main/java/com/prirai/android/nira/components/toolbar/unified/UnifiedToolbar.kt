package com.prirai.android.nira.components.toolbar.unified

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.components.toolbar.BrowserToolbarView
import com.prirai.android.nira.components.toolbar.BrowserToolbarViewInteractor
import com.prirai.android.nira.components.toolbar.ToolbarIntegration
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.components.toolbar.modern.ModernToolbarSystem
import com.prirai.android.nira.components.toolbar.modern.TabGroupWithProfileSwitcher
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.toolbar.ContextualBottomToolbar
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.CustomTabSessionState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.toolbar.ScrollableToolbar
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.android.content.res.resolveAttribute
import com.prirai.android.nira.components.toolbar.modern.ModernScrollBehavior

/**
 * UnifiedToolbar - Centralized toolbar component that unifies tab bar, address bar, and contextual toolbar.
 *
 * Uses the proper components:
 * - TabGroupWithProfileSwitcher: Enhanced tab bar with profile pill
 * - BrowserToolbar: Address bar for URL/search
 * - ContextualBottomToolbar: Context-aware action buttons
 * 
 * Wraps ModernToolbarSystem which provides:
 * - Material 3 theming with elevation overlay
 * - Proper scroll behavior
 * - Edge-to-edge window insets
 * - Top/bottom positioning support
 */
class UnifiedToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : android.widget.FrameLayout(context, attrs, defStyleAttr), ScrollableToolbar {

    // Preferences
    private val prefs = UserPreferences(context)

    // The actual toolbar system
    private val toolbarSystem: ModernToolbarSystem = ModernToolbarSystem(context, attrs, defStyleAttr)

    // Toolbar components
    private var tabGroupBar: TabGroupWithProfileSwitcher? = null
    private var addressBarContainer: ViewGroup? = null
    private var browserToolbar: BrowserToolbar? = null
    private var browserToolbarView: BrowserToolbarView? = null
    private var contextualToolbar: ContextualBottomToolbar? = null
    
    // Store reference for tab updates
    private var browserStore: BrowserStore? = null
    
    // Tab selection callback
    private var onTabSelectedCallback: ((String) -> Unit)? = null
    
    // Reload/Stop button integration
    private var reloadStopIntegration: com.prirai.android.nira.integration.ReloadStopButtonIntegration? = null
    
    // Contextual toolbar listener reference (used when contextual toolbar is disabled)
    private var contextualToolbarListener: ContextualBottomToolbar.ContextualToolbarListener? = null

    // Configuration
    private var showTabGroupBar: Boolean = prefs.showTabGroupBar
    private var showContextualToolbar: Boolean = prefs.showContextualToolbar
    
    // Bottom components container reference (for TOP toolbar mode)
    private var bottomComponentsContainer: ViewGroup? = null
    private var shouldHideBottomComponents = false

    init {
        // Allow children to draw outside bounds (for elevated tab pills during drag)
        clipChildren = false
        clipToPadding = false
        
        // Add toolbarSystem as child
        addView(toolbarSystem, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))
        
        // Set toolbar position from preferences
        val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
            ModernToolbarSystem.ToolbarPosition.BOTTOM
        } else {
            ModernToolbarSystem.ToolbarPosition.TOP
        }
        toolbarSystem.setToolbarPosition(toolbarPos)
        shouldHideBottomComponents = (toolbarPos == ModernToolbarSystem.ToolbarPosition.TOP)
    }
    
    // Forward ScrollableToolbar methods to toolbarSystem and behavior
    override fun enableScrolling() {
        toolbarSystem.enableScrolling()
        (layoutParams as? CoordinatorLayout.LayoutParams)?.let {
            (it.behavior as? ModernScrollBehavior)?.enableScrolling()
        }
    }
    
    override fun disableScrolling() {
        toolbarSystem.disableScrolling()
        (layoutParams as? CoordinatorLayout.LayoutParams)?.let {
            (it.behavior as? ModernScrollBehavior)?.disableScrolling()
        }
    }
    
    override fun expand() {
        toolbarSystem.expand()
        // Also show bottom components if in TOP mode
        if (shouldHideBottomComponents) {
            bottomComponentsContainer?.let { container ->
                container.visibility = View.VISIBLE
                container.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
        }
    }
    
    override fun collapse() {
        toolbarSystem.collapse()
        // Also hide bottom components if in TOP mode
        if (shouldHideBottomComponents) {
            bottomComponentsContainer?.let { container ->
                // Set GONE immediately to prevent space reservation
                container.visibility = View.GONE
                // Still animate for smooth visual effect (though invisible)
                val height = container.height.toFloat()
                container.animate()
                    .translationY(height)
                    .alpha(0f)
                    .setDuration(200)
                    .start()
            }
        }
    }

    /**
     * Setup the unified toolbar with all components.
     *
     * @param parent The parent container (typically a CoordinatorLayout)
     * @param lifecycleOwner The lifecycle owner for observing state changes
     * @param interactor Handles toolbar interactions
     * @param customTabSession Optional custom tab session
     * @param store The browser store for tab observation
     * @return The configured UnifiedToolbar instance
     */
    fun setup(
        parent: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        interactor: BrowserToolbarViewInteractor,
        customTabSession: CustomTabSessionState? = null,
        store: BrowserStore
    ): UnifiedToolbar {

        // Store reference
        this.browserStore = store

        // Add components in order based on toolbar position
        val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
            ModernToolbarSystem.ToolbarPosition.BOTTOM
        } else {
            ModernToolbarSystem.ToolbarPosition.TOP
        }

        when (toolbarPos) {
            ModernToolbarSystem.ToolbarPosition.TOP -> {
                // TOP: Address Bar only in the top toolbar system
                // Tab bar and contextual toolbar will be in a separate bottom container
                addAddressBar(parent, lifecycleOwner, interactor, customTabSession, store)
                // Create bottom components (they will be retrieved separately)
                addBottomComponentsForTopToolbar(store, lifecycleOwner)
                // Add browser actions to address bar if contextual toolbar is disabled
                if (!showContextualToolbar) {
                    addBrowserActionsToAddressBar()
                }
            }
            ModernToolbarSystem.ToolbarPosition.BOTTOM -> {
                // BOTTOM: Contextual Toolbar -> Address Bar -> Tab Bar (all together)
                addContextualToolbar()
                addAddressBar(parent, lifecycleOwner, interactor, customTabSession, store)
                addTabGroupBar(store, lifecycleOwner)
                // Add browser actions to address bar if contextual toolbar is disabled
                if (!showContextualToolbar) {
                    addBrowserActionsToAddressBar()
                }
            }
        }

        // Setup scroll behavior if enabled
        if (prefs.hideBarWhileScrolling) {
            setupScrollBehavior(parent)
        }

        return this
    }

    /**
     * Creates the tab group bar component (TabGroupWithProfileSwitcher).
     * This is separated from addTabGroupBar to allow creating the component
     * without immediately adding it to the toolbar system (needed for TOP toolbar mode).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun createTabGroupBar(
        store: BrowserStore,
        lifecycleOwner: LifecycleOwner
    ) {
        if (!showTabGroupBar) {
            return
        }

        tabGroupBar = TabGroupWithProfileSwitcher(context).apply {
            // Material 3 theming is already applied by TabGroupWithProfileSwitcher
            
            // Setup with all required callbacks for tab interaction and profile management
            setup(
                onTabSelected = { tabId ->
                    // Delegate to external callback set via setOnTabSelectedListener()
                    onTabSelectedCallback?.invoke(tabId)
                },
                onTabClosed = { tabId ->
                    // Close the tab using browser's tab management system
                    context.components.tabsUseCases.removeTab(tabId)
                },
                onIslandRenamed = { islandId, newName ->
                    // Island renaming is handled by the tab group system
                },
                onNewTabInIsland = { islandId ->
                    // Create a new tab and add it to the specified island (tab group)
                    // Get current profile info for contextId
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    val currentProfile = profileManager.getActiveProfile()
                    val isPrivateMode = profileManager.isPrivateMode()
                    val contextId = if (isPrivateMode) "private" else "profile_${currentProfile.id}"
                    
                    val newTabId = context.components.tabsUseCases.addTab(
                        url = "about:homepage",
                        selectTab = true,
                        private = isPrivateMode,
                        contextId = contextId
                    )

                    // Add the new tab to the island/group using UnifiedTabGroupManager
                    lifecycleOwner.lifecycleScope.launch {
                        val unifiedManager = com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager.getInstance(context)
                        unifiedManager.addTabToGroup(newTabId, islandId)
                    }
                },
                onProfileSelected = { profile ->
                    // Switch to the selected profile and reload to apply changes
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    profileManager.setActiveProfile(profile)
                    profileManager.setPrivateMode(false) // Switching to a profile exits private mode
                    
                    // Manually trigger tab bar update with filtered tabs
                    val expectedContextId = "profile_${profile.id}"
                    val state = store.state
                    val filteredTabs = state.tabs.filter { tab ->
                        tab.content.private == false && tab.contextId == expectedContextId
                    }
                    
                    tabGroupBar?.updateTabs(filteredTabs, state.selectedTabId)
                    tabGroupBar?.updateProfileIcon(profile)
                    
                    context.components.sessionUseCases.reload()
                },
                onPrivateModeSelected = {
                    // Private mode was already set by the menu handler, just update the UI
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    val isPrivateMode = profileManager.isPrivateMode()
                    val currentProfile = profileManager.getActiveProfile()
                    
                    // Manually trigger tab bar update with filtered tabs
                    val expectedContextId = if (isPrivateMode) {
                        "private"
                    } else {
                        "profile_${currentProfile.id}"
                    }
                    val state = store.state
                    val filteredTabs = state.tabs.filter { tab ->
                        tab.content.private == isPrivateMode && tab.contextId == expectedContextId
                    }
                    
                    tabGroupBar?.updateTabs(filteredTabs, state.selectedTabId)
                    
                    context.components.sessionUseCases.reload()
                }
            )
        }

        // Observe browser store and filter tabs by current profile and private mode.
        // This ensures that:
        // 1. Each profile only sees its own tabs (contextId matches "profile_{id}")
        // 2. Private mode only shows private tabs (contextId = "private")
        // 3. Tab bar updates automatically when profile switches or tabs change
        lifecycleOwner.lifecycleScope.launch {
            store.flowScoped { flow ->
                flow.collect { state ->
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    val isPrivateMode = profileManager.isPrivateMode()
                    val currentProfile = profileManager.getActiveProfile()
                    
                    // Determine expected contextId based on current browsing mode
                    // Private mode uses "private", normal mode uses "profile_{id}"
                    val expectedContextId = if (isPrivateMode) {
                        "private"
                    } else {
                        "profile_${currentProfile.id}"
                    }
                    
                    // Filter tabs to only show those belonging to current profile/mode
                    val filteredTabs = state.tabs.filter { tab ->
                        tab.content.private == isPrivateMode && tab.contextId == expectedContextId
                    }
                    
                    tabGroupBar?.updateTabs(filteredTabs, state.selectedTabId)
                }
            }
        }
    }
    
    /**
     * Adds the tab group bar component (TabGroupWithProfileSwitcher) to the unified toolbar.
     *
     * This method:
     * 1. Creates TabGroupWithProfileSwitcher with all necessary callbacks
     * 2. Sets up profile switching and private mode toggling
     * 3. Observes browser store to filter tabs by current profile and private mode
     * 4. Adds the component to the ModernToolbarSystem
     *
     * Tab filtering logic:
     * - Only shows tabs matching current profile (contextId = "profile_{id}")
     * - In private mode, only shows tabs with contextId = "private"
     * - Ensures each profile sees only its own tabs
     *
     * @param store The browser store to observe for tab changes
     * @param lifecycleOwner Lifecycle owner to scope the store observation
     */
    private fun addTabGroupBar(
        store: BrowserStore,
        lifecycleOwner: LifecycleOwner
    ) {
        createTabGroupBar(store, lifecycleOwner)
        if (tabGroupBar != null) {
            toolbarSystem.addComponent(tabGroupBar!!, ModernToolbarSystem.ComponentType.TAB_GROUP)
        }
    }

    /**
     * Add address bar component - using BrowserToolbarView for proper integration
     */
    private fun addAddressBar(
        parent: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        interactor: BrowserToolbarViewInteractor,
        customTabSession: CustomTabSessionState?,
        store: BrowserStore
    ) {
        // Create a temporary container for BrowserToolbarView
        val tempContainer = android.widget.FrameLayout(context)
        
        val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
            ToolbarPosition.BOTTOM
        } else {
            ToolbarPosition.TOP
        }
        
        // Use BrowserToolbarView to get properly configured toolbar
        browserToolbarView = BrowserToolbarView(
            container = tempContainer,
            toolbarPosition = toolbarPos,
            interactor = interactor,
            customTabSession = customTabSession,
            lifecycleOwner = lifecycleOwner
        )
        
        // Extract the toolbar from the temporary container
        browserToolbar = browserToolbarView!!.view
        
        // Remove from temp container's parent if it has one
        (browserToolbar?.parent as? ViewGroup)?.removeView(browserToolbar)
        
        // Add directly to toolbar system
        toolbarSystem.addComponent(browserToolbar!!, ModernToolbarSystem.ComponentType.ADDRESS_BAR)
        
        // Start the toolbar integration to connect it to browser state
        browserToolbarView!!.toolbarIntegration.start()
        
        // Stop integration when lifecycle is destroyed
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                browserToolbarView?.toolbarIntegration?.stop()
            }
        })
        
        // Add reload/stop button integration
        setupReloadStopButton(store, lifecycleOwner)
    }
    
    /**
     * Setup reload/stop button integration
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun setupReloadStopButton(store: BrowserStore, lifecycleOwner: LifecycleOwner) {
        val toolbar = browserToolbar ?: return
        
        reloadStopIntegration = com.prirai.android.nira.integration.ReloadStopButtonIntegration(
            context = context,
            store = store,
            toolbar = toolbar,
            onReload = {
                context.components.sessionUseCases.reload()
            },
            onStop = {
                context.components.sessionUseCases.stopLoading()
            }
        )
        
        // Start the integration to observe loading state
        reloadStopIntegration?.start()
        
        // Stop when lifecycle is destroyed
        lifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                reloadStopIntegration?.stop()
                reloadStopIntegration = null
            }
        })
    }

    /**
     * Add bottom components (tab bar and contextual) when toolbar is at TOP
     * This creates a separate bottom container for these components
     */
    private fun addBottomComponentsForTopToolbar(store: BrowserStore, lifecycleOwner: LifecycleOwner) {
        // For top toolbar mode, create components but don't add them to the toolbar system
        // They will be retrieved via getBottomComponentsContainer() and positioned separately
        
        // Always create contextual toolbar for the container (it might be hidden)
        contextualToolbar = ContextualBottomToolbar(context).apply {
            // No need to set background - it already applies Material 3 theming
            if (!showContextualToolbar) {
                visibility = View.GONE
            }
            
            // Apply window insets to add padding for navigation bar
            // Now that we're using CoordinatorLayout, this won't cause container size issues
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
                insets
            }
        }
        
        // Create tab group bar if enabled
        if (showTabGroupBar) {
            createTabGroupBar(store, lifecycleOwner)
        }
        
    }

    /**
     * Add contextual toolbar component (ContextualBottomToolbar)
     */
    private fun addContextualToolbar() {
        if (!showContextualToolbar) {
            // When contextual toolbar is disabled, don't create it
            // Buttons will be added to address bar in setup()
            return
        }

        contextualToolbar = ContextualBottomToolbar(context).apply {
            // No need to set background - it already applies Material 3 theming
        }

        toolbarSystem.addComponent(contextualToolbar!!, ModernToolbarSystem.ComponentType.CONTEXTUAL)
    }
    
    // Store tab count view reference to update the count
    private var tabCountBadgeView: android.widget.TextView? = null
    
    /**
     * Add browser actions (tab count, menu) to address bar when contextual toolbar is disabled
     */
    private fun addBrowserActionsToAddressBar() {
        val toolbar = browserToolbar ?: return
        
        // Add menu button first
        val menuAction = mozilla.components.browser.toolbar.BrowserToolbar.Button(
            imageDrawable = androidx.core.content.ContextCompat.getDrawable(context, com.prirai.android.nira.R.drawable.ic_more_vert)!!,
            contentDescription = "Menu",
            listener = {
                contextualToolbarListener?.onMenuClicked()
            }
        )
        toolbar.addBrowserAction(menuAction)
        
        // Create custom tab count button - exact replica of ContextualBottomToolbar's tab count
        val tabCountContainer = android.widget.FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                (48 * context.resources.displayMetrics.density).toInt(),
                (48 * context.resources.displayMetrics.density).toInt()
            )
            val padding = (4 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            
            // Add ripple effect (unbounded for circular feedback)
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            
            // Add square background (same as ContextualBottomToolbar)
            val backgroundView = android.widget.ImageView(context).apply {
                setImageDrawable(androidx.core.content.ContextCompat.getDrawable(context, com.prirai.android.nira.R.drawable.tab_number_background))
                val size = (28 * context.resources.displayMetrics.density).toInt()
                layoutParams = android.widget.FrameLayout.LayoutParams(size, size).apply {
                    gravity = android.view.Gravity.CENTER
                }
                scaleType = android.widget.ImageView.ScaleType.FIT_XY
                imageTintList = android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSurface,
                        android.graphics.Color.BLACK
                    )
                )
            }
            addView(backgroundView)
            
            // Add count text (same as ContextualBottomToolbar)
            val countText = android.widget.TextView(context).apply {
                val size = (28 * context.resources.displayMetrics.density).toInt()
                layoutParams = android.widget.FrameLayout.LayoutParams(size, size).apply {
                    gravity = android.view.Gravity.CENTER
                }
                gravity = android.view.Gravity.CENTER
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSurface,
                        android.graphics.Color.BLACK
                    )
                )
                maxLines = 1
            }
            addView(countText)
            tabCountBadgeView = countText
            
            setOnClickListener {
                contextualToolbarListener?.onTabCountClicked()
            }
        }
        
        // Add tab count view directly to browser actions container at the beginning
        toolbar.asView().post {
            val browserActions = toolbar.asView().findViewById<ViewGroup>(
                mozilla.components.browser.toolbar.R.id.mozac_browser_toolbar_browser_actions
            )
            if (browserActions != null) {
                // Add at index 0 so it appears before the menu button
                browserActions.addView(tabCountContainer, 0)
            }
        }
        
        // Start observing tab count to update the text
        observeTabCountForBadge()
    }
    
    /**
     * Observe store to update tab count badge
     */
    private fun observeTabCountForBadge() {
        val store = context.components.store
        val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner ?: return
        
        lifecycleOwner.lifecycleScope.launch {
            store.flowScoped { flow ->
                flow.collect { state ->
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    val isPrivateMode = profileManager.isPrivateMode()
                    val currentProfile = profileManager.getActiveProfile()
                    val expectedContextId = if (isPrivateMode) {
                        "private"
                    } else {
                        "profile_${currentProfile.id}"
                    }
                    
                    val filteredTabCount = state.tabs.count { tab ->
                        tab.content.private == isPrivateMode && tab.contextId == expectedContextId
                    }
                    
                    tabCountBadgeView?.text = if (filteredTabCount > 99) "99+" else filteredTabCount.toString()
                }
            }
        }
    }

    /**
     * Setup scroll behavior for hiding toolbar on scroll
     */
    private fun setupScrollBehavior(parent: ViewGroup) {
        if (parent is CoordinatorLayout) {
            val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
                ToolbarPosition.BOTTOM
            } else {
                ToolbarPosition.TOP
            }
            
            val layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = when (toolbarPos) {
                    ToolbarPosition.BOTTOM -> android.view.Gravity.BOTTOM
                    ToolbarPosition.TOP -> android.view.Gravity.TOP
                }

                behavior = ModernScrollBehavior(context, null)
            }

            this.layoutParams = layoutParams
        }
    }

    /**
     * Update the contextual toolbar based on browsing context
     */
    fun updateContextualToolbar(
        tab: TabSessionState?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        tabCount: Int,
        isHomepage: Boolean = false
    ) {
        contextualToolbar?.updateForContext(
            tab = tab,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            tabCount = tabCount,
            isHomepage = isHomepage
        )
    }

    /**
     * Set listener for contextual toolbar interactions
     */
    fun setContextualToolbarListener(listener: ContextualBottomToolbar.ContextualToolbarListener) {
        // Store listener for use when contextual toolbar is disabled
        contextualToolbarListener = listener
        contextualToolbar?.listener = listener
    }
    
    /**
     * Set listener for tab selection in tab bar
     */
    fun setOnTabSelectedListener(listener: (String) -> Unit) {
        onTabSelectedCallback = listener
    }

    /**
     * Get the browser toolbar component
     */
    fun getBrowserToolbar(): BrowserToolbar? = browserToolbar

    /**
     * Get the contextual toolbar component
     */
    fun getContextualToolbar(): ContextualBottomToolbar? = contextualToolbar

    /**
     * Get the tab group bar component
     */
    fun getTabGroupBar(): TabGroupWithProfileSwitcher? = tabGroupBar
    
    /**
     * Get a container with bottom components (tab bar and contextual) for TOP toolbar mode.
     * This returns a LinearLayout containing the components that should be positioned at the bottom.
     * Returns null if toolbar is not in TOP mode.
     */
    fun getBottomComponentsContainer(): ViewGroup? {
        val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
            ToolbarPosition.BOTTOM
        } else {
            ToolbarPosition.TOP
        }
        
        if (toolbarPos != ToolbarPosition.TOP) {
            return null
        }
        
        // Create a container for bottom components
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // No padding - the toolbars will handle their own spacing
        }
        
        // Add tab bar first (at top of bottom container)
        tabGroupBar?.let {
            container.addView(it, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        
        // Add contextual toolbar below tab bar
        contextualToolbar?.let {
            container.addView(it, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        
        // Store reference for expand/collapse operations
        bottomComponentsContainer = container
        
        return if (container.childCount > 0) container else null
    }

    /**
     * Update component visibility based on settings
     */
    fun updateComponentVisibility(
        showTabBar: Boolean = showTabGroupBar,
        showContextual: Boolean = showContextualToolbar
    ) {
        showTabGroupBar = showTabBar
        showContextualToolbar = showContextual

        tabGroupBar?.visibility = if (showTabBar) View.VISIBLE else View.GONE
        contextualToolbar?.visibility = if (showContextual) View.VISIBLE else View.GONE
    }

    /**
     * Set the engine view for scroll behavior integration
     */
    fun setEngineView(engine: EngineView) {
        toolbarSystem.setEngineView(engine)
    }
    
    /**
     * Apply private mode theming
     */
    fun applyPrivateMode(isPrivate: Boolean) {
        // Private mode changes the Material 3 surface color
        val elevationDp = 3f * resources.displayMetrics.density
        val bgColor = if (isPrivate) {
            // Purple for private mode
            android.graphics.Color.parseColor("#6A1B9A")
        } else {
            // Normal Material 3 elevated surface
            com.google.android.material.elevation.ElevationOverlayProvider(context)
                .compositeOverlayWithThemeSurfaceColorIfNeeded(elevationDp)
        }

        toolbarSystem.setBackgroundColor(bgColor)
        
        // Components already have their own background handling
    }

    companion object {
        /**
         * Create and attach a unified toolbar to a parent container
         */
        fun create(
            context: Context,
            parent: ViewGroup,
            lifecycleOwner: LifecycleOwner,
            interactor: BrowserToolbarViewInteractor,
            customTabSession: CustomTabSessionState? = null,
            store: BrowserStore
        ): UnifiedToolbar {
            val toolbar = UnifiedToolbar(context)

            // Add to parent
            if (parent is CoordinatorLayout) {
                val prefs = UserPreferences(context)
                val toolbarPos = if (prefs.toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
                    ToolbarPosition.BOTTOM
                } else {
                    ToolbarPosition.TOP
                }
                
                val layoutParams = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = when (toolbarPos) {
                        ToolbarPosition.BOTTOM -> android.view.Gravity.BOTTOM
                        ToolbarPosition.TOP -> android.view.Gravity.TOP
                    }
                }
                parent.addView(toolbar, layoutParams)
            } else {
                parent.addView(toolbar, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }

            // Setup toolbar
            return toolbar.setup(
                parent,
                lifecycleOwner,
                interactor,
                customTabSession,
                store
            )
        }
    }
}
