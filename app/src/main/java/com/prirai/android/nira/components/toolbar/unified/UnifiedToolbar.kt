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
import mozilla.components.ui.widgets.behavior.EngineViewScrollingBehavior
import mozilla.components.ui.widgets.behavior.ViewPosition as MozacToolbarPosition

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

    // Configuration
    private var showTabGroupBar: Boolean = prefs.showTabGroupBar
    private var showContextualToolbar: Boolean = prefs.showContextualToolbar

    init {
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
    }
    
    // Forward ScrollableToolbar methods to toolbarSystem
    override fun enableScrolling() = toolbarSystem.enableScrolling()
    override fun disableScrolling() = toolbarSystem.disableScrolling()
    override fun expand() = toolbarSystem.expand()
    override fun collapse() = toolbarSystem.collapse()

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
                // TOP: Tab Bar -> Address Bar -> Contextual Toolbar
                addTabGroupBar(store, lifecycleOwner)
                addAddressBar(parent, lifecycleOwner, interactor, customTabSession, store)
                addContextualToolbar()
            }
            ModernToolbarSystem.ToolbarPosition.BOTTOM -> {
                // BOTTOM: Contextual Toolbar -> Address Bar -> Tab Bar
                addContextualToolbar()
                addAddressBar(parent, lifecycleOwner, interactor, customTabSession, store)
                addTabGroupBar(store, lifecycleOwner)
            }
        }

        // Setup scroll behavior if enabled
        if (prefs.hideBarWhileScrolling) {
            setupScrollBehavior(parent)
        }

        return this
    }

    /**
     * Add tab group bar component (TabGroupWithProfileSwitcher)
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun addTabGroupBar(
        store: BrowserStore,
        lifecycleOwner: LifecycleOwner
    ) {
        if (!showTabGroupBar) {
            return
        }

        tabGroupBar = TabGroupWithProfileSwitcher(context).apply {
            // No need to set background - it already applies Material 3 theming
            
            // Setup TabGroupWithProfileSwitcher with all callbacks
            setup(
                onTabSelected = { tabId ->
                    // Invoke the callback set from outside
                    onTabSelectedCallback?.invoke(tabId)
                },
                onTabClosed = { tabId ->
                    // Tab closing handled by browser
                },
                onIslandRenamed = { islandId, newName ->
                    // Island rename handled
                },
                onNewTabInIsland = { islandId ->
                    // New tab in island handled
                },
                onProfileSelected = { profile ->
                    // Switch to the selected profile
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    profileManager.setActiveProfile(profile)
                    // Reload to apply profile change
                    context.components.sessionUseCases.reload()
                },
                onPrivateModeSelected = {
                    // Toggle private mode
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    val isCurrentlyPrivate = profileManager.isPrivateMode()
                    profileManager.setPrivateMode(!isCurrentlyPrivate)
                    // Reload to apply private mode change
                    context.components.sessionUseCases.reload()
                }
            )
        }

        // Observe store to update tabs with proper filtering by profile/private mode
        lifecycleOwner.lifecycleScope.launch {
            store.flowScoped { flow ->
                flow.collect { state ->
                    val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
                    val isPrivateMode = profileManager.isPrivateMode()
                    val currentProfile = profileManager.getActiveProfile()
                    
                    // Filter tabs based on current mode and profile (same logic as TabsBottomSheetFragment)
                    val expectedContextId = if (isPrivateMode) {
                        "private"
                    } else {
                        "profile_${currentProfile.id}"
                    }
                    
                    val filteredTabs = state.tabs.filter { tab ->
                        tab.content.private == isPrivateMode && tab.contextId == expectedContextId
                    }
                    
                    tabGroupBar?.updateTabs(filteredTabs, state.selectedTabId)
                }
            }
        }

        toolbarSystem.addComponent(tabGroupBar!!, ModernToolbarSystem.ComponentType.TAB_GROUP)
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
     * Add contextual toolbar component (ContextualBottomToolbar)
     */
    private fun addContextualToolbar() {
        if (!showContextualToolbar) {
            return
        }

        contextualToolbar = ContextualBottomToolbar(context).apply {
            // No need to set background - it already applies Material 3 theming
        }

        toolbarSystem.addComponent(contextualToolbar!!, ModernToolbarSystem.ComponentType.CONTEXTUAL)
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

                val mozacPosition = when (toolbarPos) {
                    ToolbarPosition.BOTTOM -> MozacToolbarPosition.BOTTOM
                    ToolbarPosition.TOP -> MozacToolbarPosition.TOP
                }

                behavior = EngineViewScrollingBehavior(context, null, mozacPosition)
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
