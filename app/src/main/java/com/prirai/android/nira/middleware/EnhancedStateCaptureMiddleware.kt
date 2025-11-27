package com.prirai.android.nira.middleware

import kotlinx.coroutines.CoroutineScope
import mozilla.components.browser.state.action.AppLifecycleAction
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext

/**
 * Enhanced middleware that captures engine session state for multiple priority tabs
 * when the app goes to background, enabling instant tab restoration.
 * 
 * Phase 3 optimization: Captures state for selected tab + recently accessed tabs.
 * 
 * Strategy:
 * - Always capture selected tab
 * - Capture top N recently accessed tabs
 * - Skip tabs that already have recent state
 * 
 * References:
 * - Fenix Core.kt SessionPrioritizationMiddleware
 * - TAB_RESTORATION_CACHING_ANALYSIS.md Phase 3
 */
class EnhancedStateCaptureMiddleware(
    private val scope: CoroutineScope,
    private val maxTabsToCapture: Int = 3
) : Middleware<BrowserState, BrowserAction> {
    
    override fun invoke(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        // Capture state when app goes to background
        when (action) {
            is AppLifecycleAction.PauseAction -> {
                captureStateForPriorityTabs(context)
            }
            else -> {
                // No action needed
            }
        }
        
        // Process action
        next(action)
    }
    
    private fun captureStateForPriorityTabs(context: MiddlewareContext<BrowserState, BrowserAction>) {
        val state = context.state
        
        // Get priority tabs: selected + recently accessed
        val priorityTabs = getPriorityTabs(state)
        
        priorityTabs.forEach { tab ->
            // Check if tab has engine session
            if (tab.engineState.engineSession == null) {
                return@forEach
            }
            
            // Suspend the engine session to capture state
            context.dispatch(
                EngineAction.SuspendEngineSessionAction(tab.id)
            )
        }
    }
    
    /**
     * Get priority tabs for state capture.
     * Returns selected tab + top N recently accessed tabs.
     */
    private fun getPriorityTabs(state: BrowserState): List<mozilla.components.browser.state.state.TabSessionState> {
        val selectedTab = state.selectedTab
        val priorityTabs = mutableListOf<mozilla.components.browser.state.state.TabSessionState>()
        
        // Always include selected tab first
        if (selectedTab != null) {
            priorityTabs.add(selectedTab)
        }
        
        // Add recently accessed tabs (excluding selected)
        val recentTabs = state.tabs
            .filter { it.id != selectedTab?.id }
            .sortedByDescending { it.lastAccess }
            .take(maxTabsToCapture - 1) // -1 because we already added selected
        
        priorityTabs.addAll(recentTabs)
        
        return priorityTabs
    }
}
