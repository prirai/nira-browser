package com.prirai.android.nira.browser.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.HomepageChoice
import com.prirai.android.nira.ui.theme.NiraTheme
import com.prirai.android.nira.components.toolbar.ToolbarMenu
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.privateTabs

import com.prirai.android.nira.browser.home.HomeMenu
import mozilla.components.browser.menu.view.MenuButton

class ComposeHomeFragment : Fragment() {

    private val browsingModeManager get() = (activity as BrowserActivity).browsingModeManager
    private val components get() = requireContext().components

    private lateinit var viewModel: HomeViewModel

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
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Get the store
                val store = requireContext().components.store

                // Observe the selected tab - read from state directly in composition
                val selectedTab = store.state.selectedTabId?.let { id ->
                    store.state.tabs.find { it.id == id }
                }

                // Determine private mode from the selected tab
                val isPrivateMode = selectedTab?.content?.private ?: browsingModeManager.mode.isPrivate

                val shortcuts by viewModel.shortcuts.collectAsState()
                val bookmarks by viewModel.bookmarks.collectAsState()
                val showAddDialog by viewModel.showAddShortcutDialog.collectAsState()
                val isBookmarkExpanded by viewModel.isBookmarkSectionExpanded.collectAsState()

                // Get tab count
                val tabCount = if (isPrivateMode) {
                    store.state.privateTabs.size
                } else {
                    store.state.normalTabs.size
                }

                // Get current profile based on tab's contextId
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

                NiraTheme(
                    isPrivateMode = isPrivateMode
                ) {
                    HomeScreen(
                        isPrivateMode = isPrivateMode,
                        shortcuts = shortcuts,
                        bookmarks = bookmarks,
                        isBookmarkExpanded = isBookmarkExpanded,
                        tabCount = tabCount,
                        currentProfile = currentProfile,
                        onProfileClick = {
                            showProfileSwitcher()
                        },
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
                        onShortcutAdd = {
                            viewModel.showAddShortcutDialog()
                        },
                        onBookmarkClick = { bookmark ->
                            if (bookmark.isFolder) {
                                // Open bookmarks bottom sheet with this specific folder
                                val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance(
                                    folderId = bookmark.id.toLongOrNull() ?: -1L
                                )
                                bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                            } else {
                                components.sessionUseCases.loadUrl(bookmark.url)
                            }
                        },
                        onBookmarkToggle = {
                            viewModel.toggleBookmarkSection()
                        },
                        onSearchClick = {
                            // Open search dialog - it will handle tab creation if needed
                            val directions = NavGraphDirections.actionGlobalSearchDialog(
                                sessionId = null
                            )
                            findNavController().navigate(directions)
                        },
                        onBookmarksButtonClick = {
                            // Open bookmarks bottom sheet
                            val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance()
                            bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                        },
                        onExtensionsClick = {
                            // Open extensions
                            val intent = android.content.Intent(
                                requireContext(),
                                com.prirai.android.nira.addons.AddonsActivity::class.java
                            )
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        },
                        onTabCountClick = {
                            // Open tabs bottom sheet
                            val tabsBottomSheet =
                                com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                            tabsBottomSheet.show(
                                parentFragmentManager,
                                com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG
                            )
                        },
                        onMenuClick = {
                            // Instead of showing compose menu, use the native browser menu
                            showNativeMenu()
                        }
                    )

                    // Add shortcut dialog
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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Update toolbar styling for private mode
        updateToolbarStyling()

        // Restore last mode on view creation
        restoreLastMode()
    }

    override fun onResume() {
        super.onResume()
        // Reload data when fragment resumes
        viewModel.loadShortcuts()
        viewModel.loadBookmarks()
        // Update styling when resuming
        updateToolbarStyling()
    }

    private fun updateToolbarStyling() {
        // Only apply purple styling if we're actually on this homepage AND in private mode
        // Check if this fragment is currently visible and resumed
        if (!isResumed || !isVisible) {
            return
        }

        val isPrivate = browsingModeManager.mode.isPrivate

        if (isPrivate) {
            // Purple background for private mode
            requireActivity().window.statusBarColor = android.graphics.Color.parseColor("#6A1B9A")
            requireActivity().window.navigationBarColor = android.graphics.Color.parseColor("#6A1B9A")
        } else {
            // Default theme color - use the actual theme attributes
            val typedValue = android.util.TypedValue()
            val theme = requireContext().theme

            // For status bar - use surface or primary variant
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
                requireActivity().window.statusBarColor = typedValue.data
            }

            // For navigation bar - use surface
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                requireActivity().window.navigationBarColor = typedValue.data
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Restore default styling when leaving the homepage
        restoreDefaultStyling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Restore default styling when view is destroyed
        restoreDefaultStyling()
    }

    private fun restoreDefaultStyling() {
        // Restore default system bar colors
        val typedValue = android.util.TypedValue()
        val theme = requireContext().theme

        // Restore status bar
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
            requireActivity().window.statusBarColor = typedValue.data
        }

        // Restore navigation bar
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
            requireActivity().window.navigationBarColor = typedValue.data
        }
    }

    private fun showProfileSwitcher() {
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
        val allProfiles = profileManager.getAllProfiles()
        val privateProfile = "🕵️ Private"

        // Build items list
        val items = allProfiles.map { "${it.emoji} ${it.name}" }.toMutableList()
        items.add(privateProfile)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Switch Profile")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == items.size - 1) {
                    // Switch to private mode
                    browsingModeManager.mode = BrowsingMode.Private
                    saveLastMode(isPrivate = true, profileId = null)

                    // Find or create a private tab
                    val store = components.store
                    val privateTabs = store.state.tabs.filter { it.content.private }

                    if (privateTabs.isEmpty()) {
                        // Create a new private tab
                        components.tabsUseCases.addTab(
                            url = "about:blank",
                            private = true,
                            contextId = "private"
                        )
                    } else {
                        // Select the first private tab
                        components.tabsUseCases.selectTab(privateTabs.first().id)
                    }
                } else {
                    // Switch to selected profile
                    val selectedProfile = allProfiles[which]
                    browsingModeManager.mode = BrowsingMode.Normal
                    profileManager.setActiveProfile(selectedProfile)
                    saveLastMode(isPrivate = false, profileId = selectedProfile.id)

                    // Find or create a tab with this profile's contextId
                    val store = components.store
                    val contextId = "profile_${selectedProfile.id}"
                    val profileTabs = store.state.tabs.filter {
                        it.contextId == contextId && !it.content.private
                    }

                    if (profileTabs.isEmpty()) {
                        // Create a new tab for this profile
                        components.tabsUseCases.addTab(
                            url = "about:blank",
                            private = false,
                            contextId = contextId
                        )
                    } else {
                        // Select the first tab of this profile
                        components.tabsUseCases.selectTab(profileTabs.first().id)
                    }
                }

                // Navigate to new home fragment to show the new profile
                try {
                    findNavController().navigate(R.id.homeFragment)
                } catch (e: Exception) {
                    // Already on homeFragment, just reload
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
            val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
            val profile = profileManager.getAllProfiles().find { it.id == lastProfileId }
                ?: com.prirai.android.nira.browser.profile.BrowserProfile.getDefaultProfile()
            profileManager.setActiveProfile(profile)
        }
    }

    private fun showNativeMenu() {
        // Create HomeMenu using Mozilla Components
        try {
            val components = requireContext().components
            var menuBuilder: mozilla.components.browser.menu.BrowserMenuBuilder? = null

            // Create menu with home-specific items
            val homeMenu = com.prirai.android.nira.browser.home.HomeMenu(
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner,
                onItemTapped = { item ->
                    when (item) {
                        is com.prirai.android.nira.browser.home.HomeMenu.Item.NewTab -> {
                            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                                .navigate(R.id.homeFragment)
                        }

                        is com.prirai.android.nira.browser.home.HomeMenu.Item.NewPrivateTab -> {
                            val browsingModeManager = (requireActivity() as BrowserActivity).browsingModeManager
                            browsingModeManager.mode = BrowsingMode.Private
                            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                                .navigate(R.id.homeFragment)
                        }

                        is com.prirai.android.nira.browser.home.HomeMenu.Item.Bookmarks -> {
                            val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance()
                            bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                        }

                        is com.prirai.android.nira.browser.home.HomeMenu.Item.History -> {
                            val intent = android.content.Intent(
                                requireContext(),
                                com.prirai.android.nira.history.HistoryActivity::class.java
                            )
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }

                        is com.prirai.android.nira.browser.home.HomeMenu.Item.AddonsManager -> {
                            val intent = android.content.Intent(
                                requireContext(),
                                com.prirai.android.nira.addons.AddonsActivity::class.java
                            )
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }

                        is com.prirai.android.nira.browser.home.HomeMenu.Item.Settings -> {
                            val intent = android.content.Intent(
                                requireContext(),
                                com.prirai.android.nira.settings.activity.SettingsActivity::class.java
                            )
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                    }
                },
                onMenuBuilderChanged = { builder ->
                    menuBuilder = builder
                }
            )

            // Build and show the menu
            menuBuilder?.let { builder ->
                val menu = builder.build(requireContext())

                // Get toolbar position preference
                val prefs = com.prirai.android.nira.preferences.UserPreferences(requireContext())
                val isBottomToolbar =
                    prefs.toolbarPosition == com.prirai.android.nira.components.toolbar.ToolbarPosition.BOTTOM.ordinal

                // Get the DecorView which is always available and stable
                val decorView = requireActivity().window.decorView as? android.view.ViewGroup

                if (decorView != null) {
                    // Create persistent anchor view
                    val anchorView = android.view.View(requireContext())
                    anchorView.id = android.view.View.generateViewId()
                    val params = android.widget.FrameLayout.LayoutParams(10, 10) // Small but visible

                    if (isBottomToolbar) {
                        // Position at bottom right
                        params.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                        params.bottomMargin = 200
                        params.rightMargin = 20
                    } else {
                        // Position at top right
                        params.gravity = android.view.Gravity.TOP or android.view.Gravity.END
                        params.topMargin = 200
                        params.rightMargin = 20
                    }

                    anchorView.layoutParams = params
                    decorView.addView(anchorView)

                    // Post to ensure view is attached
                    anchorView.post {
                        try {
                            // Show menu
                            menu.show(anchor = anchorView)

                            // Clean up anchor after a reasonable timeout (menu auto-dismisses on selection)
                            anchorView.postDelayed({
                                try {
                                    decorView.removeView(anchorView)
                                } catch (e: Exception) {
                                    // Already removed
                                }
                            }, 30000) // 30 seconds max
                        } catch (e: Exception) {
                            android.util.Log.e("ComposeHomeFragment", "Error showing menu", e)
                            try {
                                decorView.removeView(anchorView)
                            } catch (ex: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ComposeHomeFragment", "Failed to show menu", e)
            // Fallback to simple dialog
            showSimpleMenu()
        }
    }

    private fun showSimpleMenu() {
        val menuItems = arrayOf(
            "New Tab",
            "New Private Tab",
            "Bookmarks",
            "History",
            "Extensions",
            "Settings"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Menu")
            .setItems(menuItems) { _, which ->
                handleMenuItem(menuItems[which])
            }
            .show()
    }

    private fun handleMenuItem(item: String) {
        when (item) {
            "New Tab" -> {
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(R.id.homeFragment)
            }

            "New Private Tab" -> {
                val browsingModeManager = (requireActivity() as BrowserActivity).browsingModeManager
                browsingModeManager.mode = BrowsingMode.Private
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(R.id.homeFragment)
            }

            "Bookmarks" -> {
                val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance()
                bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
            }

            "History" -> {
                val intent = android.content.Intent(
                    requireContext(),
                    com.prirai.android.nira.history.HistoryActivity::class.java
                )
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }

            "Extensions" -> {
                val intent =
                    android.content.Intent(requireContext(), com.prirai.android.nira.addons.AddonsActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }

            "Settings" -> {
                val intent = android.content.Intent(
                    requireContext(),
                    com.prirai.android.nira.settings.activity.SettingsActivity::class.java
                )
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
    }

    companion object {
        fun newInstance(): ComposeHomeFragment {
            return ComposeHomeFragment()
        }
    }
}
