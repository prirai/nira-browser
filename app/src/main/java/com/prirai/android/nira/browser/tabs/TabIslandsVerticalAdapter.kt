package com.prirai.android.nira.browser.tabs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.components.toolbar.modern.TabIsland
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.concept.base.images.ImageLoadRequest
import mozilla.components.support.ktx.util.URLStringUtils

/**
 * Adapter for displaying tab islands vertically in bottom sheet
 * Similar to horizontal toolbar islands but stacked vertically
 */
class TabIslandsVerticalAdapter(
    private val context: Context,
    private val onTabClick: (String) -> Unit,
    private val onTabClose: (String) -> Unit,
    private val onIslandHeaderClick: (String) -> Unit,
    private val onIslandLongPress: (String) -> Unit,
    private val onUngroupedTabLongPress: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ListItem>()
    private var selectedTabId: String? = null
    private var allTabs = listOf<TabSessionState>()
    private val thumbnailLoader = ThumbnailLoader(context.components.thumbnailStorage)

    companion object {
        private const val VIEW_TYPE_ISLAND_COLLAPSED = 0
        private const val VIEW_TYPE_ISLAND_EXPANDED_HEADER = 1
        private const val VIEW_TYPE_TAB_IN_ISLAND = 2
        private const val VIEW_TYPE_ISLAND_BOTTOM_CAP = 3
        private const val VIEW_TYPE_TAB_UNGROUPED = 4
        private const val VIEW_TYPE_UNGROUPED_HEADER = 5
    }

    sealed class ListItem {
        data class CollapsedIsland(val island: TabIsland, val tabCount: Int) : ListItem()
        data class ExpandedIslandHeader(val island: TabIsland, val tabCount: Int) : ListItem()
        data class TabInIsland(val tab: TabSessionState, val island: TabIsland, val isFirst: Boolean) : ListItem()
        data class IslandBottomCap(val island: TabIsland) : ListItem()
        data class UngroupedTab(val tab: TabSessionState) : ListItem()
        object UngroupedHeader : ListItem()
    }

    fun updateData(
        islands: List<TabIsland>,
        ungroupedTabs: List<TabSessionState>,
        allTabs: List<TabSessionState>,
        selectedTabId: String?
    ) {
        this.selectedTabId = selectedTabId
        this.allTabs = allTabs
        items.clear()

        // Add islands
        for (island in islands) {
            val tabsInIsland = allTabs.filter { island.tabIds.contains(it.id) }
            val tabCount = tabsInIsland.size

            if (island.isCollapsed) {
                // Show collapsed island
                items.add(ListItem.CollapsedIsland(island, tabCount))
            } else {
                // Show expanded island with header, tabs, and bottom cap
                items.add(ListItem.ExpandedIslandHeader(island, tabCount))
                tabsInIsland.forEachIndexed { index, tab ->
                    items.add(ListItem.TabInIsland(tab, island, isFirst = index == 0))
                }
                items.add(ListItem.IslandBottomCap(island))
            }
        }

        // Add ungrouped tabs
        if (ungroupedTabs.isNotEmpty()) {
            items.add(ListItem.UngroupedHeader)
            for (tab in ungroupedTabs) {
                items.add(ListItem.UngroupedTab(tab))
            }
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListItem.CollapsedIsland -> VIEW_TYPE_ISLAND_COLLAPSED
        is ListItem.ExpandedIslandHeader -> VIEW_TYPE_ISLAND_EXPANDED_HEADER
        is ListItem.TabInIsland -> VIEW_TYPE_TAB_IN_ISLAND
        is ListItem.IslandBottomCap -> VIEW_TYPE_ISLAND_BOTTOM_CAP
        is ListItem.UngroupedTab -> VIEW_TYPE_TAB_UNGROUPED
        is ListItem.UngroupedHeader -> VIEW_TYPE_UNGROUPED_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ISLAND_COLLAPSED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_collapsed_island_vertical, parent, false)
                CollapsedIslandViewHolder(view)
            }

            VIEW_TYPE_ISLAND_EXPANDED_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_expanded_island_header_vertical, parent, false)
                ExpandedIslandHeaderViewHolder(view)
            }

            VIEW_TYPE_TAB_IN_ISLAND -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_tab_in_island, parent, false)
                TabInIslandViewHolder(view)
            }

            VIEW_TYPE_ISLAND_BOTTOM_CAP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_island_bottom_cap, parent, false)
                IslandBottomCapViewHolder(view)
            }

            VIEW_TYPE_TAB_UNGROUPED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_tab_in_list, parent, false)
                TabViewHolder(view)
            }

            VIEW_TYPE_UNGROUPED_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_ungrouped_tabs_header, parent, false)
                UngroupedHeaderViewHolder(view)
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.CollapsedIsland -> (holder as CollapsedIslandViewHolder).bind(item)
            is ListItem.ExpandedIslandHeader -> (holder as ExpandedIslandHeaderViewHolder).bind(item)
            is ListItem.TabInIsland -> (holder as TabInIslandViewHolder).bind(item.tab, item.island, item.isFirst)
            is ListItem.IslandBottomCap -> (holder as IslandBottomCapViewHolder).bind()
            is ListItem.UngroupedTab -> (holder as TabViewHolder).bind(item.tab, null)
            is ListItem.UngroupedHeader -> (holder as UngroupedHeaderViewHolder).bind()
        }
    }

    override fun getItemCount() = items.size

    /**
     * Get item at position for drag-and-drop operations
     */
    fun getItemAt(position: Int): ListItem? {
        return if (position in 0 until items.size) {
            items[position]
        } else {
            null
        }
    }

    /**
     * Find the position of a tab by its ID
     */
    fun findPositionOfTab(tabId: String): Int {
        items.forEachIndexed { index, item ->
            when (item) {
                is ListItem.TabInIsland -> if (item.tab.id == tabId) return index
                is ListItem.UngroupedTab -> if (item.tab.id == tabId) return index
                else -> {}
            }
        }
        return -1
    }

    // Collapsed Island ViewHolder
    inner class CollapsedIslandViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.collapsed_island_name)
        private val tabCountText: TextView = itemView.findViewById(R.id.collapsed_island_tab_count)
        private val colorIndicator: View = itemView.findViewById(R.id.collapsed_island_color)
        private val containerCard: CardView = itemView.findViewById(R.id.collapsed_island_card)

        fun bind(item: ListItem.CollapsedIsland) {
            val island = item.island
            val displayName = if (island.name.isNotBlank()) {
                island.name
            } else {
                "${item.tabCount} tabs"
            }
            nameText.text = displayName
            tabCountText.text = item.tabCount.toString()
            colorIndicator.setBackgroundColor(island.color)

            // Accessibility
            containerCard.contentDescription = context.getString(
                R.string.collapsed_island_description,
                displayName,
                item.tabCount
            )

            containerCard.setOnClickListener {
                onIslandHeaderClick(island.id)
            }

            containerCard.setOnLongClickListener {
                onIslandLongPress(island.id)
                true
            }
        }
    }

    // Expanded Island Header ViewHolder
    inner class ExpandedIslandHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.island_name)
        private val tabCountText: TextView = itemView.findViewById(R.id.island_tab_count)
        private val colorIndicator: View = itemView.findViewById(R.id.island_color)
        private val collapseButton: ImageView = itemView.findViewById(R.id.island_collapse_button)

        fun bind(item: ListItem.ExpandedIslandHeader) {
            val island = item.island
            val displayName = if (island.name.isNotBlank()) {
                island.name
            } else {
                "${item.tabCount} tabs"
            }
            nameText.text = displayName
            tabCountText.text = item.tabCount.toString()
            colorIndicator.setBackgroundColor(island.color)
            collapseButton.rotation = 180f // Down arrow for expanded state

            // Accessibility
            itemView.contentDescription = context.getString(
                R.string.expanded_island_description,
                displayName,
                item.tabCount
            )
            collapseButton.contentDescription = context.getString(R.string.collapse_island_button)

            itemView.setOnClickListener {
                onIslandHeaderClick(island.id)
            }

            itemView.setOnLongClickListener {
                onIslandLongPress(island.id)
                true
            }
        }
    }

    // Tab In Island ViewHolder
    inner class TabInIslandViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tabTitle: TextView = itemView.findViewById(R.id.tab_title)
        private val tabUrl: TextView = itemView.findViewById(R.id.tab_url)
        private val tabIcon: ImageView = itemView.findViewById(R.id.tab_icon)
        private val closeButton: ImageView = itemView.findViewById(R.id.close_button)
        private val selectedIndicator: View = itemView.findViewById(R.id.selected_indicator)
        private val divider: View = itemView.findViewById(R.id.divider)

        fun bind(tab: TabSessionState, island: TabIsland, isFirst: Boolean) {
            val isSelected = tab.id == selectedTabId

            // Hide divider for first tab
            divider.visibility = if (isFirst) View.GONE else View.VISIBLE

            // Set tab title
            // Show title if available, only show "Loading..." when actively loading a real URL
            val isRealUrl = tab.content.url.isNotBlank() &&
                    !tab.content.url.startsWith("about:")
            val displayTitle = when {
                tab.content.title.isNotBlank() -> tab.content.title
                tab.content.loading && isRealUrl -> "Loading..."
                tab.content.url.isNotBlank() && !tab.content.url.startsWith("about:") -> URLStringUtils.toDisplayUrl(tab.content.url)
                else -> "New Tab"
            }
            tabTitle.text = displayTitle

            // Set URL
            val displayUrl = URLStringUtils.toDisplayUrl(tab.content.url)
            tabUrl.text = displayUrl
            tabUrl.isVisible = true

            // Load favicon/thumbnail
            val iconSize = itemView.resources.getDimensionPixelSize(R.dimen.tab_icon_size)
            thumbnailLoader.loadIntoView(
                tabIcon,
                ImageLoadRequest(tab.id, iconSize, isPrivate = tab.content.private)
            )

            // Highlight selected tab
            itemView.alpha = if (isSelected) 1.0f else 0.7f
            if (isSelected) {
                selectedIndicator.setBackgroundResource(R.drawable.selected_tab_highlight_background)
                tabTitle.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                selectedIndicator.background = null
                tabTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            // Accessibility
            val islandInfo = if (island.name.isNotBlank()) {
                context.getString(R.string.tab_in_island_description, island.name)
            } else ""
            itemView.contentDescription = if (isSelected) {
                context.getString(R.string.selected_tab_description, displayTitle, displayUrl, islandInfo)
            } else {
                context.getString(R.string.tab_description, displayTitle, displayUrl, islandInfo)
            }
            closeButton.contentDescription = context.getString(R.string.close_tab_button, displayTitle)

            // Click handlers
            itemView.setOnClickListener {
                onTabClick(tab.id)
            }

            closeButton.setOnClickListener {
                onTabClose(tab.id)
            }
        }
    }

    // Island Bottom Cap ViewHolder
    class IslandBottomCapViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind() {
            // Nothing to bind, just visual element
        }
    }

    // Tab ViewHolder (for ungrouped tabs)
    inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tabTitle: TextView = itemView.findViewById(R.id.tab_title)
        private val tabUrl: TextView = itemView.findViewById(R.id.tab_url)
        private val tabIcon: ImageView = itemView.findViewById(R.id.tab_icon)
        private val closeButton: ImageView = itemView.findViewById(R.id.close_button)
        private val selectedIndicator: View = itemView.findViewById(R.id.selected_indicator)
        private val groupIndicator: View? = itemView.findViewById(R.id.group_indicator)

        fun bind(tab: TabSessionState, island: TabIsland?) {
            val isSelected = tab.id == selectedTabId

            // Set tab title
            // Show title if available, only show "Loading..." when actively loading a real URL
            val isRealUrl = tab.content.url.isNotBlank() &&
                    !tab.content.url.startsWith("about:")
            val displayTitle = when {
                tab.content.title.isNotBlank() -> tab.content.title
                tab.content.loading && isRealUrl -> "Loading..."
                tab.content.url.isNotBlank() && !tab.content.url.startsWith("about:") -> URLStringUtils.toDisplayUrl(tab.content.url)
                else -> "New Tab"
            }
            tabTitle.text = displayTitle

            // Set URL
            val displayUrl = URLStringUtils.toDisplayUrl(tab.content.url)
            tabUrl.text = displayUrl
            tabUrl.isVisible = true

            // Load favicon/thumbnail
            val iconSize = itemView.resources.getDimensionPixelSize(R.dimen.tab_icon_size)
            thumbnailLoader.loadIntoView(
                tabIcon,
                ImageLoadRequest(tab.id, iconSize, isPrivate = tab.content.private)
            )

            // Highlight selected tab
            itemView.alpha = if (isSelected) 1.0f else 0.7f
            if (isSelected) {
                selectedIndicator.setBackgroundResource(R.drawable.selected_tab_highlight_background)
                tabTitle.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                selectedIndicator.background = null
                tabTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            // Hide group indicator for ungrouped tabs
            groupIndicator?.isVisible = false

            // Accessibility
            val islandInfo = if (island != null && island.name.isNotBlank()) {
                context.getString(R.string.tab_in_island_description, island.name)
            } else ""
            itemView.contentDescription = if (isSelected) {
                context.getString(R.string.selected_tab_description, displayTitle, displayUrl, islandInfo)
            } else {
                context.getString(R.string.tab_description, displayTitle, displayUrl, islandInfo)
            }
            closeButton.contentDescription = context.getString(R.string.close_tab_button, displayTitle)

            // Click handlers
            itemView.setOnClickListener {
                onTabClick(tab.id)
            }

            closeButton.setOnClickListener {
                onTabClose(tab.id)
            }

            // Add touch handling for drag vs long press
            if (island == null) {
                var startTime: Long = 0
                var startX: Float = 0f
                var startY: Float = 0f
                val longPressThreshold = 500L // milliseconds
                val moveThreshold = 20f // pixels

                itemView.setOnTouchListener { view, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            startTime = System.currentTimeMillis()
                            startX = event.x
                            startY = event.y
                            false // Allow drag to take precedence
                        }

                        android.view.MotionEvent.ACTION_MOVE -> {
                            val deltaX = kotlin.math.abs(event.x - startX)
                            val deltaY = kotlin.math.abs(event.y - startY)
                            // If moved more than threshold, cancel long press
                            if (deltaX > moveThreshold || deltaY > moveThreshold) {
                                return@setOnTouchListener false // Let drag handle it
                            }
                            false
                        }

                        android.view.MotionEvent.ACTION_UP -> {
                            val duration = System.currentTimeMillis() - startTime
                            val deltaX = kotlin.math.abs(event.x - startX)
                            val deltaY = kotlin.math.abs(event.y - startY)

                            // Only trigger long press if held long enough without significant movement
                            if (duration >= longPressThreshold && deltaX < moveThreshold && deltaY < moveThreshold) {
                                onUngroupedTabLongPress(tab.id)
                                return@setOnTouchListener true
                            }
                            false
                        }

                        else -> false
                    }
                }
            }
        }
    }

    // Ungrouped Header ViewHolder
    class UngroupedHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.header_title)

        fun bind() {
            titleText.text = itemView.context.getString(R.string.ungrouped_tabs)
            titleText.setTextColor(itemView.context.getColor(android.R.color.white))
            // Accessibility
            itemView.contentDescription = itemView.context.getString(R.string.ungrouped_tabs_header_description)
        }

        fun setDragMode(isDragging: Boolean) {
            if (isDragging) {
                titleText.text = "UNGROUP"
                titleText.setTextColor(itemView.context.getColor(android.R.color.holo_red_light))
            } else {
                titleText.text = itemView.context.getString(R.string.ungrouped_tabs)
                titleText.setTextColor(itemView.context.getColor(android.R.color.white))
            }
        }
    }
}
