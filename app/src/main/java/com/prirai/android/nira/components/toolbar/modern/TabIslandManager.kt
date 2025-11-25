package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import mozilla.components.browser.state.state.SessionState

/**
 * Manages Tab Islands - grouping, ungrouping, renaming, collapsing, and persistence.
 * Handles automatic grouping based on parent-child relationships and manual grouping.
 */
class TabIslandManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // In-memory cache of islands
    private val islands = mutableMapOf<String, TabIsland>()

    // Map of tab ID to island ID for quick lookup
    private val tabToIslandMap = mutableMapOf<String, String>()

    // Track parent-child relationships for automatic grouping
    private val parentChildRelationships = mutableMapOf<String, String>()

    init {
        loadIslands()
    }

    companion object {
        private const val PREFS_NAME = "tab_islands"
        private const val KEY_ISLANDS = "islands"
        private const val KEY_TAB_TO_ISLAND_MAP = "tab_to_island_map"
        private const val KEY_PARENT_CHILD_MAP = "parent_child_map"

        @Volatile
        private var instance: TabIslandManager? = null

        fun getInstance(context: Context): TabIslandManager {
            return instance ?: synchronized(this) {
                instance ?: TabIslandManager(context.applicationContext).also { instance = it }
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
        // Remove tabs from existing islands first
        tabIds.forEach { tabId ->
            tabToIslandMap[tabId]?.let { islandId ->
                removeTabFromIsland(tabId, islandId)
            }
        }

        val island = TabIsland.create(
            tabIds = tabIds,
            colorIndex = colorIndex ?: islands.size,
            name = name
        )

        islands[island.id] = island
        tabIds.forEach { tabId ->
            tabToIslandMap[tabId] = island.id
        }

        saveIslands()
        return island
    }

    /**
     * Adds a tab to an existing island
     */
    fun addTabToIsland(tabId: String, islandId: String): Boolean {
        val island = islands[islandId] ?: return false

        // Remove from previous island if exists
        tabToIslandMap[tabId]?.let { previousIslandId ->
            if (previousIslandId != islandId) {
                removeTabFromIsland(tabId, previousIslandId)
            }
        }

        islands[islandId] = island.withTabAdded(tabId)
        tabToIslandMap[tabId] = islandId

        saveIslands()
        return true
    }

    /**
     * Removes a tab from an island
     */
    fun removeTabFromIsland(tabId: String, islandId: String): Boolean {
        val island = islands[islandId] ?: return false

        islands[islandId] = island.withTabRemoved(tabId)
        tabToIslandMap.remove(tabId)

        // If island is now empty, remove it
        if (island.isEmpty()) {
            islands.remove(islandId)
        }

        saveIslands()
        return true
    }

    /**
     * Removes a tab from any island it belongs to
     */
    fun removeTabFromAnyIsland(tabId: String): Boolean {
        val islandId = tabToIslandMap[tabId] ?: return false
        return removeTabFromIsland(tabId, islandId)
    }

    /**
     * Renames an island
     */
    fun renameIsland(islandId: String, newName: String): Boolean {
        val island = islands[islandId] ?: return false
        islands[islandId] = island.withName(newName)
        saveIslands()
        return true
    }

    /**
     * Changes the color of an island
     */
    fun changeIslandColor(islandId: String, newColor: Int): Boolean {
        val island = islands[islandId] ?: return false
        islands[islandId] = island.withColor(newColor)
        saveIslands()
        return true
    }

    /**
     * Toggles the collapse state of an island.
     * When expanding an island, all other islands are collapsed to maintain single-expand behavior.
     * Use this for the toolbar pill bar.
     */
    fun toggleIslandCollapse(islandId: String): Boolean {
        val island = islands[islandId] ?: return false
        val newCollapsedState = !island.isCollapsed

        // If expanding this island, collapse all others
        if (!newCollapsedState) {
            // Collapse all other islands
            islands.forEach { (otherId, otherIsland) ->
                if (otherId != islandId && !otherIsland.isCollapsed) {
                    islands[otherId] = otherIsland.withCollapsed(true)
                }
            }
        }

        islands[islandId] = island.withCollapsed(newCollapsedState)
        saveIslands()
        return true
    }

    /**
     * Toggles the collapse state of an island for the bottom sheet.
     * Allows multiple islands to be expanded at the same time.
     * State persists across reopens.
     */
    fun toggleIslandCollapseBottomSheet(islandId: String): Boolean {
        val island = islands[islandId] ?: return false
        val newCollapsedState = !island.isCollapsed

        islands[islandId] = island.withCollapsed(newCollapsedState)
        saveIslands()
        return true
    }

    /**
     * Collapses an island
     */
    fun collapseIsland(islandId: String): Boolean {
        val island = islands[islandId] ?: return false
        islands[islandId] = island.withCollapsed(true)
        saveIslands()
        return true
    }

    /**
     * Expands an island and collapses all others to maintain single-expand behavior.
     */
    fun expandIsland(islandId: String): Boolean {
        val island = islands[islandId] ?: return false

        // Collapse all other islands when expanding this one
        islands.forEach { (otherId, otherIsland) ->
            if (otherId != islandId && !otherIsland.isCollapsed) {
                islands[otherId] = otherIsland.withCollapsed(true)
            }
        }

        islands[islandId] = island.withCollapsed(false)
        saveIslands()
        return true
    }

    /**
     * Deletes an island (ungroups all tabs)
     */
    fun deleteIsland(islandId: String): Boolean {
        val island = islands[islandId] ?: return false

        // Remove all tab associations
        island.tabIds.forEach { tabId ->
            tabToIslandMap.remove(tabId)
        }

        islands.remove(islandId)
        saveIslands()
        return true
    }

    /**
     * Gets the island for a given tab ID
     */
    fun getIslandForTab(tabId: String): TabIsland? {
        val islandId = tabToIslandMap[tabId] ?: return null
        return islands[islandId]
    }

    /**
     * Gets all islands
     */
    fun getAllIslands(): List<TabIsland> {
        return islands.values.toList().sortedBy { it.createdAt }
    }

    /**
     * Gets an island by ID
     */
    fun getIsland(islandId: String): TabIsland? {
        return islands[islandId]
    }

    /**
     * Checks if a tab belongs to any island
     */
    fun isTabInIsland(tabId: String): Boolean {
        return tabToIslandMap.containsKey(tabId)
    }

    /**
     * Records parent-child relationship for automatic grouping
     */
    fun recordParentChildRelationship(childTabId: String, parentTabId: String) {
        parentChildRelationships[childTabId] = parentTabId
        saveIslands()
    }

    /**
     * Automatically groups a new tab with its parent if parent is in an island
     */
    fun autoGroupWithParent(newTabId: String, parentTabId: String): Boolean {
        recordParentChildRelationship(newTabId, parentTabId)

        // Check if parent is in an island
        val parentIslandId = tabToIslandMap[parentTabId] ?: return false

        // Add child to the same island
        return addTabToIsland(newTabId, parentIslandId)
    }

    /**
     * Groups tabs by domain similarity (manual operation)
     */
    fun groupTabsByDomain(tabs: List<SessionState>): List<TabIsland> {
        val domainGroups = mutableMapOf<String, MutableList<String>>()

        tabs.forEach { tab ->
            val domain = extractDomain(tab.content.url ?: "")
            if (domain.isNotBlank()) {
                domainGroups.getOrPut(domain) { mutableListOf() }.add(tab.id)
            }
        }

        val createdIslands = mutableListOf<TabIsland>()
        var colorIndex = islands.size

        domainGroups.forEach { (domain, tabIds) ->
            if (tabIds.size > 1) {
                val island = createIsland(
                    tabIds = tabIds,
                    name = domain,
                    colorIndex = colorIndex++
                )
                createdIslands.add(island)
            }
        }

        return createdIslands
    }

    /**
     * Reorders tabs within an island
     */
    fun reorderTabsInIsland(islandId: String, newTabOrder: List<String>): Boolean {
        val island = islands[islandId] ?: return false
        islands[islandId] = island.withTabsReordered(newTabOrder)
        saveIslands()
        return true
    }

    /**
     * Converts tabs list to display items with island information
     * Maintains the original tab order - islands appear where their first tab was
     */
    fun createDisplayItems(tabs: List<SessionState>): List<TabPillItem> {
        val displayItems = mutableListOf<TabPillItem>()
        val processedIslands = mutableSetOf<String>()
        val processedTabs = mutableSetOf<String>()

        // Process tabs in their original order
        tabs.forEach { tab ->
            // Skip if we already added this tab as part of an island
            if (processedTabs.contains(tab.id)) {
                return@forEach
            }

            val islandId = tabToIslandMap[tab.id]

            if (islandId != null && !processedIslands.contains(islandId)) {
                // First tab of an island - add the island here
                val island = islands[islandId]
                if (island != null) {
                    processedIslands.add(islandId)

                    if (island.isCollapsed) {
                        // Show collapsed island pill
                        displayItems.add(
                            TabPillItem.CollapsedIsland(
                                island = island,
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
                                island = island,
                                tabs = islandTabs
                            )
                        )
                    }

                    // Mark all island tabs as processed
                    island.tabIds.forEach { processedTabs.add(it) }
                }
            } else if (islandId == null) {
                // Tab is not in any island - add it in its original position
                displayItems.add(TabPillItem.Tab(session = tab))
                processedTabs.add(tab.id)
            }
        }

        return displayItems
    }

    /**
     * Cleans up islands when a tab is closed
     */
    fun onTabClosed(tabId: String) {
        removeTabFromAnyIsland(tabId)
        parentChildRelationships.remove(tabId)
        saveIslands()
    }

    /**
     * Cleans up islands when all tabs are closed
     */
    fun onAllTabsClosed() {
        islands.clear()
        tabToIslandMap.clear()
        parentChildRelationships.clear()
        saveIslands()
    }

    // Persistence methods

    private fun saveIslands() {
        try {
            val islandsAdapter = moshi.adapter<List<TabIsland>>(
                Types.newParameterizedType(List::class.java, TabIsland::class.java)
            )
            val mapAdapter = moshi.adapter<Map<String, String>>(
                Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            )

            prefs.edit().apply {
                putString(KEY_ISLANDS, islandsAdapter.toJson(islands.values.toList()))
                putString(KEY_TAB_TO_ISLAND_MAP, mapAdapter.toJson(tabToIslandMap))
                putString(KEY_PARENT_CHILD_MAP, mapAdapter.toJson(parentChildRelationships))
                apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("TabIslandManager", "Failed to save islands", e)
        }
    }

    private fun loadIslands() {
        try {
            val islandsAdapter = moshi.adapter<List<TabIsland>>(
                Types.newParameterizedType(List::class.java, TabIsland::class.java)
            )
            val mapAdapter = moshi.adapter<Map<String, String>>(
                Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            )

            // Load islands
            val islandsJson = prefs.getString(KEY_ISLANDS, null)
            if (islandsJson != null) {
                val loadedIslands = islandsAdapter.fromJson(islandsJson)
                if (loadedIslands != null) {
                    islands.clear()
                    loadedIslands.forEach { island ->
                        islands[island.id] = island
                    }
                }
            }

            // Load tab to island map
            val mapJson = prefs.getString(KEY_TAB_TO_ISLAND_MAP, null)
            if (mapJson != null) {
                val loadedMap = mapAdapter.fromJson(mapJson)
                if (loadedMap != null) {
                    tabToIslandMap.clear()
                    tabToIslandMap.putAll(loadedMap)
                }
            }

            // Load parent-child relationships
            val parentChildJson = prefs.getString(KEY_PARENT_CHILD_MAP, null)
            if (parentChildJson != null) {
                val loadedMap = mapAdapter.fromJson(parentChildJson)
                if (loadedMap != null) {
                    parentChildRelationships.clear()
                    parentChildRelationships.putAll(loadedMap)
                }
            }

            // Clean up stale entries
            cleanupStaleData()
        } catch (e: Exception) {
            android.util.Log.e("TabIslandManager", "Failed to load islands", e)
            islands.clear()
            tabToIslandMap.clear()
            parentChildRelationships.clear()
        }
    }

    private fun cleanupStaleData() {
        // Remove empty islands
        val emptyIslands = islands.filter { it.value.isEmpty() }.keys
        emptyIslands.forEach { islandId ->
            islands.remove(islandId)
        }

        // Remove orphaned tab mappings
        val validTabIds = islands.values.flatMap { it.tabIds }.toSet()
        tabToIslandMap.keys.retainAll(validTabIds)
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
