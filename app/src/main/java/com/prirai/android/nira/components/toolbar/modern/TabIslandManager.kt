package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.SessionState

/**
 * Manages Tab Islands - grouping, ungrouping, renaming, collapsing, and persistence.
 * Now uses UnifiedTabGroupManager as backend for synchronization with tab sheet.
 * This class provides tab bar specific functionality while delegating persistence
 * to the unified manager.
 */
class TabIslandManager(private val context: Context) {

    // Delegate persistence to unified manager
    private val unifiedManager: UnifiedTabGroupManager by lazy { 
        UnifiedTabGroupManager.getInstance(context)
    }
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // Persist collapse state in SharedPreferences for sync between tab bar and tab sheet
    private val prefs = context.getSharedPreferences("tab_island_prefs", Context.MODE_PRIVATE)
    private val collapseState = mutableMapOf<String, Boolean>()
    
    // Listeners for island changes
    private val changeListeners = mutableListOf<() -> Unit>()
    
    // Debouncing for order rebuilding to prevent cascading updates
    private var rebuildOrderJob: Job? = null

    companion object {
        @Volatile
        private var instance: TabIslandManager? = null

        fun getInstance(context: Context): TabIslandManager {
            return instance ?: synchronized(this) {
                instance ?: TabIslandManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Register a listener for island changes
     */
    fun addChangeListener(listener: () -> Unit) {
        if (!changeListeners.contains(listener)) {
            changeListeners.add(listener)
        }
    }
    
    /**
     * Unregister a listener
     */
    fun removeChangeListener(listener: () -> Unit) {
        changeListeners.remove(listener)
    }
    
    /**
     * Notify all listeners of changes
     */
    private fun notifyListeners() {
        changeListeners.forEach { it.invoke() }
    }

    init {
        // Load collapse state from preferences
        val savedCollapsed = prefs.getStringSet("collapsed_islands", emptySet()) ?: emptySet()
        savedCollapsed.forEach { collapseState[it] = true }
        
        // Listen to unified manager for changes
        scope.launch {
            unifiedManager.groupEvents.collect {
                notifyListeners()
            }
        }
    }
    
    private fun saveCollapseState() {
        prefs.edit().putStringSet("collapsed_islands", collapseState.filter { it.value }.keys).apply()
    }
    
    /**
     * Schedule order rebuild with debouncing to prevent cascading updates
     */
    private fun scheduleOrderRebuild(profileId: String, contextId: String) {
        rebuildOrderJob?.cancel()
        rebuildOrderJob = scope.launch {
            delay(100) // 100ms debounce
            val allTabs = context.components.store.state.tabs
            val orderManager = com.prirai.android.nira.browser.tabs.compose.TabOrderManager.getInstance(context, unifiedManager)
            orderManager.rebuildOrderForProfile(profileId, allTabs.filter { 
                val tabContextId = it.contextId ?: "profile_default"
                tabContextId == contextId || (contextId == "profile_default" && tabContextId == null)
            })
        }
    }


    /**
     * Creates a new island from the given tabs.
     * Returns the created island with the actual group ID from UnifiedTabGroupManager.
     */
    suspend fun createIsland(
        tabIds: List<String>,
        name: String? = null,
        colorIndex: Int? = null
    ): TabIsland {
        val color = if (colorIndex != null) {
            TabIsland.DEFAULT_COLORS[colorIndex % TabIsland.DEFAULT_COLORS.size]
        } else {
            TabIsland.DEFAULT_COLORS.random()
        }
        
        // Get contextId from the first tab to determine profile
        val store = context.components.store
        val allTabs = store.state.tabs
        val firstTab = allTabs.find { it.id == tabIds.firstOrNull() }
        
        // Fallback: if tab not found or has null contextId, check all tabs in the group
        var contextId = firstTab?.contextId
        if (contextId == null) {
            // Try to find contextId from any tab in the group
            for (tabId in tabIds) {
                val tab = allTabs.find { it.id == tabId }
                if (tab?.contextId != null) {
                    contextId = tab.contextId
                    break
                }
            }
            // If still null, use selected tab's contextId as fallback
            if (contextId == null) {
                val selectedTab = allTabs.find { it.id == store.state.selectedTabId }
                contextId = selectedTab?.contextId
            }
            // Last resort: use default profile
            if (contextId == null) {
                contextId = "profile_default"
            }
        }
        
        android.util.Log.d("TabIslandManager", "Creating island with tabIds: $tabIds")
        android.util.Log.d("TabIslandManager", "Store has ${allTabs.size} tabs")
        android.util.Log.d("TabIslandManager", "First tab ID to find: ${tabIds.firstOrNull()}")
        android.util.Log.d("TabIslandManager", "First tab found: ${firstTab?.id}, contextId: $contextId, title: ${firstTab?.content?.title}")
        android.util.Log.d("TabIslandManager", "All tab IDs in store: ${allTabs.map { it.id }}")
        android.util.Log.d("TabIslandManager", "Final contextId to use: $contextId")
        
        // Create group and get the actual group data with correct ID
        val groupData = unifiedManager.createGroup(
            tabIds = tabIds,
            name = name ?: "",
            color = color,
            contextId = contextId
        )
        
        android.util.Log.d("TabIslandManager", "Created group ${groupData.id} with contextId: ${groupData.contextId}")
        
        // Update TabOrderManager to include the new group in its order (debounced)
        val profileId = when {
            contextId == "private" -> "private"
            contextId == "profile_default" || contextId == null -> "default"
            contextId.startsWith("profile_") -> contextId.removePrefix("profile_")
            else -> "default"
        }
        
        scheduleOrderRebuild(profileId, contextId)
        
        // Return TabIsland with the actual group ID from unified manager
        return TabIsland(
            id = groupData.id, // Use the actual group ID, not a new UUID
            name = groupData.name,
            color = groupData.color,
            tabIds = groupData.tabIds.toMutableList(),
            isCollapsed = false
        )
    }

    fun addTabToIsland(tabId: String, islandId: String): Boolean {
        scope.launch {
            unifiedManager.addTabToGroup(tabId, islandId)
        }
        
        return true
    }

    fun removeTabFromIsland(tabId: String, islandId: String): Boolean {
        scope.launch {
            unifiedManager.removeTabFromGroup(tabId)
        }
        
        return true
    }

    /**
     * Removes a tab from any island it belongs to
     */
    fun removeTabFromAnyIsland(tabId: String): Boolean {
        scope.launch {
            unifiedManager.removeTabFromGroup(tabId)
        }
        return true
    }

    /**
     * Renames an island
     */
    fun renameIsland(islandId: String, newName: String): Boolean {
        scope.launch {
            unifiedManager.renameGroup(islandId, newName)
        }
        return true
    }

    /**
     * Changes the color of an island
     */
    fun changeIslandColor(islandId: String, newColor: Int): Boolean {
        scope.launch {
            unifiedManager.changeGroupColor(islandId, newColor)
        }
        return true
    }

    /**
     * Toggles the collapse state of an island.
     * When expanding an island, all other islands are collapsed to maintain single-expand behavior.
     * Use this for the toolbar pill bar.
     */
    fun toggleIslandCollapse(islandId: String): Boolean {
        val currentState = collapseState[islandId] ?: false
        val newState = !currentState

        // If expanding this island, collapse all others
        if (!newState) {
            collapseState.keys.forEach { id ->
                if (id != islandId) {
                    collapseState[id] = true
                }
            }
        }

        collapseState[islandId] = newState
        saveCollapseState()
        notifyListeners()
        return true
    }

    /**
     * Toggles the collapse state of an island for the bottom sheet.
     * Allows multiple islands to be expanded at the same time.
     * State persists across reopens.
     */
    fun toggleIslandCollapseBottomSheet(islandId: String): Boolean {
        val currentState = collapseState[islandId] ?: false
        collapseState[islandId] = !currentState
        saveCollapseState()
        notifyListeners()
        return true
    }

    /**
     * Collapses an island
     */
    fun collapseIsland(islandId: String): Boolean {
        collapseState[islandId] = true
        saveCollapseState()
        notifyListeners()
        return true
    }

    /**
     * Expands an island and collapses all others to maintain single-expand behavior.
     */
    fun expandIsland(islandId: String): Boolean {
        // Collapse all other islands when expanding this one
        collapseState.keys.forEach { id ->
            if (id != islandId) {
                collapseState[id] = true
            }
        }

        collapseState[islandId] = false
        saveCollapseState()
        notifyListeners()
        return true
    }

    /**
     * Deletes an island (ungroups all tabs)
     */
    fun deleteIsland(islandId: String): Boolean {
        scope.launch {
            unifiedManager.deleteGroup(islandId)
        }
        
        collapseState.remove(islandId)
        return true
    }

    /**
     * Gets the island for a given tab ID
     */
    fun getIslandForTab(tabId: String): TabIsland? {
        val group = unifiedManager.getGroupForTab(tabId) ?: return null
        return groupToIsland(group)
    }

    /**
     * Gets all islands
     */
    fun getAllIslands(): List<TabIsland> {
        return unifiedManager.getAllGroups().map { groupToIsland(it) }
    }

    /**
     * Gets an island by ID
     */
    fun getIsland(islandId: String): TabIsland? {
        val group = unifiedManager.getGroup(islandId) ?: return null
        return groupToIsland(group)
    }

    /**
     * Checks if a tab belongs to any island
     */
    fun isTabInIsland(tabId: String): Boolean {
        return unifiedManager.isTabGrouped(tabId)
    }

    /**
     * Cleans up islands when a tab is closed
     */
    fun onTabClosed(tabId: String) {
        scope.launch {
            unifiedManager.onTabClosed(tabId)
        }
    }

    /**
     * Cleans up islands when all tabs are closed
     */
    fun onAllTabsClosed() {
        scope.launch {
            unifiedManager.clearAllGroups()
        }
        collapseState.clear()
    }

    /**
     * Converts tabs list to display items using TabOrderManager for consistency.
     * This ensures tab bar and tab sheet have the same ordering.
     */
    fun createDisplayItems(tabs: List<SessionState>): List<TabPillItem> {
        val displayItems = mutableListOf<TabPillItem>()
        
        // Get the current context ID to determine which profile we're showing
        val store = context.components.store
        val selectedTab = tabs.find { it.id == store.state.selectedTabId }
        val contextId = selectedTab?.contextId ?: if (tabs.any { it.content.private }) "private" else "profile_default"
        
        // Determine profile ID from context ID
        val profileId = when {
            contextId == "private" -> "private"
            contextId == "profile_default" || contextId == null -> "default"
            contextId.startsWith("profile_") -> contextId.removePrefix("profile_")
            else -> "default"
        }
        
        // Get the unified order from TabOrderManager (using runBlocking since this is called from UI)
        val orderManager = com.prirai.android.nira.browser.tabs.compose.TabOrderManager.getInstance(context, unifiedManager)
        val order = kotlinx.coroutines.runBlocking {
            orderManager.loadOrder(profileId)
        }
        
        val tabsById = tabs.associateBy { it.id }
        val allGroups = unifiedManager.getAllGroups()
        val groupsById = allGroups.associateBy { it.id }
        
        // Process items according to the unified order
        for (orderItem in order.primaryOrder) {
            when (orderItem) {
                is com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder.OrderItem.SingleTab -> {
                    val tab = tabsById[orderItem.tabId]
                    if (tab != null) {
                        displayItems.add(
                            TabPillItem.Tab(
                                session = tab,
                                islandId = null
                            )
                        )
                    }
                }
                is com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder.OrderItem.TabGroup -> {
                    val group = groupsById[orderItem.groupId]
                    if (group != null) {
                        val island = groupToIsland(group)
                        val isCollapsed = collapseState[island.id] ?: false
                        
                        if (isCollapsed) {
                            // Show collapsed island pill
                            displayItems.add(
                                TabPillItem.CollapsedIsland(
                                    island = island.copy(isCollapsed = true),
                                    tabCount = island.size()
                                )
                            )
                        } else {
                            // Show expanded island group (header + tabs as one unit)
                            val islandTabs = orderItem.tabIds.mapNotNull { tabId ->
                                tabsById[tabId]
                            }
                            displayItems.add(
                                TabPillItem.ExpandedIslandGroup(
                                    island = island.copy(isCollapsed = false),
                                    tabs = islandTabs
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // Add any tabs that aren't in the order (safety fallback)
        tabs.forEach { tab ->
            val alreadyAdded = displayItems.any { item ->
                when (item) {
                    is TabPillItem.Tab -> item.session.id == tab.id
                    is TabPillItem.ExpandedIslandGroup -> item.tabs.any { it.id == tab.id }
                    is TabPillItem.CollapsedIsland -> item.island.tabIds.contains(tab.id)
                    else -> false
                }
            }
            if (!alreadyAdded) {
                // This tab is not in any order, add it at the end
                displayItems.add(TabPillItem.Tab(session = tab, islandId = null))
            }
        }

        return displayItems
    }

    /**
     * Converts TabGroupData to TabIsland
     */
    private fun groupToIsland(group: TabGroupData): TabIsland {
        return TabIsland(
            id = group.id,
            name = group.name,
            color = group.color,
            tabIds = group.tabIds.toMutableList(),
            isCollapsed = collapseState[group.id] ?: false,
            createdAt = group.createdAt,
            lastModifiedAt = group.createdAt
        )
    }

    // Deprecated methods kept for compatibility
    @Deprecated("Use unified manager directly", ReplaceWith(""))
    fun recordParentChildRelationship(childTabId: String, parentTabId: String) {
        // No-op
    }

    @Deprecated("Use unified manager directly", ReplaceWith(""))
    fun autoGroupWithParent(newTabId: String, parentTabId: String): Boolean {
        return false
    }

    @Deprecated("Use unified manager directly", ReplaceWith(""))
    fun groupTabsByDomain(tabs: List<SessionState>): List<TabIsland> {
        return emptyList()
    }

    @Deprecated("Use unified manager directly", ReplaceWith(""))
    fun reorderTabsInIsland(islandId: String, newTabOrder: List<String>): Boolean {
        return false
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URL(url)
            uri.host?.removePrefix("www.") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
