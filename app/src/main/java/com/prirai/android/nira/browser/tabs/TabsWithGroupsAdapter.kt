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
    private val onTabLongPress: (String, View) -> Boolean,
    private val groupColor: Int
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
            val isSelected = tab.id == selectedId
            cardView.isSelected = isSelected

            val title = tab.content.title.ifBlank { "New Tab" }
            tabTitle.text = title
            tabUrl.text = tab.content.url
            
            // Highlight selected tab with theme-respectful stroke (not fill)
            if (isSelected) {
                val strokeWidth = (2 * cardView.context.resources.displayMetrics.density).toInt()
                cardView.strokeWidth = strokeWidth
                cardView.strokeColor = groupColor
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
            
            // Apply corner radius based on position - using dimen resource for consistency
            val cornerRadius = cardView.context.resources.getDimension(R.dimen.search_result_corner_radius)
            cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(if (isFirst) cornerRadius else 0f)
                .setTopRightCornerSize(if (isFirst) cornerRadius else 0f)
                .setBottomLeftCornerSize(if (isLast) cornerRadius else 0f)
                .setBottomRightCornerSize(if (isLast) cornerRadius else 0f)
                .build()
            
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
                                v.performClick()
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
    private val context: android.content.Context,
    private val onTabClick: (String) -> Unit,
    private val onTabClose: (String) -> Unit,
    private val onGroupClick: (String) -> Unit,
    private val onGroupMoreClick: (String, View) -> Unit,
    private val onTabLongPress: (String, View) -> Boolean,
    private val onGroupTabLongPress: (String, String, View) -> Boolean = { _, _, _ -> false } // tabId, groupId, view
) : ListAdapter<TabItem, RecyclerView.ViewHolder>(TabItemDiffCallback()) {

    private var selectedTabId: String? = null
    private val collapsedGroups = mutableSetOf<String>()
    private val prefs = context.getSharedPreferences("tab_groups_prefs", android.content.Context.MODE_PRIVATE)
    
    init {
        // Load collapsed groups from preferences
        val savedCollapsed = prefs.getStringSet("collapsed_groups", emptySet()) ?: emptySet()
        collapsedGroups.addAll(savedCollapsed)
    }
    
    private fun saveCollapsedState() {
        prefs.edit().putStringSet("collapsed_groups", collapsedGroups).apply()
    }

    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_SINGLE_TAB = 1
    }

    fun updateItems(items: List<TabItem>, selectedId: String?) {
        selectedTabId = selectedId
        submitList(items)
    }

    fun toggleGroup(groupId: String) {
        if (collapsedGroups.contains(groupId)) {
            collapsedGroups.remove(groupId)
        } else {
            collapsedGroups.add(groupId)
        }
        saveCollapsedState()
        notifyDataSetChanged()
    }

    fun expandGroup(groupId: String) {
        if (collapsedGroups.contains(groupId)) {
            collapsedGroups.remove(groupId)
            saveCollapsedState()
            notifyDataSetChanged()
        }
    }

    fun collapseGroup(groupId: String) {
        if (!collapsedGroups.contains(groupId)) {
            collapsedGroups.add(groupId)
            saveCollapsedState()
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
                    !collapsedGroups.contains(item.groupId),
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
            tabCount.text = "${group.tabs.size}" // Just the number
            
            colorStripe.setBackgroundColor(group.color)
            
            // Apply subtle fill color like in tab pill bar
            val isDark = isDarkMode()
            val backgroundColor = if (isDark) {
                adjustColorForDarkMode(group.color)
            } else {
                adjustColorForLightMode(group.color)
            }
            
            cardView.setCardBackgroundColor(backgroundColor)
            
            // Adjust text colors based on background
            val textColor = if (isDark) {
                0xFFE0E0E0.toInt()
            } else {
                0xFF424242.toInt()
            }
            groupName.setTextColor(textColor)
            tabCount.setTextColor(textColor)

            expandIcon.animate().rotation(if (isExpanded) 180f else 0f).setDuration(200).start()
            expandIcon.setColorFilter(textColor)

            if (isExpanded) {
                tabsRecyclerView.visibility = View.VISIBLE
                setupGroupTabs(tabsRecyclerView, group.tabs, group.groupId, group.color, selectedId)
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
        
        private fun isDarkMode(): Boolean {
            return when (cardView.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
                else -> false
            }
        }
        
        private fun adjustColorForDarkMode(color: Int): Int {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color, hsv)
            
            // Reduce brightness significantly for dark mode
            hsv[2] = (hsv[2] * 0.3f).coerceIn(0.2f, 0.4f)
            // Increase saturation slightly
            hsv[1] = (hsv[1] * 1.2f).coerceAtMost(1f)
            
            return android.graphics.Color.HSVToColor(hsv)
        }
        
        private fun adjustColorForLightMode(color: Int): Int {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color, hsv)
            
            // Increase brightness for light mode (pastel colors)
            hsv[2] = (hsv[2] * 1.2f).coerceIn(0.85f, 0.95f)
            // Decrease saturation for softer appearance
            hsv[1] = (hsv[1] * 0.6f).coerceIn(0.3f, 0.7f)
            
            return android.graphics.Color.HSVToColor(hsv)
        }

        private fun setupGroupTabs(recyclerView: RecyclerView, tabs: List<TabSessionState>, groupId: String, groupColor: Int, selectedId: String?) {
            val adapter = GroupTabsAdapter(
                onTabClick = onTabClick,
                onTabClose = onTabClose,
                onTabLongPress = { tabId, view -> onGroupTabLongPress(tabId, groupId, view) },
                groupColor = groupColor
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
            val isSelected = tab.id == selectedId
            cardView.isSelected = isSelected

            val title = tab.content.title.ifBlank { "New Tab" }
            tabTitle.text = title
            tabUrl.text = tab.content.url
            
            // Highlight selected tab with theme-respectful stroke - use primary color for ungrouped tabs
            if (isSelected) {
                val strokeWidth = (2 * cardView.context.resources.displayMetrics.density).toInt()
                cardView.strokeWidth = strokeWidth
                cardView.strokeColor = androidx.core.content.ContextCompat.getColor(
                    cardView.context,
                    R.color.chip_stroke_color_themed
                )
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
