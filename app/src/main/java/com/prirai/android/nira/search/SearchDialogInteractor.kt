package com.prirai.android.nira.search

import com.prirai.android.nira.search.awesomebar.AwesomeBarInteractor
import com.prirai.android.nira.search.toolbar.ToolbarInteractor
import mozilla.components.browser.state.search.SearchEngine

/**
 * Interactor for the search screen
 * Provides implementations for the AwesomeBarView and ToolbarView
 */
class SearchDialogInteractor(
    private val searchController: SearchDialogController
) : AwesomeBarInteractor, ToolbarInteractor {

    override fun onUrlCommitted(url: String) {
        searchController.handleUrlCommitted(url)
    }

    override fun onEditingCanceled() {
        searchController.handleEditingCancelled()
    }

    override fun onTextChanged(text: String) {
        searchController.handleTextChanged(text)
    }

    override fun onUrlTapped(url: String) {
        searchController.handleUrlTapped(url)
    }

    override fun onSearchTermsTapped(searchTerms: String) {
        searchController.handleSearchTermsTapped(searchTerms)
    }

    override fun onSearchShortcutEngineSelected(searchEngine: SearchEngine) {
        searchController.handleSearchShortcutEngineSelected(searchEngine)
    }

    override fun onSearchShortcutsButtonClicked() {
        searchController.handleSearchShortcutsButtonClicked()
    }

    override fun onClickSearchEngineSettings() {
        searchController.handleClickSearchEngineSettings()
    }

    override fun onExistingSessionSelected(tabId: String) {
        searchController.handleExistingSessionSelected(tabId)
    }
}
