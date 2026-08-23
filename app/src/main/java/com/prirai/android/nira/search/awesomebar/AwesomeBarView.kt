package com.prirai.android.nira.search.awesomebar

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat.SRC_IN
import androidx.core.graphics.drawable.toBitmap
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.browser.SearchEngineList
import com.prirai.android.nira.browser.bookmark.CustomBookmarksStorage
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.search.SearchEngineSource
import com.prirai.android.nira.search.SearchFragmentState
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.awesomebar.provider.BookmarksStorageSuggestionProvider
import mozilla.components.feature.awesomebar.provider.SearchSuggestionProvider
import mozilla.components.feature.search.SearchUseCases
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.ktx.android.content.getColorFromAttr

/**
 * View that contains and configures the BrowserAwesomeBar
 * TODO: suggestions based on bookmarks
 */
class AwesomeBarView(
    private val activity: BrowserActivity,
    val interactor: AwesomeBarInteractor,
    val view: AwesomeBarWrapper,
) {
    private val sessionProvider: NiraTabSuggestionProvider
    private val historyStorageProvider: NiraHistorySuggestionProvider
    private val bookmarksStorageSuggestionProvider: BookmarksStorageSuggestionProvider
    private val shortcutsEnginePickerProvider: ShortcutsSuggestionProvider
    private val defaultSearchSuggestionProvider: SearchSuggestionProvider
    private val defaultSearchActionProvider: SearchForQueryProvider
    private val searchSuggestionProviderMap: MutableMap<SearchEngine, List<AwesomeBar.SuggestionProvider>>
    private var providersInUse = mutableSetOf<AwesomeBar.SuggestionProvider>()

    private val loadUrlUseCase = object : SessionUseCases.LoadUrlUseCase {
        override fun invoke(
            url: String,
            flags: EngineSession.LoadUrlFlags,
            additionalHeaders: Map<String, String>?,
            originalInput: String?
        ) {
            interactor.onUrlTapped(url)
        }
    }

    private val searchUseCase = object : SearchUseCases.SearchUseCase {
        override fun invoke(
            searchTerms: String,
            searchEngine: SearchEngine?,
            parentSessionId: String?
        ) {
            interactor.onSearchTermsTapped(searchTerms)
        }
    }

    private val shortcutSearchUseCase = object : SearchUseCases.SearchUseCase {
        override fun invoke(
            searchTerms: String,
            searchEngine: SearchEngine?,
            parentSessionId: String?
        ) {
            interactor.onSearchTermsTapped(searchTerms)
        }
    }

    private val selectTabUseCase = object : TabsUseCases.SelectTabUseCase {
        override fun invoke(tabId: String) {
            interactor.onExistingSessionSelected(tabId)
        }
    }

    init {
        val components = activity.components
        val primaryTextColor = activity.getColorFromAttr(android.R.attr.textColorPrimary)

        val engineForSpeculativeConnects = when (activity.browsingModeManager.mode) {
            BrowsingMode.Normal -> components.engine
            BrowsingMode.Private -> null
        }
        sessionProvider =
            NiraTabSuggestionProvider(
                context = activity,
                store = components.store,
                selectTabUseCase = selectTabUseCase,
                removeTabUseCase = components.tabsUseCases.removeTab,
                switchToTabDescription = activity.resources.getString(R.string.switch_to_tab),
                suggestionsHeader = activity.getString(R.string.tabs)
            )

        historyStorageProvider =
            NiraHistorySuggestionProvider(
                historyStorage = components.historyStorage,
                loadUrlUseCase = loadUrlUseCase,
                icons = components.icons,
                engine = engineForSpeculativeConnects,
                suggestionsHeader = activity.getString(R.string.action_history)
            )

        bookmarksStorageSuggestionProvider =
            BookmarksStorageSuggestionProvider(
                bookmarksStorage = CustomBookmarksStorage(activity),
                loadUrlUseCase = loadUrlUseCase,
                icons = components.icons,
                engine = engineForSpeculativeConnects,
                showEditSuggestion = false,
                suggestionsHeader = activity.getString(R.string.action_bookmarks)
            )

        val searchBitmap = getDrawable(activity, mozilla.components.ui.icons.R.drawable.mozac_ic_search_24)!!.apply {
            colorFilter = createBlendModeColorFilterCompat(primaryTextColor, SRC_IN)
        }.toBitmap()

        val selectedEngine = components.store.state.search.selectedOrDefaultSearchEngine(
            activity.browsingModeManager.mode.isPrivate
        ) ?: SearchEngineList(activity).getSelectedEngine(UserPreferences(activity))
        val suggestionLimit = UserPreferences(activity).searchSuggestionCount.coerceIn(1, 10)

        defaultSearchSuggestionProvider =
            SearchSuggestionProvider(
                searchEngine = selectedEngine,
                searchUseCase = searchUseCase,
                fetchClient = components.client,
                limit = suggestionLimit,
                mode = SearchSuggestionProvider.Mode.MULTIPLE_SUGGESTIONS,
                icon = searchBitmap,
                showDescription = false,
                engine = engineForSpeculativeConnects,
                filterExactMatch = true,
                private = when (activity.browsingModeManager.mode) {
                    BrowsingMode.Normal -> false
                    BrowsingMode.Private -> true
                }
            )

        defaultSearchActionProvider =
            SearchForQueryProvider(
                searchUseCase = searchUseCase,
                icon = searchBitmap,
                titleFor = { query -> activity.getString(R.string.search_for_query, query) }
            )

        shortcutsEnginePickerProvider =
            ShortcutsSuggestionProvider(
                store = components.store,
                context = activity,
                selectShortcutEngine = interactor::onSearchShortcutEngineSelected
            )

        searchSuggestionProviderMap = HashMap()
    }

    fun update(context: Context, state: SearchFragmentState) {
        updateSuggestionProvidersVisibility(context, state)

        if (state.query.isNotEmpty() && state.query == state.url && !state.showSearchShortcuts) {
            return
        }

        view.onInputChanged(state.query)
    }

    private fun updateSuggestionProvidersVisibility(context: Context, state: SearchFragmentState) {
        if (state.showSearchShortcuts) {
            handleDisplayShortcutsProviders()
            return
        }

        val providersToAdd = getProvidersToAdd(context, state)
        val providersToRemove = getProvidersToRemove(context, state)

        performProviderListChanges(providersToAdd, providersToRemove)
    }

    private fun performProviderListChanges(
        providersToAdd: MutableSet<AwesomeBar.SuggestionProvider>,
        providersToRemove: MutableSet<AwesomeBar.SuggestionProvider>
    ) {
        for (provider in providersToAdd) {
            if (providersInUse.none { it.id == provider.id }) {
                providersInUse.add(provider)
                view.addProviders(provider)
            }
        }

        for (provider in providersToRemove) {
            val existing = providersInUse.filter { it.id == provider.id }
            if (existing.isNotEmpty()) {
                providersInUse.removeAll(existing.toSet())
                view.removeProviders(*existing.toTypedArray())
            }
        }
    }

    private fun getProvidersToAdd(context: Context, state: SearchFragmentState): MutableSet<AwesomeBar.SuggestionProvider> {
        val providersToAdd = mutableSetOf<AwesomeBar.SuggestionProvider>()

        if (state.showHistorySuggestions) {
            providersToAdd.add(historyStorageProvider)
        }

        if (state.showBookmarkSuggestions) {
            providersToAdd.add(bookmarksStorageSuggestionProvider)
        }

        if (state.showSearchSuggestions) {
            getSelectedSearchSuggestionProvider(context, state).forEach { provider ->
                providersToAdd.add(provider)
            }
        }

        if (!activity.browsingModeManager.mode.isPrivate) {
            providersToAdd.add(sessionProvider)
        }

        return providersToAdd
    }

    private fun getProvidersToRemove(context: Context, state: SearchFragmentState): MutableSet<AwesomeBar.SuggestionProvider> {
        val providersToRemove = mutableSetOf<AwesomeBar.SuggestionProvider>()

        providersToRemove.add(shortcutsEnginePickerProvider)

        if (!state.showHistorySuggestions) {
            providersToRemove.add(historyStorageProvider)
        }

        if (!state.showSearchSuggestions) {
            providersToRemove.addAll(getSelectedSearchSuggestionProvider(context, state))
        }

        if (activity.browsingModeManager.mode.isPrivate) {
            providersToRemove.add(sessionProvider)
        }

        return providersToRemove
    }

    private fun getSelectedSearchSuggestionProvider(context: Context, state: SearchFragmentState): List<AwesomeBar.SuggestionProvider> {
        //TODO: Clean this up when switching to search suggestion provider option
        return when (state.searchEngineSource) {
            is SearchEngineSource.Default -> {
                if (UserPreferences(context).searchSuggestionsEnabled) {
                    listOf(
                        HeaderedSuggestionProvider(
                            defaultSearchActionProvider,
                            header = "",
                            priority = 50
                        ),
                        HeaderedSuggestionProvider(
                            defaultSearchSuggestionProvider,
                            header = context.getString(R.string.search_suggestions),
                            priority = 40
                        )
                    )
                } else {
                    listOf(
                        HeaderedSuggestionProvider(
                            defaultSearchActionProvider,
                            header = "",
                            priority = 50
                        )
                    )
                }
            }
            is SearchEngineSource.Shortcut -> getSuggestionProviderForEngine(
                state.searchEngineSource.searchEngine
            )
            is SearchEngineSource.None -> emptyList()
        }
    }

    private fun handleDisplayShortcutsProviders() {
        view.removeAllProviders()
        providersInUse.clear()
        providersInUse.add(shortcutsEnginePickerProvider)
        view.addProviders(shortcutsEnginePickerProvider)
    }

    private fun getSuggestionProviderForEngine(engine: SearchEngine): List<AwesomeBar.SuggestionProvider> {
        return searchSuggestionProviderMap.getOrPut(engine) {
            val components = activity.components
            val primaryTextColor = activity.getColorFromAttr(android.R.attr.textColorPrimary)

            val searchBitmap = getDrawable(activity, mozilla.components.ui.icons.R.drawable.mozac_ic_search_24)!!.apply {
                colorFilter = createBlendModeColorFilterCompat(primaryTextColor, SRC_IN)
            }.toBitmap()

            val engineForSpeculativeConnects = when (activity.browsingModeManager.mode) {
                BrowsingMode.Normal -> components.engine
                BrowsingMode.Private -> null
            }

            listOf(
                HeaderedSuggestionProvider(
                    SearchForQueryProvider(
                        searchUseCase = shortcutSearchUseCase,
                        icon = searchBitmap,
                        titleFor = { query -> activity.getString(R.string.search_for_query, query) }
                    ),
                    header = "",
                    priority = 50
                ),
                HeaderedSuggestionProvider(
                    SearchSuggestionProvider(
                        searchEngine = engine,
                        searchUseCase = shortcutSearchUseCase,
                        fetchClient = components.client,
                        limit = UserPreferences(activity).searchSuggestionCount.coerceIn(1, 10),
                        mode = SearchSuggestionProvider.Mode.MULTIPLE_SUGGESTIONS,
                        icon = searchBitmap,
                        engine = engineForSpeculativeConnects,
                        filterExactMatch = true,
                        private = when (activity.browsingModeManager.mode) {
                            BrowsingMode.Normal -> false
                            BrowsingMode.Private -> true
                        }
                    ),
                    header = activity.getString(R.string.search_suggestions),
                    priority = 40
                )
            )
        }
    }
}
