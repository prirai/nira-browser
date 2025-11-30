package com.prirai.android.nira.browser.profile

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext

/**
 * Middleware that tracks profile IDs for tabs
 * Adds profile metadata to tabs when they are created
 */
class ProfileMiddleware(
    private val profileManager: ProfileManager
) : Middleware<BrowserState, BrowserAction> {
    
    override fun invoke(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        when (action) {
            is TabListAction.AddTabAction -> {
                // Store profile ID in tab's contextId field
                // This is a Gecko-native field for cookie isolation
                val currentProfile = profileManager.getActiveProfile()
                val isPrivate = profileManager.isPrivateMode()
                
                // Use contextId for profile isolation (Gecko native)
                // Private tabs always use contextId = "private"
                val contextId = if (isPrivate) {
                    "private"
                } else {
                    "profile_${currentProfile.id}"
                }
                
                android.util.Log.d("ProfileMiddleware", "Adding tab with contextId=$contextId, profile=${currentProfile.name}, isPrivate=$isPrivate")
                
                // Update the tab with contextId before adding
                val updatedTab = action.tab.copy(
                    contextId = contextId
                )
                
                next(TabListAction.AddTabAction(updatedTab, action.select))
                return
            }
            else -> {
                // Pass through all other actions
                next(action)
            }
        }
    }
}
