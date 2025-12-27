package com.prirai.android.nira.browser.tabs.dragdrop

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists custom tab ordering across app restarts
 */
class TabOrderPersistence(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "tab_order_prefs",
        Context.MODE_PRIVATE
    )
    
    /**
     * Save custom order for a profile
     */
    fun saveOrder(profileId: String, orderedTabIds: List<String>) {
        val key = "order_$profileId"
        val value = orderedTabIds.joinToString(",")
        prefs.edit().putString(key, value).apply()
    }
    
    /**
     * Load custom order for a profile
     * Returns null if no custom order exists
     */
    fun loadOrder(profileId: String): List<String>? {
        val key = "order_$profileId"
        val value = prefs.getString(key, null) ?: return null
        return if (value.isEmpty()) null else value.split(",")
    }
    
    /**
     * Clear custom order for a profile
     */
    fun clearOrder(profileId: String) {
        val key = "order_$profileId"
        prefs.edit().remove(key).apply()
    }
}
