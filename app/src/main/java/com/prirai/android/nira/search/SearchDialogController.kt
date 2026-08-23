package com.prirai.android.nira.search

import androidx.navigation.NavController
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.BrowserDirection
import com.prirai.android.nira.browser.SearchEngineList
import com.prirai.android.nira.preferences.UserPreferences
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases

/**
 * An interface that handles the view manipulation of the Search, triggered by the Interactor
 */
interface SearchController {
    fun handleUrlCommitted(url: String)
    fun handleEditingCancelled()
    fun handleTextChanged(text: String)
    fun handleUrlTapped(url: String)
    fun handleSearchTermsTapped(searchTerms: String)
    fun handleSearchShortcutEngineSelected(searchEngine: SearchEngine)
    fun handleClickSearchEngineSettings()
    fun handleExistingSessionSelected(tabId: String)
    fun handleSearchShortcutsButtonClicked()
}

class SearchDialogController(
    private val activity: BrowserActivity,
    private val store: BrowserStore,
    private val tabsUseCases: TabsUseCases,
    private val fragmentStore: SearchFragmentStore,
    private val navController: NavController,
    private val dismissDialog: () -> Unit,
    private val clearToolbarFocus: () -> Unit,
    private val focusToolbar: () -> Unit
) : SearchController {

    override fun handleUrlCommitted(url: String) {
        when (url) {
            "moz://a" -> openSearchOrUrl("https://mozilla.org")
            else ->
                if (url.isNotBlank()) {
                    openSearchOrUrl(url)
                }
        }
        dismissDialog()
    }

    private fun openSearchOrUrl(url: String) {
        clearToolbarFocus()

        val searchEngine = resolveSearchEngine()
        val shouldCreateNewTab = shouldCreateNewTabForSearch()

        activity.openToBrowserAndLoad(
            searchTermOrURL = url,
            newTab = shouldCreateNewTab,
            from = BrowserDirection.FromSearchDialog,
            engine = searchEngine
        )
    }

    private fun resolveSearchEngine(): SearchEngine? {
        fragmentStore.state.searchEngineSource.searchEngine?.let { return it }
        fragmentStore.state.defaultEngine?.let { return it }
        store.state.search.selectedOrDefaultSearchEngine?.let { return it }
        return try {
            SearchEngineList(activity).getSelectedEngine(UserPreferences(activity))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Creates a new tab ONLY when no tab is selected.
     * Otherwise, reuses the current tab (including homepage tabs).
     */
    private fun shouldCreateNewTabForSearch(): Boolean {
        val tabId = fragmentStore.state.tabId
        
        // Only create new tab if no tab is selected
        // Otherwise, always reuse the current tab (even if it's showing homepage)
        return tabId == null
    }

    override fun handleEditingCancelled() {
        clearToolbarFocus()
    }

    override fun handleTextChanged(text: String) {
        val textMatchesCurrentUrl = fragmentStore.state.url == text
        val textMatchesCurrentSearch = fragmentStore.state.searchTerms == text

        fragmentStore.dispatch(SearchFragmentAction.UpdateQuery(text))
        fragmentStore.dispatch(
            SearchFragmentAction.ShowSearchShortcutEnginePicker(
                (textMatchesCurrentUrl || textMatchesCurrentSearch || text.isEmpty())
            )
        )
        fragmentStore.dispatch(
            SearchFragmentAction.AllowSearchSuggestionsInPrivateModePrompt(
                text.isNotEmpty() &&
                        activity.browsingModeManager.mode.isPrivate
            )
        )
    }

    override fun handleUrlTapped(url: String) {
        clearToolbarFocus()
        
        val shouldCreateNewTab = shouldCreateNewTabForSearch()

        activity.openToBrowserAndLoad(
            searchTermOrURL = url,
            newTab = shouldCreateNewTab,
            from = BrowserDirection.FromSearchDialog
        )
    }

    override fun handleSearchTermsTapped(searchTerms: String) {
        clearToolbarFocus()

        val searchEngine = resolveSearchEngine()
        val shouldCreateNewTab = shouldCreateNewTabForSearch()

        activity.openToBrowserAndLoad(
            searchTermOrURL = searchTerms,
            newTab = shouldCreateNewTab,
            from = BrowserDirection.FromSearchDialog,
            engine = searchEngine,
            forceSearch = true
        )
    }

    override fun handleSearchShortcutEngineSelected(searchEngine: SearchEngine) {
        focusToolbar()
        fragmentStore.dispatch(SearchFragmentAction.SearchShortcutEngineSelected(searchEngine))
    }

    override fun handleSearchShortcutsButtonClicked() {
        val isOpen = fragmentStore.state.showSearchShortcuts
        fragmentStore.dispatch(SearchFragmentAction.ShowSearchShortcutEnginePicker(!isOpen))
    }

    override fun handleClickSearchEngineSettings() {
        clearToolbarFocus()
    }

    override fun handleExistingSessionSelected(tabId: String) {
        clearToolbarFocus()

        tabsUseCases.selectTab(tabId)

        activity.openToBrowser(
            from = BrowserDirection.FromSearchDialog
        )
    }
}
