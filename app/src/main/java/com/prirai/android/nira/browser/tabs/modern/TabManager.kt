package com.prirai.android.nira.browser.tabs.modern

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabgroups.TabGroupData

/**
 * Simplified tab manager - single source of truth for all tab state.
 * Replaces the complex TabViewModel + TabOrderManager + UnifiedTabOrder stack.
 */
class TabManager(
    private val context: Context,
    private val store: BrowserStore,
    private val groupManager: UnifiedTabGroupManager
) {
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    data class TabState(
        val tabs: List<TabSessionState> = emptyList(),
        val selectedTabId: String? = null,
        val groups: List<TabGroupData> = emptyList(),
        val expandedGroupIds: Set<String> = emptySet(),
        val currentProfileId: String = "default"
    )
    
    private val _state = MutableStateFlow(TabState())
    val state: StateFlow<TabState> = _state.asStateFlow()
    
    fun loadProfile(profileId: String, isPrivate: Boolean) {
        val contextId = when {
            isPrivate -> "private"
            profileId == "default" -> "profile_default"
            else -> "profile_$profileId"
        }
        
        val tabs = if (isPrivate) {
            store.state.privateTabs
        } else {
            store.state.tabs.filter { tab ->
                val tabContextId = tab.contextId ?: "profile_default"
                tabContextId == contextId
            }
        }
        
        val selectedTab = store.state.selectedTabId
        val groups = groupManager.getAllGroups().filter { it.contextId == contextId }
        
        _state.value = TabState(
            tabs = tabs,
            selectedTabId = selectedTab,
            groups = groups,
            expandedGroupIds = groups.map { it.id }.toSet(),
            currentProfileId = profileId
        )
    }
    
    fun selectTab(tabId: String) {
        store.dispatch(mozilla.components.browser.state.action.TabListAction.SelectTabAction(tabId))
    }
    
    fun closeTab(tabId: String) {
        store.dispatch(mozilla.components.browser.state.action.TabListAction.RemoveTabAction(tabId))
    }
    
    fun toggleGroup(groupId: String) {
        val current = _state.value.expandedGroupIds
        _state.value = _state.value.copy(
            expandedGroupIds = if (groupId in current) {
                current - groupId
            } else {
                current + groupId
            }
        )
    }
    
    fun closeGroup(groupId: String) {
        scope.launch {
            val group = _state.value.groups.find { it.id == groupId } ?: return@launch
            group.tabIds.forEach { tabId ->
                closeTab(tabId)
            }
            groupManager.deleteGroup(groupId)
        }
    }
    
    fun ungroupTabs(groupId: String) {
        scope.launch {
            groupManager.deleteGroup(groupId)
        }
    }
    
    fun addTabToGroup(tabId: String, groupId: String) {
        scope.launch {
            groupManager.addTabToGroup(tabId, groupId)
        }
    }
    
    fun removeTabFromGroup(tabId: String) {
        scope.launch {
            groupManager.removeTabFromGroup(tabId)
        }
    }
    
    fun renameGroup(groupId: String, newName: String) {
        scope.launch {
            groupManager.updateGroup(groupId, name = newName)
        }
    }
    
    fun changeGroupColor(groupId: String, color: Int) {
        scope.launch {
            groupManager.updateGroup(groupId, color = color)
        }
    }
    
    fun createGroup(name: String, color: Int, tabIds: List<String>, contextId: String) {
        scope.launch {
            groupManager.createGroup(tabIds, name, color, contextId = contextId)
        }
    }
}
