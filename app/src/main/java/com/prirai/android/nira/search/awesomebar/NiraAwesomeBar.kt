package com.prirai.android.nira.search.awesomebar

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.concept.awesomebar.AwesomeBar.GroupedSuggestion
import mozilla.components.concept.awesomebar.AwesomeBar.Suggestion
import mozilla.components.concept.awesomebar.AwesomeBar.SuggestionProvider
import mozilla.components.concept.awesomebar.AwesomeBar.SuggestionProviderGroup

private val GroupCornerRadius = 24.dp
private val CardHorizontalPadding = 8.dp

private data class SuggestionSection(
    val group: SuggestionProviderGroup,
    val suggestions: List<Suggestion>,
)

@Composable
fun NiraAwesomeBar(
    text: String,
    providers: List<SuggestionProvider>,
    hiddenSuggestions: Set<GroupedSuggestion>,
    onSuggestionClicked: (Suggestion) -> Unit,
    onRemoveClicked: (GroupedSuggestion) -> Unit,
    onScroll: () -> Unit,
) {
    var sections by remember { mutableStateOf(emptyList<SuggestionSection>()) }

    LaunchedEffect(text, providers) {
        if (providers.isEmpty() || text.isBlank()) {
            sections = emptyList()
            return@LaunchedEffect
        }
        sections = fetchSections(text, providers)
    }

    val visibleSections = remember(sections, hiddenSuggestions) {
        sections.mapNotNull { section ->
            val visible = section.suggestions.filterNot { suggestion ->
                GroupedSuggestion(suggestion, section.group.id) in hiddenSuggestions
            }
            if (visible.isEmpty()) null else section.copy(suggestions = visible)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        userScrollEnabled = true
    ) {
        visibleSections.forEach { section ->
            if (!section.group.title.isNullOrBlank()) {
                item(key = "header-${section.group.id}") {
                    Text(
                        text = section.group.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }
            }
            itemsIndexed(
                items = section.suggestions,
                key = { _, suggestion -> "${section.group.id}-${suggestion.provider.id}-${suggestion.id}" }
            ) { index, suggestion ->
                SuggestionCardRow(
                    suggestion = suggestion,
                    isFirst = index == 0,
                    isLast = index == section.suggestions.lastIndex,
                    onClick = { onSuggestionClicked(suggestion) },
                    onRemove = {
                        onRemoveClicked(GroupedSuggestion(suggestion, section.group.id))
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestionCardRow(
    suggestion: Suggestion,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (isFirst) GroupCornerRadius else 0.dp,
        topEnd = if (isFirst) GroupCornerRadius else 0.dp,
        bottomStart = if (isLast) GroupCornerRadius else 0.dp,
        bottomEnd = if (isLast) GroupCornerRadius else 0.dp,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalPadding)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = suggestion.icon
                if (icon != null) {
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Text(
                    text = suggestion.title?.ifBlank { null } ?: suggestion.description.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp)
                )

                if (suggestion.isRemovalAllowed) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_small),
                            contentDescription = stringResource(R.string.remove_history_item),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!isLast) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 48.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

private suspend fun fetchSections(
    text: String,
    providers: List<SuggestionProvider>,
): List<SuggestionSection> = coroutineScope {
    val groups = providers
        .groupBy { it.groupTitle() ?: it.id }
        .map { (title, groupedProviders) ->
            SuggestionProviderGroup(
                providers = groupedProviders,
                title = groupedProviders.first().groupTitle() ?: title,
                priority = groupedProviders.maxOf { providerPriority(it) },
            )
        }
        .sortedByDescending { it.priority }

    groups.map { group ->
        async {
            val suggestions = group.providers
                .map { provider ->
                    async {
                        provider.onInputChanged(text).filterIsInstance<Suggestion>()
                    }
                }
                .awaitAll()
                .flatten()
                .sortedByDescending { it.score }
            SuggestionSection(group, suggestions)
        }
    }.awaitAll().filter { it.suggestions.isNotEmpty() }
}

internal class HeaderedSuggestionProvider(
    private val delegate: SuggestionProvider,
    private val header: String,
    val priority: Int,
) : SuggestionProvider by delegate {
    override fun groupTitle(): String = header
}

internal fun providerPriority(provider: SuggestionProvider): Int {
    return when (provider) {
        is HeaderedSuggestionProvider -> provider.priority
        is SearchForQueryProvider -> 50
        is mozilla.components.feature.awesomebar.provider.SearchSuggestionProvider -> 40
        is NiraTabSuggestionProvider -> 30
        is NiraHistorySuggestionProvider -> 20
        is mozilla.components.feature.awesomebar.provider.BookmarksStorageSuggestionProvider -> 10
        else -> 0
    }
}
