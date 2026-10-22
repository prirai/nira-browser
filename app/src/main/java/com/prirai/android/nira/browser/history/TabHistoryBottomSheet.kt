package com.prirai.android.nira.browser.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.tabs.NewTabCreator
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.state.content.HistoryState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flowScoped

/**
 * Which direction of the current tab's session history the popup shows.
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
 * Long-press back/forward popup (Chrome/Fenix style).
 *
 * Shows the current tab's session history in one direction, most recent
 * entry first, with the current page pinned at the top (filled dot,
 * disabled). Tapping an entry jumps there via
 * `sessionUseCases.goToHistoryIndex`.
 *
 * The stored [HistoryState] can lag behind the engine's real session
 * history, so - like upstream Fenix (Bug 1970055) - the sheet first
 * dispatches [EngineAction.FlushEngineSessionStateAction] to force a
 * re-sync, then collects `content.history` reactively and re-submits the
 * rows when the flushed state lands.
 */
class TabHistoryBottomSheet : BottomSheetDialogFragment() {

    private val mode by lazy { TabHistoryMode.fromName(arguments?.getString(KEY_MODE)) }
    private val sessionId get() = arguments?.getString(KEY_SESSION_ID)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_tab_history_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val components = requireContext().components

        view.findViewById<TextView>(R.id.tabHistorySheetTitle).setText(
            if (mode is TabHistoryMode.Back) R.string.back else R.string.forward
        )

        val recyclerView = view.findViewById<RecyclerView>(R.id.tabHistoryRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val adapter = TabHistoryAdapter { row ->
            val tab = components.store.state.findCustomTabOrSelectedTab(sessionId)
            if (row.index >= 0) {
                tab?.let { components.sessionUseCases.goToHistoryIndex(row.index, it.id) }
            } else {
                // Synthetic "New Tab" entry: land the tab on its new tab
                // page (the engine has no history entry for it).
                tab?.let { NewTabCreator.setTabToNewTabPage(components.store, it.id) }
            }
            dismiss()
        }
        recyclerView.adapter = adapter

        // Force the engine to re-sync its session history into the store;
        // the flushed state is picked up by the flow below.
        components.store.state.findCustomTabOrSelectedTab(sessionId)?.let {
            components.store.dispatch(EngineAction.FlushEngineSessionStateAction(it.id))
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
                    adapter.submitRows(rows)
                }
        }
    }

    private fun buildRows(history: HistoryState?): List<TabHistoryRow> {
        val items = history?.items ?: return emptyList()
        val current = history.currentIndex
        if (items.isEmpty() || current !in items.indices) return emptyList()

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

        return when (mode) {
            // Current page first, then back entries newest -> oldest.
            TabHistoryMode.Back -> {
                var rows = listOf(row(current, isCurrent = true)) +
                    ((current - 1) downTo 0).map { row(it, isCurrent = false) }

                // Chrome lists the new tab page as the oldest back entry
                // even though the engine has no history entry for it
                // (about:homepage cannot be an engine page) - synthesize
                // it at the bottom. Normal tabs only; the private home is
                // a different surface.
                if (!isPrivateTab && items.firstOrNull()?.uri !in NEW_TAB_PAGE_URLS) {
                    rows += TabHistoryRow(
                        index = SYNTHETIC_NEW_TAB_INDEX,
                        title = getString(R.string.tab_history_new_tab),
                        uri = "about:homepage",
                        isCurrent = false
                    )
                }
                rows
            }

            // Current page first, then forward entries oldest -> newest.
            TabHistoryMode.Forward -> listOf(row(current, isCurrent = true)) +
                ((current + 1) until items.size).map { row(it, isCurrent = false) }
        }
    }

    companion object {
        const val TAG = "TabHistoryBottomSheet"
        private const val KEY_MODE = "mode"
        private const val KEY_SESSION_ID = "sessionId"

        /** Marks the synthesized "New Tab" row (not a real history index). */
        private const val SYNTHETIC_NEW_TAB_INDEX = -1
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
         * matching Chrome (the caller's long-press haptic still fires).
         *
         * [TabSessionState.content.canGoBack]/[canGoForward] are used as
         * the emptiness signal: they are maintained by the engine on every
         * navigation and are more reliable than the [HistoryState] indexes,
         * which can be stale until the sheet flushes the engine state.
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
