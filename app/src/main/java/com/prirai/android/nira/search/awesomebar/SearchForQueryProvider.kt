package com.prirai.android.nira.search.awesomebar

import android.graphics.Bitmap
import java.util.UUID
import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.feature.search.SearchUseCases

class SearchForQueryProvider(
    private val searchUseCase: SearchUseCases.SearchUseCase,
    private val icon: Bitmap?,
    private val titleFor: (String) -> String,
    private val suggestionsHeader: String? = null,
) : AwesomeBar.SuggestionProvider {

    override val id: String = UUID.randomUUID().toString()

    override fun groupTitle(): String? = suggestionsHeader

    override suspend fun onInputChanged(text: String): List<AwesomeBar.Suggestion> {
        if (text.isBlank()) {
            return emptyList()
        }

        return listOf(
            AwesomeBar.Suggestion(
                provider = this,
                id = FIXED_ID,
                title = titleFor(text),
                icon = icon,
                score = Int.MAX_VALUE,
                onSuggestionClicked = { searchUseCase.invoke(text) },
            )
        )
    }

    companion object {
        private const val FIXED_ID = "nira.search.for.query"
    }
}
