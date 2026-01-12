package com.prirai.android.nira.browser.tabs.dragdrop

import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.CoroutineScope

/**
 * @deprecated Legacy - no longer used. Use UnifiedDragSystem (Compose-based) instead.
 */
@Deprecated("Legacy - no longer used")
class UnifiedDragHelper(
    adapter: Any,
    groupManager: UnifiedTabGroupManager,
    scope: CoroutineScope,
    onUpdate: () -> Unit,
    getCurrentFlatList: () -> List<Any>,
    onOrderChanged: () -> Unit,
    isGridView: Boolean,
    spanCount: Int
) {
    fun attachToRecyclerView(rv: RecyclerView?) {
        // Empty stub - kept for compilation compatibility
    }
}
