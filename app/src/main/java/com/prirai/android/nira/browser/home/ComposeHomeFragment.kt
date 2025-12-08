package com.prirai.android.nira.browser.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.toColorInt
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
import com.prirai.android.nira.browser.home.compose.*
import com.prirai.android.nira.browser.shortcuts.ShortcutDatabase
import com.prirai.android.nira.browser.tabgroups.TabGroupBar
import com.prirai.android.nira.browser.tabgroups.TabGroupManager
import com.prirai.android.nira.components.toolbar.BrowserToolbarViewInteractor
import com.prirai.android.nira.components.toolbar.ToolbarMenu
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.components.toolbar.modern.ModernContextualToolbar
import com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.HomepageBackgroundChoice
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.selector.selectedTab

class ComposeHomeFragment : Fragment() {

    private val browsingModeManager get() = (activity as BrowserActivity).browsingModeManager
    private val components get() = requireContext().components

    private lateinit var viewModel: HomeViewModel
    private var unifiedToolbar: UnifiedToolbar? = null

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
            context = requireContext(),
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
                val selectedTab = store.state.selectedTabId?.let { id ->
                    store.state.tabs.find { it.id == id }
                }

                val isPrivateMode = selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate

                val shortcuts by viewModel.shortcuts.collectAsState()
                val bookmarks by viewModel.bookmarks.collectAsState()
                val showAddDialog by viewModel.showAddShortcutDialog.collectAsState()
                val isBookmarkExpanded by viewModel.isBookmarkSectionExpanded.collectAsState()

                val tabCount = if (isPrivateMode) {
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
                            ProfileInfo("private", "Private", "🕵️", true)
                        }

                        tabContextId != null && tabContextId.startsWith("profile_") -> {
                            val profileId = tabContextId.removePrefix("profile_")
                            val profile = profileManager.getAllProfiles().find { it.id == profileId }
                                ?: profileManager.getActiveProfile()
                            ProfileInfo(profile.id, profile.name, profile.emoji, false)
                        }

                        else -> {
                            val profile = profileManager.getActiveProfile()
                            ProfileInfo(profile.id, profile.name, profile.emoji, false)
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

                NiraTheme(isPrivateMode = isPrivateMode) {
                    HomeScreen(
                        isPrivateMode = isPrivateMode,
                        shortcuts = shortcuts,
                        bookmarks = bookmarks,
                        isBookmarkExpanded = isBookmarkExpanded,
                        tabCount = tabCount,
                        currentProfile = currentProfile,
                        backgroundImageUrl = backgroundImageUrl,
                        onProfileClick = { showProfileSwitcher() },
                        onShortcutClick = { shortcut ->
                            components.sessionUseCases.loadUrl(shortcut.url)
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
                            }
                        },
                        onBookmarkToggle = { viewModel.toggleBookmarkSection() },
                        onSearchClick = {
                            val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = null)
                            findNavController().navigate(directions)
                        },
                        onBookmarksButtonClick = {
                            val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance()
                            bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                        },
                        onExtensionsClick = {
                            val intent = android.content.Intent(
                                requireContext(),
                                com.prirai.android.nira.addons.AddonsActivity::class.java
                            )
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        },
                        onTabCountClick = {
                            val tabsBottomSheet =
                                com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                            tabsBottomSheet.show(
                                parentFragmentManager,
                                com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG
                            )
                        },
                        onMenuClick = { showNativeMenu() }
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
        val tabGroupManager = requireContext().components.tabGroupManager

        // Create toolbar interactor
        val toolbarInteractor = object : BrowserToolbarViewInteractor {
            override fun onBrowserToolbarPaste(text: String) {
                val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = null)
                findNavController().navigate(directions)
            }

            override fun onBrowserToolbarPasteAndGo(text: String) {
                components.sessionUseCases.loadUrl(text)
            }

            override fun onBrowserToolbarClicked() {
                val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = null)
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
        
        // For TOP toolbar mode, add bottom components directly to fragment layout
        if (prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.TOP.ordinal) {
            val bottomContainer = unifiedToolbar?.getBottomComponentsContainer()
            
            bottomContainer?.let { container ->
                val layoutParams = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM
                }
                
                coordinatorLayout.addView(container, layoutParams)
                container.visibility = android.view.View.VISIBLE
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
                val directions = NavGraphDirections.actionGlobalSearchDialog(sessionId = null)
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
            tabCount = components.store.state.tabs.size,
            isHomepage = true
        )

        // Apply private mode if needed
        val isPrivate = components.store.state.selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate
        unifiedToolbar?.applyPrivateMode(isPrivate)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateToolbarStyling()
        restoreLastMode()
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

        val isPrivate = browsingModeManager.mode.isPrivate

        if (isPrivate) {
            requireActivity().window.statusBarColor = "#6A1B9A".toColorInt()
            requireActivity().window.navigationBarColor = "#6A1B9A".toColorInt()
        } else {
            val typedValue = android.util.TypedValue()
            val theme = requireContext().theme

            if (theme.resolveAttribute(
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    typedValue,
                    true
                )
            ) {
                requireActivity().window.statusBarColor = typedValue.data
            }

            val elevationDp = 3f * resources.displayMetrics.density
            val elevatedColor = com.google.android.material.elevation.ElevationOverlayProvider(requireContext())
                .compositeOverlayWithThemeSurfaceColorIfNeeded(elevationDp)
            requireActivity().window.navigationBarColor = elevatedColor
        }

        unifiedToolbar?.applyPrivateMode(isPrivate)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        restoreDefaultStyling()
    }

    private fun restoreDefaultStyling() {
        val typedValue = android.util.TypedValue()
        val theme = requireContext().theme

        if (theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceVariant,
                typedValue,
                true
            )
        ) {
            requireActivity().window.statusBarColor = typedValue.data
        }

        requireActivity().window.navigationBarColor = android.graphics.Color.TRANSPARENT
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

    private fun showProfileSwitcher() {
        val profileManager =
            com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val allProfiles = profileManager.getAllProfiles()
        val privateProfile = "🕵️ Private"

        val items = allProfiles.map { "${it.emoji} ${it.name}" }.toMutableList()
        items.add(privateProfile)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Switch Profile")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == items.size - 1) {
                    browsingModeManager.mode = BrowsingMode.Private
                    saveLastMode(isPrivate = true, profileId = null)

                    val store = components.store
                    val privateTabs = store.state.tabs.filter { it.content.private }

                    if (privateTabs.isEmpty()) {
                        components.tabsUseCases.addTab(
                            url = "about:blank",
                            private = true,
                            contextId = "private"
                        )
                    } else {
                        components.tabsUseCases.selectTab(privateTabs.first().id)
                    }
                } else {
                    val selectedProfile = allProfiles[which]
                    browsingModeManager.mode = BrowsingMode.Normal
                    profileManager.setActiveProfile(selectedProfile)
                    saveLastMode(isPrivate = false, profileId = selectedProfile.id)

                    val store = components.store
                    val contextId = "profile_${selectedProfile.id}"
                    val profileTabs = store.state.tabs.filter {
                        it.contextId == contextId && !it.content.private
                    }

                    if (profileTabs.isEmpty()) {
                        components.tabsUseCases.addTab(
                            url = "about:blank",
                            private = false,
                            contextId = contextId
                        )
                    } else {
                        components.tabsUseCases.selectTab(profileTabs.first().id)
                    }
                }

                try {
                    findNavController().navigate(R.id.homeFragment)
                } catch (e: Exception) {
                    viewModel.loadShortcuts()
                    viewModel.loadBookmarks()
                }
            }
            .show()
    }

    private fun saveLastMode(isPrivate: Boolean, profileId: String?) {
        val prefs = requireContext().getSharedPreferences("home_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("last_was_private", isPrivate)
            putString("last_profile_id", profileId)
            apply()
        }
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

    private fun showNativeMenu() {
        var menuBuilder: mozilla.components.browser.menu.BrowserMenuBuilder? = null

        HomeMenu(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            onItemTapped = { item ->
                when (item) {
                    is HomeMenu.Item.NewTab -> {
                        // Create new tab in current profile with about:homepage
                        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                        val currentProfile = profileManager.getActiveProfile()
                        val contextId = "profile_${currentProfile.id}"
                        
                        components.tabsUseCases.addTab(
                            url = "about:homepage",
                            selectTab = true,
                            private = false,
                            contextId = contextId
                        )
                        
                        // Stay on homeFragment as it will show the new tab
                    }

                    is HomeMenu.Item.NewPrivateTab -> {
                        // Create new private tab with about:homepage
                        browsingModeManager.mode = BrowsingMode.Private
                        
                        components.tabsUseCases.addTab(
                            url = "about:homepage",
                            selectTab = true,
                            private = true,
                            contextId = "private"
                        )
                        
                        // Stay on homeFragment as it will show the new tab
                    }

                    is HomeMenu.Item.Bookmarks -> {
                        val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance()
                        bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                    }

                    is HomeMenu.Item.History -> {
                        startActivity(android.content.Intent(
                            requireContext(),
                            com.prirai.android.nira.history.HistoryActivity::class.java
                        ).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }

                    is HomeMenu.Item.AddonsManager -> {
                        startActivity(android.content.Intent(
                            requireContext(),
                            com.prirai.android.nira.addons.AddonsActivity::class.java
                        ).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }

                    is HomeMenu.Item.Settings -> {
                        startActivity(android.content.Intent(
                            requireContext(),
                            com.prirai.android.nira.settings.activity.SettingsActivity::class.java
                        ).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }
            },
            onMenuBuilderChanged = { builder ->
                menuBuilder = builder
            }
        )

        menuBuilder?.let { builder ->
            val menu = builder.build(requireContext())
            val prefs = UserPreferences(requireContext())
            val isBottomToolbar =
                prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal

            val decorView = requireActivity().window.decorView as ViewGroup
            val anchorView = View(requireContext()).apply {
                id = View.generateViewId()
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
                menu.show(anchor = anchorView)
            }
        }
    }
}
