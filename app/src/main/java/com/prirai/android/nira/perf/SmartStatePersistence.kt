package com.prirai.android.nira.perf

import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState

/**
 * Smart state persistence strategy inspired by Firefox.
 * 
 * Only saves full engine state for:
 * 1. Selected tab (always)
 * 2. Recently accessed tabs (top 2-3)
 * 
 * Other tabs save URL-only for fast restoration.
 * 
 * References:
 * - mozilla/components/browser/state/src/main/java/mozilla/components/browser/state/engine/middleware/SessionPrioritizationMiddleware.kt
 */
class SmartStatePersistence {
    
    companion object {
        // Only keep full state for these many tabs
        const val MAX_TABS_WITH_FULL_STATE = 3
        
        // Maximum age for full state (24 hours)
        const val MAX_STATE_AGE_MS = 24 * 60 * 60 * 1000L
    }
    
    /**
     * Determine which tabs should have full state saved.
     * 
     * @param state Current browser state
     * @return Set of tab IDs that should have full state
     */
    fun getTabsForFullStatePersistence(state: BrowserState): Set<String> {
        val selectedTabId = state.selectedTabId
        
        // Sort tabs by last access time (most recent first)
        val recentTabs = state.tabs
            .sortedByDescending { it.lastAccess }
            .take(MAX_TABS_WITH_FULL_STATE)
            .map { it.id }
            .toMutableSet()
        
        // Always include selected tab
        if (selectedTabId != null) {
            recentTabs.add(selectedTabId)
        }
        
        return recentTabs
    }
    
    /**
     * Check if a tab's state should be saved.
     * 
     * @param tab The tab to check
     * @param importantTabIds Set of tab IDs that need full state
     * @return true if full state should be saved
     */
    fun shouldSaveFullState(
        tab: TabSessionState,
        importantTabIds: Set<String>
    ): Boolean {
        // Check if tab is in important set
        if (tab.id in importantTabIds) {
            return true
        }
        
        // Check if state is recent enough
        val stateAge = System.currentTimeMillis() - tab.lastAccess
        if (stateAge > MAX_STATE_AGE_MS) {
            return false
        }
        
        return false
    }
    
    /**
     * Calculate state size in bytes (approximate).
     * Used for cache management.
     */
    fun estimateStateSize(state: mozilla.components.concept.engine.EngineSessionState): Long {
        return try {
            state.toString().length.toLong()
        } catch (e: Exception) {
            0L
        }
    }
}
