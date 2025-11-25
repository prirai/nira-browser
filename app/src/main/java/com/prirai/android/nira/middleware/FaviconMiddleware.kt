package com.prirai.android.nira.middleware

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext
import com.prirai.android.nira.utils.FaviconCache

/**
 * Middleware that automatically saves favicons when they are loaded
 */
class FaviconMiddleware(
    private val faviconCache: FaviconCache,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : Middleware<BrowserState, BrowserAction> {

    override fun invoke(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        // Process the action first
        next(action)
        
        // Handle favicon updates
        when (action) {
            is ContentAction.UpdateIconAction -> {
                val tab = context.state.tabs.find { it.id == action.sessionId }
                if (tab != null && action.icon != null) {
                    // Save the favicon asynchronously
                    scope.launch {
                        faviconCache.saveFavicon(tab.content.url, action.icon)
                    }
                }
            }
            else -> {
                // No action needed for other actions
            }
        }
    }
}