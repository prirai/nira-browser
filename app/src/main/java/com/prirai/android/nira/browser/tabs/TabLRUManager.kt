package com.prirai.android.nira.browser.tabs

import android.content.Context
import java.util.LinkedList
import androidx.core.content.edit

class TabLRUManager private constructor(context: Context) {
    
    private val lruQueue = LinkedList<String>()
    private val prefs = context.getSharedPreferences("tab_lru", Context.MODE_PRIVATE)
    
    init {
        loadFromPrefs()
    }
    
    private val swipeNavigatedTabs = mutableSetOf<String>()
    
    fun markAsSwipeNavigation(tabId: String) {
        swipeNavigatedTabs.add(tabId)
    }
    
    fun onTabSelected(tabId: String) {
        if (swipeNavigatedTabs.remove(tabId)) {
            return
        }
        
        lruQueue.remove(tabId)
        lruQueue.addFirst(tabId)
        saveToPrefs()
    }
    
    fun onTabClosed(tabId: String) {
        lruQueue.remove(tabId)
        saveToPrefs()
    }
    
    /**
     * Synchronize LRU queue with current tabs after restore.
     * Removes stale IDs and adds new tabs to the end.
     */
    fun synchronizeWithTabs(currentTabIds: List<String>) {
        // Remove tab IDs that no longer exist
        lruQueue.retainAll(currentTabIds.toSet())
        
        // Add new tabs that aren't in the queue yet (at the end)
        for (tabId in currentTabIds) {
            if (!lruQueue.contains(tabId)) {
                lruQueue.addLast(tabId)
            }
        }
        
        saveToPrefs()
    }
    
    fun getTabAtLRUOffset(currentTabId: String, offset: Int): String? {
        val currentIndex = lruQueue.indexOf(currentTabId)
        if (currentIndex == -1) return null
        
        val targetIndex = currentIndex + offset
        
        // Handle rollover: if at the end (most recent) and swiping right (-1),
        // wrap around to the oldest tab (last in queue)
        return when {
            targetIndex < 0 -> {
                // Swiping to newer tabs, but already at newest
                // Rollover to oldest tab
                lruQueue.lastOrNull()
            }
            targetIndex >= lruQueue.size -> {
                // Swiping to older tabs, but already at oldest
                // Rollover to newest tab
                lruQueue.firstOrNull()
            }
            else -> {
                lruQueue[targetIndex]
            }
        }
    }
    
    fun getMostRecentTab(): String? = lruQueue.firstOrNull()
    
    private fun saveToPrefs() {
        prefs.edit { putString("lru_order", lruQueue.joinToString(",")) }
    }
    
    private fun loadFromPrefs() {
        val saved = prefs.getString("lru_order", "") ?: ""
        if (saved.isNotEmpty()) {
            lruQueue.clear()
            lruQueue.addAll(saved.split(",").filter { it.isNotEmpty() })
        }
    }
    
    companion object {
        @Volatile
        private var instance: TabLRUManager? = null
        
        fun getInstance(context: Context): TabLRUManager {
            return instance ?: synchronized(this) {
                instance ?: TabLRUManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
