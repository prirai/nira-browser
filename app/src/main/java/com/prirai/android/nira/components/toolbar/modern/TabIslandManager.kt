package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import mozilla.components.browser.state.state.SessionState
import com.prirai.android.nira.browser.tabgroups.TabGroupManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

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
    
    // UI-only state for collapse/expand (not persisted)
    private val collapseState = mutableMapOf<String, Boolean>()
    
    // Listeners for island changes
    private val changeListeners = mutableListOf<() -> Unit>()

    companion object {
        private const val TAG = "TabIslandManager"

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
        // Listen to unified manager for changes
        scope.launch {
            unifiedManager.groupEvents.collect {
                notifyListeners()
            }
        }
    }


    /**
     * Creates a new island from the given tabs
     */
    fun createIsland(
        tabIds: List<String>,
        name: String? = null,
        colorIndex: Int? = null
    ): TabIsland {
        Log.d(TAG, "createIsland: Creating island with ${tabIds.size} tabs, name=$name")
        
        val color = if (colorIndex != null) {
            TabIsland.DEFAULT_COLORS[colorIndex % TabIsland.DEFAULT_COLORS.size]
        } else {
            TabIsland.DEFAULT_COLORS.random()
        }
        
        scope.launch {
            val group = unifiedManager.createGroup(
                tabIds = tabIds,
                name = name ?: "",
                color = color
            )
            Log.d(TAG, "createIsland: Created group ${group.id}")
        }
        
        // Return optimistic result
        return TabIsland(
            id = java.util.UUID.randomUUID().toString(),
            name = name ?: "",
            color = color,
            tabIds = tabIds.toMutableList(),
            isCollapsed = false
        )
    }

    /**
     * Adds a tab to an existing island
     */
    fun addTabToIsland(tabId: String, islandId: String): Boolean {
        Log.d(TAG, "addTabToIsland: Adding tab $tabId to island $islandId")

        scope.launch {
            unifiedManager.addTabToGroup(tabId, islandId)
        }
        
        return true
    }

    /**
     * Removes a tab from an island
     */
    fun removeTabFromIsland(tabId: String, islandId: String): Boolean {
        Log.d(TAG, "removeTabFromIsland: Removing tab $tabId from island $islandId")

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
        notifyListeners()
        return true
    }

    /**
     * Collapses an island
     */
    fun collapseIsland(islandId: String): Boolean {
        collapseState[islandId] = true
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
        notifyListeners()
        return true
    }

    /**
     * Deletes an island (ungroups all tabs)
     */
    fun deleteIsland(islandId: String): Boolean {
        Log.d(TAG, "deleteIsland: Deleting island $islandId")

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
     * Converts tabs list to display items with island information
     * Maintains the original tab order - islands appear where their first tab was
     */
    fun createDisplayItems(tabs: List<SessionState>): List<TabPillItem> {
        val displayItems = mutableListOf<TabPillItem>()
        val processedIslands = mutableSetOf<String>()
        val processedTabs = mutableSetOf<String>()

        val allGroups = unifiedManager.getAllGroups()
        val tabToGroupMap = mutableMapOf<String, TabGroupData>()
        allGroups.forEach { group ->
            group.tabIds.forEach { tabId ->
                tabToGroupMap[tabId] = group
            }
        }

        // Process tabs in their original order
        tabs.forEach { tab ->
            // Skip if we already added this tab as part of an island
            if (processedTabs.contains(tab.id)) {
                return@forEach
            }

            val group = tabToGroupMap[tab.id]

            if (group != null && !processedIslands.contains(group.id)) {
                // First tab of an island - add the island here
                processedIslands.add(group.id)

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
                    val islandTabs = island.tabIds.mapNotNull { tabId ->
                        tabs.find { it.id == tabId }
                    }
                    displayItems.add(
                        TabPillItem.ExpandedIslandGroup(
                            island = island.copy(isCollapsed = false),
                            tabs = islandTabs
                        )
                    )
                }

                // Mark all island tabs as processed
                island.tabIds.forEach { processedTabs.add(it) }
            } else if (group == null) {
                // Tab is not in any island - add it in its original position
                displayItems.add(TabPillItem.Tab(session = tab))
                processedTabs.add(tab.id)
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
