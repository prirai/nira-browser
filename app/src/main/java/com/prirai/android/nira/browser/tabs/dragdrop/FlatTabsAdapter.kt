package com.prirai.android.nira.browser.tabs.dragdrop

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * @deprecated Legacy - no longer used. Use Compose-based TabSheetListView/TabSheetGridView instead.
 */
@Deprecated("Legacy - no longer used")
class FlatTabsAdapter(
    context: Context,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (String, View) -> Boolean,
    onGroupedTabLongPress: (String, String, View) -> Boolean,
    onGroupHeaderClick: (String) -> Unit,
    onGroupMoreClick: (String, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val currentList: List<Any> = emptyList()

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // Empty stub - kept for compilation compatibility
        return object : RecyclerView.ViewHolder(android.widget.FrameLayout(parent.context)) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Empty stub - kept for compilation compatibility
    }

    override fun getItemCount(): Int = 0
}
