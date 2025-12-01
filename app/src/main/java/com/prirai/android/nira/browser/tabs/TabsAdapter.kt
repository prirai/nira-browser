package com.prirai.android.nira.browser.tabs

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Simple adapter for displaying tabs in a flat list with card UI
 */
class TabsAdapter(
    private val onTabClick: (String) -> Unit,
    private val onTabClose: (String) -> Unit
) : ListAdapter<TabSessionState, TabsAdapter.TabViewHolder>(TabDiffCallback()) {

    private var selectedTabId: String? = null

    fun updateTabs(tabs: List<TabSessionState>, selectedId: String?) {
        selectedTabId = selectedId
        submitList(tabs)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab_card, parent, false)
        return TabViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(getItem(position), selectedTabId)
    }

    inner class TabViewHolder(private val cardView: MaterialCardView) : RecyclerView.ViewHolder(cardView) {
        private val favicon: ImageView = cardView.findViewById(R.id.favicon)
        private val tabTitle: TextView = cardView.findViewById(R.id.tabTitle)
        private val tabUrl: TextView = cardView.findViewById(R.id.tabUrl)
        private val closeButton: ImageView = cardView.findViewById(R.id.closeButton)

        fun bind(tab: TabSessionState, selectedId: String?) {
            // Set selected state
            cardView.isSelected = tab.id == selectedId

            // Set title
            val title = tab.content.title.ifBlank { "New Tab" }
            tabTitle.text = title

            // Set URL
            tabUrl.text = tab.content.url

            // Load favicon
            if (tab.content.icon != null) {
                favicon.setImageBitmap(tab.content.icon)
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    val context = cardView.context
                    val faviconCache = context.components.faviconCache
                    val cachedIcon = faviconCache.loadFavicon(tab.content.url)
                    if (cachedIcon != null) {
                        favicon.setImageBitmap(cachedIcon)
                    } else {
                        favicon.setImageResource(R.drawable.ic_baseline_link)
                    }
                }
            }

            // Click listeners
            cardView.setOnClickListener {
                onTabClick(tab.id)
            }

            closeButton.setOnClickListener {
                onTabClose(tab.id)
            }
        }
    }

    private class TabDiffCallback : DiffUtil.ItemCallback<TabSessionState>() {
        override fun areItemsTheSame(oldItem: TabSessionState, newItem: TabSessionState): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TabSessionState, newItem: TabSessionState): Boolean {
            return oldItem.content.title == newItem.content.title &&
                    oldItem.content.url == newItem.content.url &&
                    oldItem.content.icon == newItem.content.icon
        }
    }
}
