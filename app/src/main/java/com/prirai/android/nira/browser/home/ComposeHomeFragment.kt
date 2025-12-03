package com.prirai.android.nira.browser.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
                // Use remember and derivedStateOf to make isPrivateMode reactive
                val isPrivateMode by remember {
                    derivedStateOf { browsingModeManager.mode.isPrivate }
                }
                val shortcuts by viewModel.shortcuts.collectAsState()
                val bookmarks by viewModel.bookmarks.collectAsState()
                val showAddDialog by viewModel.showAddShortcutDialog.collectAsState()
                val isBookmarkExpanded by viewModel.isBookmarkSectionExpanded.collectAsState()
                
                // Get tab count - also make it reactive
                val tabCount by remember {
                    derivedStateOf {
                        val store = requireContext().components.store.state
                        if (browsingModeManager.mode.isPrivate) {
                            store.privateTabs.size
                        } else {
                            store.normalTabs.size
                        }
                    }
                }
                
                // Get current profile
                val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(requireContext())
                val currentProfile by remember {
                    derivedStateOf {
                        if (browsingModeManager.mode.isPrivate) {
                            ProfileInfo("private", "Private", "🕵️", true)
                        } else {
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
                            val intent = android.content.Intent(requireContext(), com.prirai.android.nira.addons.AddonsActivity::class.java)
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        },
                        onTabCountClick = {
                            // Open tabs bottom sheet
                            val tabsBottomSheet = com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                            tabsBottomSheet.show(parentFragmentManager, com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG)
                        },
                        onMenuClick = {
                            // Show home menu
                            showHomeMenu()
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
        
        // Restore last mode on view creation
        restoreLastMode()
    }
    
    override fun onResume() {
        super.onResume()
        // Reload data when fragment resumes
        viewModel.loadShortcuts()
        viewModel.loadBookmarks()
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
                } else {
                    // Switch to selected profile
                    val selectedProfile = allProfiles[which]
                    browsingModeManager.mode = BrowsingMode.Normal
                    profileManager.setActiveProfile(selectedProfile)
                    saveLastMode(isPrivate = false, profileId = selectedProfile.id)
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
    
    private fun showHomeMenu() {
        val menuButton = MenuButton(requireContext())
        val homeMenu = HomeMenu(
            lifecycleOwner = viewLifecycleOwner,
            context = requireContext(),
            onItemTapped = { item: HomeMenu.Item ->
                when (item) {
                    HomeMenu.Item.NewTab -> {
                        androidx.navigation.fragment.NavHostFragment.findNavController(this)
                            .navigate(R.id.homeFragment)
                    }
                    HomeMenu.Item.NewPrivateTab -> {
                        val browsingModeManager = (requireActivity() as BrowserActivity).browsingModeManager
                        browsingModeManager.mode = BrowsingMode.Private
                        androidx.navigation.fragment.NavHostFragment.findNavController(this)
                            .navigate(R.id.homeFragment)
                    }
                    HomeMenu.Item.Bookmarks -> {
                        val bookmarksBottomSheet = BookmarksBottomSheetFragment.newInstance()
                        bookmarksBottomSheet.show(parentFragmentManager, "BookmarksBottomSheet")
                    }
                    HomeMenu.Item.History -> {
                        val intent = android.content.Intent(requireContext(), com.prirai.android.nira.history.HistoryActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                    HomeMenu.Item.AddonsManager -> {
                        val intent = android.content.Intent(requireContext(), com.prirai.android.nira.addons.AddonsActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                    HomeMenu.Item.Settings -> {
                        val intent = android.content.Intent(requireContext(), com.prirai.android.nira.settings.activity.SettingsActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                }
            },
            onMenuBuilderChanged = { builder ->
                menuButton.menuBuilder = builder
            }
        )
        
        // Show menu anchored to the bottom of the screen
        view?.let { v -> 
            // Create an anchor view at the bottom
            val anchorView = v.findViewById<android.view.View>(android.R.id.content) ?: v
            menuButton.menuBuilder?.build(requireContext())?.show(
                anchor = anchorView,
                orientation = mozilla.components.browser.menu.BrowserMenu.Orientation.UP
            )
        }
    }
    
    companion object {
        fun newInstance(): ComposeHomeFragment {
            return ComposeHomeFragment()
        }
    }
}
