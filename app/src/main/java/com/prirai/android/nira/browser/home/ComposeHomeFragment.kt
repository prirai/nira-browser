package com.prirai.android.nira.browser.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.NavGraphDirections
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment
import com.prirai.android.nira.browser.home.compose.AddShortcutDialog
import com.prirai.android.nira.browser.home.compose.HomeScreen
import com.prirai.android.nira.browser.home.compose.HomeViewModel
import com.prirai.android.nira.browser.home.compose.HomeViewModelFactory
import com.prirai.android.nira.browser.home.compose.ProfileInfo
import com.prirai.android.nira.browser.shortcuts.ShortcutDatabase
import com.prirai.android.nira.components.toolbar.BrowserToolbarViewInteractor
import com.prirai.android.nira.components.toolbar.ToolbarMenu
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.HomepageBackgroundChoice
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.lib.state.ext.observeAsComposableState

class ComposeHomeFragment : Fragment() {

    private val browsingModeManager get() = (activity as BrowserActivity).browsingModeManager
    private val components get() = requireContext().components

    private lateinit var viewModel: HomeViewModel
    private var unifiedToolbar: UnifiedToolbar? = null
    
    companion object {
        // Flag to prevent automatic navigation back to browser when explicitly navigating to home
        private var shouldPreventAutoNavigation = false
        
        fun navigateToHome() {
            shouldPreventAutoNavigation = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database with migrations
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shortcutentity ADD COLUMN title TEXT")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE shortcutentity_new (uid INTEGER NOT NULL, url TEXT, title TEXT, PRIMARY KEY(uid))")
                db.execSQL("INSERT INTO shortcutentity_new (uid, url, title) SELECT uid, url, title FROM shortcutentity")
                db.execSQL("DROP TABLE shortcutentity")
                db.execSQL("ALTER TABLE shortcutentity_new RENAME TO shortcutentity")
            }
        }

        val database = Room.databaseBuilder(
            requireContext(),
            ShortcutDatabase::class.java,
            "shortcut-database"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        val factory = HomeViewModelFactory(
            bookmarkManager = BookmarkManager.getInstance(requireContext()),
            shortcutDao = database.shortcutDao()
        )

        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create a CoordinatorLayout as root container for the homepage
        val coordinatorLayout = CoordinatorLayout(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // IMPORTANT: Don't use fitsSystemWindows here!
            // UnifiedToolbar handles window insets directly via its own WindowInsetsListener.
            // Setting fitsSystemWindows=true here would add extra padding at the bottom
            // (for navigation bar), causing inconsistent spacing compared to BrowserFragment
            // which doesn't use fitsSystemWindows in its layout XML.
            fitsSystemWindows = false
        }

        // Create ComposeView for the main content
        val composeView = ComposeView(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val store = requireContext().components.store
                
                // Observe selected tab to trigger recomposition when it changes (which happens during profile switch)
                val selectedTabId by store.observeAsComposableState { state -> state.selectedTabId }
                
                val selectedTab = selectedTabId?.let { id ->
                    store.state.tabs.find { it.id == id }
                }

                val isPrivateMode = selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate

                val shortcuts by viewModel.shortcuts.collectAsState()
                val bookmarks by viewModel.bookmarks.collectAsState()
                val showAddDialog by viewModel.showAddShortcutDialog.collectAsState()
                val isBookmarkExpanded by viewModel.isBookmarkSectionExpanded.collectAsState()

                if (isPrivateMode) {
                    store.state.privateTabs.size
                } else {
                    store.state.normalTabs.size
                }

                val profileManager =
                    com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                val currentProfile = run {
                    val tabContextId = selectedTab?.contextId
                    when {
                        tabContextId == "private" || isPrivateMode -> {
                            ProfileInfo("private", "Private", "🕵️", true, 0)
                        }

                        tabContextId != null && tabContextId.startsWith("profile_") -> {
                            val profileId = tabContextId.removePrefix("profile_")
                            val profile = profileManager.getAllProfiles().find { it.id == profileId }
                                ?: profileManager.getActiveProfile()
                            ProfileInfo(profile.id, profile.name, profile.emoji, false, profile.color)
                        }

                        else -> {
                            val profile = profileManager.getActiveProfile()
                            ProfileInfo(profile.id, profile.name, profile.emoji, false, profile.color)
                        }
                    }
                }

                val prefs = UserPreferences(requireContext())
                val backgroundImageUrl = when (prefs.homepageBackgroundChoice) {
                    HomepageBackgroundChoice.NONE.ordinal -> null
                    HomepageBackgroundChoice.URL.ordinal,
                    HomepageBackgroundChoice.GALLERY.ordinal -> prefs.homepageBackgroundUrl
                    else -> null
                }
                
                // Check if toolbar is at top
                val isToolbarAtTop = prefs.toolbarPosition == ToolbarPosition.TOP.ordinal

                NiraTheme(
                    isPrivateMode = isPrivateMode,
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors
                ) {
                    HomeScreen(
                        isPrivateMode = isPrivateMode,
                        shortcuts = shortcuts,
                        bookmarks = bookmarks,
                        isBookmarkExpanded = isBookmarkExpanded,
                        currentProfile = currentProfile,
                        onProfileClick = {}, // Profile icon is now display-only
                        backgroundImageUrl = backgroundImageUrl,
                        isToolbarAtTop = isToolbarAtTop,
                        onShortcutClick = { shortcut ->
                            components.sessionUseCases.loadUrl(shortcut.url)
                            // Navigate to browser to show the loaded page
                            try {
                                findNavController().navigate(R.id.browserFragment)
                            } catch (e: Exception) {
                                // Ignore navigation errors
                            }
                        },
                        onShortcutDelete = { shortcut ->
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Delete Shortcut")
                                .setMessage("Are you sure you want to delete '${shortcut.title}'?")
                                .setPositiveButton("Delete") { _, _ ->
                                    viewModel.deleteShortcut(shortcut)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        },
                        onShortcutAdd = { viewModel.showAddShortcutDialog() },
                        onBookmarkClick = { bookmark ->
                            if (bookmark.isFolder) {
                                val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance(
                                    folderId = bookmark.id.toLongOrNull() ?: -1L
                                )
                                bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                            } else {
                                components.sessionUseCases.loadUrl(bookmark.url)
                                // Navigate to browser to show the loaded page
                                try {
                                    findNavController().navigate(R.id.browserFragment)
                                } catch (e: Exception) {
                                    // Ignore navigation errors
                                }
                            }
                        },
                        onBookmarkToggle = { viewModel.toggleBookmarkSection() },
                        onSearchClick = {
                            // Pass current selected tab ID for proper context
                            val sessionId = components.store.state.selectedTabId
                            val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = sessionId)
                            findNavController().navigate(directions)
                        }
                    )

                    if (showAddDialog) {
                        AddShortcutDialog(
                            onDismiss = { viewModel.hideAddShortcutDialog() },
                            onSave = { url, title ->
                                viewModel.addShortcut(url, title)
                            }
                        )
                    }
                }
            }
        }

        // Add compose view to coordinator layout
        coordinatorLayout.addView(composeView)

        // Setup UnifiedToolbar in the same coordinatorLayout
        setupUnifiedToolbar(coordinatorLayout)

        return coordinatorLayout
    }

    private fun setupUnifiedToolbar(coordinatorLayout: CoordinatorLayout) {
        val prefs = UserPreferences(requireContext())
        requireContext().components.tabGroupManager

        // Create toolbar interactor
        val toolbarInteractor = object : BrowserToolbarViewInteractor {
            override fun onBrowserToolbarPaste(text: String) {
                // Pass current selected tab ID so search dialog knows the context
                val sessionId = components.store.state.selectedTabId
                val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = sessionId)
                findNavController().navigate(directions)
            }

            override fun onBrowserToolbarPasteAndGo(text: String) {
                components.sessionUseCases.loadUrl(text)
            }

            override fun onBrowserToolbarClicked() {
                // Pass current selected tab ID so search dialog knows the context
                val sessionId = components.store.state.selectedTabId
                val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = sessionId)
                findNavController().navigate(directions)
            }

            override fun onBrowserToolbarMenuItemTapped(item: ToolbarMenu.Item) {
                // Handle menu items if needed
            }

            override fun onTabCounterClicked() {
                val tabsBottomSheet =
                    com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                tabsBottomSheet.show(parentFragmentManager, com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG)
            }

            override fun onScrolled(offset: Int) {
                // Handle scroll if needed
            }
        }

        // Create UnifiedToolbar in the coordinatorLayout (not fragment container)
        unifiedToolbar = UnifiedToolbar.create(
            context = requireContext(),
            parent = coordinatorLayout,
            lifecycleOwner = viewLifecycleOwner,
            interactor = toolbarInteractor,
            customTabSession = null,
            store = components.store
        )
        
        // Add swipe gesture support to toolbar for tab switching
        setupToolbarGestureHandler(coordinatorLayout)
        
        // For TOP toolbar mode, add bottom components directly to fragment layout
        if (prefs.toolbarPosition == ToolbarPosition.TOP.ordinal) {
            val bottomContainer = unifiedToolbar?.getBottomComponentsContainer()
            
            bottomContainer?.let { container ->
                val layoutParams = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM
                }
                
                // Apply window insets to avoid navigation bar
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                    val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    view.setPadding(0, 0, 0, systemBars.bottom)
                    insets
                }
                
                coordinatorLayout.addView(container, layoutParams)
                container.visibility = View.VISIBLE
                
                // Request insets to be applied
                androidx.core.view.ViewCompat.requestApplyInsets(container)
            }
        }

        // Set tab selection listener - using EXACT same approach as TabsBottomSheetFragment
        unifiedToolbar?.setOnTabSelectedListener { tabId ->
            components.tabsUseCases.selectTab(tabId)
            
            // Navigate to browser if the selected tab is not homepage
            val selectedTab = components.store.state.tabs.find { it.id == tabId }
            if (selectedTab?.content?.url != "about:homepage" && selectedTab?.content?.url?.isNotBlank() == true) {
                // Use activity's openToBrowser method like TabsBottomSheetFragment does
                (requireActivity() as BrowserActivity).openToBrowser(
                    from = com.prirai.android.nira.BrowserDirection.FromHome,
                    customTabSessionId = tabId
                )
            }
        }

        // Setup contextual toolbar listener
        unifiedToolbar?.setContextualToolbarListener(object :
            com.prirai.android.nira.toolbar.ContextualBottomToolbar.ContextualToolbarListener {
            override fun onBackClicked() {
                handleTabSwipe(false, components.store.state.selectedTab?.content?.private ?: false)
            }

            override fun onForwardClicked() {
                handleTabSwipe(true, components.store.state.selectedTab?.content?.private ?: false)
            }

            override fun onShareClicked() {
                // Not applicable for homepage
            }

            override fun onSearchClicked() {
                // Pass current selected tab ID for proper context
                val sessionId = components.store.state.selectedTabId
                val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = sessionId)
                findNavController().navigate(directions)
            }

            override fun onNewTabClicked() {
                // Get current profile info for contextId
                val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                val isPrivateMode = components.store.state.selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate
                val currentProfile = profileManager.getActiveProfile()
                val contextId = if (isPrivateMode) "private" else "profile_${currentProfile.id}"
                
                // Create new tab with about:homepage
                components.tabsUseCases.addTab(
                    url = "about:homepage",
                    selectTab = true,
                    private = isPrivateMode,
                    contextId = contextId
                )
                
                // Navigate to browser fragment to show the new tab
                findNavController().navigate(R.id.browserFragment)
            }

            override fun onTabCountClicked() {
                val tabsBottomSheet =
                    com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                tabsBottomSheet.show(
                    parentFragmentManager,
                    com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG
                )
            }

            override fun onMenuClicked() {
                showNativeMenu()
            }

            override fun onBookmarksClicked() {
                val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance()
                bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
            }
        })

        // Update toolbar for homepage context
        unifiedToolbar?.updateContextualToolbar(
            tab = null,
            canGoBack = false,
            canGoForward = false,
            tabCount = getFilteredTabCount(),
            isHomepage = true
        )

        // Apply private mode if needed
        val isPrivate = components.store.state.selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate
        unifiedToolbar?.applyPrivateMode(isPrivate)
        
        // Force tab bar to update with current profile's tabs
        // This ensures the tab bar shows the correct profile immediately when fragment loads
        forceTabBarUpdate()
    }

    private var lastBackPressTime = 0L
    private val BACK_PRESS_TIMEOUT = 4000L // 4 seconds

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateToolbarStyling()
        restoreLastMode()

        // Observe tab state changes - only navigate when URL actually changes to non-homepage
        val store = components.store
        store.flowScoped(viewLifecycleOwner) { flow ->
            flow.map { state -> 
                state.selectedTabId?.let { tabId ->
                    val tab = state.tabs.find { it.id == tabId }
                    tab?.content?.url
                }
            }.distinctUntilChanged()
            .collect { url ->
                // Don't auto-navigate if we explicitly navigated to home from browser
                if (shouldPreventAutoNavigation) {
                    shouldPreventAutoNavigation = false
                    return@collect
                }
                
                // Only navigate to browser when URL changes to a non-homepage URL
                // This prevents automatic navigation back when user explicitly navigates to home
                if (url != null && url != "about:homepage" && url != "about:privatebrowsing") {
                    // Navigate to browser fragment to show the tab
                    if (isAdded && view != null) {
                        try {
                            findNavController().navigate(R.id.browserFragment)
                        } catch (e: Exception) {
                            // Navigation failed, ignore
                        }
                    }
                }
            }
        }
        
        // Observe selected tab changes to update tab bar filtering
        store.flowScoped(viewLifecycleOwner) { flow ->
            flow.map { state -> state.selectedTabId }
                .distinctUntilChanged()
                .collect { _ ->
                    // Check if fragment is still attached before accessing context
                    if (!isAdded || context == null) return@collect
                    
                    // Update toolbar tab count when selected tab changes
                    unifiedToolbar?.updateContextualToolbar(
                        tab = null,
                        canGoBack = false,
                        canGoForward = false,
                        tabCount = getFilteredTabCount(),
                        isHomepage = true
                    )
                    
                    // Also update private mode state
                    val isPrivate = store.state.selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate
                    unifiedToolbar?.applyPrivateMode(isPrivate)
                }
        }

        // Handle double back press to exit app
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < BACK_PRESS_TIMEOUT) {
                    // Second press within timeout - close app
                    requireActivity().finish()
                } else {
                    // First press - show message
                    lastBackPressTime = currentTime
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Perform back gesture again to close app",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadShortcuts()
        viewModel.loadBookmarks()
        updateToolbarStyling()
    }

    private fun updateToolbarStyling() {
        if (!isResumed || !isVisible) {
            return
        }

        // Check the actual selected tab's private state, not the global browsing mode
        val isPrivate = components.store.state.selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate
        
        // Use ThemeManager to apply system bars theme
        com.prirai.android.nira.theme.ThemeManager.applySystemBarsTheme(
            requireActivity(),
            isPrivate
        )

        unifiedToolbar?.applyPrivateMode(isPrivate)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        restoreDefaultStyling()
    }

    private fun restoreDefaultStyling() {
        // Use ThemeManager to restore system bars based on actual tab state
        val isPrivate = components.store.state.selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate
        com.prirai.android.nira.theme.ThemeManager.applySystemBarsTheme(
            requireActivity(),
            isPrivate
        )
    }

    private fun handleTabSwipe(isNext: Boolean, isPrivateMode: Boolean) {
        val currentTab = components.store.state.selectedTab ?: return
        val tabs = components.store.state.getNormalOrPrivateTabs(isPrivateMode)
        val currentIndex = tabs.indexOfFirst { it.id == currentTab.id }

        if (currentIndex == -1) return

        val newIndex = if (isNext) currentIndex + 1 else currentIndex - 1

        if (newIndex in tabs.indices) {
            val newTab = tabs[newIndex]
            components.tabsUseCases.selectTab(newTab.id)

            if (newTab.content.url != "about:homepage") {
                findNavController().navigate(R.id.browserFragment)
            }
        }
    }

    private fun saveLastMode(isPrivate: Boolean, profileId: String?) {
        val prefs = requireContext().getSharedPreferences("home_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("last_was_private", isPrivate)
            putString("last_profile_id", profileId)
            apply()
        }
    }
    
    private fun updateProfileUI() {
        // Update toolbar styling to reflect the new mode (private/normal)
        updateToolbarStyling()
        
        // The Compose UI is reactive and observes store.state.selectedTabId
        // When we select/create a tab with the new profile context above,
        // the Compose HomeScreen will automatically recompose and show the correct profile info
        // because it reads from store.state.selectedTab?.contextId in its setContent block
        
        // Update the unified toolbar's tab count and private mode state
        val isPrivate = components.store.state.selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate
        unifiedToolbar?.applyPrivateMode(isPrivate)
        unifiedToolbar?.updateContextualToolbar(
            tab = null,
            canGoBack = false,
            canGoForward = false,
            tabCount = getFilteredTabCount(),
            isHomepage = true
        )
    }

    private fun restoreLastMode() {
        val prefs = requireContext().getSharedPreferences("home_prefs", android.content.Context.MODE_PRIVATE)
        val wasPrivate = prefs.getBoolean("last_was_private", false)
        val lastProfileId = prefs.getString("last_profile_id", "default")

        if (wasPrivate) {
            browsingModeManager.mode = BrowsingMode.Private
        } else {
            browsingModeManager.mode = BrowsingMode.Normal
            val profileManager =
                com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
            val profile = profileManager.getAllProfiles().find { it.id == lastProfileId }
                ?: com.prirai.android.nira.browser.profile.BrowserProfile.getDefaultProfile()
            profileManager.setActiveProfile(profile)
        }
    }

    private fun setupToolbarGestureHandler(coordinatorLayout: CoordinatorLayout) {
        val toolbar = unifiedToolbar?.getBrowserToolbar() ?: return
        
        // Create a fake tab for preview (will not be visible on homepage)
        val fakeTab = com.prirai.android.nira.browser.FakeTab(requireContext())
        fakeTab.visibility = View.GONE
        coordinatorLayout.addView(fakeTab)
        
        // Create gesture handler for home page
        val gestureHandler = com.prirai.android.nira.browser.toolbar.HomeToolbarGestureHandler(
            activity = requireActivity(),
            contentLayout = coordinatorLayout,
            tabPreview = fakeTab,
            toolbarLayout = toolbar,
            store = components.store,
            selectTabUseCase = components.tabsUseCases.selectTab
        )
        
        // Create gesture detector for toolbar
        val gestureDetector = androidx.core.view.GestureDetectorCompat(
            requireContext(),
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                private var activeListener: com.prirai.android.nira.browser.SwipeGestureListener? = null
                private var handledInitialScroll = false
                
                override fun onDown(e: android.view.MotionEvent): Boolean = true
                
                override fun onScroll(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    val start = e1?.let { android.graphics.PointF(it.rawX, it.rawY) } 
                        ?: return false
                    val next = android.graphics.PointF(e2.rawX, e2.rawY)
                    
                    if (activeListener == null && !handledInitialScroll) {
                        if (gestureHandler.onSwipeStarted(start, next)) {
                            activeListener = gestureHandler
                        }
                        handledInitialScroll = true
                    }
                    activeListener?.onSwipeUpdate(distanceX, distanceY)
                    return activeListener != null
                }
                
                override fun onFling(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    activeListener?.onSwipeFinished(velocityX, velocityY)
                    val handled = activeListener != null
                    activeListener = null
                    handledInitialScroll = false
                    return handled
                }
            }
        )
        
        // Set touch listener on toolbar
        toolbar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_CANCEL,
                android.view.MotionEvent.ACTION_UP -> {
                    gestureDetector.onTouchEvent(event)
                }
                else -> gestureDetector.onTouchEvent(event)
            }
        }
    }

    private fun showNativeMenu() {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val currentProfile = profileManager.getActiveProfile()
        
        // Create menu items using custom Material 3 menu
        val menuItems = listOf(
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "new_tab",
                title = getString(R.string.mozac_browser_menu_new_tab),
                iconRes = R.drawable.mozac_ic_tab_new_24,
                onClick = {
                    val contextId = "profile_${currentProfile.id}"
                    components.tabsUseCases.addTab(
                        url = "about:homepage",
                        selectTab = true,
                        private = false,
                        contextId = contextId
                    )
                }
            ),
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "new_private_tab",
                title = getString(R.string.mozac_browser_menu_new_private_tab),
                iconRes = R.drawable.ic_incognito,
                onClick = {
                    browsingModeManager.mode = BrowsingMode.Private
                    components.tabsUseCases.addTab(
                        url = "about:homepage",
                        selectTab = true,
                        private = true,
                        contextId = "private"
                    )
                }
            ),
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider,
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "history",
                title = getString(R.string.action_history),
                iconRes = R.drawable.ic_baseline_history,
                onClick = {
                    startActivity(android.content.Intent(
                        requireContext(),
                        com.prirai.android.nira.history.HistoryActivity::class.java
                    ).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            ),
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "bookmarks",
                title = getString(R.string.action_bookmarks),
                iconRes = R.drawable.ic_baseline_bookmark,
                onClick = {
                    val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance()
                    bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                }
            ),
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider,
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "settings",
                title = getString(R.string.settings),
                iconRes = R.drawable.ic_round_settings,
                onClick = {
                    startActivity(android.content.Intent(
                        requireContext(),
                        com.prirai.android.nira.settings.activity.SettingsActivity::class.java
                    ).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            ),
            com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                id = "addons",
                title = getString(R.string.extensions),
                iconRes = R.drawable.mozac_ic_extension_24,
                onClick = {
                    val extensionsBottomSheet = com.prirai.android.nira.addons.ExtensionsBottomSheetFragment.newInstance()
                    extensionsBottomSheet.show(parentFragmentManager, com.prirai.android.nira.addons.ExtensionsBottomSheetFragment.TAG)
                }
            )
        )
        
        // Show menu from UnifiedToolbar's menu button
        unifiedToolbar?.showMenu(menuItems)
    }
    
    /**
     * Force the tab bar to update with the current profile's tabs.
     * Called when ComposeHomeFragment loads to ensure correct filtering immediately.
     */
    private fun forceTabBarUpdate() {
        if (!isAdded || context == null) return
        
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val store = components.store.state
        val isPrivateMode = profileManager.isPrivateMode()
        val currentProfile = profileManager.getActiveProfile()
        
        // Determine expected contextId based on current browsing mode
        val expectedContextId = if (isPrivateMode) {
            "private"
        } else {
            "profile_${currentProfile.id}"
        }
        
        // Filter tabs to only show those belonging to current profile/mode
        val filteredTabs = store.tabs.filter { tab ->
            if (tab.contextId == null) {
                // Guest tabs from custom tabs - show in normal mode only
                !isPrivateMode && !tab.content.private
            } else {
                // Regular profile tabs
                tab.content.private == isPrivateMode && tab.contextId == expectedContextId
            }
        }
        
        // Force update the tab bar
        unifiedToolbar?.forceUpdateTabBar(filteredTabs, store.selectedTabId)
    }
    
    /**
     * Get tab count filtered by current profile + guest tabs (null contextId)
     * Uses the same logic as the tab bar to ensure consistency
     */
    private fun getFilteredTabCount(): Int {
        // Safety check - return 0 if fragment is not attached
        if (!isAdded || context == null) return 0
        
        val store = components.store.state
        val selectedTab = store.selectedTab
        
        // Get the context from the selected tab, just like the tab bar does
        val contextId = selectedTab?.contextId
        val isPrivateMode = selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate
        
        return store.tabs.count { tab ->
            val tabIsPrivate = tab.content.private
            
            if (tabIsPrivate != isPrivateMode) {
                false
            } else if (isPrivateMode) {
                tab.contextId == "private"
            } else {
                // Use the selected tab's contextId for filtering
                val expectedContextId = contextId ?: "profile_default"
                // Include tabs with matching contextId OR guest tabs (null contextId for backward compatibility)
                (tab.contextId == expectedContextId) || (tab.contextId == null && expectedContextId == "profile_default")
            }
        }
    }
}
