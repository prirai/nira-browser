package com.prirai.android.nira.browser.tabgroups

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.expansionDataStore: DataStore<Preferences> by preferencesDataStore(name = "group_expansion_state")

/**
 * Manages the expand/collapse states for tab groups.
 * Persists state across app restarts using DataStore.
 * Provides reactive StateFlow for UI components to observe expansion state.
 */
class GroupExpansionStateManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: GroupExpansionStateManager? = null

        fun getInstance(context: Context): GroupExpansionStateManager {
            return instance ?: synchronized(this) {
                instance ?: GroupExpansionStateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // In-memory state flow for reactive updates
    private val _expandedGroupIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedGroupIds: StateFlow<Set<String>> = _expandedGroupIds.asStateFlow()

    private val EXPANDED_GROUPS_KEY = stringPreferencesKey("expanded_group_ids")

    /**
     * Initialize the manager by loading persisted state
     */
    suspend fun initialize() {
        val expandedIds = loadExpandedGroupIds()
        _expandedGroupIds.value = expandedIds
    }

    /**
     * Load expanded group IDs from DataStore
     */
    private suspend fun loadExpandedGroupIds(): Set<String> {
        return try {
            context.expansionDataStore.data
                .map { prefs ->
                    prefs[EXPANDED_GROUPS_KEY]?.let { jsonString ->
                        json.decodeFromString<Set<String>>(jsonString)
                    } ?: emptySet()
                }
                .first()
        } catch (e: Exception) {
            // Return empty set if loading fails
            emptySet()
        }
    }

    /**
     * Save expanded group IDs to DataStore
     */
    private suspend fun saveExpandedGroupIds(expandedIds: Set<String>) {
        try {
            context.expansionDataStore.edit { prefs ->
                prefs[EXPANDED_GROUPS_KEY] = json.encodeToString(expandedIds)
            }
        } catch (e: Exception) {
            // Log error but don't crash
            android.util.Log.e("GroupExpansionStateManager", "Failed to save expansion state", e)
        }
    }

    /**
     * Check if a group is expanded
     */
    fun isGroupExpanded(groupId: String): Boolean {
        return _expandedGroupIds.value.contains(groupId)
    }

    /**
     * Toggle the expansion state of a group
     */
    suspend fun toggleGroupExpansion(groupId: String) {
        val currentExpanded = _expandedGroupIds.value
        val newExpanded = if (currentExpanded.contains(groupId)) {
            currentExpanded - groupId
        } else {
            currentExpanded + groupId
        }

        _expandedGroupIds.value = newExpanded
        saveExpandedGroupIds(newExpanded)
    }

    /**
     * Expand a specific group
     */
    suspend fun expandGroup(groupId: String) {
        val currentExpanded = _expandedGroupIds.value
        if (!currentExpanded.contains(groupId)) {
            val newExpanded = currentExpanded + groupId
            _expandedGroupIds.value = newExpanded
            saveExpandedGroupIds(newExpanded)
        }
    }

    /**
     * Collapse a specific group
     */
    suspend fun collapseGroup(groupId: String) {
        val currentExpanded = _expandedGroupIds.value
        if (currentExpanded.contains(groupId)) {
            val newExpanded = currentExpanded - groupId
            _expandedGroupIds.value = newExpanded
            saveExpandedGroupIds(newExpanded)
        }
    }

    /**
     * Expand multiple groups
     */
    suspend fun expandGroups(groupIds: Set<String>) {
        val currentExpanded = _expandedGroupIds.value
        val newExpanded = currentExpanded + groupIds
        if (newExpanded != currentExpanded) {
            _expandedGroupIds.value = newExpanded
            saveExpandedGroupIds(newExpanded)
        }
    }

    /**
     * Collapse multiple groups
     */
    suspend fun collapseGroups(groupIds: Set<String>) {
        val currentExpanded = _expandedGroupIds.value
        val newExpanded = currentExpanded - groupIds
        if (newExpanded != currentExpanded) {
            _expandedGroupIds.value = newExpanded
            saveExpandedGroupIds(newExpanded)
        }
    }

    /**
     * Set the expansion state for multiple groups at once
     */
    suspend fun setExpansionState(expandedGroupIds: Set<String>) {
        _expandedGroupIds.value = expandedGroupIds
        saveExpandedGroupIds(expandedGroupIds)
    }

    /**
     * Clear all expansion states (collapse all groups)
     */
    suspend fun collapseAllGroups() {
        _expandedGroupIds.value = emptySet()
        saveExpandedGroupIds(emptySet())
    }

    /**
     * Expand all groups
     */
    suspend fun expandAllGroups(allGroupIds: Set<String>) {
        _expandedGroupIds.value = allGroupIds
        saveExpandedGroupIds(allGroupIds)
    }

    /**
     * Remove expansion state for groups that no longer exist
     * Call this when groups are deleted to clean up orphaned states
     */
    suspend fun cleanupOrphanedStates(existingGroupIds: Set<String>) {
        val currentExpanded = _expandedGroupIds.value
        val cleanedExpanded = currentExpanded.filter { it in existingGroupIds }.toSet()

        if (cleanedExpanded != currentExpanded) {
            _expandedGroupIds.value = cleanedExpanded
            saveExpandedGroupIds(cleanedExpanded)
        }
    }
}