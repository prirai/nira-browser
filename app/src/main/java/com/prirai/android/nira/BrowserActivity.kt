package com.prirai.android.nira

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import mozilla.components.browser.state.action.AppLifecycleAction

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
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
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
// import com.prirai.android.nira.browser.home.HomeFragmentDirections // Removed - using BrowserFragment for homepage
import com.prirai.android.nira.databinding.ActivityMainBinding
import com.prirai.android.nira.ext.alreadyOnDestination
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.isAppInDarkTheme
import com.prirai.android.nira.ext.nav
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.search.SearchDialogFragmentDirections
import com.prirai.android.nira.theme.applyAppTheme
import com.prirai.android.nira.utils.Utils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.IconRequest
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
    lateinit private var currentTheme: BrowsingMode

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

        super.onCreate(savedInstanceState)

        // OPTIMIZATION: Don't access components.publicSuffixList here - defer it
        // This was triggering lazy initialization chain

        browsingModeManager = createBrowsingModeManager(
            if (UserPreferences(this).lastKnownPrivate) BrowsingMode.Private else BrowsingMode.Normal
        )
        currentTheme = browsingModeManager.mode

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)

        if (UserPreferences(this).firstLaunch) {
            UserPreferences(this).firstLaunch = false
        }

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
        
        // Make navigation bar transparent globally to prevent black bar with gesture navigation
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        // Remove navigation bar contrast enforcement (removes the white pill/scrim on Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        // Hide the navigation bar completely and make content appear behind it
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            val userPrefs = UserPreferences(this)
            val isBottomToolbar = userPrefs.shouldUseBottomToolbar
            
            // Always respect status bar padding to prevent content overlap
            val topPadding = bars.top

            // Dynamic status bar based on toolbar position
            if (isBottomToolbar) {
                setupStatusBarForBottomToolbar(userPrefs)
                // CRITICAL: Hide navigation toolbar stub to prevent any space
                hideNavigationToolbarForBottomMode()
            } else {
                window.statusBarColor = getColor(R.color.statusbar_background)
                androidx.core.view.WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = true
            }

            v.updatePadding(
                left = bars.left,
                top = topPadding,
                right = bars.right,
                bottom = 0, // Don't add bottom padding - let content flow behind navigation bar
            )
            val insetsController = WindowCompat.getInsetsController(window, v)
            insetsController.isAppearanceLightStatusBars = !isAppInDarkTheme()
            WindowInsetsCompat.CONSUMED
        }

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
        browsingModeManager.mode = BrowsingMode.Normal

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

    protected open fun createBrowsingModeManager(initialMode: BrowsingMode): BrowsingModeManager {
        return DefaultBrowsingModeManager(initialMode, UserPreferences(this)) { newMode ->
            if (newMode != currentTheme) {
                if (!isFinishing) recreate()
            }
        }
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
                override fun setCustomView(view: android.view.View?) {}
                override fun setCustomView(view: android.view.View?, layoutParams: LayoutParams?) {}
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
                override fun getThemedContext(): android.content.Context = this@BrowserActivity
                override fun getCustomView(): android.view.View? = null
                override fun getTitle(): CharSequence? = null
                override fun getSubtitle(): CharSequence? = null
                override fun getNavigationMode(): Int = 0
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

        navigationToolbar.visibility = android.view.View.VISIBLE
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

    @Suppress("SpreadOperator")
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

    @Suppress("LongParameterList")
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
        if ((!forceSearch && searchTermOrURL.isUrl()) || engine == null) {
            if (newTab) {
                components.tabsUseCases.addTab.invoke(
                    searchTermOrURL.toNormalizedUrl(),
                    flags = flags,
                    private = true,
                )
            } else {
                components.sessionUseCases.loadUrl.invoke(searchTermOrURL.toNormalizedUrl(), flags)
            }
        } else {
            if (newTab) {
                components.searchUseCases.newTabSearch
                    .invoke(
                        searchTermOrURL,
                        SessionState.Source.Internal.UserEntered,
                        true,
                        searchEngine = engine
                    )
            } else {
                components.searchUseCases.defaultSearch.invoke(searchTermOrURL, engine)
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
            if (SearchEngineList().getEngines()[UserPreferences(this).searchEngineChoice].type == SearchEngine.Type.BUNDLED) {
                components.searchUseCases.selectSearchEngine(
                    SearchEngineList().getEngines()[UserPreferences(this).searchEngineChoice]
                )
            } else {
                components.searchUseCases.addSearchEngine(
                    SearchEngineList().getEngines()[UserPreferences(
                        this
                    ).searchEngineChoice]
                )
                components.searchUseCases.selectSearchEngine(
                    SearchEngineList().getEngines()[UserPreferences(this).searchEngineChoice]
                )
            }
        }
    }

    /**
     * Hide navigation toolbar completely for bottom toolbar mode
     */
    private fun hideNavigationToolbarForBottomMode() {
        // Hide the ViewStub itself to prevent any space reservation
        binding.navigationToolbarStub.visibility = android.view.View.GONE
        binding.navigationToolbarStub.layoutParams?.apply {
            height = 0
            width = 0
        }

        // If already inflated, hide the toolbar completely
        if (isToolbarInflated) {
            navigationToolbar.visibility = android.view.View.GONE
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
        }
        
        // Make navigation bar transparent and content flows behind it
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        // Remove navigation bar contrast enforcement (removes the white pill/scrim on Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        // Hide the navigation bar completely and make content appear behind it
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }


    companion object {
        const val OPEN_TO_BROWSER = "open_to_browser"
    }
}
