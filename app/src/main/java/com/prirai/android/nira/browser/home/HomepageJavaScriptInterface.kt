package com.prirai.android.nira.browser.home

import android.content.Context
import android.webkit.JavascriptInterface
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.browser.shortcuts.ShortcutDatabase
import com.prirai.android.nira.browser.shortcuts.ShortcutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mozilla.components.support.base.log.logger.Logger
import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript interface for the HTML-based homepage
 * Provides access to shortcuts, bookmarks and handles search/navigation
 */
class HomepageJavaScriptInterface(private val context: Context) {

    private val logger = Logger("HomepageJavaScriptInterface")

    @JavascriptInterface
    fun getShortcuts(): String {
        val shortcuts = JSONArray()

        try {
            // Use the process-wide singleton so migrations run once and we don't
            // race with other callsites opening the same SQLite file. The JS
            // bridge is synchronous, so we hop to Dispatchers.IO via runBlocking
            // instead of the previous `.allowMainThreadQueries()` builder flag.
            val dao = ShortcutDatabase.getInstance(context).shortcutDao()
            val items = runBlocking(Dispatchers.IO) { dao.getAll() }

            items.take(12).forEach { shortcut: ShortcutEntity ->
                val obj = JSONObject()
                obj.put("uid", shortcut.uid) // Add ID for deletion
                obj.put("title", shortcut.title ?: "")
                obj.put("url", shortcut.url ?: "")
                // Icon will be loaded via favicon cache or use fallback letter
                obj.put("icon", null)
                shortcuts.put(obj)
            }
        } catch (e: Exception) {
            // The homepage JS bridge is synchronous - we return a JSON string
            // that the WebView shows as the shortcut grid. If we can't read
            // the DB (schema mismatch, disk full, IO error, ...) the grid
            // ends up empty; log so we can diagnose why instead of showing
            // the user an unexplained blank grid.
            logger.error("Failed to load shortcuts for homepage", e)
        }

        return shortcuts.toString()
    }
    
    @JavascriptInterface
    fun getBookmarks(): String {
        val bookmarks = JSONArray()
        
        try {
            val manager = BookmarkManager.getInstance(context)
            
            // Get all bookmarks from the root folder recursively
            val allBookmarks = mutableListOf<BookmarkSiteItem>()
            collectBookmarks(manager.root, allBookmarks)
            
            // Take up to 20 most recent bookmarks
            allBookmarks.take(20).forEach { bookmark ->
                val obj = JSONObject()
                obj.put("title", bookmark.title ?: "")
                obj.put("url", bookmark.url ?: "")
                obj.put("id", bookmark.id)
                // Icon will be loaded via favicon cache or use fallback
                obj.put("icon", null)
                bookmarks.put(obj)
            }
        } catch (e: Exception) {
            // Same rationale as getShortcuts() above: log rather than silently
            // return an empty list.
            logger.error("Failed to load bookmarks for homepage", e)
        }

        return bookmarks.toString()
    }
    
    /**
     * Recursively collect all bookmark sites from a folder
     */
    private fun collectBookmarks(folder: BookmarkFolderItem, result: MutableList<BookmarkSiteItem>) {
        folder.list.forEach { item ->
            when (item) {
                is BookmarkSiteItem -> result.add(item)
                is BookmarkFolderItem -> collectBookmarks(item, result)
            }
        }
    }
}
