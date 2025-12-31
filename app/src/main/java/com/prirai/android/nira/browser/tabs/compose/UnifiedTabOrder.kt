package com.prirai.android.nira.browser.tabs.compose

import kotlinx.serialization.Serializable

/**
 * Single source of truth for tab ordering across all views.
 * Represents the hierarchical structure of tabs and groups.
 */
@Serializable
data class UnifiedTabOrder(
    val profileId: String,
    val primaryOrder: List<OrderItem>,
    val lastModified: Long = System.currentTimeMillis()
) {
    @Serializable
    sealed class OrderItem {
        @Serializable
        data class SingleTab(val tabId: String) : OrderItem()
    }
    
    /**
     * Get all tab IDs in flat order
     */
    fun getAllTabIds(): List<String> {
        return primaryOrder.map { (it as OrderItem.SingleTab).tabId }
    }
    
    /**
     * Get the flat position of a tab
     */
    fun getTabPosition(tabId: String): Int? {
        val allTabs = getAllTabIds()
        return allTabs.indexOf(tabId).takeIf { it >= 0 }
    }
    
    /**
     * Get the position of an item in the primary order
     */
    fun getItemPosition(itemId: String): Int? {
        return primaryOrder.indexOfFirst { item ->
            (item as OrderItem.SingleTab).tabId == itemId
        }.takeIf { it >= 0 }
    }
}
