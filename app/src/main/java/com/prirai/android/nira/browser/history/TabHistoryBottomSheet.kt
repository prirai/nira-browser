@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.tabs.NewTabCreator
import com.prirai.android.nira.browser.tabs.NewTabCreator.NEW_TAB_INDEX
import com.prirai.android.nira.browser.tabs.compose.FaviconImageFromUrl
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.state.content.HistoryState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

/**
 * Which toolbar button opened the popup. The popup itself always shows the
 * full session chart (Firefox behaviour) - the mode only gates WHICH
 * button can open it (back requires back entries, forward requires
 * forward entries).
 */
sealed class TabHistoryMode {
    data object Back : TabHistoryMode()
    data object Forward : TabHistoryMode()

    companion object {
        private const val NAME_BACK = "back"
        private const val NAME_FORWARD = "forward"

        fun toName(mode: TabHistoryMode): String = when (mode) {
            Back -> NAME_BACK
            Forward -> NAME_FORWARD
        }

        fun fromName(name: String?): TabHistoryMode = when (name) {
            NAME_FORWARD -> Forward
            else -> Back
        }
    }
}

/**
 * One row of the tab-history popup: an [index] into the session's
 * [HistoryState] plus its display data.
 *
 * The row for the current page has [isCurrent] set; it renders with a filled
 * dot and does not respond to clicks (Chrome/Fenix behaviour).
 */
data class TabHistoryRow(
    val index: Int,
    val title: String,
    val uri: String,
    val isCurrent: Boolean,
)

/**
 * Long-press back/forward popup (Firefox style).
 *
 * Shows the current tab's full session chart - the sites before, the
 * current page and the sites forward - regardless of which button opened
 * it. The list is laid out like Firefox: chronological order rendered
 * bottom-up (oldest at the bottom, newest at the top), scrolled so the
 * current page sits centered, the current row marked with a filled dot
 * and disabled. Tapping any other entry jumps there via
 * `sessionUseCases.goToHistoryIndex`.
 *
 * The stored [HistoryState] can lag behind the engine's real session
 * history, so - like upstream Fenix (Bug 1970055) - the sheet first
 * dispatches [EngineAction.FlushEngineSessionStateAction] to force a
 * re-sync, then collects `content.history` reactively and re-submits the
 * rows when the flushed state lands. The flush is skipped while the tab
 * sits on its synthetic new tab page ([NEW_TAB_INDEX]): that state is
 * authoritative by design and a flush would overwrite it with the
 * engine's raw position at the newest entry.
 */
class TabHistoryBottomSheet : BottomSheetDialogFragment() {

    private val sessionId get() = arguments?.getString(KEY_SESSION_ID)

    /** The latest chart rows, collected from the store and rendered by Compose. */
    private val rowsState = mutableStateOf<List<TabHistoryRow>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val context = LocalContext.current
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors
            ) {
                TabHistorySheetContent(
                    rows = rowsState.value,
                    onRowClick = ::onRowClicked,
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val components = requireContext().components

        // Force the engine to re-sync its session history into the store;
        // the flushed state is picked up by the flow below. Skipped on the
        // synthetic new tab page - see the class kdoc.
        components.store.state.findCustomTabOrSelectedTab(sessionId)?.let { tab ->
            if (tab.content.history.currentIndex != NEW_TAB_INDEX) {
                components.store.dispatch(EngineAction.FlushEngineSessionStateAction(tab.id))
            }
        }

        // Collect history reactively: the first emission uses the current
        // (possibly stale) store state, the next one carries the flushed
        // state with the full, correct entry list.
        var hasShownRows = false
        components.store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
            flow.mapNotNull { state -> state.findCustomTabOrSelectedTab(sessionId)?.content?.history }
                .distinctUntilChanged()
                .collect { history ->
                    val rows = buildRows(history)
                    if (rows.isEmpty()) {
                        // Empty before anything was shown: the flush may not
                        // have landed yet - keep waiting. Empty after rows
                        // were shown (tab navigated/closed while open):
                        // dismiss.
                        if (hasShownRows) {
                            dismissAllowingStateLoss()
                        }
                        return@collect
                    }
                    hasShownRows = true
                    rowsState.value = rows
                }
        }
    }

    private fun onRowClicked(row: TabHistoryRow) {
        val components = requireContext().components
        components.store.state.findCustomTabOrSelectedTab(sessionId)?.let { tab ->
            if (row.index >= 0) {
                components.sessionUseCases.goToHistoryIndex(row.index, tab.id)
                // When the tapped entry is the engine's current one the
                // dispatch above is a no-op and the engine emits no
                // location change - sync the state so the tab always
                // leaves the synthetic new tab page. For real jumps the
                // engine re-reports the same values, making this
                // idempotent.
                components.store.dispatch(ContentAction.UpdateUrlAction(tab.id, row.uri))
                components.store.dispatch(ContentAction.UpdateTitleAction(tab.id, row.title))
            } else {
                // "New Tab" row: land the tab on its new tab page (the
                // engine has no history entry for it).
                NewTabCreator.setTabToNewTabPage(components.store, tab.id)
            }
        }
        dismiss()
    }

    /**
     * Builds the full chart rows in chronological order (oldest first):
     * back entries, the current page, then forward entries. When the tab
     * sits on its new tab page the current row IS the "New Tab" row and
     * every real entry is forward of it. A normal tab whose history does
     * not start at the new tab page gets a synthetic "New Tab" row as the
     * oldest entry, like Chrome's back popup.
     */
    private fun buildRows(history: HistoryState?): List<TabHistoryRow> {
        val items = history?.items ?: return emptyList()
        if (items.isEmpty()) return emptyList()
        val current = history.currentIndex

        val isPrivateTab =
            components.store.state.findCustomTabOrSelectedTab(sessionId)?.content?.private == true

        fun row(index: Int, isCurrent: Boolean) = TabHistoryRow(
            index = index,
            title = if (items[index].uri in NEW_TAB_PAGE_URLS) {
                // The new tab page entry shows up as "New Tab", matching
                // the tab pill's title for homepage tabs.
                getString(R.string.tab_history_new_tab)
            } else {
                items[index].title
            },
            uri = items[index].uri,
            isCurrent = isCurrent
        )

        val currentRow = when {
            current == NEW_TAB_INDEX -> TabHistoryRow(
                index = NEW_TAB_INDEX,
                title = getString(R.string.tab_history_new_tab),
                uri = "about:homepage",
                isCurrent = true
            )
            current in items.indices -> row(current, isCurrent = true)
            else -> return emptyList()
        }

        val backRows = if (current > 0) {
            ((current - 1) downTo 0).map { row(it, isCurrent = false) }
        } else {
            emptyList()
        }

        val forwardRows = when {
            current == NEW_TAB_INDEX -> items.indices.map { row(it, isCurrent = false) }
            current in items.indices && current < items.lastIndex ->
                ((current + 1) until items.size).map { row(it, isCurrent = false) }
            else -> emptyList()
        }

        var rows = backRows + currentRow + forwardRows

        // Chrome lists the new tab page as the oldest entry even though the
        // engine has no history entry for it (about:homepage cannot be an
        // engine page) - synthesize it at the chart's beginning. Normal
        // tabs only; the private home is a different surface.
        if (current >= 0 && !isPrivateTab && items.firstOrNull()?.uri !in NEW_TAB_PAGE_URLS) {
            rows = listOf(
                TabHistoryRow(
                    index = NEW_TAB_INDEX,
                    title = getString(R.string.tab_history_new_tab),
                    uri = "about:homepage",
                    isCurrent = false
                )
            ) + rows
        }

        return rows
    }

    companion object {
        const val TAG = "TabHistoryBottomSheet"
        private const val KEY_MODE = "mode"
        private const val KEY_SESSION_ID = "sessionId"

        private val NEW_TAB_PAGE_URLS = setOf("about:homepage", "about:blank")

        fun newInstance(mode: TabHistoryMode, sessionId: String? = null): TabHistoryBottomSheet {
            return TabHistoryBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(KEY_MODE, TabHistoryMode.toName(mode))
                    putString(KEY_SESSION_ID, sessionId)
                }
            }
        }

        /**
         * Opens the sheet only when [mode] has at least one entry for the
         * tab resolved from [sessionId] - an empty direction shows nothing,
         * matching Firefox (the caller's long-press haptic still fires).
         *
         * [mozilla.components.browser.state.state.TabSessionState.content.canGoBack]/
         * `canGoForward` are used as the emptiness signal: they are maintained by
         * the engine on every navigation (and synced by the synthetic new tab
         * page state) and are more reliable than the [HistoryState] indexes,
         * which can be stale until the sheet flushes the engine state.
         *
         * @return whether the sheet was shown.
         */
        fun showIfAvailable(
            fragmentManager: androidx.fragment.app.FragmentManager,
            store: BrowserStore,
            mode: TabHistoryMode,
            sessionId: String?
        ): Boolean {
            val tab = store.state.findCustomTabOrSelectedTab(sessionId) ?: return false
            val hasEntries = when (mode) {
                TabHistoryMode.Back -> tab.content.canGoBack
                TabHistoryMode.Forward -> tab.content.canGoForward
            }
            if (!hasEntries) return false

            newInstance(mode, sessionId).show(fragmentManager, TAG)
            return true
        }
    }
}

/**
 * The Compose body of the tab-history sheet: Firefox's bottom-up chart
 * rendered with the shared grouped-card row, so the history chart reads as
 * the same design as the awesome bar's suggestion list.
 */
@Composable
private fun TabHistorySheetContent(
    rows: List<TabHistoryRow>,
    onRowClick: (TabHistoryRow) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val currentIndex = rows.indexOfFirst { it.isCurrent }.takeIf { it >= 0 }

    // Center the current page once, after the first layout completes
    // (Firefox behaviour; replaces the View implementation's
    // CenteringLayoutManager).
    LaunchedEffect(currentIndex, rows.size) {
        if (currentIndex != null && rows.isNotEmpty()) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val itemHeight = info.visibleItemsInfo.firstOrNull { it.index == currentIndex }?.size
                ?: info.visibleItemsInfo.firstOrNull()?.size
                ?: 120
            listState.scrollToItem(
                currentIndex,
                scrollOffset = -(info.viewportSize.height - itemHeight) / 2,
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        NiraSheetHeader(
            title = stringResource(R.string.action_history),
            onClose = onDismiss,
            showDragHandle = false,
            dividerAlpha = null,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> "${row.index}-${row.uri}" }) { index, row ->
                // Reversed rendering (oldest at the bottom): adapter position 0
                // is the visually BOTTOM row and the last position the visual
                // top - the corner and divider assignment follows the mirror.
                TabHistoryCardRow(
                    row = row,
                    isTopRow = index == rows.lastIndex,
                    isBottomRow = index == 0,
                    showDivider = index > 0,
                    onClick = onRowClick,
                )
            }
        }
    }
}

/**
 * One history row on the shared grouped-card idiom. The current page
 * renders with the filled dot, grayed title and no click; every other
 * row carries the favicon and jumps on tap.
 */
@Composable
private fun TabHistoryCardRow(
    row: TabHistoryRow,
    isTopRow: Boolean,
    isBottomRow: Boolean,
    showDivider: Boolean,
    onClick: (TabHistoryRow) -> Unit,
) {
    NiraCardRow(
        title = row.title.ifBlank { row.uri.tryGetHostFromUrl() },
        isTopRow = isTopRow,
        isBottomRow = isBottomRow,
        showDivider = showDivider,
        onClick = if (row.isCurrent) {
            null
        } else {
            { onClick(row) }
        },
        titleColor = if (row.isCurrent) {
MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        leading = {
            if (row.isCurrent) {
                // Current page: filled dot
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(10.dp),
                ) {}
            } else {
                FaviconImageFromUrl(
                    url = row.uri,
                    title = row.title,
                    size = 24.dp,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        },
    )
}
