package com.prirai.android.nira.browser.tabs.dragdrop
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
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
    val currentList = emptyList<Any>()
    fun updateItems(items: List<Any>, selectedId: String?) {}
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(android.view.View(parent.context)) {}
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
    override fun getItemCount() = 0
}
