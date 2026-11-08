@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.bookmark.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.NavGraphDirections
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.theme.NiraTheme

/**
 * The bookmarks bottom sheet: the folder/list view with breadcrumb folder
 * navigation, add/edit/delete for bookmarks and folders, the folder-selection
 * move flow and multiselect mode, rendered with the shared Material 3
 * Expressive components.
 *
 * Public entry API is unchanged: `newInstance()` + `show(fragmentManager,
 * tag)`, with the same class name and package.
 */
class BookmarksBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val manager = BookmarkManager.getInstance(requireContext())
        val startFolder = resolveStartFolder(manager)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val prefs = UserPreferences(context)
                NiraTheme(
                    darkTheme = ThemeManager.isDarkMode(context),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors,
                ) {
                    BookmarksSheet(
                        manager = manager,
                        initialFolder = startFolder,
                        onOpenBookmark = ::openBookmark,
                        onDismiss = ::dismiss,
                    )
                }
            }
        }
    }

    private fun resolveStartFolder(manager: BookmarkManager): BookmarkFolderItem {
        val folderId = arguments?.getLong(ARG_FOLDER_ID, -1L) ?: -1L
        if (folderId == -1L) {
            return manager.root
        }
        return findFolderById(manager.root, folderId) ?: manager.root
    }

    private fun findFolderById(folder: BookmarkFolderItem, targetId: Long): BookmarkFolderItem? {
        if (folder.id == targetId) return folder
        for (item in folder.list) {
            if (item is BookmarkFolderItem) {
                val found = findFolderById(item, targetId)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Opens a tapped bookmark in a new selected tab and, when the sheet was
     * opened from the home screen, navigates to the browser, as the old
     * RecyclerView implementation did.
     */
    private fun openBookmark(site: BookmarkSiteItem) {
        requireContext().components.tabsUseCases.addTab.invoke(
            url = site.url,
            selectTab = true,
        )
        try {
            val navController = NavHostFragment.findNavController(this)
            if (navController.currentDestination?.id == R.id.homeFragment) {
                navController.navigate(NavGraphDirections.actionGlobalBrowser(null))
            }
        } catch (e: Exception) {
            // Ignore navigation errors
        }
        dismiss()
    }

    companion object {
        private const val ARG_FOLDER_ID = "folder_id"

        fun newInstance(folderId: Long = -1L): BookmarksBottomSheetFragment {
            return BookmarksBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_FOLDER_ID, folderId)
                }
            }
        }
    }
}
