@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.BrowserDirection
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabs.compose.FaviconImageFromUrl
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.components.NiraSearchField
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.components.animateNiraListItem
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.browser.state.state.TabSessionState

/**
 * The unified search sheet, opened from the tab sheet's Search button: one query
 * searched across tabs, bookmarks and history, with the results grouped into
 * cards - tabs under their profile chip, bookmarks under their folder path chip,
 * history under its time-group chip.
 *
 * This is NOT the address-bar search dialog (`search/SearchDialogFragment`) - the
 * two surfaces are deliberately separate (see the LLM agent guide).
 */
class TabSearchFragment : BottomSheetDialogFragment() {

    private val queryState = mutableStateOf("")
    private val resultsState = mutableStateOf<List<SearchResultItem>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val context = LocalContext.current
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors,
            ) {
                val query = queryState.value
                // Search fires per keystroke like the View implementation did; a new
                // query cancels the previous round, so the slow grouped history query
                // of a stale prefix never lands over a newer result set.
                LaunchedEffect(query) { performSearch(query) }
                TabSearchSheetContent(
                    query = query,
                    results = resultsState.value,
                    onQueryChange = { queryState.value = it },
                    onItemClicked = { onItemClicked(it) },
                    onTabCloseClicked = { onTabCloseClicked(it) },
                    onDismiss = { dismiss() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Match the TabsBottomSheetFragment height behavior: a tall, expanded sheet.
        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        val behavior = bottomSheetDialog.behavior

        val screenHeight = resources.displayMetrics.heightPixels
        val desiredHeight = (screenHeight * SHEET_HEIGHT_FRACTION).toInt()

        behavior.isFitToContents = false
        behavior.peekHeight = desiredHeight
        behavior.expandedOffset = screenHeight - desiredHeight
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isHideable = true
        behavior.isDraggable = true

        bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
            val layoutParams = bottomSheet.layoutParams
            layoutParams.height = desiredHeight
            bottomSheet.layoutParams = layoutParams
        }

        bottomSheetDialog.setCancelable(true)
        bottomSheetDialog.setCanceledOnTouchOutside(true)
    }

    private suspend fun performSearch(query: String) {
        if (query.isEmpty()) {
            resultsState.value = emptyList()
            return
        }

        val results = mutableListOf<SearchResultItem>()

        val tabResults = searchTabs(query)
        if (tabResults.isNotEmpty()) {
            results.add(SearchResultItem.HeaderResult(getString(R.string.tabs)))
            tabResults.groupBy { it.profileName }.forEach { (profileName, items) ->
                results.add(SearchResultItem.SubHeaderResult(profileName))
                results.addAll(items)
            }
        }

        val bookmarkResults = searchBookmarks(query)
        if (bookmarkResults.isNotEmpty()) {
            results.add(SearchResultItem.HeaderResult(getString(R.string.action_bookmarks)))
            bookmarkResults.groupBy { it.folderPath }.forEach { (folderPath, items) ->
                results.add(SearchResultItem.SubHeaderResult(folderPath))
                results.addAll(items)
            }
        }

        val historyResults = searchHistory(query)
        if (historyResults.isNotEmpty()) {
            results.add(SearchResultItem.HeaderResult(getString(R.string.action_history)))
            historyResults.groupBy { it.timeGroup }.forEach { (timeGroup, items) ->
                results.add(SearchResultItem.SubHeaderResult(timeGroup))
                results.addAll(items)
            }
        }

        resultsState.value = results
    }

    private fun searchTabs(query: String): List<SearchResultItem.TabResult> {
        val store = requireContext().components.store.state
        val allProfiles = ProfileManager.getInstance(requireContext()).getAllProfiles()
        val allGroups = UnifiedTabGroupManager.getInstance(requireContext()).getAllGroups()

        return store.tabs
            .filter { tab ->
                !tab.content.private &&
                    (
                        tab.content.title.contains(query, ignoreCase = true) ||
                            tab.content.url.contains(query, ignoreCase = true)
                        )
            }
            .map { tab ->
                val profileId = tab.contextId?.removePrefix("profile_") ?: ""
                val profile = allProfiles.find { it.id == profileId }
                val group = allGroups.find { it.tabIds.contains(tab.id) }
                SearchResultItem.TabResult(
                    tab = tab,
                    profileName = profile?.let { it.name }
                        ?: getString(R.string.tabsearch_unknown_profile),
                    groupName = group?.name,
                )
            }
            .take(MAX_RESULTS_PER_SECTION)
    }

    private fun searchBookmarks(query: String): List<SearchResultItem.BookmarkResult> {
        val results = mutableListOf<SearchResultItem.BookmarkResult>()
        try {
            searchBookmarksRecursive(
                folder = BookmarkManager.getInstance(requireContext()).root,
                query = query,
                results = results,
                path = getString(R.string.action_bookmarks),
            )
        } catch (e: Exception) {
            // A malformed bookmark tree yields no results rather than failing the search
        }
        return results.take(MAX_RESULTS_PER_SECTION)
    }

    private fun searchBookmarksRecursive(
        folder: BookmarkFolderItem,
        query: String,
        results: MutableList<SearchResultItem.BookmarkResult>,
        path: String,
    ) {
        folder.list.forEach { item ->
            when (item) {
                is BookmarkSiteItem -> {
                    val title = item.title ?: ""
                    if (title.contains(query, ignoreCase = true) ||
                        item.url.contains(query, ignoreCase = true)
                    ) {
                        results.add(
                            SearchResultItem.BookmarkResult(
                                title = title,
                                url = item.url,
                                folderPath = path,
                            ),
                        )
                    }
                }
                is BookmarkFolderItem -> {
                    val newPath = if (item.title?.isNotEmpty() == true) {
                        "$path > ${item.title}"
                    } else {
                        path
                    }
                    searchBookmarksRecursive(item, query, results, newPath)
                }
            }
        }
    }

    private suspend fun searchHistory(query: String): List<SearchResultItem.HistoryResult> {
        val results = mutableListOf<SearchResultItem.HistoryResult>()
        try {
            // The detailed grouped history query is intentionally here and only here -
            // it is too slow for the address-bar suggestion pipeline.
            val visits = requireContext().components.historyStorage
                .getDetailedVisits(0, excludeTypes = emptyList())
            visits.forEach { visit ->
                if (visit.title?.contains(query, ignoreCase = true) == true ||
                    visit.url.contains(query, ignoreCase = true)
                ) {
                    results.add(
                        SearchResultItem.HistoryResult(
                            title = visit.title ?: visit.url,
                            url = visit.url,
                            visitTime = visit.visitTime,
                            timeGroup = getString(getTimeGroupRes(visit.visitTime)),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            // History storage being unavailable just yields no results
        }
        return results.take(MAX_RESULTS_PER_SECTION)
    }

    @StringRes
    private fun getTimeGroupRes(visitTime: Long): Int {
        val diff = System.currentTimeMillis() - visitTime
        return when {
            diff < 3_600_000L -> R.string.tabsearch_time_last_hour
            diff < 86_400_000L -> R.string.tabsearch_time_today
            diff < 172_800_000L -> R.string.tabsearch_time_yesterday
            diff < 604_800_000L -> R.string.tabsearch_time_this_week
            diff < 2_592_000_000L -> R.string.tabsearch_time_this_month
            else -> R.string.tabsearch_time_older
        }
    }

    private fun onItemClicked(item: SearchResultItem) {
        when (item) {
            is SearchResultItem.HeaderResult, is SearchResultItem.SubHeaderResult -> Unit
            is SearchResultItem.TabResult -> {
                // Switch to the tab
                requireContext().components.tabsUseCases.selectTab(item.tab.id)
                try {
                    val navController = NavHostFragment.findNavController(this)
                    if (navController.currentDestination?.id == R.id.homeFragment) {
                        (requireActivity() as BrowserActivity).openToBrowser(
                            from = BrowserDirection.FromHome,
                            customTabSessionId = item.tab.id,
                        )
                    }
                } catch (e: Exception) {
                    // Navigation is best-effort; the tab is already selected
                }
                dismiss()
            }
            is SearchResultItem.BookmarkResult -> openInNewTab(item.url)
            is SearchResultItem.HistoryResult -> openInNewTab(item.url)
        }
    }

    private fun openInNewTab(url: String) {
        val activity = requireActivity() as BrowserActivity
        val isPrivate = activity.browsingModeManager.mode.isPrivate
        val currentProfile = activity.browsingModeManager.currentProfile
        val contextId = BrowserProfile.contextIdFor(currentProfile, isPrivate)

        requireContext().components.tabsUseCases.addTab(
            url = url,
            private = isPrivate,
            contextId = contextId,
            selectTab = true,
        )
        dismiss()
    }

    private fun onTabCloseClicked(tabResult: SearchResultItem.TabResult) {
        requireContext().components.tabsUseCases.removeTab(tabResult.tab.id)
        // Drop the row synchronously (before the store's async removal lands) and
        // prune any sub-header or header its removal left empty.
        val withoutTab = resultsState.value.filterNot {
            it is SearchResultItem.TabResult && it.tab.id == tabResult.tab.id
        }
        resultsState.value = pruneEmptyGroups(withoutTab)
    }

    private fun pruneEmptyGroups(items: List<SearchResultItem>): List<SearchResultItem> {
        val pruned = items.toMutableList()

        // Sub-headers followed by nothing, another sub-header or a header are empty
        var i = pruned.lastIndex
        while (i >= 0) {
            if (pruned[i] is SearchResultItem.SubHeaderResult) {
                val next = pruned.getOrNull(i + 1)
                if (next == null ||
                    next is SearchResultItem.SubHeaderResult ||
                    next is SearchResultItem.HeaderResult
                ) {
                    pruned.removeAt(i)
                }
            }
            i--
        }

        // Headers followed by nothing or another header are empty
        i = pruned.lastIndex
        while (i >= 0) {
            if (pruned[i] is SearchResultItem.HeaderResult) {
                val next = pruned.getOrNull(i + 1)
                if (next == null || next is SearchResultItem.HeaderResult) {
                    pruned.removeAt(i)
                }
            }
            i--
        }

        return pruned
    }

    companion object {
        const val TAG = "TabSearchFragment"
        private const val SHEET_HEIGHT_FRACTION = 0.85
        private const val MAX_RESULTS_PER_SECTION = 10

        fun newInstance() = TabSearchFragment()
    }
}

sealed class SearchResultItem {
    data class HeaderResult(val title: String) : SearchResultItem()
    data class SubHeaderResult(val title: String) : SearchResultItem()

    data class TabResult(
        val tab: TabSessionState,
        val profileName: String,
        val groupName: String?,
    ) : SearchResultItem()

    data class BookmarkResult(
        val title: String,
        val url: String,
        val folderPath: String,
    ) : SearchResultItem()

    data class HistoryResult(
        val title: String,
        val url: String,
        val visitTime: Long,
        val timeGroup: String,
    ) : SearchResultItem()
}

/** Unique across the mixed-type list: the prefixed index disambiguates non-tab rows. */
private fun SearchResultItem.lazyKey(index: Int): String = when (this) {
    is SearchResultItem.HeaderResult -> "header-$index"
    is SearchResultItem.SubHeaderResult -> "subheader-$index"
    is SearchResultItem.TabResult -> "tab-${tab.id}"
    is SearchResultItem.BookmarkResult -> "bookmark-$index-$url"
    is SearchResultItem.HistoryResult -> "history-$index-$visitTime"
}

private fun isFirstInSubGroup(items: List<SearchResultItem>, index: Int): Boolean {
    if (index == 0) return false
    return items[index - 1] is SearchResultItem.SubHeaderResult
}

private fun isLastInSubGroup(items: List<SearchResultItem>, index: Int): Boolean {
    if (index == items.lastIndex) return true
    val next = items.getOrNull(index + 1)
    return next == null ||
        next is SearchResultItem.SubHeaderResult ||
        next is SearchResultItem.HeaderResult
}

private fun isLastInMainGroup(items: List<SearchResultItem>, index: Int): Boolean {
    if (index == items.lastIndex) return true
    val next = items.getOrNull(index + 1)
    return next == null || next is SearchResultItem.HeaderResult
}

/**
 * The Compose body of the unified search sheet: the shared sheet header, the pill
 * search field, and the grouped result cards. The sheet chrome (rounded corners,
 * surface color) comes from the dialog theme, so the content is a bare Column.
 */
@Composable
private fun TabSearchSheetContent(
    query: String,
    results: List<SearchResultItem>,
    onQueryChange: (String) -> Unit,
    onItemClicked: (SearchResultItem) -> Unit,
    onTabCloseClicked: (SearchResultItem.TabResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Auto-focus the field so the keyboard is up when the sheet opens.
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        NiraSheetHeader(
            title = stringResource(R.string.tabsearch_title),
            onClose = onDismiss,
        )

        NiraSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.search_tabs_bookmarks_history),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            focusRequester = focusRequester,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        val enterFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exitFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = results.isEmpty(),
            transitionSpec = { fadeIn(enterFade).togetherWith(fadeOut(exitFade)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "tabSearchResults",
        ) { showEmpty ->
            if (showEmpty) {
                NiraEmptyState(
                    title = stringResource(R.string.search_tabs_bookmarks_history),
                    subtitle = stringResource(R.string.search_tabs_description),
                    icon = Icons.Rounded.Search,
                )
            } else {
                SearchResultList(
                    results = results,
                    onItemClicked = onItemClicked,
                    onTabCloseClicked = onTabCloseClicked,
                )
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<SearchResultItem>,
    onItemClicked: (SearchResultItem) -> Unit,
    onTabCloseClicked: (SearchResultItem.TabResult) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(results, key = { index, item -> item.lazyKey(index) }) { index, item ->
            val itemModifier = Modifier.then(animateNiraListItem())
            when (item) {
                is SearchResultItem.HeaderResult -> SectionHeader(
                    title = item.title,
                    modifier = itemModifier,
                )
                is SearchResultItem.SubHeaderResult -> SubHeaderChip(
                    title = item.title,
                    modifier = itemModifier,
                )
                is SearchResultItem.TabResult -> {
                    val lastInSubGroup = isLastInSubGroup(results, index)
                    ResultRow(
                        title = item.tab.content.title.ifEmpty { item.tab.content.url },
                        url = item.tab.content.url,
                        isTopRow = isFirstInSubGroup(results, index),
                        isBottomRow = lastInSubGroup,
                        showDivider = lastInSubGroup && !isLastInMainGroup(results, index),
                        onClick = { onItemClicked(item) },
                        modifier = itemModifier,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!item.groupName.isNullOrEmpty()) {
                                    GroupChip(label = item.groupName)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                IconButton(
                                    onClick = { onTabCloseClicked(item) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close_small),
                                        contentDescription = stringResource(R.string.menu_close_tab),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                    )
                }
                is SearchResultItem.BookmarkResult -> {
                    val lastInSubGroup = isLastInSubGroup(results, index)
                    ResultRow(
                        title = item.title.ifBlank { item.url },
                        url = item.url,
                        isTopRow = isFirstInSubGroup(results, index),
                        isBottomRow = lastInSubGroup,
                        showDivider = lastInSubGroup && !isLastInMainGroup(results, index),
                        onClick = { onItemClicked(item) },
                        modifier = itemModifier,
                    )
                }
                is SearchResultItem.HistoryResult -> {
                    val lastInSubGroup = isLastInSubGroup(results, index)
                    ResultRow(
                        title = item.title,
                        url = item.url,
                        isTopRow = isFirstInSubGroup(results, index),
                        isBottomRow = lastInSubGroup,
                        showDivider = lastInSubGroup && !isLastInMainGroup(results, index),
                        onClick = { onItemClicked(item) },
                        modifier = itemModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/** The profile / folder / date chip that labels one result sub-group. */
@Composable
private fun SubHeaderChip(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FilterChip(
            selected = false,
            onClick = {},
            label = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            border = null,
        )
    }
}

/** The small tab-group label a tab row carries next to its close button. */
@Composable
private fun GroupChip(label: String) {
    FilterChip(
        selected = false,
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = null,
        modifier = Modifier.height(22.dp),
    )
}

/** One result row on the shared grouped-card idiom, with the site's favicon. */
@Composable
private fun ResultRow(
    title: String,
    url: String,
    isTopRow: Boolean,
    isBottomRow: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    NiraCardRow(
        title = title,
        isTopRow = isTopRow,
        isBottomRow = isBottomRow,
        showDivider = showDivider,
        modifier = modifier,
        onClick = onClick,
        leading = {
            FaviconImageFromUrl(
                url = url,
                title = title,
                size = 24.dp,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        },
        trailing = trailing,
    )
}
