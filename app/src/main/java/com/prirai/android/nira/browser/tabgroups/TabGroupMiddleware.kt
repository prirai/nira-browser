package com.prirai.android.nira.browser.tabgroups

import android.util.Log
import androidx.core.net.toUri
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
    private val tabGroupManager: UnifiedTabGroupManager
) : Middleware<BrowserState, BrowserAction> {

    companion object {
        private const val TAG = "TabGroupMiddleware"
    }

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
    
    /**
     * Handles new tab creation to determine if it should be auto-grouped.
     *
     * This method analyzes newly created tabs to detect if they were opened from
     * a link in another tab. If so, it automatically groups them together to
     * maintain browsing context.
     *
     * Detection logic:
     * - Checks if tab has a parentId (set by context menu "open in new tab")
     * - Falls back to using currently selected tab as source
     * - Skips tabs with blank URLs (waiting for actual URL to load)
     * - Skips system URLs (about:, chrome:)
     *
     * Grouping triggers:
     * - Any tab opened from another tab with a valid URL
     * - Both same-domain and cross-domain links
     * - Delegates actual grouping to UnifiedTabGroupManager.handleNewTabFromLink()
     *
     * Logging:
     * - All decisions are logged with tag "TabGroupMiddleware"
     * - Useful for debugging why tabs are/aren't grouped
     * - Use: adb logcat | grep TabGroupMiddleware
     *
     * @param state Current browser state containing all tabs
     * @param action The AddTabAction containing the new tab
     */
    private fun handleNewTab(state: BrowserState, action: TabListAction.AddTabAction) {
        val newTab = action.tab
        val newTabUrl = newTab.content.url

        Log.d(TAG, "handleNewTab: tabId=${newTab.id}, url=$newTabUrl, source=${newTab.source}")

        // Check both the URL in the tab and the URL being loaded
        val effectiveUrl = if (newTabUrl.isBlank() || newTabUrl == "about:blank") {
            // If the tab has no URL yet, wait for the actual URL to be loaded
            // We'll catch it on the content update
            Log.d(TAG, "Tab has blank URL, skipping for now")
            return
        } else {
            newTabUrl
        }

        // Don't auto-group tabs with about: or chrome: URLs
        if (effectiveUrl.startsWith("about:") || effectiveUrl.startsWith("chrome:")) {
            Log.d(TAG, "Skipping system URL: $effectiveUrl")
            return
        }

        // Find the currently selected tab or the tab with parentId
        val sourceTab = if (newTab.parentId != null) {
            // If tab has a parent, use that as the source
            state.tabs.find { it.id == newTab.parentId }
        } else {
            // Otherwise use the currently selected tab
            state.selectedTabId?.let { selectedId ->
                if (selectedId != newTab.id) {
                    state.tabs.find { it.id == selectedId }
                } else {
                    // If the new tab is already selected, find the previous one
                    state.tabs.filter { it.id != newTab.id }
                        .maxByOrNull { it.lastAccess }
                }
            }
        }

        Log.d(TAG, "Source tab: ${sourceTab?.id}, url=${sourceTab?.content?.url}")

        if (sourceTab != null) {
            val sourceUrl = sourceTab.content.url

            // Group all links opened from another tab (both same-domain and cross-domain)
            if (sourceUrl.isNotBlank() && sourceUrl != "about:blank") {
                Log.d(TAG, "Link opened from source tab, grouping tabs")

                // Group the new tab with the source tab
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        tabGroupManager.handleNewTabFromLink(
                            newTabId = newTab.id,
                            newTabUrl = effectiveUrl,
                            sourceTabId = sourceTab.id,
                            sourceTabUrl = sourceUrl
                        )
                        Log.d(TAG, "Successfully grouped tabs")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to group tabs", e)
                    }
                }
            }
        } else {
            Log.d(TAG, "No source tab found")
        }
    }
    
    private fun handleTabSelection(state: BrowserState, action: TabListAction.SelectTabAction) {
        // UnifiedTabGroupManager handles state updates automatically via StateFlow
        // No manual refresh needed
    }
    
    /**
     * Extract domain from URL for comparison.
     */
    private fun extractDomain(url: String): String {
        return try {
            url.toUri().host?.replace("www.", "") ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}