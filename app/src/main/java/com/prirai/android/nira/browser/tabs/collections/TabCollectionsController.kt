package com.prirai.android.nira.browser.tabs.collections

import android.content.Context
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.recover.RecoverableTab
import mozilla.components.feature.tab.collections.Tab
import mozilla.components.feature.tab.collections.TabCollection

/** Glue between the tab sheet and the AC tab-collections storage. */
class TabCollectionsController(private val context: Context) {
    private val components get() = context.applicationContext.components
    private val storage get() = components.tabCollectionStorage

    val collections get() = storage.getCollections()

    /** Tabs worth saving: real pages, not the new-tab placeholder. */
    fun savable(tabs: List<TabSessionState>) =
        tabs.filter { !it.content.private && !it.content.url.startsWith("about:") }

    suspend fun create(name: String, tabs: List<TabSessionState>) = withContext(Dispatchers.IO) {
        storage.createCollection(name, savable(tabs))
    }

    suspend fun addTo(collection: TabCollection, tabs: List<TabSessionState>) = withContext(Dispatchers.IO) {
        storage.addTabsToCollection(collection, savable(tabs))
    }

    suspend fun rename(collection: TabCollection, name: String) = withContext(Dispatchers.IO) {
        storage.renameCollection(collection, name)
    }

    suspend fun delete(collection: TabCollection) = withContext(Dispatchers.IO) {
        storage.removeCollection(collection)
    }

    suspend fun removeTab(collection: TabCollection, tab: Tab) = withContext(Dispatchers.IO) {
        storage.removeTabFromCollection(collection, tab)
    }

    /** Restores into [profileId] (not the profile the tabs were saved from) so they show up in the sheet. */
    suspend fun restore(tabs: List<Tab>, profileId: String) {
        val contextId = BrowserProfile.contextIdFor(profileId)
        val recovered = withContext(Dispatchers.IO) {
            tabs.mapNotNull { it.restore(context.filesDir, components.engine, restoreSessionId = false) }
        }.map { it.withContext(contextId) }
        if (recovered.isEmpty()) return
        components.store.dispatch(
            TabListAction.RestoreAction(
                recovered,
                selectedTabId = null,
                restoreLocation = TabListAction.RestoreAction.RestoreLocation.END,
            ),
        )
    }

    private fun RecoverableTab.withContext(contextId: String?) =
        copy(state = state.copy(contextId = contextId, private = false))
}
