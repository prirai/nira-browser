package com.prirai.android.nira.browser.profile

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores metadata mapping tabs to profiles
 * Since BrowserStore doesn't have custom metadata fields,
 * we maintain this mapping separately
 */
class TabProfileMetadata private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "tab_profile_metadata"
        private const val KEY_PREFIX = "tab_"
        
        @Volatile
        private var instance: TabProfileMetadata? = null
        
        fun getInstance(context: Context): TabProfileMetadata {
            return instance ?: synchronized(this) {
                instance ?: TabProfileMetadata(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Associate a tab with a profile
     */
    fun setTabProfile(tabId: String, profileId: String) {
        prefs.edit().putString(KEY_PREFIX + tabId, profileId).apply()
    }
    
    /**
     * Get the profile associated with a tab
     * Returns "default" if not set
     */
    fun getTabProfile(tabId: String): String {
        return prefs.getString(KEY_PREFIX + tabId, "default") ?: "default"
    }
    
    /**
     * Remove metadata for a tab
     */
    fun removeTab(tabId: String) {
        prefs.edit().remove(KEY_PREFIX + tabId).apply()
    }
    
    /**
     * Get all tabs for a profile
     */
    fun getTabsForProfile(profileId: String): List<String> {
        return prefs.all.entries
            .filter { it.key.startsWith(KEY_PREFIX) && it.value == profileId }
            .map { it.key.removePrefix(KEY_PREFIX) }
    }
    
    /**
     * Clear all metadata
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Clean up metadata for tabs that no longer exist
     */
    fun cleanupStale(existingTabIds: Set<String>) {
        val allKeys = prefs.all.keys
        val toRemove = allKeys.filter { key ->
            if (key.startsWith(KEY_PREFIX)) {
                val tabId = key.removePrefix(KEY_PREFIX)
                tabId !in existingTabIds
            } else {
                false
            }
        }
        
        if (toRemove.isNotEmpty()) {
            val editor = prefs.edit()
            toRemove.forEach { editor.remove(it) }
            editor.apply()
        }
    }
}
