package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * ViewModel for managing tabs, groups, and their order in Compose UI.
 * Serves as single source of truth, bridging UnifiedTabGroupManager and Compose UI.
 */
class TabViewModel(
    private val context: Context,
    private val groupManager: UnifiedTabGroupManager
) : ViewModel() {
    
    private val _tabs = MutableStateFlow<List<TabSessionState>>(emptyList())
    val tabs: StateFlow<List<TabSessionState>> = _tabs.asStateFlow()
    
    private val _groups = MutableStateFlow<List<TabGroupData>>(emptyList())
    val groups: StateFlow<List<TabGroupData>> = _groups.asStateFlow()
    
    private val _expandedGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedGroups: StateFlow<Set<String>> = _expandedGroups.asStateFlow()
    
    private val _selectedTabId = MutableStateFlow<String?>(null)
    val selectedTabId: StateFlow<String?> = _selectedTabId.asStateFlow()
    
    private var currentProfileId: String? = null
    private val orderManager by lazy { TabOrderManager(context, groupManager) }
    private var loadJob: kotlinx.coroutines.Job? = null
    
    /**
     * Load tabs and groups for a profile
     */
    fun loadTabsForProfile(profileId: String, tabs: List<TabSessionState>, selectedTabId: String?) {
        // Cancel any ongoing load operation
        loadJob?.cancel()
        
        // Immediately clear state to prevent stale data
        _tabs.value = emptyList()
        _groups.value = emptyList()
        _expandedGroups.value = emptySet()
        _selectedTabId.value = null
        
        // Update current profile
        currentProfileId = profileId
        
        // Start new load operation
        loadJob = viewModelScope.launch {
            _selectedTabId.value = selectedTabId
            
            // Load groups from UnifiedTabGroupManager, filtered by contextId
            val allGroups = groupManager.getAllGroups()
            
            // Determine the expected contextId for this profile
            // Private mode uses "private", default profile uses null, others use "profile_{id}"
            val profileContextId = when (profileId) {
                "private" -> "private"
                "default" -> null
                else -> "profile_$profileId"
            }
            
            // Filter groups to only show those matching the current profile's contextId
            // Default profile (contextId = null) shows only groups with null contextId
            // Other profiles show only groups matching their specific contextId
            val groupsList = allGroups.filter { group ->
                when {
                    // Default profile shows only groups with null contextId
                    profileId == "default" -> group.contextId == null
                    // Private and other profiles show only groups matching their contextId
                    else -> group.contextId == profileContextId
                }
            }.filter { group ->
                // Additionally, only show groups that have tabs in the current tab list
                group.tabIds.any { tabId -> tabs.any { it.id == tabId } }
            }
            
            _groups.value = groupsList
            
            // Initialize all groups as expanded
            _expandedGroups.value = groupsList.map { it.id }.toSet()
            
            // Update tabs based on saved order
            _tabs.value = getOrderedTabs(tabs, profileId, groupsList)
        }
    }
    
    /**
     * Force immediate refresh of tabs and groups
     */
    fun forceRefresh(tabs: List<TabSessionState>, selectedTabId: String?) {
        currentProfileId?.let { profileId ->
            // Cancel any ongoing operation first
            loadJob?.cancel()
            loadTabsForProfile(profileId, tabs, selectedTabId)
        }
    }
    
    /**
     * Get tabs in the correct order
     */
    private suspend fun getOrderedTabs(
        tabs: List<TabSessionState>,
        profileId: String,
        groups: List<TabGroupData>
    ): List<TabSessionState> {
        orderManager.loadOrder(profileId)
        
        val order = orderManager.currentOrder.value
        if (order == null || order.primaryOrder.isEmpty()) {
            // No saved order, initialize from current state
            orderManager.initializeFromCurrentState(profileId, tabs.map { it.id }, groups)
            return tabs
        }
        
        // Build ordered list
        val tabMap = tabs.associateBy { it.id }
        val orderedTabs = mutableListOf<TabSessionState>()
        val processedIds = mutableSetOf<String>()
        
        // First add groups in order
        val groupsById = groups.associateBy { it.id }
        order.primaryOrder.forEach { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> {
                    tabMap[item.tabId]?.let {
                        orderedTabs.add(it)
                        processedIds.add(item.tabId)
                    }
                }
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    // Add tabs from this group
                    groupsById[item.groupId]?.tabIds?.forEach { tabId ->
                        tabMap[tabId]?.let {
                            orderedTabs.add(it)
                            processedIds.add(tabId)
                        }
                    }
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
        currentProfileId?.let { profileId ->
            // Cancel any ongoing operation first
            loadJob?.cancel()
            loadTabsForProfile(profileId, tabs, selectedTabId)
        }
    }
    
    /**
     * Toggle group expanded/collapsed state
     */
    fun toggleGroupExpanded(groupId: String) {
        val current = _expandedGroups.value.toMutableSet()
        if (current.contains(groupId)) {
            current.remove(groupId)
        } else {
            current.add(groupId)
        }
        _expandedGroups.value = current
    }
    
    /**
     * Group two tabs together
     */
    fun groupTabsTogether(tab1Id: String, tab2Id: String) {
        viewModelScope.launch {
            currentProfileId?.let { profileId ->
                // Get tabs to determine contextId
                val tab1 = _tabs.value.find { it.id == tab1Id }
                val tab2 = _tabs.value.find { it.id == tab2Id }
                
                // Validate contextId - tabs must have same contextId or both null
                if (tab1 != null && tab2 != null) {
                    val contextId1 = tab1.contextId
                    val contextId2 = tab2.contextId
                    
                    // Can only group if contextIds match (both null or both same value)
                    if (contextId1 == contextId2) {
                        // Create new group with both tabs
                        groupManager.createGroup(
                            tabIds = listOf(tab1Id, tab2Id),
                            name = "New Group",
                            color = generateRandomGroupColor(),
                            contextId = contextId1 // Use the common contextId
                        )
                        
                        // Reload to reflect changes
                        updateTabs(_tabs.value, _selectedTabId.value)
                        
                        // Save new order
                        saveCurrentOrder(profileId)
                    }
                }
            }
        }
    }
    
    /**
     * Move tab to an existing group
     */
    fun moveTabToGroup(tabId: String, groupId: String) {
        viewModelScope.launch {
            currentProfileId?.let { profileId ->
                // Get the tab and the target group
                val tab = _tabs.value.find { it.id == tabId }
                val targetGroup = groupManager.getGroup(groupId)
                
                if (tab != null && targetGroup != null) {
                    // Validate contextId - tab must have same contextId as group (or both null)
                    if (tab.contextId == targetGroup.contextId) {
                        // Add tab to group
                        groupManager.addTabToGroup(
                            tabId = tabId,
                            groupId = groupId,
                            position = null,
                            notifyChange = true
                        )
                        
                        // Reload
                        updateTabs(_tabs.value, _selectedTabId.value)
                        
                        // Save order
                        saveCurrentOrder(profileId)
                    }
                }
            }
        }
    }
    
    /**
     * Ungroup a tab from its group
     */
    fun ungroupTab(tabId: String, groupId: String) {
        viewModelScope.launch {
            currentProfileId?.let { profileId ->
                // Remove tab from group
                groupManager.removeTabFromGroup(tabId, notifyChange = true)
                
                // If group now has only 1 tab, ungroup that tab too
                val group = groupManager.getGroup(groupId)
                if (group != null && group.tabIds.size == 1) {
                    groupManager.removeTabFromGroup(group.tabIds[0], notifyChange = true)
                }
                
                // Reload
                updateTabs(_tabs.value, _selectedTabId.value)
                
                // Save order
                saveCurrentOrder(profileId)
            }
        }
    }
    
    /**
     * Rename a group
     */
    fun renameGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            groupManager.renameGroup(groupId, newName)
            
            // Reload groups
            currentProfileId?.let { profileId ->
                updateTabs(_tabs.value, _selectedTabId.value)
            }
        }
    }
    
    /**
     * Change group color
     */
    fun changeGroupColor(groupId: String, color: Int) {
        viewModelScope.launch {
            groupManager.changeGroupColor(groupId, color)
            
            // Reload groups
            currentProfileId?.let { profileId ->
                updateTabs(_tabs.value, _selectedTabId.value)
            }
        }
    }
    
    /**
     * Ungroup all tabs in a group
     */
    fun ungroupAll(groupId: String) {
        viewModelScope.launch {
            currentProfileId?.let { profileId ->
                val group = groupManager.getGroup(groupId)
                group?.tabIds?.forEach { tabId ->
                    groupManager.removeTabFromGroup(tabId, notifyChange = false)
                }
                
                // Reload
                updateTabs(_tabs.value, _selectedTabId.value)
                
                // Save order
                saveCurrentOrder(profileId)
            }
        }
    }
    
    /**
     * Reorder tabs/groups
     */
    fun reorderTabs(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentTabs = _tabs.value.toMutableList()
            if (fromIndex in currentTabs.indices && toIndex in currentTabs.indices) {
                val item = currentTabs.removeAt(fromIndex)
                currentTabs.add(toIndex, item)
                _tabs.value = currentTabs
                
                // Save new order
                currentProfileId?.let { saveCurrentOrder(it) }
            }
        }
    }
    
    /**
     * Save current tab order to persistent storage
     */
    private suspend fun saveCurrentOrder(profileId: String) {
        val items = mutableListOf<UnifiedTabOrder.OrderItem>()
        val processedTabs = mutableSetOf<String>()
        
        // Add groups first
        _groups.value.forEach { group ->
            items.add(UnifiedTabOrder.OrderItem.TabGroup(
                groupId = group.id,
                groupName = group.name,
                color = group.color,
                isExpanded = true,
                tabIds = group.tabIds
            ))
            processedTabs.addAll(group.tabIds)
        }
        
        // Add ungrouped tabs
        _tabs.value.forEach { tab ->
            if (!processedTabs.contains(tab.id)) {
                items.add(UnifiedTabOrder.OrderItem.SingleTab(tab.id))
            }
        }
        
        val order = UnifiedTabOrder(
            profileId = profileId,
            primaryOrder = items,
            lastModified = System.currentTimeMillis()
        )
        
        orderManager.saveOrder(order)
    }
    
    private fun generateRandomGroupColor(): Int {
        val colors = listOf(
            android.graphics.Color.parseColor("#FF6B6B"),
            android.graphics.Color.parseColor("#4ECDC4"),
            android.graphics.Color.parseColor("#45B7D1"),
            android.graphics.Color.parseColor("#FFA07A"),
            android.graphics.Color.parseColor("#98D8C8"),
            android.graphics.Color.parseColor("#F7DC6F"),
            android.graphics.Color.parseColor("#BB8FCE"),
            android.graphics.Color.parseColor("#85C1E2"),
            android.graphics.Color.parseColor("#F8B739"),
            android.graphics.Color.parseColor("#52B788")
        )
        return colors.random()
    }
    
    /**
     * Create a new group with tabs
     */
    fun createGroup(tabIds: List<String>, name: String? = null, contextId: String? = null) {
        viewModelScope.launch {
            currentProfileId?.let { profileId ->
                groupManager.createGroup(
                    tabIds = tabIds,
                    name = name,
                    profileId = profileId,
                    contextId = contextId
                )
                
                // Reload
                updateTabs(_tabs.value, _selectedTabId.value)
                saveCurrentOrder(profileId)
            }
        }
    }
    
    /**
     * Add tab to existing group
     */
    fun addTabToGroup(tabId: String, groupId: String) {
        viewModelScope.launch {
            currentProfileId?.let { profileId ->
                groupManager.addTabToGroup(tabId, groupId)
                
                // Reload
                updateTabs(_tabs.value, _selectedTabId.value)
                saveCurrentOrder(profileId)
            }
        }
    }
    
    /**
     * Remove tab from its group
     */
    fun removeTabFromGroup(tabId: String) {
        viewModelScope.launch {
            currentProfileId?.let { profileId ->
                groupManager.removeTabFromGroup(tabId)
                
                // Reload
                updateTabs(_tabs.value, _selectedTabId.value)
                saveCurrentOrder(profileId)
            }
        }
    }
    
    /**
     * Merge two groups
     */
    fun mergeGroups(sourceGroupId: String, targetGroupId: String) {
        viewModelScope.launch {
            currentProfileId?.let { profileId ->
                val sourceGroup = groupManager.getGroup(sourceGroupId)
                sourceGroup?.tabIds?.forEach { tabId ->
                    groupManager.removeTabFromGroup(tabId, notifyChange = false)
                    groupManager.addTabToGroup(tabId, targetGroupId, notifyChange = false)
                }
                
                // Reload
                updateTabs(_tabs.value, _selectedTabId.value)
                saveCurrentOrder(profileId)
            }
        }
    }
    
    /**
     * Reorder tab within a group
     */
    fun reorderTabInGroup(draggedTabId: String, targetTabId: String, groupId: String) {
        viewModelScope.launch {
            currentProfileId?.let { profileId ->
                val group = groupManager.getGroup(groupId) ?: return@launch
                val tabIds = group.tabIds.toMutableList()
                
                val fromIndex = tabIds.indexOf(draggedTabId)
                val toIndex = tabIds.indexOf(targetTabId)
                
                if (fromIndex != -1 && toIndex != -1) {
                    tabIds.removeAt(fromIndex)
                    tabIds.add(toIndex, draggedTabId)
                    
                    // Update group with new order
                    groupManager.updateGroup(
                        groupId = groupId,
                        name = group.name,
                        color = group.color,
                        tabIds = tabIds
                    )
                    
                    // Reload
                    updateTabs(_tabs.value, _selectedTabId.value)
                    saveCurrentOrder(profileId)
                }
            }
        }
    }
}
