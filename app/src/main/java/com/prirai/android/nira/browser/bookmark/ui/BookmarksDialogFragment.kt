@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.bookmark.ui

import android.os.Bundle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.navigation.fragment.NavHostFragment
import com.prirai.android.nira.NavGraphDirections
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.components.NiraWindowDialogFragment

/**
 * The bookmarks floating window: the folder/list view with breadcrumb folder
 * navigation, add/edit/delete for bookmarks and folders, the folder-selection
 * move flow and multiselect mode, rendered with the shared Material 3
 * Expressive components.
 *
 * Public entry API is unchanged: `newInstance()` + `show(fragmentManager,
 * tag)`, with the same class name and package.
 */
class BookmarksDialogFragment : NiraWindowDialogFragment() {

    private val manager by lazy { BookmarkManager.getInstance(requireContext()) }

    @Composable
    override fun WindowContent() {
        BookmarksSheet(
            manager = manager,
            initialFolder = resolveStartFolder(manager),
            onOpenBookmark = ::openBookmark,
            onDismiss = ::dismiss,
        )
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

        fun newInstance(folderId: Long = -1L): BookmarksDialogFragment {
            return BookmarksDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_FOLDER_ID, folderId)
                }
            }
        }
    }
}
