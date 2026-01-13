package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.state.TabSessionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

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

    private val _currentProfileId = MutableStateFlow<String?>(null)
    val currentProfileId: StateFlow<String?> = _currentProfileId.asStateFlow()

    private val orderManager by lazy { TabOrderManager.getInstance(context, groupManager) }
    private var loadJob: kotlinx.coroutines.Job? = null
    private var rebuildJob: kotlinx.coroutines.Job? = null

    // Expose order for UI to use
    val currentOrder: StateFlow<UnifiedTabOrder?> = orderManager.currentOrder

    init {
        // Observe group events and refresh when groups change
        viewModelScope.launch {
            groupManager.groupEvents.collect { event ->
                // Debounce rapid group events to prevent flickering
                rebuildJob?.cancel()
                rebuildJob = launch {
                    delay(150) // Debounce for 150ms
                    // Refresh groups when any group event occurs
                    _currentProfileId.value?.let { profileId ->
                        refreshGroupsForProfile(profileId)
                        // Also reload the order to reflect group changes
                        orderManager.rebuildOrderForProfile(profileId, _tabs.value)
                    }
                }
            }
        }
    }

    /**
     * Refresh groups for the current profile
     */
    private suspend fun refreshGroupsForProfile(profileId: String) {
        val allGroups = groupManager.getAllGroups()

        // Determine the expected contextId for this profile
        val profileContextId = when (profileId) {
            "private" -> "private"
            "default" -> "profile_default"
            else -> "profile_$profileId"
        }

        // Filter groups to only show those matching the current profile's contextId
        val groupsList = allGroups.filter { group ->
            when {
                // Default profile shows groups with "profile_default" OR null (backwards compat)
                profileId == "default" -> group.contextId == "profile_default" || group.contextId == null
                // Private and other profiles show only groups matching their contextId
                else -> group.contextId == profileContextId
            }
        }.filter { group ->
            // Additionally, only show groups that have tabs in the current tab list
            group.tabIds.any { tabId -> _tabs.value.any { it.id == tabId } }
        }

        _groups.value = groupsList

        // Preserve currently expanded groups or expand new ones
        val currentExpanded = _expandedGroups.value
        val newExpanded = groupsList.map { it.id }.toSet()
        _expandedGroups.value = currentExpanded + newExpanded
    }

    /**
     * Load tabs and groups for a profile
     */
    fun loadTabsForProfile(profileId: String, tabs: List<TabSessionState>, selectedTabId: String?) {
        // Cancel any ongoing load operation
        loadJob?.cancel()

        // Update current profile immediately
        _currentProfileId.value = profileId

        // Start new load operation
        loadJob = viewModelScope.launch {
            // Load groups from UnifiedTabGroupManager, filtered by contextId
            val allGroups = groupManager.getAllGroups()

            // Determine the expected contextId for this profile
            // Private mode uses "private", default profile uses "profile_default", others use "profile_{id}"
            val profileContextId = when (profileId) {
                "private" -> "private"
                "default" -> "profile_default"
                else -> "profile_$profileId"
            }

            // Filter groups to only show those matching the current profile's contextId
            // For backwards compatibility, default profile also accepts null contextId
            val groupsList = allGroups.filter { group ->
                when {
                    // Default profile shows groups with "profile_default" OR null (backwards compat)
                    profileId == "default" -> group.contextId == "profile_default" || group.contextId == null
                    // Private and other profiles show only groups matching their contextId
                    else -> group.contextId == profileContextId
                }
            }.filter { group ->
                // Additionally, only show groups that have tabs in the current tab list
                group.tabIds.any { tabId -> tabs.any { it.id == tabId } }
            }

            // Get ordered tabs
            val orderedTabs = getOrderedTabs(tabs, profileId, groupsList)

            // Update all state atomically to prevent flickering
            _tabs.value = orderedTabs
            _groups.value = groupsList
            _expandedGroups.value = groupsList.map { it.id }.toSet()
            _selectedTabId.value = selectedTabId

            // Auto-expand group containing selected tab
            selectedTabId?.let { tabId ->
                orderManager.expandGroupContainingTab(tabId)
            }
        }
    }

    /**
     * Force immediate refresh of tabs and groups
     */
    fun forceRefresh(tabs: List<TabSessionState>, selectedTabId: String?) {
        _currentProfileId.value?.let { profileId ->
            // Cancel any ongoing operation first
            loadJob?.cancel()

            // Immediately reload groups and tabs
            loadJob = viewModelScope.launch {
                _selectedTabId.value = selectedTabId

                // Reload groups from UnifiedTabGroupManager to get latest state
                val allGroups = groupManager.getAllGroups()

                // Determine the expected contextId for this profile
                val profileContextId = when (profileId) {
                    "private" -> "private"
                    "default" -> "profile_default"
                    else -> "profile_$profileId"
                }

                // Filter groups to only show those matching the current profile's contextId
                val groupsList = allGroups.filter { group ->
                    when {
                        // Default profile shows groups with null OR "profile_default" contextId
                        profileId == "default" -> group.contextId == null || group.contextId == "profile_default"
                        else -> group.contextId == profileContextId
                    }
                }.filter { group ->
                    // Only show groups that have tabs in the current tab list
                    group.tabIds.any { tabId -> tabs.any { it.id == tabId } }
                }

                _groups.value = groupsList

                // Preserve currently expanded groups or expand new ones
                val currentExpanded = _expandedGroups.value
                val newExpanded = groupsList.map { it.id }.toSet()
                _expandedGroups.value = currentExpanded + newExpanded

                // Update tabs based on saved order
                _tabs.value = getOrderedTabs(tabs, profileId, groupsList)

                // Auto-expand group containing selected tab
                selectedTabId?.let { tabId ->
                    orderManager.expandGroupContainingTab(tabId)
                }
            }
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
        _currentProfileId.value?.let { profileId ->
            // Cancel any ongoing operation first
            loadJob?.cancel()
            loadTabsForProfile(profileId, tabs, selectedTabId)
        }
    }

    /**
     * Toggle group expanded/collapsed state
     */
    fun toggleGroupExpanded(groupId: String) {
        android.util.Log.d("TabViewModel", "toggleGroupExpanded called for: $groupId")
        val current = _expandedGroups.value.toMutableSet()
        if (current.contains(groupId)) {
            android.util.Log.d("TabViewModel", "Collapsing group $groupId")
            current.remove(groupId)
        } else {
            android.util.Log.d("TabViewModel", "Expanding group $groupId")
            current.add(groupId)
        }
        _expandedGroups.value = current

        // Also update in the order manager to keep them in sync
        viewModelScope.launch {
            try {
                orderManager.toggleGroupExpansion(groupId)
            } catch (e: Exception) {
                android.util.Log.e("TabViewModel", "Error toggling group in order manager", e)
            }
        }
    }

    /**
     * Group two tabs together
     */
    fun groupTabsTogether(tab1Id: String, tab2Id: String) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                // Get tabs to determine contextId
                val tab1 = _tabs.value.find { it.id == tab1Id }
                val tab2 = _tabs.value.find { it.id == tab2Id }

                // Validate contextId - tabs must have same contextId or both null
                if (tab1 != null && tab2 != null) {
                    val contextId1 = tab1.contextId
                    val contextId2 = tab2.contextId

                    // Can only group if contextIds match (both null or both same value)
                    if (contextId1 == contextId2) {
                        // Normalize contextId: if null and default profile, use "profile_default"
                        val normalizedContextId = if (contextId1 == null && profileId == "default") {
                            "profile_default"
                        } else {
                            contextId1
                        }

                        // Create new group with both tabs
                        groupManager.createGroup(
                            tabIds = listOf(tab1Id, tab2Id),
                            name = "New Group",
                            color = generateRandomGroupColor(),
                            contextId = normalizedContextId
                        )

                        // Save new order after group creation completes
                        saveCurrentOrder(profileId)

                        // Note: Group events observer will trigger the UI refresh
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
            _currentProfileId.value?.let { profileId ->
                // Get the tab and the target group
                val tab = _tabs.value.find { it.id == tabId }
                val targetGroup = groupManager.getGroup(groupId)

                if (tab != null && targetGroup != null) {
                    // Normalize contextIds for comparison
                    val tabContextId = tab.contextId?.takeIf { it.isNotEmpty() }
                        ?: if (profileId == "default") "profile_default" else null
                    val groupContextId = targetGroup.contextId?.takeIf { it.isNotEmpty() }
                        ?: if (profileId == "default") "profile_default" else null

                    // Validate contextId - tab must have same contextId as group
                    if (tabContextId == groupContextId ||
                        (profileId == "default" && (tabContextId == null || tabContextId == "profile_default") &&
                                (groupContextId == null || groupContextId == "profile_default"))
                    ) {
                        // Add tab to group
                        groupManager.addTabToGroup(
                            tabId = tabId,
                            groupId = groupId,
                            position = null,
                            notifyChange = true
                        )

                        // Save order
                        saveCurrentOrder(profileId)

                        // Note: Group events observer will trigger the UI refresh
                    }
                }
            }
        }
    }

    /**
     * Rename a group
     */
    fun renameGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            groupManager.renameGroup(groupId, newName)

            // Note: Group events observer will trigger the UI refresh
        }
    }

    /**
     * Change group color
     */
    fun changeGroupColor(groupId: String, color: Int) {
        viewModelScope.launch {
            groupManager.changeGroupColor(groupId, color)

            // Note: Group events observer will trigger the UI refresh
        }
    }

    /**
     * Ungroup all tabs in a group
     */
    fun ungroupAll(groupId: String) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                val group = groupManager.getGroup(groupId)
                group?.tabIds?.forEach { tabId ->
                    groupManager.removeTabFromGroup(tabId, notifyChange = true)
                }

                // Save order
                saveCurrentOrder(profileId)

                // Note: Group events observer will trigger the UI refresh
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
                _currentProfileId.value?.let { saveCurrentOrder(it) }
            }
        }
    }

    /**
     * Save current tab order to persistent storage
     */
    private suspend fun saveCurrentOrder(profileId: String) {
        // Get current order or create a new one
        val currentOrder = orderManager.currentOrder.value

        if (currentOrder != null && currentOrder.profileId == profileId) {
            // Update existing order: sync group data and add new tabs
            val groupsById = _groups.value.associateBy { it.id }
            val existingTabIds = mutableSetOf<String>()

            // Update existing items
            val updatedItems = currentOrder.primaryOrder.mapNotNull { item ->
                when (item) {
                    is UnifiedTabOrder.OrderItem.TabGroup -> {
                        // Update group data if group still exists
                        val group = groupsById[item.groupId]
                        if (group != null) {
                            existingTabIds.addAll(group.tabIds)
                            UnifiedTabOrder.OrderItem.TabGroup(
                                groupId = group.id,
                                groupName = group.name,
                                color = group.color,
                                isExpanded = _expandedGroups.value.contains(group.id),
                                tabIds = group.tabIds
                            )
                        } else {
                            null // Remove group if it no longer exists
                        }
                    }

                    is UnifiedTabOrder.OrderItem.SingleTab -> {
                        // Keep single tab if it still exists and isn't in a group
                        val tabExists = _tabs.value.any { it.id == item.tabId }
                        val inGroup = _groups.value.any { group -> group.tabIds.contains(item.tabId) }
                        if (tabExists && !inGroup) {
                            existingTabIds.add(item.tabId)
                            item
                        } else {
                            null // Remove if tab doesn't exist or is now in a group
                        }
                    }
                }
            }.toMutableList()

            // Add new groups that aren't in the order yet
            _groups.value.forEach { group ->
                val exists = updatedItems.any {
                    it is UnifiedTabOrder.OrderItem.TabGroup && it.groupId == group.id
                }
                if (!exists) {
                    updatedItems.add(
                        UnifiedTabOrder.OrderItem.TabGroup(
                            groupId = group.id,
                            groupName = group.name,
                            color = group.color,
                            isExpanded = true,
                            tabIds = group.tabIds
                        )
                    )
                    existingTabIds.addAll(group.tabIds)
                }
            }

            // Add new ungrouped tabs at the end
            _tabs.value.forEach { tab ->
                val inGroup = _groups.value.any { group -> group.tabIds.contains(tab.id) }
                if (!existingTabIds.contains(tab.id) && !inGroup) {
                    updatedItems.add(UnifiedTabOrder.OrderItem.SingleTab(tab.id))
                }
            }

            orderManager.saveOrder(
                currentOrder.copy(
                    primaryOrder = updatedItems,
                    lastModified = System.currentTimeMillis()
                )
            )
        } else {
            // Create new order from scratch
            val items = mutableListOf<UnifiedTabOrder.OrderItem>()
            val processedTabs = mutableSetOf<String>()

            // Add groups
            _groups.value.forEach { group ->
                items.add(
                    UnifiedTabOrder.OrderItem.TabGroup(
                        groupId = group.id,
                        groupName = group.name,
                        color = group.color,
                        isExpanded = true,
                        tabIds = group.tabIds
                    )
                )
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
            _currentProfileId.value?.let { profileId ->
                groupManager.createGroup(
                    tabIds = tabIds,
                    name = name,
                    profileId = profileId,
                    contextId = contextId
                )

                saveCurrentOrder(profileId)

                // Note: Group events observer will trigger the UI refresh
            }
        }
    }

    /**
     * Add tab to existing group
     */
    fun addTabToGroup(tabId: String, groupId: String) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                groupManager.addTabToGroup(tabId, groupId)

                saveCurrentOrder(profileId)

                // Note: Group events observer will trigger the UI refresh
            }
        }
    }

    /**
     * Remove tab from its group
     */
    fun removeTabFromGroup(tabId: String) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                groupManager.removeTabFromGroup(tabId)

                saveCurrentOrder(profileId)

                // Note: Group events observer will trigger the UI refresh
            }
        }
    }

    /**
     * Merge two groups
     */
    fun mergeGroups(sourceGroupId: String, targetGroupId: String) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                val sourceGroup = groupManager.getGroup(sourceGroupId)
                sourceGroup?.tabIds?.forEach { tabId ->
                    groupManager.removeTabFromGroup(tabId, notifyChange = true)
                    groupManager.addTabToGroup(tabId, targetGroupId, notifyChange = true)
                }

                saveCurrentOrder(profileId)

                // Note: Group events observer will trigger the UI refresh
            }
        }
    }

    /**
     * Reorder tab within a group
     * @param insertAfter If true, insert after target; if false, insert before target
     */
    fun reorderTabInGroup(draggedTabId: String, targetTabId: String, groupId: String, insertAfter: Boolean = true) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                val group = groupManager.getGroup(groupId) ?: return@launch
                val tabIds = group.tabIds.toMutableList()

                val fromIndex = tabIds.indexOf(draggedTabId)
                val toIndex = tabIds.indexOf(targetTabId)

                if (fromIndex != -1 && toIndex != -1) {
                    tabIds.removeAt(fromIndex)

                    // Recalculate target index after removal
                    val adjustedToIndex = tabIds.indexOf(targetTabId)

                    // Insert before or after target based on parameter
                    val insertIndex = if (insertAfter) {
                        adjustedToIndex + 1
                    } else {
                        adjustedToIndex
                    }

                    tabIds.add(insertIndex, draggedTabId)

                    // Update group with new order
                    groupManager.updateGroup(
                        groupId = groupId,
                        name = group.name,
                        color = group.color,
                        tabIds = tabIds
                    )

                    saveCurrentOrder(profileId)

                    // Note: Group events observer will trigger the UI refresh
                }
            }
        }
    }

    /**
     * Reorder an entire group to a new position
     */
    fun reorderGroup(groupId: String, targetIndex: Int) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                orderManager.loadOrder(profileId)
                val current = orderManager.currentOrder.value ?: return@launch

                // Find the group in the order
                val groupIndex = current.primaryOrder.indexOfFirst {
                    it is UnifiedTabOrder.OrderItem.TabGroup && it.groupId == groupId
                }

                if (groupIndex != -1 && groupIndex != targetIndex) {
                    val size = current.primaryOrder.size
                    val validTargetIndex = targetIndex.coerceIn(0, size - 1)
                    if (groupIndex != validTargetIndex) {
                        orderManager.reorderItem(groupIndex, validTargetIndex)
                        saveCurrentOrder(profileId)
                    }
                }
            }
        }
    }

    /**
     * Move a tab to a specific position in the overall order
     */
    fun moveTabToPosition(tabId: String, targetIndex: Int) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                orderManager.loadOrder(profileId)
                val current = orderManager.currentOrder.value ?: return@launch

                // Find the tab - could be a SingleTab or inside a TabGroup
                var tabIndex = -1
                var isInGroup = false

                current.primaryOrder.forEachIndexed { index, item ->
                    when (item) {
                        is UnifiedTabOrder.OrderItem.SingleTab -> {
                            if (item.tabId == tabId) {
                                tabIndex = index
                            }
                        }

                        is UnifiedTabOrder.OrderItem.TabGroup -> {
                            if (tabId in item.tabIds) {
                                tabIndex = index
                                isInGroup = true
                            }
                        }
                    }
                }

                if (tabIndex != -1) {
                    val size = current.primaryOrder.size
                    val validTargetIndex = targetIndex.coerceIn(0, size)

                    // If tab is in a group at the found position, we need to extract it first
                    if (isInGroup && tabIndex != validTargetIndex) {
                        // Remove from group and add as single tab at target position
                        orderManager.removeTabFromGroup(tabId, validTargetIndex)
                        saveCurrentOrder(profileId)
                    } else if (!isInGroup && tabIndex != validTargetIndex) {
                        // Tab is already single, just reorder
                        orderManager.reorderItem(tabIndex, validTargetIndex)
                        saveCurrentOrder(profileId)
                    }
                }
            }
        }
    }

    // ========== Unified Menu Actions ==========

    /**
     * Create a new tab in the current profile
     */
    fun createNewTab() {
        viewModelScope.launch {
            val profileId = _currentProfileId.value ?: return@launch
            // This will be handled by the caller (e.g., BrowserActivity)
            // through the components.tabsUseCases.addTab()
        }
    }

    /**
     * Duplicate a tab
     */
    fun duplicateTab(tabId: String) {
        viewModelScope.launch {
            val tab = _tabs.value.find { it.id == tabId }
            if (tab == null) {
                android.util.Log.e("TabViewModel", "Cannot duplicate - tab not found: $tabId")
                return@launch
            }

            // Get group if tab is in a group
            val groupId = _groups.value.find { group ->
                group.tabIds.contains(tabId)
            }?.id

            android.util.Log.d("TabViewModel", "Duplicating tab $tabId in group $groupId, url: ${tab.content.url}")

            // Note: Actual tab creation must be handled by the caller through:
            // components.tabsUseCases.addTab(url = tab.content.url, private = tab.content.private)
            // Then if groupId != null: addTabToGroup(newTabId, groupId)

            // For now, we'll emit an event that can be observed
            _duplicateTabEvent.emit(DuplicateTabRequest(tabId, tab.content.url, groupId))
        }
    }

    // Event flow for duplicate tab requests
    private val _duplicateTabEvent = MutableSharedFlow<DuplicateTabRequest>()
    val duplicateTabEvent: SharedFlow<DuplicateTabRequest> = _duplicateTabEvent.asSharedFlow()

    data class DuplicateTabRequest(
        val originalTabId: String,
        val url: String,
        val groupId: String?
    )

    /**
     * Toggle pin status of a tab
     */
    fun togglePinTab(tabId: String) {
        viewModelScope.launch {
            // This will be handled by the caller
            // through components.tabsUseCases.pinTab() or unpinTab()
        }
    }

    /**
     * Show dialog to add tab to a group
     */
    fun showAddToGroupDialog(tabId: String) {
        // This will be handled by the caller to show the group selection dialog
    }

    /**
     * Enter multi-select mode for batch operations
     */
    fun enterMultiSelectMode() {
        // This will be handled by the caller to enter multi-select mode
    }

    /**
     * Close a specific tab
     */
    fun closeTab(tabId: String) {
        viewModelScope.launch {
            // This will be handled by the caller
            // through components.tabsUseCases.removeTab(tabId)
        }
    }

    /**
     * Close all other tabs except the specified one
     */
    fun closeOtherTabs(keepTabId: String) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                // Get all tab IDs except the one to keep
                val tabsToClose = _tabs.value.filter { it.id != keepTabId }.map { it.id }
                // This will be handled by the caller to close these tabs
                // The caller should iterate through tabsToClose and call removeTab() for each
            }
        }
    }

    /**
     * Pin a tab (move to first position in its group or root)
     */
    fun pinTab(tabId: String) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                val tab = _tabs.value.find { it.id == tabId } ?: return@launch
                val group = _groups.value.find { it.tabIds.contains(tabId) }

                if (group != null) {
                    // Tab is in a group - move to first position in that group
                    val updatedTabIds = listOf(tabId) + group.tabIds.filter { it != tabId }
                    groupManager.updateGroup(
                        groupId = group.id,
                        name = group.name,
                        color = group.color,
                        tabIds = updatedTabIds
                    )
                } else {
                    // Tab is not in a group - move to first position in root
                    moveTabToPosition(tabId, 0)
                }

                saveCurrentOrder(profileId)
            }
        }
    }

    /**
     * Pin a group (move to first position in order)
     */
    fun pinGroup(groupId: String) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                reorderGroup(groupId, 0)
            }
        }
    }

    /**
     * Close all tabs in a group
     */
    fun closeAllTabsInGroup(groupId: String) {
        viewModelScope.launch {
            val group = _groups.value.find { it.id == groupId } ?: return@launch
            // This will be handled by the caller
            // to close all tabs in group.tabIds
        }
    }

    /**
     * Create a new tab in a specific group
     */
    fun createNewTabInGroup(groupId: String, contextId: String) {
        viewModelScope.launch {
            try {
                // Create a new tab with the group's context
                val store = context.components.store
                val newTabId = context.components.tabsUseCases.addTab(
                    url = "about:blank",
                    contextId = contextId,
                    selectTab = false
                )

                // Add the new tab to the group
                delay(100) // Wait for tab to be fully created
                addTabToGroup(newTabId, groupId)

                android.util.Log.d("TabViewModel", "Created new tab $newTabId in group $groupId")
            } catch (e: Exception) {
                android.util.Log.e("TabViewModel", "Failed to create new tab in group", e)
            }
        }
    }

    /**
     * Move a group to a different profile
     */
    fun moveGroupToProfile(groupId: String, profileId: String) {
        viewModelScope.launch {
            val group = groupManager.getGroup(groupId) ?: return@launch

            // Update the group's contextId to match the new profile
            val newContextId = when (profileId) {
                "private" -> "private"
                "default" -> "profile_default"
                else -> "profile_$profileId"
            }

            // Update the group
            groupManager.updateGroup(
                groupId = groupId,
                name = group.name,
                color = group.color,
                tabIds = group.tabIds,
                contextId = newContextId
            )

            // Refresh the UI
            _currentProfileId.value?.let { currentProfile ->
                refreshGroupsForProfile(currentProfile)
            }
        }
    }

    /**
     * Ungroup all tabs in a group (alias for ungroupAll)
     */
    fun ungroupAllTabs(groupId: String) {
        ungroupAll(groupId)
    }

    /**
     * Create group with tabs (alias for createGroup)
     */
    fun createGroupWithTabs(tabIds: List<String>, name: String? = null) {
        createGroup(tabIds, name)
    }

    // Methods for drag-and-drop integration

    /**
     * Create a new group from two tabs
     */
    fun createGroupFromTabs(tabId1: String, tabId2: String) {
        groupTabsTogether(tabId1, tabId2)
    }

    /**
     * Add tab to group, removing from previous group if needed
     */
    fun addTabToGroup(tabId: String, groupId: String, fromGroupId: String?) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                // If tab was in another group, remove it first
                if (fromGroupId != null) {
                    groupManager.removeTabFromGroup(tabId, notifyChange = false)
                }

                // Add to new group
                groupManager.addTabToGroup(tabId, groupId, position = null, notifyChange = true)

                saveCurrentOrder(profileId)
            }
        }
    }

    /**
     * Ungroup a tab (remove from group)
     */
    fun ungroupTab(tabId: String, groupId: String?) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                groupManager.removeTabFromGroup(tabId, notifyChange = true)

                // If group has only 1 tab left, ungroup that too
                if (groupId != null) {
                    val group = groupManager.getGroup(groupId)
                    if (group != null && group.tabIds.size == 1) {
                        groupManager.removeTabFromGroup(group.tabIds[0], notifyChange = true)
                    }
                }

                saveCurrentOrder(profileId)
            }
        }
    }

    /**
     * Reorder a group to a new position
     */
    fun reorderGroup(groupId: String, targetGroupId: String) {
        viewModelScope.launch {
            _currentProfileId.value?.let { profileId ->
                val order = orderManager.currentOrder.value ?: return@launch

                val fromIndex = order.primaryOrder.indexOfFirst {
                    it is UnifiedTabOrder.OrderItem.TabGroup && it.groupId == groupId
                }
                val toIndex = order.primaryOrder.indexOfFirst {
                    it is UnifiedTabOrder.OrderItem.TabGroup && it.groupId == targetGroupId
                }

                if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                    orderManager.reorderItem(fromIndex, toIndex)
                    saveCurrentOrder(profileId)
                }
            }
        }
    }

    /**
     * Toggle group collapse state
     */
    fun toggleGroupCollapse(groupId: String) {
        toggleGroupExpanded(groupId)
    }

    /**
     * Select a tab
     */
    fun selectTab(tabId: String) {
        _selectedTabId.value = tabId
    }
}
