package com.prirai.android.nira.browser.tabs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import com.prirai.android.nira.R
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.concept.base.images.ImageLoadRequest

sealed class TabGridItem {
    data class GroupHeader(
        val groupId: String,
        val name: String,
        val color: Int,
        val tabs: List<TabSessionState>
    ) : TabGridItem()
    
    data class Tab(val tab: TabSessionState, val groupId: String? = null) : TabGridItem()
}

class TabsGridAdapter(
    private val thumbnailLoader: ThumbnailLoader,
    private val onTabClick: (String) -> Unit,
    private val onTabClose: (String) -> Unit,
    private val onGroupMoreClick: (String, View) -> Unit
) : ListAdapter<TabGridItem, RecyclerView.ViewHolder>(TabGridItemDiffCallback()) {

    private var selectedTabId: String? = null

    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_TAB = 1
    }

    fun updateItems(items: List<TabGridItem>, selectedId: String?) {
        selectedTabId = selectedId
        submitList(items)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TabGridItem.GroupHeader -> VIEW_TYPE_GROUP
            is TabGridItem.Tab -> VIEW_TYPE_TAB
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_tab_group_grid, parent, false)
                GroupViewHolder(view, thumbnailLoader, onTabClick, onTabClose, onGroupMoreClick)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_tab_grid, parent, false)
                TabViewHolder(view as MaterialCardView, thumbnailLoader)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TabGridItem.GroupHeader -> (holder as GroupViewHolder).bind(item, selectedTabId)
            is TabGridItem.Tab -> (holder as TabViewHolder).bind(item.tab, selectedTabId)
        }
    }

    class GroupViewHolder(
        itemView: View,
        private val thumbnailLoader: ThumbnailLoader,
        private val onTabClick: (String) -> Unit,
        private val onTabClose: (String) -> Unit,
        private val onGroupMoreClick: (String, View) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val groupCard: MaterialCardView = itemView.findViewById(R.id.groupCard)
        private val colorStripe: View = itemView.findViewById(R.id.colorStripe)
        private val groupName: TextView = itemView.findViewById(R.id.groupName)
        private val tabsRecyclerView: RecyclerView = itemView.findViewById(R.id.groupTabsRecyclerView)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)
        
        private val groupTabsAdapter = GridGroupTabsAdapter(thumbnailLoader, onTabClick, onTabClose)

        init {
            tabsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = groupTabsAdapter
            }
        }

        fun bind(item: TabGridItem.GroupHeader, selectedId: String?) {
            colorStripe.setBackgroundColor(item.color)
            
            // For Unnamed groups, hide only the text but keep header visible for color bar and menu
            if (item.name.equals("Unnamed", ignoreCase = true) || item.name.isBlank()) {
                groupName.text = "" // Empty text
                groupName.visibility = View.VISIBLE
            } else {
                groupName.text = item.name
                groupName.visibility = View.VISIBLE
            }
            
            // Always show color stripe and menu button
            colorStripe.visibility = View.VISIBLE
            moreButton.visibility = View.VISIBLE
            
            groupCard.setCardBackgroundColor(
                androidx.core.content.ContextCompat.getColor(
                    itemView.context,
                    R.color.m3_surface_container_high
                )
            )
            
            moreButton.setOnClickListener {
                onGroupMoreClick(item.groupId, it)
            }
            
            groupTabsAdapter.updateTabs(item.tabs, selectedId)
        }
    }

    inner class TabViewHolder(
        private val cardView: MaterialCardView,
        private val thumbnailLoader: ThumbnailLoader
    ) : RecyclerView.ViewHolder(cardView) {
        private val thumbnail: ImageView = cardView.findViewById(R.id.tabThumbnail)
        private val title: TextView = cardView.findViewById(R.id.tabTitle)
        private val closeButton: ImageButton = cardView.findViewById(R.id.closeButton)

        fun bind(tab: TabSessionState, selectedId: String?) {
            val isSelected = tab.id == selectedId
            val isGuestTab = tab.contextId == null // Orange border for guest tabs (null contextId)
            
            // Set stroke based on selection and guest status
            when {
                isSelected -> {
                    cardView.strokeWidth = (3 * cardView.context.resources.displayMetrics.density).toInt()
                    cardView.strokeColor = androidx.core.content.ContextCompat.getColor(
                        cardView.context,
                        R.color.m3_primary
                    )
                }
                isGuestTab -> {
                    cardView.strokeWidth = (3 * cardView.context.resources.displayMetrics.density).toInt()
                    cardView.strokeColor = android.graphics.Color.parseColor("#FF9500") // Orange
                }
                else -> {
                    cardView.strokeWidth = (1 * cardView.context.resources.displayMetrics.density).toInt()
                    cardView.strokeColor = androidx.core.content.ContextCompat.getColor(
                        cardView.context,
                        R.color.tab_card_stroke
                    )
                }
            }

            // Set title
            val displayTitle = when {
                tab.content.title.isNotBlank() -> tab.content.title
                tab.content.url.startsWith("about:homepage") -> "New Tab"
                tab.content.url.isNotBlank() -> tab.content.url
                else -> "New Tab"
            }
            title.text = displayTitle

            // Load thumbnail using ThumbnailLoader with actual view dimensions
            // Post to ensure view is measured
            thumbnail.post {
                val width = thumbnail.width
                val height = thumbnail.height
                if (width > 0 && height > 0) {
                    // Use the larger dimension for better quality
                    val size = maxOf(width, height)
                    thumbnailLoader.loadIntoView(
                        thumbnail,
                        ImageLoadRequest(tab.id, size, isPrivate = tab.content.private)
                    )
                } else {
                    // Fallback to calculated size based on dp
                    val displayMetrics = thumbnail.context.resources.displayMetrics
                    val widthDp = 120 // match_parent in grid column
                    val heightDp = 120 // fixed height from layout
                    val size = (maxOf(widthDp, heightDp) * displayMetrics.density).toInt()
                    thumbnailLoader.loadIntoView(
                        thumbnail,
                        ImageLoadRequest(tab.id, size, isPrivate = tab.content.private)
                    )
                }
            }
            
            // Show placeholder for New Tab
            if (tab.content.url.startsWith("about:homepage") || tab.content.url == "about:blank") {
                thumbnail.setImageResource(R.drawable.ic_search)
                thumbnail.scaleType = ImageView.ScaleType.CENTER
            }

            cardView.setOnClickListener {
                onTabClick(tab.id)
            }

            closeButton.setOnClickListener {
                onTabClose(tab.id)
            }
        }
    }

    private class TabGridItemDiffCallback : DiffUtil.ItemCallback<TabGridItem>() {
        override fun areItemsTheSame(oldItem: TabGridItem, newItem: TabGridItem): Boolean {
            return when {
                oldItem is TabGridItem.GroupHeader && newItem is TabGridItem.GroupHeader ->
                    oldItem.groupId == newItem.groupId
                oldItem is TabGridItem.Tab && newItem is TabGridItem.Tab ->
                    oldItem.tab.id == newItem.tab.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TabGridItem, newItem: TabGridItem): Boolean {
            return oldItem == newItem
        }
    }
}

// Adapter for tabs inside a group in grid view
class GridGroupTabsAdapter(
    private val thumbnailLoader: ThumbnailLoader,
    private val onTabClick: (String) -> Unit,
    private val onTabClose: (String) -> Unit
) : RecyclerView.Adapter<GridGroupTabsAdapter.GroupTabViewHolder>() {
    
    private var tabs: List<TabSessionState> = emptyList()
    private var selectedTabId: String? = null
    
    fun updateTabs(newTabs: List<TabSessionState>, selectedId: String?) {
        tabs = newTabs
        selectedTabId = selectedId
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupTabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab_in_group_grid, parent, false)
        return GroupTabViewHolder(view, thumbnailLoader, onTabClick, onTabClose)
    }
    
    override fun onBindViewHolder(holder: GroupTabViewHolder, position: Int) {
        holder.bind(tabs[position], selectedTabId)
    }
    
    override fun getItemCount() = tabs.size
    
    class GroupTabViewHolder(
        itemView: View,
        private val thumbnailLoader: ThumbnailLoader,
        private val onTabClick: (String) -> Unit,
        private val onTabClose: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.tabCard)
        private val thumbnail: ImageView = itemView.findViewById(R.id.tabThumbnail)
        private val title: TextView = itemView.findViewById(R.id.tabTitle)
        private val closeButton: ImageButton = itemView.findViewById(R.id.closeButton)
        
        fun bind(tab: TabSessionState, selectedId: String?) {
            val isSelected = tab.id == selectedId
            val isGuestTab = tab.contextId == null // Orange border for guest tabs (null contextId)
            
            // Apply consistent rounding (same as all grid items)
            val radius = 12f * cardView.context.resources.displayMetrics.density
            cardView.radius = radius
            
            // Clip thumbnail to match card corners
            thumbnail.clipToOutline = true
            thumbnail.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            
            // Set stroke based on selection and guest status
            when {
                isSelected -> {
                    cardView.strokeWidth = (2 * cardView.context.resources.displayMetrics.density).toInt()
                    cardView.strokeColor = androidx.core.content.ContextCompat.getColor(cardView.context, R.color.m3_primary)
                }
                isGuestTab -> {
                    cardView.strokeWidth = (2 * cardView.context.resources.displayMetrics.density).toInt()
                    cardView.strokeColor = android.graphics.Color.parseColor("#FF9500")
                }
                else -> {
                    cardView.strokeWidth = (1 * cardView.context.resources.displayMetrics.density).toInt()
                    cardView.strokeColor = androidx.core.content.ContextCompat.getColor(cardView.context, R.color.m3_outline_variant)
                }
            }
            
            // Set title
            val displayTitle = when {
                tab.content.title.isNotBlank() -> tab.content.title
                tab.content.url.startsWith("about:homepage") -> "New Tab"
                tab.content.url.isNotBlank() -> tab.content.url
                else -> "New Tab"
            }
            title.text = displayTitle
            
            // Load thumbnail with actual view dimensions for better quality
            thumbnail.post {
                val width = thumbnail.width
                val height = thumbnail.height
                if (width > 0 && height > 0) {
                    val size = maxOf(width, height)
                    thumbnailLoader.loadIntoView(
                        thumbnail,
                        ImageLoadRequest(tab.id, size, isPrivate = tab.content.private)
                    )
                } else {
                    // Fallback to calculated size
                    val displayMetrics = thumbnail.context.resources.displayMetrics
                    val size = (140 * displayMetrics.density).toInt() // 140dp card width
                    thumbnailLoader.loadIntoView(
                        thumbnail,
                        ImageLoadRequest(tab.id, size, isPrivate = tab.content.private)
                    )
                }
            }
            
            // Show placeholder for New Tab
            if (tab.content.url.startsWith("about:homepage") || tab.content.url == "about:blank") {
                thumbnail.setImageResource(R.drawable.ic_search)
                thumbnail.scaleType = ImageView.ScaleType.CENTER
            }
            
            cardView.setOnClickListener {
                onTabClick(tab.id)
            }
            
            closeButton.setOnClickListener {
                onTabClose(tab.id)
            }
        }
    }
}
