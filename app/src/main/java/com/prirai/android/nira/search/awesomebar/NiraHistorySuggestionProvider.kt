package com.prirai.android.nira.search.awesomebar

import java.util.UUID
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.IconRequest
import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.feature.session.SessionUseCases

class NiraHistorySuggestionProvider(
    private val historyStorage: HistoryStorage,
    private val loadUrlUseCase: SessionUseCases.LoadUrlUseCase,
    private val icons: BrowserIcons? = null,
    private val engine: Engine? = null,
    private val maxNumberOfSuggestions: Int = 20,
    private val suggestionsHeader: String? = null,
) : AwesomeBar.SuggestionProvider {

    override val id: String = UUID.randomUUID().toString()

    override fun groupTitle(): String? = suggestionsHeader

    override suspend fun onInputChanged(text: String): List<AwesomeBar.Suggestion> {
        if (text.isEmpty()) {
            return emptyList()
        }

        historyStorage.cancelReads(text)
        val results = historyStorage
            .getSuggestions(text, maxNumberOfSuggestions)
            .sortedByDescending { it.score }
            .distinctBy { it.id }
            .take(maxNumberOfSuggestions)

        results.firstOrNull()?.url?.let { engine?.speculativeConnect(it) }

        val iconRequests = results.map { icons?.loadIcon(IconRequest(url = it.url, waitOnNetworkLoad = false)) }
        return results.zip(iconRequests) { result, icon ->
            AwesomeBar.Suggestion(
                provider = this,
                id = result.id,
                icon = icon?.await()?.bitmap,
                title = result.title?.ifBlank { null } ?: result.url,
                description = result.url,
                editSuggestion = null,
                isRemovalAllowed = true,
                score = result.score,
                flags = setOf(AwesomeBar.Suggestion.Flag.HISTORY),
                onSuggestionClicked = { loadUrlUseCase(result.url) },
            )
        }
    }
}
