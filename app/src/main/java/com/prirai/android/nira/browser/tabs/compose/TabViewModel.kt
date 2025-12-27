package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for managing tab order and state across all views.
 * Ensures single source of truth and proper lifecycle management.
 */
class TabViewModel(context: Context, groupManager: UnifiedTabGroupManager) : ViewModel() {
    
    private val orderManager = TabOrderManager(context, groupManager)
    
    /**
     * Current tab order - shared across all views
     */
    val tabOrder: StateFlow<UnifiedTabOrder?> = orderManager.currentOrder
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    /**
     * Load order for a profile
     */
    fun loadOrder(profileId: String) {
        viewModelScope.launch {
            orderManager.loadOrder(profileId)
        }
    }
    
    /**
     * Initialize order from current tabs (migration helper)
     */
    fun initializeFromTabs(profileId: String, tabIds: List<String>) {
        viewModelScope.launch {
            orderManager.initializeFromTabs(profileId, tabIds)
        }
    }
    
    /**
     * Reorder items
     */
    fun reorderItem(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            orderManager.reorderItem(fromIndex, toIndex)
        }
    }
    
    /**
     * Create a new group
     */
    fun createGroup(tabIds: List<String>, groupName: String, color: Int) {
        viewModelScope.launch {
            orderManager.createGroup(tabIds, groupName, color)
        }
    }
    
    /**
     * Add tab to group
     */
    fun addTabToGroup(tabId: String, groupId: String) {
        viewModelScope.launch {
            orderManager.addTabToGroup(tabId, groupId)
        }
    }
    
    /**
     * Remove tab from group
     */
    fun removeTabFromGroup(tabId: String) {
        viewModelScope.launch {
            orderManager.removeTabFromGroup(tabId)
        }
    }
    
    /**
     * Toggle group expansion
     */
    fun toggleGroupExpansion(groupId: String) {
        viewModelScope.launch {
            orderManager.toggleGroupExpansion(groupId)
        }
    }
    
    /**
     * Rename group
     */
    fun renameGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            orderManager.renameGroup(groupId, newName)
        }
    }
    
    /**
     * Change group color
     */
    fun changeGroupColor(groupId: String, color: Int) {
        viewModelScope.launch {
            orderManager.changeGroupColor(groupId, color)
        }
    }
    
    /**
     * Disband group
     */
    fun disbandGroup(groupId: String) {
        viewModelScope.launch {
            orderManager.disbandGroup(groupId)
        }
    }
    
    /**
     * Get the order manager for direct access if needed
     */
    fun getOrderManager(): TabOrderManager = orderManager
}
