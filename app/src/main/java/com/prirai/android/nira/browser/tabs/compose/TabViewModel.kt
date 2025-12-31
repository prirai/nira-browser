package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * ViewModel for managing tabs order in Compose UI.
 * Connects TabOrderManager with UI state.
 */
class TabViewModel(
    private val context: Context
) : ViewModel() {
    
    private val _tabs = MutableStateFlow<List<TabSessionState>>(emptyList())
    val tabs: StateFlow<List<TabSessionState>> = _tabs.asStateFlow()
    
    private val _selectedTabId = MutableStateFlow<String?>(null)
    val selectedTabId: StateFlow<String?> = _selectedTabId.asStateFlow()
    
    private val _currentProfileId = MutableStateFlow<String?>(null)
    val currentProfileId: StateFlow<String?> = _currentProfileId.asStateFlow()
    
    private val orderManager by lazy { TabOrderManager.getInstance(context) }
    private var loadJob: Job? = null
    
    // Expose order for UI to use
    val currentOrder: StateFlow<UnifiedTabOrder?> = orderManager.currentOrder
    
    /**
     * Load tabs for a profile
     */
    fun loadTabsForProfile(profileId: String, tabs: List<TabSessionState>, selectedTabId: String?) {
        // Cancel any ongoing load operation
        loadJob?.cancel()
        
        // Update current profile immediately
        _currentProfileId.value = profileId
        
        // Start new load operation
        loadJob = viewModelScope.launch {
            // Get ordered tabs
            val orderedTabs = getOrderedTabs(tabs, profileId)
            
            // Update all state atomically to prevent flickering
            _tabs.value = orderedTabs
            _selectedTabId.value = selectedTabId
        }
    }
    
    /**
     * Force immediate refresh of tabs
     */
    fun forceRefresh(tabs: List<TabSessionState>, selectedTabId: String?) {
        _currentProfileId.value?.let { profileId ->
            // Cancel any ongoing operation first
            loadJob?.cancel()
            
            // Immediately reload tabs
            loadJob = viewModelScope.launch {
                _selectedTabId.value = selectedTabId
                
                // Update tabs based on saved order
                _tabs.value = getOrderedTabs(tabs, profileId)
            }
        }
    }
    
    /**
     * Get tabs in the correct order
     */
    private suspend fun getOrderedTabs(
        tabs: List<TabSessionState>,
        profileId: String
    ): List<TabSessionState> {
        orderManager.loadOrder(profileId)
        
        val order = orderManager.currentOrder.value
        if (order == null || order.primaryOrder.isEmpty()) {
            // No saved order, initialize from current state
            orderManager.rebuildOrderForProfile(profileId, tabs)
            return tabs
        }
        
        // Build ordered list
        val tabMap = tabs.associateBy { it.id }
        val orderedTabs = mutableListOf<TabSessionState>()
        val processedIds = mutableSetOf<String>()
        
        // First add tabs in order
        order.primaryOrder.forEach { item ->
            if (item is UnifiedTabOrder.OrderItem.SingleTab) {
                tabMap[item.tabId]?.let {
                    orderedTabs.add(it)
                    processedIds.add(item.tabId)
                }
            }
        }
        
        // Add any tabs not in the order at the end
        tabs.forEach { tab ->
            if (!processedIds.contains(tab.id)) {
                orderedTabs.add(tab)
            }
        }
        
        return orderedTabs
    }
    
    /**
     * Update tab list (for refreshing)
     */
    fun updateTabs(tabs: List<TabSessionState>, selectedTabId: String?) {
        _currentProfileId.value?.let { profileId ->
            // Cancel any ongoing operation first
            loadJob?.cancel()
            loadTabsForProfile(profileId, tabs, selectedTabId)
        }
    }
    
    /**
     * Reorder tabs
     */
    fun reorderTabs(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentTabs = _tabs.value.toMutableList()
            if (fromIndex in currentTabs.indices && toIndex in currentTabs.indices) {
                val item = currentTabs.removeAt(fromIndex)
                currentTabs.add(toIndex, item)
                _tabs.value = currentTabs
                
                // Save new order
                _currentProfileId.value?.let { saveCurrentOrder(it) }
            }
        }
    }
    
    /**
     * Save current tab order to persistent storage
     */
    private suspend fun saveCurrentOrder(profileId: String) {
        val tabIds = _tabs.value.map { it.id }
        
        val items = tabIds.map { tabId ->
            UnifiedTabOrder.OrderItem.SingleTab(tabId)
        }
        
        val order = UnifiedTabOrder(
            profileId = profileId,
            primaryOrder = items,
            lastModified = System.currentTimeMillis()
        )
        
        orderManager.saveOrder(order)
    }
    
    /**
     * Move a tab to a specific position in the overall order
     */
    fun moveTabToPosition(tabId: String, targetIndex: Int) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                orderManager.loadOrder(profileId)
                val current = orderManager.currentOrder.value ?: return@launch
                
                // Find the tab in the order
                val tabIndex = current.primaryOrder.indexOfFirst {
                    it is UnifiedTabOrder.OrderItem.SingleTab && it.tabId == tabId
                }
                
                if (tabIndex != -1 && tabIndex != targetIndex) {
                    orderManager.reorderItem(tabIndex, targetIndex.coerceIn(0, current.primaryOrder.size))
                    saveCurrentOrder(profileId)
                }
            }
        }
    }

    // Deprecated methods stubs removed as they are no longer needed
}
