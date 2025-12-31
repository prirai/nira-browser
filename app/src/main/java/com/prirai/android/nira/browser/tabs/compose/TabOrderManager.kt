package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import com.prirai.android.nira.ext.components

private val Context.orderDataStore: DataStore<Preferences> by preferencesDataStore(name = "tab_order")

/**
 * Manages tab ordering across all views.
 * Single source of truth for tab positions.
 * Singleton to ensure all components share the same order state.
 */
class TabOrderManager private constructor(
    private val context: Context
) {
    
    companion object {
        @Volatile
        private var instance: TabOrderManager? = null
        
        fun getInstance(context: Context): TabOrderManager {
            return instance ?: synchronized(this) {
                instance ?: TabOrderManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _currentOrder = MutableStateFlow<UnifiedTabOrder?>(null)
    val currentOrder: StateFlow<UnifiedTabOrder?> = _currentOrder.asStateFlow()
    
    /**
     * Load order for a specific profile
     */
    suspend fun loadOrder(profileId: String): UnifiedTabOrder {
        val key = stringPreferencesKey("order_$profileId")
        val order = context.orderDataStore.data
            .map { prefs ->
                try {
                    prefs[key]?.let { json.decodeFromString<UnifiedTabOrder>(it) }
                } catch (e: Exception) {
                    null
                }
            }
            .first()
            ?: UnifiedTabOrder(profileId, emptyList())
        
        // Ensure order is clean (only SingleTabs)
        val cleanedOrder = cleanupLegacyItems(order)
        _currentOrder.value = cleanedOrder
        return cleanedOrder
    }
    
    /**
     * Clean up any legacy items if present (fallback safety)
     */
    private suspend fun cleanupLegacyItems(order: UnifiedTabOrder): UnifiedTabOrder {
        val cleanedPrimaryOrder = order.primaryOrder.filterIsInstance<UnifiedTabOrder.OrderItem.SingleTab>()
        
        return if (cleanedPrimaryOrder.size != order.primaryOrder.size) {
            order.copy(primaryOrder = cleanedPrimaryOrder).also { cleaned ->
                saveOrder(cleaned)
            }
        } else {
            order
        }
    }
    
    /**
     * Save current order
     */
    suspend fun saveOrder(order: UnifiedTabOrder) {
        val key = stringPreferencesKey("order_${order.profileId}")
        context.orderDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(order)
        }
        _currentOrder.value = order.copy(lastModified = System.currentTimeMillis())
    }
    
    // === REORDERING OPERATIONS ===
    
    /**
     * Reorder an item in the primary order
     */
    suspend fun reorderItem(fromIndex: Int, toIndex: Int) {
        val current = _currentOrder.value ?: return
        if (fromIndex == toIndex) return
        if (fromIndex < 0 || fromIndex >= current.primaryOrder.size) return
        if (toIndex < 0 || toIndex >= current.primaryOrder.size) return
        
        val newOrder = current.primaryOrder.toMutableList()
        val item = newOrder.removeAt(fromIndex)
        newOrder.add(toIndex, item)
        saveOrder(current.copy(primaryOrder = newOrder))
    }

    /**
     * Remove a tab completely from the order
     */
    suspend fun removeTab(tabId: String) {
        val current = _currentOrder.value ?: return
        
        val newOrder = current.primaryOrder.filter { item ->
            (item as? UnifiedTabOrder.OrderItem.SingleTab)?.tabId != tabId
        }
        
        if (newOrder.size != current.primaryOrder.size) {
            saveOrder(current.copy(primaryOrder = newOrder))
        }
    }

    /**
     * Rebuild order for a profile from current tabs.
     * Used when significant changes happen to ensure order stays in sync.
     */
    suspend fun rebuildOrderForProfile(profileId: String, tabs: List<mozilla.components.browser.state.state.SessionState>) {
        val tabIds = tabs.map { it.id }
        
        // Load existing order or create new one
        val existingOrder = try {
            loadOrder(profileId)
        } catch (e: Exception) {
            UnifiedTabOrder(profileId, emptyList())
        }
        
        // Build a map of existing positions
        val existingPositions = mutableMapOf<String, Int>()
        existingOrder.primaryOrder.forEachIndexed { index, item ->
            if (item is UnifiedTabOrder.OrderItem.SingleTab) {
                existingPositions[item.tabId] = index
            }
        }
        
        // Create new order items
        val itemsWithPositions = mutableListOf<Pair<Int, UnifiedTabOrder.OrderItem>>()
        val processedTabIds = mutableSetOf<String>()
        
        // Process tabs
        tabIds.forEach { tabId ->
            if (tabId !in processedTabIds) {
                val position = existingPositions[tabId] ?: Int.MAX_VALUE
                itemsWithPositions.add(
                    position to UnifiedTabOrder.OrderItem.SingleTab(tabId)
                )
                processedTabIds.add(tabId)
            }
        }
        
        // Sort by position and extract items
        val newOrder = itemsWithPositions.sortedBy { it.first }.map { it.second }
        
        // Save the new order
        val updatedOrder = UnifiedTabOrder(
            profileId = profileId,
            primaryOrder = newOrder,
            lastModified = System.currentTimeMillis()
        )
        saveOrder(updatedOrder)
        _currentOrder.value = updatedOrder
        
        android.util.Log.d("TabOrderManager", "Rebuilt order for profile $profileId: ${newOrder.size} items")
    }
}
