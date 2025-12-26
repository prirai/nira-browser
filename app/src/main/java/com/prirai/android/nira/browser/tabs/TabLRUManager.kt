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
     * Order: [accessed tabs in LRU order] + [unvisited tabs in their original order]
     */
    fun synchronizeWithTabs(currentTabIds: List<String>) {
        // Keep only existing tabs in LRU queue (these are accessed tabs)
        lruQueue.retainAll(currentTabIds.toSet())
        
        // Find tabs not in LRU queue (unvisited tabs) and add them in order they appear
        val unvisitedTabs = currentTabIds.filter { !lruQueue.contains(it) }
        lruQueue.addAll(unvisitedTabs)
        
        saveToPrefs()
    }
    
    fun getTabAtLRUOffset(currentTabId: String, offset: Int): String? {
        val currentIndex = lruQueue.indexOf(currentTabId)
        if (currentIndex == -1) return null
        
        val targetIndex = currentIndex + offset
        
        // Wrap around at boundaries for circular navigation
        return when {
            targetIndex < 0 -> {
                // Wrap to the end (oldest/last unvisited tab)
                lruQueue.lastOrNull()
            }
            targetIndex >= lruQueue.size -> {
                // Wrap to the beginning (most recent tab)
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
