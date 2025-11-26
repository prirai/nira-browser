package com.prirai.android.nira.browser.home

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.room.Room
import com.prirai.android.nira.browser.shortcuts.ShortcutDatabase
import com.prirai.android.nira.browser.shortcuts.ShortcutEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript interface for the HTML-based homepage
 * Provides access to shortcuts and handles search/navigation
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
}
