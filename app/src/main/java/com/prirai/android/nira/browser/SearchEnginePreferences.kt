package com.prirai.android.nira.browser

import android.content.Context
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import mozilla.components.browser.icons.IconRequest
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.feature.search.ext.createSearchEngine

object SearchEnginePreferences {

    suspend fun apply(context: Context, private: Boolean = false) {
        val prefs = UserPreferences(context)
        val components = context.components
        val catalog = SearchEngineList(context)
        val fallback = if (private && prefs.privateSearchEngineChoice >= 0) {
            catalog.getEngines()[prefs.privateSearchEngineChoice.coerceIn(catalog.getEngines().indices)]
        } else {
            catalog.getSelectedEngine(prefs)
        }

        val selected = if (!private && prefs.customSearchEngine && prefs.customSearchEngineURL.isNotBlank()) {
            val custom = createSearchEngine(
                name = "Custom Search",
                url = SearchEngineList.normalizeCustomSearchUrl(prefs.customSearchEngineURL),
                icon = components.icons.loadIcon(IconRequest(prefs.customSearchEngineURL)).await().bitmap,
            )
            val existing = components.store.state.search.searchEngines.find {
                it.id == custom.id || it.resultUrls == custom.resultUrls
            }
            if (existing == null) {
                components.searchUseCases.addSearchEngine(custom)
                custom
            } else {
                existing
            }
        } else {
            resolveFromStore(context, fallback) ?: fallback.also { engine ->
                if (engine.type != SearchEngine.Type.BUNDLED ||
                    components.store.state.search.searchEngines.none { it.id == engine.id }
                ) {
                    components.searchUseCases.addSearchEngine(engine)
                }
            }
        }

        if (private) {
            components.searchUseCases.selectPrivateSearchEngine(selected)
        } else {
            components.searchUseCases.selectSearchEngine(selected)
        }
    }

    private fun resolveFromStore(context: Context, fallback: SearchEngine): SearchEngine? {
        val engines = context.components.store.state.search.searchEngines
        return engines.find { it.id == fallback.id }
            ?: engines.find { it.name.equals(fallback.name, ignoreCase = true) }
    }
}
