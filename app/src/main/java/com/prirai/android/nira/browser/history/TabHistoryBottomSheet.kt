package com.prirai.android.nira.browser.history

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.tabs.NewTabCreator
import com.prirai.android.nira.browser.tabs.NewTabCreator.NEW_TAB_INDEX
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.state.content.HistoryState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flowScoped

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

    /** Adapter position of the current row, used to center the list. */
    private var currentAdapterPosition: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_tab_history_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val components = requireContext().components

        view.findViewById<TextView>(R.id.tabHistorySheetTitle).setText(R.string.action_history)

        val recyclerView = view.findViewById<RecyclerView>(R.id.tabHistoryRecyclerView)
        recyclerView.layoutManager = CenteringLayoutManager(requireContext(), recyclerView)
        val adapter = TabHistoryAdapter { row ->
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
        recyclerView.adapter = adapter

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
                    currentAdapterPosition = rows.indexOfFirst { it.isCurrent }.takeIf { it >= 0 }
                    adapter.submitRows(rows)
                }
        }
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

    /**
     * Firefox layout: the chart renders bottom-up (oldest at the bottom,
     * newest at the top) and the current page is centered once, after the
     * first layout completes. If the current row is not laid out within
     * the sheet's natural height the sheet expands first so the centering
     * can reach it (upstream Fenix does the same).
     */
    private inner class CenteringLayoutManager(
        context: Context,
        private val recyclerView: RecyclerView,
    ) : LinearLayoutManager(context) {
        private var shouldCenterOnCurrent = true

        init {
            reverseLayout = true
        }

        override fun onLayoutCompleted(state: RecyclerView.State?) {
            super.onLayoutCompleted(state)
            if (!shouldCenterOnCurrent) return
            val position = currentAdapterPosition ?: return
            val itemView = recyclerView.findViewHolderForAdapterPosition(position)?.itemView
                ?: run {
                    // Out of reach at the sheet's natural height: expand,
                    // the relayout centers on the next pass.
                    (dialog as? BottomSheetDialog)?.behavior?.state =
                        BottomSheetBehavior.STATE_EXPANDED
                    return
                }
            shouldCenterOnCurrent = false
            scrollToPositionWithOffset(position, (height - itemView.height) / 2)
        }
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
         * [TabSessionState.content.canGoBack]/[canGoForward] are used as
         * the emptiness signal: they are maintained by the engine on every
         * navigation (and synced by the synthetic new tab page state) and
         * are more reliable than the [HistoryState] indexes, which can be
         * stale until the sheet flushes the engine state.
         *
         * @return whether the sheet was shown.
         */
        fun showIfAvailable(
            fragmentManager: FragmentManager,
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
