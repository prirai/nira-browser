package com.prirai.android.nira.middleware

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.AppLifecycleAction
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext

/**
 * Enhanced middleware that triggers form data checks for priority tabs
 * when the app goes to background. This ensures state is captured without
 * suspending sessions (which would cause page reloads).
 * 
 * Unlike the previous implementation, this does NOT dispatch SuspendEngineSessionAction
 * which was causing autofill-triggered page reloads.
 * 
 * Strategy:
 * - Call checkForFormData() on selected tab when app pauses
 * - This triggers state capture through EngineMiddleware
 * - No session suspension = no page reload
 * 
 * References:
 * - Firefox SessionPrioritizationMiddleware (uses checkForFormData, not suspend)
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
        // Check for form data when app goes to background
        when (action) {
            is AppLifecycleAction.PauseAction -> {
                // Use checkForFormData instead of suspend to avoid page reload
                scope.launch {
                    context.state.selectedTab?.engineState?.engineSession?.checkForFormData(
                        adjustPriority = false
                    )
                }
            }
            else -> {
                // No action needed
            }
        }
        
        // Process action
        next(action)
    }
}
