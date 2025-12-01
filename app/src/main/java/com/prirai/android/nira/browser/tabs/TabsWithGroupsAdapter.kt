package com.prirai.android.nira.browser.tabs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
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
 * Adapter for tabs within a group with long-press support
 */
class GroupTabsAdapter(
    private val onTabClick: (String) -> Unit,
    private val onTabClose: (String) -> Unit,
    private val onTabLongPress: (String, View) -> Boolean
) : ListAdapter<TabSessionState, GroupTabsAdapter.TabViewHolder>(TabDiffCallback()) {

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
        val isFirst = position == 0
        val isLast = position == itemCount - 1
        holder.bind(getItem(position), selectedTabId, isFirst, isLast)
    }

    inner class TabViewHolder(private val cardView: MaterialCardView) : RecyclerView.ViewHolder(cardView) {
        private val favicon: ImageView = cardView.findViewById(R.id.favicon)
        private val tabTitle: TextView = cardView.findViewById(R.id.tabTitle)
        private val tabUrl: TextView = cardView.findViewById(R.id.tabUrl)
        private val closeButton: ImageView = cardView.findViewById(R.id.closeButton)

        fun bind(tab: TabSessionState, selectedId: String?, isFirst: Boolean, isLast: Boolean) {
            cardView.isSelected = tab.id == selectedId

            val title = tab.content.title.ifBlank { "New Tab" }
            tabTitle.text = title
            tabUrl.text = tab.content.url
            
            // Apply corner radius based on position
            val cornerRadius = 12f * cardView.context.resources.displayMetrics.density
            when {
                isFirst && isLast -> {
                    // Single item - all corners rounded
                    cardView.radius = cornerRadius
                }
                isFirst -> {
                    // First item - only top corners rounded
                    cardView.radius = 0f
                    cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                        .setTopLeftCornerSize(cornerRadius)
                        .setTopRightCornerSize(cornerRadius)
                        .setBottomLeftCornerSize(0f)
                        .setBottomRightCornerSize(0f)
                        .build()
                }
                isLast -> {
                    // Last item - only bottom corners rounded
                    cardView.radius = 0f
                    cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                        .setTopLeftCornerSize(0f)
                        .setTopRightCornerSize(0f)
                        .setBottomLeftCornerSize(cornerRadius)
                        .setBottomRightCornerSize(cornerRadius)
                        .build()
                }
                else -> {
                    // Middle items - no corners rounded
                    cardView.radius = 0f
                }
            }
            
            // Remove margins between items in a group
            val layoutParams = cardView.layoutParams as? ViewGroup.MarginLayoutParams
            layoutParams?.let {
                it.setMargins(0, 0, 0, 0)
                cardView.layoutParams = it
            }

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

            cardView.setOnClickListener {
                onTabClick(tab.id)
            }

            // Add touch listener for drag-out-to-ungroup
            var startY = 0f
            var isDragging = false
            
            cardView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        isDragging = false
                        false
                    }
                    
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaY = kotlin.math.abs(startY - event.rawY)
                        
                        if (deltaY > 30 && !isDragging) {
                            isDragging = true
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }
                        
                        if (isDragging) {
                            // Visual feedback during drag
                            val progress = (deltaY / 150f).coerceIn(0f, 1f)
                            cardView.alpha = 1f - (progress * 0.3f)
                            cardView.scaleX = 1f - (progress * 0.1f)
                            cardView.scaleY = 1f - (progress * 0.1f)
                            true
                        } else {
                            false
                        }
                    }
                    
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            val deltaY = kotlin.math.abs(startY - event.rawY)
                            if (deltaY > 150) {
                                // Trigger ungroup - call long press to show menu
                                cardView.performLongClick()
                            } else {
                                // Spring back
                                cardView.animate()
                                    .alpha(1f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(200)
                                    .start()
                            }
                            isDragging = false
                            true
                        } else {
                            false
                        }
                    }
                    
                    else -> false
                }
            }

            cardView.setOnLongClickListener {
                if (!isDragging) {
                    onTabLongPress(tab.id, it)
                } else {
                    false
                }
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

class TabsWithGroupsAdapter(
    private val onTabClick: (String) -> Unit,
    private val onTabClose: (String) -> Unit,
    private val onGroupClick: (String) -> Unit,
    private val onGroupMoreClick: (String, View) -> Unit,
    private val onTabLongPress: (String, View) -> Boolean,
    private val onGroupTabLongPress: (String, String, View) -> Boolean = { _, _, _ -> false } // tabId, groupId, view
) : ListAdapter<TabItem, RecyclerView.ViewHolder>(TabItemDiffCallback()) {

    private var selectedTabId: String? = null
    private val expandedGroups = mutableSetOf<String>()

    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_SINGLE_TAB = 1
    }

    fun updateItems(items: List<TabItem>, selectedId: String?) {
        selectedTabId = selectedId
        submitList(items)
    }

    fun toggleGroup(groupId: String) {
        if (expandedGroups.contains(groupId)) {
            expandedGroups.remove(groupId)
        } else {
            expandedGroups.add(groupId)
        }
        notifyDataSetChanged()
    }

    fun expandGroup(groupId: String) {
        if (!expandedGroups.contains(groupId)) {
            expandedGroups.add(groupId)
            notifyDataSetChanged()
        }
    }

    fun collapseGroup(groupId: String) {
        if (expandedGroups.contains(groupId)) {
            expandedGroups.remove(groupId)
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TabItem.Group -> VIEW_TYPE_GROUP
            is TabItem.SingleTab -> VIEW_TYPE_SINGLE_TAB
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_tab_group, parent, false)
                GroupViewHolder(view as MaterialCardView)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_tab_card, parent, false)
                SingleTabViewHolder(view as MaterialCardView)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TabItem.Group -> {
                (holder as GroupViewHolder).bind(
                    item,
                    expandedGroups.contains(item.groupId),
                    selectedTabId
                )
            }
            is TabItem.SingleTab -> {
                (holder as SingleTabViewHolder).bind(item.tab, selectedTabId)
            }
        }
    }

    inner class GroupViewHolder(private val cardView: MaterialCardView) : RecyclerView.ViewHolder(cardView) {
        private val expandIcon: ImageView = cardView.findViewById(R.id.expandIcon)
        private val colorStripe: View = cardView.findViewById(R.id.colorStripe)
        private val groupName: TextView = cardView.findViewById(R.id.groupName)
        private val tabCount: TextView = cardView.findViewById(R.id.tabCount)
        private val moreButton: ImageView = cardView.findViewById(R.id.moreButton)
        private val groupHeader: View = cardView.findViewById(R.id.groupHeader)
        private val tabsRecyclerView: RecyclerView = cardView.findViewById(R.id.groupTabsRecyclerView)

        fun bind(group: TabItem.Group, isExpanded: Boolean, selectedId: String?) {
            groupName.text = if (group.name.isBlank()) "" else group.name
            tabCount.text = "${group.tabs.size} tab${if (group.tabs.size != 1) "s" else ""}"
            
            colorStripe.setBackgroundColor(group.color)

            expandIcon.animate().rotation(if (isExpanded) 180f else 0f).setDuration(200).start()

            if (isExpanded) {
                tabsRecyclerView.visibility = View.VISIBLE
                setupGroupTabs(tabsRecyclerView, group.tabs, group.groupId, selectedId)
            } else {
                tabsRecyclerView.visibility = View.GONE
            }

            groupHeader.setOnClickListener {
                onGroupClick(group.groupId)
            }

            moreButton.setOnClickListener {
                onGroupMoreClick(group.groupId, it)
            }
        }

        private fun setupGroupTabs(recyclerView: RecyclerView, tabs: List<TabSessionState>, groupId: String, selectedId: String?) {
            val adapter = GroupTabsAdapter(
                onTabClick = onTabClick,
                onTabClose = onTabClose,
                onTabLongPress = { tabId, view -> onGroupTabLongPress(tabId, groupId, view) }
            )
            recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
            recyclerView.adapter = adapter
            adapter.updateTabs(tabs, selectedId)
        }
    }

    inner class SingleTabViewHolder(private val cardView: MaterialCardView) : RecyclerView.ViewHolder(cardView) {
        private val favicon: ImageView = cardView.findViewById(R.id.favicon)
        private val tabTitle: TextView = cardView.findViewById(R.id.tabTitle)
        private val tabUrl: TextView = cardView.findViewById(R.id.tabUrl)
        private val closeButton: ImageView = cardView.findViewById(R.id.closeButton)

        fun bind(tab: TabSessionState, selectedId: String?) {
            cardView.isSelected = tab.id == selectedId

            val title = tab.content.title.ifBlank { "New Tab" }
            tabTitle.text = title
            tabUrl.text = tab.content.url

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

    private class TabItemDiffCallback : DiffUtil.ItemCallback<TabItem>() {
        override fun areItemsTheSame(oldItem: TabItem, newItem: TabItem): Boolean {
            return when {
                oldItem is TabItem.Group && newItem is TabItem.Group -> oldItem.groupId == newItem.groupId
                oldItem is TabItem.SingleTab && newItem is TabItem.SingleTab -> oldItem.tab.id == newItem.tab.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TabItem, newItem: TabItem): Boolean {
            return oldItem == newItem
        }
    }
}
