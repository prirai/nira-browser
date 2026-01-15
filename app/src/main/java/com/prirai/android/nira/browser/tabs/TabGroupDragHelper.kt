package com.prirai.android.nira.browser.tabs

import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.CoroutineScope

/**
 * @deprecated Legacy - no longer used. Use UnifiedDragSystem instead.
 */
@Deprecated("Legacy - no longer used")
class TabGroupDragHelper(
    adapter: Any,
    groupManager: UnifiedTabGroupManager,
    scope: CoroutineScope,
    onUpdate: () -> Unit
) {
    fun attachToRecyclerView(rv: RecyclerView?) {
        // Empty stub - kept for compilation compatibility
    }
}
