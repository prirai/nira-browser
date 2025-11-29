package com.prirai.android.nira.browser.home

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.room.Room
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.browser.shortcuts.ShortcutDatabase
import com.prirai.android.nira.browser.shortcuts.ShortcutEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript interface for the HTML-based homepage
 * Provides access to shortcuts, bookmarks and handles search/navigation
 */
class HomepageJavaScriptInterface(private val context: Context) {
    
    @JavascriptInterface
    fun getShortcuts(): String {
        val shortcuts = JSONArray()
        
        try {
            val database = Room.databaseBuilder(
                context,
                ShortcutDatabase::class.java,
                "shortcut-database"
            ).allowMainThreadQueries().build()
            
            val dao = database.shortcutDao()
            val items = dao.getAll()
            
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
            // Return empty array on error
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
            // Return empty array on error
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
