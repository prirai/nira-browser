package com.prirai.android.nira.browser.tabgroups

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext

/**
 * Middleware that monitors tab creation and applies cross-domain grouping logic.
 */
class TabGroupMiddleware(
    private val tabGroupManager: TabGroupManager
) : Middleware<BrowserState, BrowserAction> {

    override fun invoke(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        // Process the action first
        next(action)
        
        // Handle post-action state changes
        when (action) {
            is TabListAction.AddTabAction -> {
                handleNewTab(context.state, action)
            }
            is TabListAction.SelectTabAction -> {
                // Update tab group context when switching tabs
                handleTabSelection(context.state, action)
            }
            else -> {
                // No action needed for other types
            }
        }
    }
    
    private fun handleNewTab(state: BrowserState, action: TabListAction.AddTabAction) {
        val newTab = action.tab
        val newTabUrl = newTab.content.url
        
        
        // Don't auto-group tabs created via NEW_TAB button or address bar
        if (newTab.source == mozilla.components.browser.state.state.SessionState.Source.Internal.NewTab ||
            newTab.source == mozilla.components.browser.state.state.SessionState.Source.Internal.UserEntered) {
            return
        }
        
        // Check both the URL in the tab and the URL being loaded
        val effectiveUrl = if (newTabUrl.isBlank() || newTabUrl == "about:blank") {
            // Check if this tab has a pending URL load
            val loadRequest = newTab.content.url
            if (loadRequest.isNotBlank() && loadRequest != "about:blank") {
                loadRequest
            } else {
                return
            }
        } else {
            newTabUrl
        }
        
        // Find the currently selected tab (this should be the source of the new tab)
        val sourceTab = state.selectedTabId?.let { selectedId ->
            if (selectedId != newTab.id) {
                state.tabs.find { it.id == selectedId }
            } else {
                // If the new tab is already selected, find the previous one
                state.tabs.filter { it.id != newTab.id }
                    .maxByOrNull { it.lastAccess }
            }
        }
        
        
        if (sourceTab != null) {
            val sourceUrl = sourceTab.content.url
            
            // Only group if both URLs are valid and from different domains
            if (sourceUrl.isNotBlank() && sourceUrl != "about:blank") {
                val sourceDomain = extractDomain(sourceUrl)
                val newDomain = extractDomain(effectiveUrl)
                
                
                if (sourceDomain != newDomain && 
                    sourceDomain != "unknown" && 
                    newDomain != "unknown") {
                    
                    
                    // This appears to be a cross-domain link - group them
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            tabGroupManager.handleNewTabFromLink(
                                newTabId = newTab.id,
                                newTabUrl = effectiveUrl,
                                sourceTabId = sourceTab.id,
                                sourceTabUrl = sourceUrl
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("TabGroupMiddleware", "Error in cross-domain grouping", e)
                        }
                    }
                } else {
                    // For same domain, don't force grouping unless it's a natural fit
                }
            }
        }
    }
    
    private fun handleTabSelection(state: BrowserState, action: TabListAction.SelectTabAction) {
        // When a tab is selected, update the tab group bar context
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val selectedTab = state.tabs.find { it.id == action.tabId }
                if (selectedTab != null) {
                    // Force refresh of current group to update UI
                    tabGroupManager.updateCurrentTabContext(selectedTab.id)
                }
            } catch (e: Exception) {
                // Ignore errors during tab selection updates
            }
        }
    }
    
    /**
     * Extract domain from URL for comparison.
     */
    private fun extractDomain(url: String): String {
        return try {
            Uri.parse(url).host?.replace("www.", "") ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}