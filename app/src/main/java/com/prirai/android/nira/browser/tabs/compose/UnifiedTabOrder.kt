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
        
        @Serializable
        data class TabGroup(
            val groupId: String,
            val groupName: String,
            val color: Int,
            val isExpanded: Boolean,
            val tabIds: List<String>
        ) : OrderItem()
    }
    
    /**
     * Get all tab IDs in flat order
     */
    fun getAllTabIds(): List<String> {
        return primaryOrder.flatMap { item ->
            when (item) {
                is OrderItem.SingleTab -> listOf(item.tabId)
                is OrderItem.TabGroup -> item.tabIds
            }
        }
    }
    
    /**
     * Find which group contains a specific tab
     */
    fun findGroupContaining(tabId: String): OrderItem.TabGroup? {
        return primaryOrder.filterIsInstance<OrderItem.TabGroup>()
            .find { tabId in it.tabIds }
    }
    
    /**
     * Get the flat position of a tab (across all groups)
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
            when (item) {
                is OrderItem.SingleTab -> item.tabId == itemId
                is OrderItem.TabGroup -> item.groupId == itemId
            }
        }.takeIf { it >= 0 }
    }
}
