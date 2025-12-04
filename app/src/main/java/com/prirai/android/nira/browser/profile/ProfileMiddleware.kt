package com.prirai.android.nira.browser.profile

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext

/**
 * Middleware that tracks profile IDs for tabs
 * Adds profile metadata to tabs when they are created
 * Respects explicitly null contextId for guest tabs (from custom tab migration)
 */
class ProfileMiddleware(
    private val profileManager: ProfileManager
) : Middleware<BrowserState, BrowserAction> {
    
    private val guestTabIds = mutableSetOf<String>()
    
    fun markAsGuestTab(tabId: String) {
        guestTabIds.add(tabId)
    }
    
    override fun invoke(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        when (action) {
            is TabListAction.AddTabAction -> {
                if (guestTabIds.contains(action.tab.id)) {
                    // Guest tab - keep contextId as null
                    guestTabIds.remove(action.tab.id)
                    next(action)
                    return
                }
                
                // If tab already has a contextId, respect it (explicitly set by caller)
                if (action.tab.contextId != null) {
                    next(action)
                    return
                }
                
                // Tab has no contextId - assign based on private flag or profile manager
                val isPrivate = action.tab.content.private || profileManager.isPrivateMode()
                val currentProfile = profileManager.getActiveProfile()
                
                val contextId = if (isPrivate) {
                    "private"
                } else {
                    "profile_${currentProfile.id}"
                }
                
                val updatedTab = action.tab.copy(contextId = contextId)
                next(TabListAction.AddTabAction(updatedTab, action.select))
                return
            }
            else -> next(action)
        }
    }
}
