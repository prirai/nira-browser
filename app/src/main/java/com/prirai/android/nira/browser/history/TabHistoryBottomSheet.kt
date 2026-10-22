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
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.state.content.HistoryState
import mozilla.components.browser.state.store.BrowserStore

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
 * The callers only open the sheet when there is at least one entry in
 * the requested direction; the empty guard here is defence in depth.
 */
class TabHistoryBottomSheet : BottomSheetDialogFragment() {

    private val mode by lazy { TabHistoryMode.fromName(arguments?.getString(KEY_MODE)) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_tab_history_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tab = requireContext().components.store.state
            .findCustomTabOrSelectedTab(arguments?.getString(KEY_SESSION_ID))
        val rows = buildRows(tab?.content?.history)

        // The entry points check this before showing; bail out silently in
        // case the history became empty between the check and render.
        if (rows.isEmpty()) {
            dismissAllowingStateLoss()
            return
        }

        view.findViewById<TextView>(R.id.tabHistorySheetTitle).setText(
            if (mode is TabHistoryMode.Back) R.string.back else R.string.forward
        )

        val recyclerView = view.findViewById<RecyclerView>(R.id.tabHistoryRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = TabHistoryAdapter { row ->
            tab?.let { requireContext().components.sessionUseCases.goToHistoryIndex(row.index, it.id) }
            dismiss()
        }.also { it.submitRows(rows) }
    }

    private fun buildRows(history: HistoryState?): List<TabHistoryRow> {
        val items = history?.items ?: return emptyList()
        val current = history.currentIndex
        if (items.isEmpty() || current !in items.indices) return emptyList()

        fun row(index: Int, isCurrent: Boolean) = TabHistoryRow(
            index = index,
            title = items[index].title,
            uri = items[index].uri,
            isCurrent = isCurrent
        )

        return when (mode) {
            // Current page first, then back entries newest -> oldest.
            TabHistoryMode.Back -> listOf(row(current, isCurrent = true)) +
                ((current - 1) downTo 0).map { row(it, isCurrent = false) }

            // Current page first, then forward entries oldest -> newest.
            TabHistoryMode.Forward -> listOf(row(current, isCurrent = true)) +
                ((current + 1) until items.size).map { row(it, isCurrent = false) }
        }
    }

    companion object {
        const val TAG = "TabHistoryBottomSheet"
        private const val KEY_MODE = "mode"
        private const val KEY_SESSION_ID = "sessionId"

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
         * @return whether the sheet was shown.
         */
        fun showIfAvailable(
            fragmentManager: FragmentManager,
            store: BrowserStore,
            mode: TabHistoryMode,
            sessionId: String?
        ): Boolean {
            val history = store.state.findCustomTabOrSelectedTab(sessionId)?.content?.history
                ?: return false
            val hasEntries = when (mode) {
                TabHistoryMode.Back -> history.currentIndex > 0
                TabHistoryMode.Forward -> history.currentIndex < history.items.lastIndex
            }
            if (!hasEntries) return false

            newInstance(mode, sessionId).show(fragmentManager, TAG)
            return true
        }
    }
}
