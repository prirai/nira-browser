package com.prirai.android.nira.browser.tabs

import android.content.Context
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.state.selector.selectedTab

/**
 * Chrome-parity new tab creation.
 *
 * In Chrome, the new tab page (NTP) is the tab's FIRST engine history
 * entry: navigating from it appends to the same tab's history, so the
 * long-press back popup lists "New Tab" and back gestures walk down to it
 * as real engine navigations (the tab pill then shows the New Tab state,
 * because content.url actually changes).
 *
 * New tabs created directly at a search result / URL miss that entry -
 * the tab was never "anywhere" before - which made the back popup skip
 * the new tab page. This helper creates the tab at about:homepage FIRST
 * - making it the history root - selects it, and then runs [load]
 * against the new tab.
 */
object NewTabCreator {

    private const val WAIT_STEP_MS = 50L
    private const val WAIT_STEPS = 20 // 1s total

    /**
     * Create a new tab at the new tab page (about:homepage) and load
     * [load] in it once the tab is selected.
     *
     * @param isPrivate whether the tab is private; decides the profile
     * partition (contextId) the same way the previous inline logic in
     * BrowserActivity did.
     */
    fun createHomeTabAndLoad(context: Context, isPrivate: Boolean, load: () -> Unit) {
        val components = context.components

        val previousSelectedTabId = components.store.state.selectedTabId
        components.tabsUseCases.addTab.invoke(
            url = "about:homepage",
            private = isPrivate,
            contextId = contextIdForNewTab(context, isPrivate),
            selectTab = true,
        )

        // AC reduces dispatches into store state synchronously, so the new
        // tab is usually selected already. The defensive wait covers the
        // rare case where the state has not landed yet - the load must
        // never hit the previously selected tab.
        val selectedNow = components.store.state.selectedTabId
        if (selectedNow != null && selectedNow != previousSelectedTabId) {
            load()
            return
        }

        MainScope().launch {
            var steps = 0
            while (components.store.state.selectedTabId == previousSelectedTabId && steps < WAIT_STEPS) {
                delay(WAIT_STEP_MS)
                steps++
            }
            load()
        }
    }

    /**
     * Mirrors the contextId resolution the search flow used inline in
     * BrowserActivity.getContextIdForNewTab: private -> "private",
     * otherwise the selected tab's profile partition when it has one, or
     * the active profile's partition. The default profile returns `null`
     * so its tabs share Gecko's default partition with custom tabs.
     */
    private fun contextIdForNewTab(context: Context, isPrivate: Boolean): String? {
        if (isPrivate) return "private"

        val tabContextId = context.components.store.state.selectedTab?.contextId
        if (tabContextId != null && tabContextId.startsWith("profile_") &&
            tabContextId != "profile_default"
        ) {
            return tabContextId
        }

        val profileManager = ProfileManager.getInstance(context)
        return BrowserProfile.contextIdFor(
            profile = profileManager.getActiveProfile(),
            isPrivate = false,
        )
    }

    /**
     * Lands [tabId] on its new tab page.
     *
     * `about:homepage` is a custom about-URL that GeckoView cannot actually
     * load, so the new tab page can never be an entry in the engine's
     * session history - it is a state transition: the BrowserFragment
     * homepage overlay takes over (driven by the content url observer) and
     * the tab pill shows "New Tab" (empty title + about:homepage), exactly
     * like a freshly created tab.
     *
     * Used when back navigation walks past the oldest history entry and
     * when the tab-history popup's synthetic "New Tab" row is tapped.
     */
    fun setTabToNewTabPage(store: BrowserStore, tabId: String) {
        store.dispatch(ContentAction.UpdateUrlAction(tabId, "about:homepage"))
        store.dispatch(ContentAction.UpdateTitleAction(tabId, ""))
    }
}
