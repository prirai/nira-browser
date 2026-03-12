package com.prirai.android.nira.search.awesomebar

import android.content.Context
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.feature.tabs.TabsUseCases
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import java.util.UUID

class NiraTabSuggestionProvider(
    private val context: Context,
    private val store: BrowserStore,
    private val selectTabUseCase: TabsUseCases.SelectTabUseCase,
    private val removeTabUseCase: TabsUseCases.RemoveTabUseCase,
    private val switchToTabDescription: String = "Switch to tab",
) : AwesomeBar.SuggestionProvider {

    override val id: String = UUID.randomUUID().toString()

    private val groupManager by lazy { UnifiedTabGroupManager.getInstance(context) }

    override suspend fun onInputChanged(text: String): List<AwesomeBar.Suggestion> {
        val state = store.state
        val selectedTabId = state.selectedTabId

        return state.tabs
            .filter { tab ->
                tab.id != selectedTabId && !tab.content.private &&
                (text.isBlank() ||
                 tab.content.title.contains(text, ignoreCase = true) ||
                 tab.content.url.contains(text, ignoreCase = true))
            }
            .mapIndexed { index, tab ->
                val group = groupManager.getGroupForTab(tab.id)
                val chips = buildList {
                    if (group != null) add(AwesomeBar.Suggestion.Chip(group.name))
                    add(AwesomeBar.Suggestion.Chip("✕"))
                }
                AwesomeBar.Suggestion(
                    provider = this,
                    id = tab.id,
                    title = tab.content.title.ifBlank { tab.content.url },
                    description = switchToTabDescription,
                    icon = null,
                    chips = chips,
                    score = Int.MAX_VALUE - index,
                    onSuggestionClicked = { selectTabUseCase.invoke(tab.id) },
                    onChipClicked = { chip ->
                        if (chip.title == "✕") {
                            removeTabUseCase.invoke(tab.id)
                        }
                    }
                )
            }
    }
}
