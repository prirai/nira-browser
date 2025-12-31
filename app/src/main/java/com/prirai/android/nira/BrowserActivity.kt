package com.prirai.android.nira

// import com.prirai.android.nira.browser.home.HomeFragmentDirections // Removed - using BrowserFragment for homepage
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.prirai.android.nira.addons.WebExtensionPopupFragment
import com.prirai.android.nira.addons.WebExtensionTabletPopupFragment
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.BrowsingModeManager
import com.prirai.android.nira.browser.DefaultBrowsingModeManager
import com.prirai.android.nira.browser.SearchEngineList
import com.prirai.android.nira.databinding.ActivityMainBinding
import com.prirai.android.nira.ext.alreadyOnDestination
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.isAppInDarkTheme
import com.prirai.android.nira.ext.nav
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.search.SearchDialogFragmentDirections
import com.prirai.android.nira.theme.applyAppTheme
import com.prirai.android.nira.utils.Utils
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.IconRequest
import mozilla.components.browser.state.action.AppLifecycleAction
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.WebExtensionState
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.contextmenu.ext.DefaultSelectionActionDelegate
import mozilla.components.feature.search.ext.createSearchEngine
import mozilla.components.support.base.feature.ActivityResultHandler
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.ktx.kotlin.isUrl
import mozilla.components.support.ktx.kotlin.toNormalizedUrl
import mozilla.components.support.locale.LocaleAwareAppCompatActivity
import mozilla.components.support.utils.SafeIntent
import mozilla.components.support.webextensions.WebExtensionPopupObserver


/**
 * Activity that holds the [BrowserFragment].
 */
open class BrowserActivity : LocaleAwareAppCompatActivity(), ComponentCallbacks2, NavHostActivity {

    lateinit var binding: ActivityMainBinding

    lateinit var browsingModeManager: BrowsingModeManager
    private lateinit var currentTheme: BrowsingMode
    
    private var isToolbarInflated = false
    private lateinit var navigationToolbar: Toolbar

    private val navHost by lazy {
        supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
    }

    private val externalSourceIntentProcessors by lazy {
        listOf(
            OpenBrowserIntentProcessor(this, ::getIntentSessionId),
            OpenSpecificTabIntentProcessor(this)
        )
    }

    private val webExtensionPopupFeature by lazy {
        WebExtensionPopupObserver(components.store, ::openPopup)
    }

    private var originalContext: Context? = null

    private var lastToolbarPosition: Int = 0

    protected open fun getIntentSessionId(intent: SafeIntent): String? = null

    @VisibleForTesting
    internal fun isActivityColdStarted(startingIntent: Intent, activityIcicle: Bundle?): Boolean {
        return activityIcicle == null && startingIntent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch from splash theme to regular theme immediately
        setTheme(R.style.AppThemeNotActionBar)
        
        // Apply user theme preferences
        com.prirai.android.nira.theme.ThemeManager.applyTheme(this)
        
        // Apply Material 3 dynamic colors (Material You) on Android 12+ if enabled
        val prefs = UserPreferences(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && prefs.dynamicColors) {
            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)

        // Check for first launch and show onboarding
        if (UserPreferences(this).firstLaunch) {
            val intent = Intent(this, com.prirai.android.nira.onboarding.OnboardingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // OPTIMIZATION: Don't access components.publicSuffixList here - defer it
        // This was triggering lazy initialization chain

        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(this)
        val initialProfile = profileManager.getActiveProfile()
        val isPrivate = UserPreferences(this).lastKnownPrivate
        
        // Sync ProfileManager's private mode state with BrowsingModeManager
        profileManager.setPrivateMode(isPrivate)
        
        browsingModeManager = createBrowsingModeManager(
            if (isPrivate) BrowsingMode.Private else BrowsingMode.Normal,
            initialProfile
        )
        currentTheme = browsingModeManager.mode
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)

        lastToolbarPosition = UserPreferences(this).toolbarPosition

        // OPTIMIZATION: Defer search engine setup to after first frame
        // This was accessing components.store.state which triggers heavy initialization
        view.post {
            setupSearchEngines()
            // Also prefetch publicSuffixList after UI is ready
            components.publicSuffixList.prefetch()
        }

        if (isActivityColdStarted(intent, savedInstanceState) &&
            !externalSourceIntentProcessors.any { it.process(intent, navHost.navController, this.intent) }
        ) {
            navigateToBrowserOnColdStart()
        }

        applyAppTheme(this)
        
        // Apply Material You dynamic colors if enabled
        if (com.prirai.android.nira.theme.ThemeManager.shouldUseDynamicColors(this)) {
            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        }
        
        // Register lifecycle observer to capture state on background
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                // App going to background - trigger state capture
                components.store.dispatch(AppLifecycleAction.PauseAction)
            }
            
            override fun onResume(owner: LifecycleOwner) {
                // App coming to foreground
                components.store.dispatch(AppLifecycleAction.ResumeAction)
            }
        })
        
        // Setup auto-tagging of new tabs with current profile
        setupTabProfileTagging()
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Setup window insets handling for edge-to-edge
        setupEdgeToEdgeInsets(view)

        // OPTIMIZATION: Components that need lifecycle registration must be initialized in onCreate
        // These still trigger lazy component init but are required for proper lifecycle
        components.notificationsDelegate.bindToActivity(this)
        
        // OPTIMIZATION: Defer non-critical component initialization to after first frame
        view.post {
            components.appRequestInterceptor.setNavController(navHost.navController)
        }
    }

    override fun onStart() {
        super.onStart()
        // Register lifecycle observer here - must be done before RESUMED state
        // This still defers the components initialization but meets lifecycle requirements
        lifecycle.addObserver(webExtensionPopupFeature)
    }

    override fun onResume() {
        super.onResume()
        
        val currentPosition = UserPreferences(this).toolbarPosition
        if (currentPosition != lastToolbarPosition) {
            lastToolbarPosition = currentPosition
            recreate()
        }
        
        // Update toolbar and status bar theme immediately
        updateToolbarAndStatusBarTheme()
    }

    final override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // TODO: temporary fix
        openToBrowser(BrowserDirection.FromGlobal)
        intent.let {
            handleNewIntent(it)
        }
    }

    open fun handleNewIntent(intent: Intent) {
        val intentProcessors = externalSourceIntentProcessors
        val intentHandled =
            intentProcessors.any { it.process(intent, navHost.navController, this.intent) }
        
        // Default mode already set in onCreate
        if (intentHandled) {
            supportFragmentManager
                .primaryNavigationFragment
                ?.childFragmentManager
                ?.fragments
                ?.lastOrNull()
        }
    }

    open fun navigateToBrowserOnColdStart() {
        if (!browsingModeManager.mode.isPrivate) {
            openToBrowser(BrowserDirection.FromGlobal, null)
        }
    }
    
    private fun setupTabProfileTagging() {
        // Tab profile tagging is now handled by ProfileMiddleware
        // which sets contextId on tab creation for proper Gecko-level cookie isolation
        // This function kept for compatibility but logic moved to middleware
    }

    protected open fun createBrowsingModeManager(
        initialMode: BrowsingMode,
        initialProfile: com.prirai.android.nira.browser.profile.BrowserProfile
    ): BrowsingModeManager {
        return DefaultBrowsingModeManager(
            initialMode, 
            initialProfile,
            UserPreferences(this),
            { newMode ->
                // Update current theme but don't recreate - profile switching handles UI updates
                currentTheme = newMode
                // Save the private mode state to both preferences for consistency
                UserPreferences(this).lastKnownPrivate = newMode.isPrivate
                com.prirai.android.nira.browser.profile.ProfileManager.getInstance(this).setPrivateMode(newMode.isPrivate)
            },
            { newProfile ->
                // Profile changed - save to ProfileManager
                com.prirai.android.nira.browser.profile.ProfileManager.getInstance(this).setActiveProfile(newProfile)
            }
        )
    }

    final override fun onBackPressed() {
        supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.forEach {
            if (it is UserInteractionHandler && it.onBackPressed()) {
                return
            }
        }
        super.onBackPressed()
    }

    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? =
        when (name) {
            EngineView::class.java.name -> components.engine.createView(context, attrs).apply {
                selectionActionDelegate = DefaultSelectionActionDelegate(
                    store = components.store,
                    context = context
                )
            }.asView()

            else -> super.onCreateView(parent, name, context, attrs)
        }

    override fun getSupportActionBarAndInflateIfNecessary(): ActionBar {
        // Don't inflate navigation toolbar when using bottom toolbar
        if (UserPreferences(this).shouldUseBottomToolbar) {
            // Return a dummy action bar to satisfy the interface
            // The bottom toolbar doesn't need an ActionBar
            return object : ActionBar() {
                override fun setCustomView(view: View?) {}
                override fun setCustomView(view: View?, layoutParams: LayoutParams?) {}
                override fun setCustomView(resId: Int) {}
                override fun setIcon(resId: Int) {}
                override fun setIcon(icon: android.graphics.drawable.Drawable?) {}
                override fun setLogo(resId: Int) {}
                override fun setLogo(logo: android.graphics.drawable.Drawable?) {}
                override fun setListNavigationCallbacks(
                    adapter: android.widget.SpinnerAdapter?,
                    callback: OnNavigationListener?
                ) {
                }

                override fun setSelectedNavigationItem(position: Int) {}
                override fun getSelectedNavigationIndex(): Int = 0
                override fun getNavigationItemCount(): Int = 0
                override fun setTitle(title: CharSequence?) {}
                override fun setTitle(resId: Int) {}
                override fun setSubtitle(subtitle: CharSequence?) {}
                override fun setSubtitle(resId: Int) {}
                override fun setDisplayOptions(options: Int) {}
                override fun setDisplayOptions(options: Int, mask: Int) {}
                override fun setDisplayUseLogoEnabled(useLogo: Boolean) {}
                override fun setDisplayShowHomeEnabled(showHome: Boolean) {}
                override fun setDisplayHomeAsUpEnabled(showHomeAsUp: Boolean) {}
                override fun setDisplayShowTitleEnabled(showTitle: Boolean) {}
                override fun setDisplayShowCustomEnabled(showCustom: Boolean) {}
                override fun setBackgroundDrawable(d: android.graphics.drawable.Drawable?) {}
                override fun getThemedContext(): Context = this@BrowserActivity
                override fun getCustomView(): View? = null
                override fun getTitle(): CharSequence? = null
                override fun getSubtitle(): CharSequence? = null
                override fun getNavigationMode(): Int = NAVIGATION_MODE_STANDARD
                override fun setNavigationMode(mode: Int) {}
                override fun getDisplayOptions(): Int = 0
                override fun newTab(): Tab? = null
                override fun addTab(tab: Tab?) {}
                override fun addTab(tab: Tab?, setSelected: Boolean) {}
                override fun addTab(tab: Tab?, position: Int) {}
                override fun addTab(tab: Tab?, position: Int, setSelected: Boolean) {}
                override fun removeTab(tab: Tab?) {}
                override fun removeTabAt(position: Int) {}
                override fun removeAllTabs() {}
                override fun selectTab(tab: Tab?) {}
                override fun getSelectedTab(): Tab? = null
                override fun getTabAt(index: Int): Tab? = null
                override fun getTabCount(): Int = 0
                override fun getHeight(): Int = 0
                override fun show() {}
                override fun hide() {}
                override fun isShowing(): Boolean = false
                override fun addOnMenuVisibilityListener(listener: OnMenuVisibilityListener?) {}
                override fun removeOnMenuVisibilityListener(listener: OnMenuVisibilityListener?) {}
                override fun setHomeButtonEnabled(enabled: Boolean) {}
                override fun setHomeAsUpIndicator(resId: Int) {}
                override fun setHomeAsUpIndicator(indicator: android.graphics.drawable.Drawable?) {}
                override fun setHomeActionContentDescription(resId: Int) {}
                override fun setHomeActionContentDescription(description: CharSequence?) {}
                override fun setHideOnContentScrollEnabled(hideOnContentScroll: Boolean) {}
                override fun isHideOnContentScrollEnabled(): Boolean = false
                override fun getHideOffset(): Int = 0
                override fun setHideOffset(offset: Int) {}
                override fun setElevation(elevation: Float) {}
                override fun getElevation(): Float = 0f
            }
        }

        if (!isToolbarInflated) {
            navigationToolbar = binding.navigationToolbarStub.inflate() as Toolbar

            setSupportActionBar(navigationToolbar)
            // Add ids to this that we don't want to have a toolbar back button
            setupNavigationToolbar()

            isToolbarInflated = true
        }

        navigationToolbar.visibility = View.VISIBLE
        navigationToolbar.layoutParams?.apply {
            height = resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)
            width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        }

        return supportActionBar!!
    }

    final override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.forEach {
            if (it is ActivityResultHandler && it.onActivityResult(requestCode, data, resultCode)) {
                return
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun setupNavigationToolbar(vararg topLevelDestinationIds: Int) {
        NavigationUI.setupWithNavController(
            navigationToolbar,
            navHost.navController,
            AppBarConfiguration.Builder(*topLevelDestinationIds).build()
        )

        navigationToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun openPopup(webExtensionState: WebExtensionState) {
        val fm: FragmentManager = supportFragmentManager
        val editNameDialogFragment =
            if (Utils().isTablet(this)) WebExtensionTabletPopupFragment()
            else WebExtensionPopupFragment()

        val bundle = Bundle()
        bundle.putString("web_extension_id", webExtensionState.id)
        intent.putExtra("web_extension_name", webExtensionState.name)

        editNameDialogFragment.arguments = bundle

        editNameDialogFragment.show(fm, "fragment_edit_name")
    }

    fun openToBrowserAndLoad(
        searchTermOrURL: String,
        newTab: Boolean,
        from: BrowserDirection,
        customTabSessionId: String? = null,
        engine: SearchEngine? = null,
        forceSearch: Boolean = false,
        flags: EngineSession.LoadUrlFlags = EngineSession.LoadUrlFlags.none()
    ) {
        openToBrowser(from, customTabSessionId)
        load(searchTermOrURL, newTab, engine, forceSearch, flags)
    }

    fun openToBrowser(from: BrowserDirection, customTabSessionId: String? = null) {
        if (navHost.navController.alreadyOnDestination(R.id.browserFragment)) return
        @IdRes val fragmentId = if (from.fragmentId != 0) from.fragmentId else null
        val directions = getNavDirections(from, customTabSessionId)
        if (directions != null) {
            navHost.navController.nav(fragmentId, directions)
        }
    }

    protected open fun getNavDirections(
        from: BrowserDirection,
        customTabSessionId: String?
    ): NavDirections? = when (from) {
        BrowserDirection.FromGlobal ->
            NavGraphDirections.actionGlobalBrowser(customTabSessionId)

        BrowserDirection.FromHome ->
            NavGraphDirections.actionGlobalBrowser(customTabSessionId)

        BrowserDirection.FromSearchDialog ->
            SearchDialogFragmentDirections.actionGlobalBrowser(customTabSessionId)
    }

    private fun load(
        searchTermOrURL: String,
        newTab: Boolean,
        engine: SearchEngine?,
        forceSearch: Boolean,
        flags: EngineSession.LoadUrlFlags = EngineSession.LoadUrlFlags.none()
    ) {
        val selectedTab = components.store.state.selectedTabId?.let { id ->
            components.store.state.tabs.find { it.id == id }
        }
        val isPrivateMode = selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate
        
        if ((!forceSearch && searchTermOrURL.isUrl()) || engine == null) {
            if (newTab) {
                // Determine contextId for proper tab grouping and visibility
                val contextId = getContextIdForNewTab(isPrivateMode, selectedTab)
                components.tabsUseCases.addTab.invoke(
                    searchTermOrURL.toNormalizedUrl(),
                    flags = flags,
                    private = isPrivateMode,
                    contextId = contextId
                )
            } else {
                components.sessionUseCases.loadUrl.invoke(searchTermOrURL.toNormalizedUrl(), flags)
            }
        } else {
            if (newTab) {
                // For search, use newTabSearch but set contextId via middleware after tab creation
                // The ProfileMiddleware will set contextId based on private mode
                components.searchUseCases.newTabSearch
                    .invoke(
                        searchTermOrURL,
                        SessionState.Source.Internal.UserEntered,
                        isPrivateMode,
                        searchEngine = engine
                    )
            } else {
                components.searchUseCases.defaultSearch.invoke(searchTermOrURL, engine)
            }
        }
    }
    
    /**
     * Get the appropriate contextId for new tabs based on browsing mode and current profile.
     * This ensures tabs are properly grouped and visible in tab bar/sheet.
     */
    private fun getContextIdForNewTab(isPrivateMode: Boolean, selectedTab: SessionState?): String {
        return if (isPrivateMode) {
            "private"
        } else {
            // Try to use contextId from selected tab if available
            val tabContextId = selectedTab?.contextId
            if (tabContextId != null && tabContextId.startsWith("profile_")) {
                tabContextId
            } else {
                // Fall back to active profile
                val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(this)
                val currentProfile = profileManager.getActiveProfile()
                "profile_${currentProfile.id}"
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        this.originalContext = base
        super.attachBaseContext(base)
    }

    /**
     * Setup search engines - extracted to separate method for deferred initialization
     */
    private fun setupSearchEngines() {
        //TODO: Move to settings page so app restart no longer required
        //TODO: Differentiate between using search engine / adding to list - the code below removes all from list as I don't support adding to list, only setting as default
        for (i in components.store.state.search.customSearchEngines) {
            components.searchUseCases.removeSearchEngine(i)
        }

        if (UserPreferences(this).customSearchEngine) {
            // SECURITY: Use lifecycle-aware coroutine scope
            lifecycleScope.launch {
                val customSearch =
                    createSearchEngine(
                        name = "Custom Search",
                        url = UserPreferences(this@BrowserActivity).customSearchEngineURL,
                        icon = components.icons.loadIcon(IconRequest(UserPreferences(this@BrowserActivity).customSearchEngineURL))
                            .await().bitmap
                    )

                runOnUiThread {
                    components.searchUseCases.addSearchEngine(
                        customSearch
                    )
                    components.searchUseCases.selectSearchEngine(
                        customSearch
                    )
                }
            }
        } else {
            if (SearchEngineList(this).getEngines()[UserPreferences(this).searchEngineChoice].type == SearchEngine.Type.BUNDLED) {
                components.searchUseCases.selectSearchEngine(
                    SearchEngineList(this).getEngines()[UserPreferences(this).searchEngineChoice]
                )
            } else {
                components.searchUseCases.addSearchEngine(
                    SearchEngineList(this).getEngines()[UserPreferences(
                        this
                    ).searchEngineChoice]
                )
                components.searchUseCases.selectSearchEngine(
                    SearchEngineList(this).getEngines()[UserPreferences(this).searchEngineChoice]
                )
            }
        }
    }

    /**
     * Hide navigation toolbar completely for bottom toolbar mode
     */
    private fun hideNavigationToolbarForBottomMode() {
        // Hide the ViewStub itself to prevent any space reservation
        binding.navigationToolbarStub.visibility = View.GONE
        binding.navigationToolbarStub.layoutParams?.apply {
            height = 0
            width = 0
        }

        // If already inflated, hide the toolbar completely
        if (isToolbarInflated) {
            navigationToolbar.visibility = View.GONE
            navigationToolbar.layoutParams?.apply {
                height = 0
                width = 0
            }
        }
    }

    /**
     * Setup status bar for bottom toolbar mode with optional blur effect
     */
    private fun setupStatusBarForBottomToolbar(userPrefs: UserPreferences) {
        // Check if we should enable blur (enabled by default on Android 12+, or if user explicitly enabled it)
        val shouldBlur = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            userPrefs.statusBarBlurEnabled // Default is true on Android 12+
        } else {
            userPrefs.statusBarBlurEnabled // Default is false on Android 11 and below
        }
        
        // Apply blur effect if enabled
        if (shouldBlur) {
            // Use system blur effect on Android 12+ (API 31+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.attributes = window.attributes.apply {
                    blurBehindRadius = 80 // Blur radius in pixels
                }
                window.setBackgroundBlurRadius(80)
            }
            // Set semi-transparent background for blur effect
            window.statusBarColor = if (isAppInDarkTheme()) {
                android.graphics.Color.argb(180, 0, 0, 0) // Semi-transparent black
            } else {
                android.graphics.Color.argb(180, 255, 255, 255) // Semi-transparent white
            }
        } else {
            // Use default theme-based background
            window.statusBarColor = if (isAppInDarkTheme()) {
                getColor(R.color.statusbar_background) // Dark theme color
            } else {
                getColor(R.color.statusbar_background) // Light theme color
            }
        }

        // Enable content to draw behind status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set status bar icons color
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isAppInDarkTheme()
            // Ensure system bars are always visible
            show(WindowInsetsCompat.Type.systemBars())
        }
        
        // Make navigation bar transparent and content flows behind it
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        // Remove navigation bar contrast enforcement (removes the white pill/scrim on Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    
    private fun updateToolbarAndStatusBarTheme() {
        val selectedTab = components.store.state.tabs.find { it.id == components.store.state.selectedTabId }
        val isPrivate = selectedTab?.content?.private == true
        
        // Update status bar
        com.prirai.android.nira.theme.ThemeManager.applySystemBarsTheme(this, isPrivate)
        
        // Update toolbar background - use safe cast as it could be UnifiedToolbar or BrowserToolbar
        val toolbar = findViewById<View>(R.id.toolbar)
        val unifiedToolbar = toolbar as? com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar
        if (isPrivate) {
            val purpleColor = com.prirai.android.nira.theme.ColorConstants.PrivateMode.PURPLE
            unifiedToolbar?.setBackgroundColor(purpleColor) ?: toolbar?.setBackgroundColor(purpleColor)
        } else {
            // Use Material 3 surface color
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            unifiedToolbar?.setBackgroundColor(typedValue.data) ?: toolbar?.setBackgroundColor(typedValue.data)
        }
    }
    
    private fun updateToolbarStyling() {
        val selectedTab = components.store.state.tabs.find { it.id == components.store.state.selectedTabId }
        val isPrivate = selectedTab?.content?.private == true
        
        // Find the toolbar view
        val toolbar = findViewById<View>(R.id.toolbar)
        
        if (isPrivate) {
            // Purple background for private mode
            val purpleColor = com.prirai.android.nira.theme.ColorConstants.PrivateMode.PURPLE
            toolbar?.setBackgroundColor(purpleColor)
            window.statusBarColor = purpleColor
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } else {
            // Default theme color
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            toolbar?.setBackgroundColor(typedValue.data)
            
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryVariant, typedValue, true)
            window.statusBarColor = typedValue.data
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }
    
    /**
     * Enable true edge-to-edge display following Mozilla's approach.
     * Makes the app draw behind system bars (status bar and navigation bar).
     */
    private fun enableEdgeToEdge() {
        // Enable edge-to-edge mode
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val isDark = isAppInDarkTheme()

        // Use semi-transparent scrim for status bar to ensure visibility on all devices
        // This works better than opaque colors on devices like Xiaomi HyperOS
        window.statusBarColor = if (isDark) {
            android.graphics.Color.argb(230, 28, 27, 31) // 90% opaque Material 3 dark surface
        } else {
            android.graphics.Color.argb(230, 255, 251, 254) // 90% opaque Material 3 light surface
        }

        // Navigation bar remains transparent - content flows behind it
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Remove navigation bar contrast enforcement (removes the white scrim on Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // Setup system bar appearance
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
    }
    
    /**
     * Setup window insets for edge-to-edge without applying padding to root view.
     * Individual components (toolbars) will handle their own insets.
     */
    private fun setupEdgeToEdgeInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            // Don't apply any padding to root view - let it truly be edge-to-edge
            // Fragments and their toolbars will handle insets individually
            
            // Just return insets for children to consume
            insets
        }
    }

    /**
     * Get current active profile ID
     */
    fun getCurrentProfileId(): String {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(this)
        return profileManager.getActiveProfile().id
    }


    companion object {
        const val OPEN_TO_BROWSER = "open_to_browser"
    }
}
