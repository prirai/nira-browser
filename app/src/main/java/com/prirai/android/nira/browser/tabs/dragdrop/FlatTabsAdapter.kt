package com.prirai.android.nira.browser.tabs.dragdrop

import android.view.LayoutInflater
import android.view.View
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
 * Flat adapter for tabs and groups - no nested RecyclerViews!
 * This is the key to enabling drag & drop for ALL tabs.
 */
class FlatTabsAdapter(
    private val onTabClick: (String) -> Unit,
    private val onTabClose: (String) -> Unit,
    private val onTabLongPress: (String, View) -> Boolean,
    private val onGroupedTabLongPress: (String, String, View) -> Boolean,
    private val onGroupHeaderClick: (String) -> Unit,
    private val onGroupMoreClick: (String, View) -> Unit
) : ListAdapter<TabListItem, RecyclerView.ViewHolder>(TabListItemDiffCallback()) {
    
    private var selectedTabId: String? = null
    
    companion object {
        private const val VIEW_TYPE_GROUP_HEADER = 0
        private const val VIEW_TYPE_GROUPED_TAB = 1
        private const val VIEW_TYPE_UNGROUPED_TAB = 2
    }
    
    fun updateItems(items: List<TabListItem>, selectedId: String?) {
        selectedTabId = selectedId
        submitList(items)
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TabListItem.GroupHeader -> VIEW_TYPE_GROUP_HEADER
            is TabListItem.GroupedTab -> VIEW_TYPE_GROUPED_TAB
            is TabListItem.UngroupedTab -> VIEW_TYPE_UNGROUPED_TAB
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_tab_group, parent, false)
                GroupHeaderViewHolder(view)
            }
            VIEW_TYPE_GROUPED_TAB -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_tab_card, parent, false)
                GroupedTabViewHolder(view as MaterialCardView)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_tab_card, parent, false)
                UngroupedTabViewHolder(view as MaterialCardView)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TabListItem.GroupHeader -> {
                (holder as GroupHeaderViewHolder).bind(item)
                
                // Remove bottom margin so header touches first tab
                val layoutParams = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
                layoutParams?.let {
                    val density = holder.itemView.context.resources.displayMetrics.density
                    val normalMargin = (16 * density).toInt()
                    it.setMargins(normalMargin, (8 * density).toInt(), normalMargin, 0)
                    holder.itemView.layoutParams = it
                }
                
                // Set rounded corners - only top corners, zero bottom corners
                if (holder.itemView is MaterialCardView) {
                    val cardView = holder.itemView as MaterialCardView
                    cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                        .setTopLeftCornerSize(12f * holder.itemView.context.resources.displayMetrics.density)
                        .setTopRightCornerSize(12f * holder.itemView.context.resources.displayMetrics.density)
                        .setBottomLeftCornerSize(0f)
                        .setBottomRightCornerSize(0f)
                        .build()
                }
            }
            is TabListItem.GroupedTab -> {
                // Determine if this is first, middle, or last tab in group
                val isFirst = position > 0 && getItem(position - 1) is TabListItem.GroupHeader
                val isLast = position == currentList.size - 1 || 
                             getItem(position + 1) !is TabListItem.GroupedTab ||
                             (getItem(position + 1) as? TabListItem.GroupedTab)?.groupId != item.groupId
                
                (holder as GroupedTabViewHolder).bind(item.tab, item.groupId, selectedTabId, isFirst, isLast)
            }
            is TabListItem.UngroupedTab -> {
                (holder as UngroupedTabViewHolder).bind(item.tab, selectedTabId)
            }
        }
    }
    
    /**
     * ViewHolder for group headers
     */
    inner class GroupHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorStripe: View = itemView.findViewById(R.id.colorStripe)
        private val groupHeader: View = itemView.findViewById(R.id.groupHeader)
        private val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)
        private val groupName: TextView = itemView.findViewById(R.id.groupName)
        private val tabCount: TextView = itemView.findViewById(R.id.tabCount)
        private val moreButton: ImageView = itemView.findViewById(R.id.moreButton)
        private val tabsRecyclerView: RecyclerView = itemView.findViewById(R.id.groupTabsRecyclerView)
        
        init {
            // Hide the nested RecyclerView - we don't use it anymore!
            tabsRecyclerView.visibility = View.GONE
        }
        
        fun bind(item: TabListItem.GroupHeader) {
            // Set color stripe
            colorStripe.setBackgroundColor(item.color)
            
            // Set group name
            groupName.text = item.name.ifBlank { "Tab Group" }
            
            // Set tab count
            tabCount.text = item.tabCount.toString()
            
            // Set expand icon rotation
            val rotation = if (item.isExpanded) 180f else 0f
            expandIcon.animate().rotation(rotation).setDuration(200).start()
            
            // Click to expand/collapse
            groupHeader.setOnClickListener {
                onGroupHeaderClick(item.groupId)
            }
            
            // More button
            moreButton.setOnClickListener {
                onGroupMoreClick(item.groupId, it)
            }
        }
    }
    
    /**
     * ViewHolder for tabs inside groups
     */
    inner class GroupedTabViewHolder(
        private val cardView: MaterialCardView
    ) : RecyclerView.ViewHolder(cardView) {
        private val favicon: ImageView = cardView.findViewById(R.id.favicon)
        private val tabTitle: TextView = cardView.findViewById(R.id.tabTitle)
        private val tabUrl: TextView = cardView.findViewById(R.id.tabUrl)
        private val closeButton: ImageView = cardView.findViewById(R.id.closeButton)
        
        fun bind(tab: TabSessionState, groupId: String, selectedId: String?, isFirst: Boolean, isLast: Boolean) {
            val isSelected = tab.id == selectedId
            
            // Adjust margins and corners for grouped appearance
            val layoutParams = cardView.layoutParams as? ViewGroup.MarginLayoutParams
            layoutParams?.let {
                val density = cardView.context.resources.displayMetrics.density
                val normalMargin = (16 * density).toInt()
                
                // Zero margin between grouped tabs
                it.setMargins(normalMargin, 0, normalMargin, 0)
                cardView.layoutParams = it
                
                // Only last tab has bottom corners rounded
                if (isLast) {
                    cardView.radius = 12f * density
                } else {
                    cardView.radius = 0f
                }
            }
            
            // Show title with fallback
            val title = when {
                tab.content.title.isNotBlank() -> tab.content.title
                tab.content.url.startsWith("about:homepage") || tab.content.url.startsWith("about:privatebrowsing") -> "New Tab"
                else -> tab.content.url.ifBlank { "New Tab" }
            }
            tabTitle.text = title
            tabUrl.text = tab.content.url
            
            // Set selected state with border
            if (isSelected) {
                val strokeWidth = (2 * cardView.context.resources.displayMetrics.density).toInt()
                cardView.strokeWidth = strokeWidth
                val theme = cardView.context.theme
                val primaryTypedValue = android.util.TypedValue()
                if (theme.resolveAttribute(android.R.attr.colorPrimary, primaryTypedValue, true)) {
                    cardView.strokeColor = primaryTypedValue.data
                } else {
                    cardView.strokeColor = androidx.core.content.ContextCompat.getColor(cardView.context, R.color.m3_primary)
                }
                cardView.cardElevation = 2f * cardView.context.resources.displayMetrics.density
            } else {
                val strokeWidth = (1 * cardView.context.resources.displayMetrics.density).toInt()
                cardView.strokeWidth = strokeWidth
                cardView.strokeColor = androidx.core.content.ContextCompat.getColor(
                    cardView.context,
                    R.color.tab_card_stroke
                )
                cardView.cardElevation = 2f * cardView.context.resources.displayMetrics.density
            }
            
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
            
            // Click handlers
            cardView.setOnClickListener {
                onTabClick(tab.id)
            }
            
            cardView.setOnLongClickListener {
                onGroupedTabLongPress(tab.id, groupId, it)
            }
            
            closeButton.setOnClickListener {
                onTabClose(tab.id)
            }
        }
    }
    
    /**
     * ViewHolder for ungrouped tabs
     */
    inner class UngroupedTabViewHolder(
        private val cardView: MaterialCardView
    ) : RecyclerView.ViewHolder(cardView) {
        private val favicon: ImageView = cardView.findViewById(R.id.favicon)
        private val tabTitle: TextView = cardView.findViewById(R.id.tabTitle)
        private val tabUrl: TextView = cardView.findViewById(R.id.tabUrl)
        private val closeButton: ImageView = cardView.findViewById(R.id.closeButton)
        
        fun bind(tab: TabSessionState, selectedId: String?) {
            val isSelected = tab.id == selectedId
            val isGuestTab = tab.contextId == null
            
            // Show title with fallback
            val title = when {
                tab.content.title.isNotBlank() -> tab.content.title
                tab.content.url.startsWith("about:homepage") || tab.content.url.startsWith("about:privatebrowsing") -> "New Tab"
                else -> tab.content.url.ifBlank { "New Tab" }
            }
            tabTitle.text = title
            tabUrl.text = tab.content.url
            
            // Set selected/guest state
            when {
                isSelected -> {
                    val strokeWidth = (2 * cardView.context.resources.displayMetrics.density).toInt()
                    cardView.strokeWidth = strokeWidth
                    val theme = cardView.context.theme
                    val primaryTypedValue = android.util.TypedValue()
                    if (theme.resolveAttribute(android.R.attr.colorPrimary, primaryTypedValue, true)) {
                        cardView.strokeColor = primaryTypedValue.data
                    } else {
                        cardView.strokeColor = androidx.core.content.ContextCompat.getColor(cardView.context, R.color.m3_primary)
                    }
                }
                isGuestTab -> {
                    val strokeWidth = (2 * cardView.context.resources.displayMetrics.density).toInt()
                    cardView.strokeWidth = strokeWidth
                    cardView.strokeColor = android.graphics.Color.parseColor("#FF9500")
                }
                else -> {
                    val strokeWidth = (1 * cardView.context.resources.displayMetrics.density).toInt()
                    cardView.strokeWidth = strokeWidth
                    cardView.strokeColor = androidx.core.content.ContextCompat.getColor(
                        cardView.context,
                        R.color.tab_card_stroke
                    )
                }
            }
            
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
            
            // Click handlers
            cardView.setOnClickListener {
                onTabClick(tab.id)
            }
            
            cardView.setOnLongClickListener {
                onTabLongPress(tab.id, it)
            }
            
            closeButton.setOnClickListener {
                onTabClose(tab.id)
            }
        }
    }
}

/**
 * DiffUtil callback for efficient list updates
 */
private class TabListItemDiffCallback : DiffUtil.ItemCallback<TabListItem>() {
    override fun areItemsTheSame(oldItem: TabListItem, newItem: TabListItem): Boolean {
        return when {
            oldItem is TabListItem.GroupHeader && newItem is TabListItem.GroupHeader -> 
                oldItem.groupId == newItem.groupId
            oldItem is TabListItem.GroupedTab && newItem is TabListItem.GroupedTab -> 
                oldItem.tab.id == newItem.tab.id
            oldItem is TabListItem.UngroupedTab && newItem is TabListItem.UngroupedTab -> 
                oldItem.tab.id == newItem.tab.id
            else -> false
        }
    }
    
    override fun areContentsTheSame(oldItem: TabListItem, newItem: TabListItem): Boolean {
        return oldItem == newItem
    }
}
