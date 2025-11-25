package com.prirai.android.nira.browser.tabs

import android.content.Context
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.tabgroups.TabGroupWithTabs
import com.prirai.android.nira.databinding.ItemTabGroupHeaderBinding
import com.prirai.android.nira.databinding.ItemTabInListBinding
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.base.images.ImageLoader
import mozilla.components.concept.base.images.ImageLoadRequest
import mozilla.components.support.ktx.util.URLStringUtils

/**
 * Adapter for displaying tabs with group support in a single column layout
 * Supports collapsing/expanding groups and drag-and-drop between groups
 */
class TabsWithGroupsAdapter(
    private val context: Context,
    private val thumbnailLoader: ImageLoader,
    private val onTabClick: (String) -> Unit,
    private val onTabClose: (String) -> Unit,
    private val onGroupExpand: (String) -> Unit,
    private val onTabMovedToGroup: (String, String?) -> Unit,
    private val onTabRemovedFromGroup: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ListItem>()
    private val expandedGroups = mutableSetOf<String>()
    private var selectedTabId: String? = null
    private var draggedItem: ListItem.TabItem? = null
    private var allTabs = listOf<TabSessionState>()

    companion object {
        private const val VIEW_TYPE_GROUP_HEADER = 0
        private const val VIEW_TYPE_TAB = 1
        private const val VIEW_TYPE_UNGROUPED_HEADER = 2
    }

    sealed class ListItem {
        data class GroupHeader(val group: TabGroupWithTabs, val isExpanded: Boolean) : ListItem()
        data class TabItem(val tab: TabSessionState, val groupId: String?) : ListItem()
        object UngroupedHeader : ListItem()
    }

    fun updateData(
        groups: List<TabGroupWithTabs>,
        ungroupedTabs: List<TabSessionState>,
        selectedTabId: String?
    ) {
        this.selectedTabId = selectedTabId
        this.allTabs = ungroupedTabs + groups.flatMap { group ->
            group.tabIds.mapNotNull { tabId ->
                context.components.store.state.tabs.find { it.id == tabId }
            }
        }
        items.clear()

        // Add grouped tabs
        for (group in groups) {
            val isExpanded = expandedGroups.contains(group.group.id)
            items.add(ListItem.GroupHeader(group, isExpanded))

            if (isExpanded) {
                for (tabId in group.tabIds) {
                    // Find the actual tab session
                    val tab = getTabById(tabId)
                    if (tab != null) {
                        items.add(ListItem.TabItem(tab, group.group.id))
                    }
                }
            }
        }

        // Add ungrouped tabs section if there are any
        if (ungroupedTabs.isNotEmpty()) {
            items.add(ListItem.UngroupedHeader)
            for (tab in ungroupedTabs) {
                items.add(ListItem.TabItem(tab, null))
            }
        }

        notifyDataSetChanged()
    }

    private fun getTabById(tabId: String): TabSessionState? {
        return context.components.store.state.tabs.find { it.id == tabId }
    }

    fun toggleGroupExpanded(groupId: String) {
        if (expandedGroups.contains(groupId)) {
            expandedGroups.remove(groupId)
        } else {
            expandedGroups.add(groupId)
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListItem.GroupHeader -> VIEW_TYPE_GROUP_HEADER
        is ListItem.TabItem -> VIEW_TYPE_TAB
        is ListItem.UngroupedHeader -> VIEW_TYPE_UNGROUPED_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP_HEADER -> {
                val binding = ItemTabGroupHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                GroupHeaderViewHolder(binding)
            }

            VIEW_TYPE_TAB -> {
                val binding = ItemTabInListBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                TabViewHolder(binding)
            }

            VIEW_TYPE_UNGROUPED_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(
                    R.layout.item_ungrouped_tabs_header,
                    parent,
                    false
                )
                UngroupedHeaderViewHolder(view)
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.GroupHeader -> (holder as GroupHeaderViewHolder).bind(item)
            is ListItem.TabItem -> (holder as TabViewHolder).bind(item)
            is ListItem.UngroupedHeader -> (holder as UngroupedHeaderViewHolder).bind()
        }
    }

    override fun getItemCount() = items.size

    fun getItem(position: Int): ListItem? = items.getOrNull(position)

    fun setDraggedItem(item: ListItem.TabItem?) {
        draggedItem = item
    }

    fun getDraggedItem() = draggedItem

    inner class GroupHeaderViewHolder(
        private val binding: ItemTabGroupHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ListItem.GroupHeader) {
            val group = item.group
            val tabCount = group.tabCount

            // Set group name or show tab count if no name
            binding.groupName.text = if (group.group.name.isNotBlank()) {
                group.group.name
            } else {
                "${tabCount} tabs"
            }

            // Set expand/collapse icon
            binding.expandIcon.rotation = if (item.isExpanded) 180f else 0f

            // Show tab count
            binding.tabCount.text = "$tabCount"
            binding.tabCount.isVisible = true

            // Handle click to expand/collapse
            binding.root.setOnClickListener {
                onGroupExpand(group.group.id)
            }
        }
    }

    inner class TabViewHolder(
        private val binding: ItemTabInListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ListItem.TabItem) {
            val tab = item.tab
            val isSelected = tab.id == selectedTabId

            // Set tab title
            // Show title if available, only show "Loading..." when actively loading a real URL
            val isRealUrl = tab.content.url.isNotBlank() &&
                    !tab.content.url.startsWith("about:")
            binding.tabTitle.text = when {
                tab.content.title.isNotBlank() -> tab.content.title
                tab.content.loading && isRealUrl -> "Loading..."
                tab.content.url.isNotBlank() && !tab.content.url.startsWith("about:") -> URLStringUtils.toDisplayUrl(tab.content.url)
                else -> "New Tab"
            }

            // Set URL
            binding.tabUrl.text = URLStringUtils.toDisplayUrl(tab.content.url)
            binding.tabUrl.isVisible = true

            // Load favicon or thumbnail
            loadTabIcon(tab)

            // Highlight selected tab
            binding.root.alpha = if (isSelected) 1.0f else 0.7f
            binding.selectedIndicator.isVisible = isSelected

            // Show group indicator if tab is in a group
            binding.groupIndicator.isVisible = item.groupId != null

            // Click to select tab
            binding.root.setOnClickListener {
                onTabClick(tab.id)
            }

            // Close button
            binding.closeButton.setOnClickListener {
                onTabClose(tab.id)
            }
        }

        private fun loadTabIcon(tab: TabSessionState) {
            val iconSize = binding.tabIcon.resources.getDimensionPixelSize(R.dimen.tab_icon_size)

            // Try to load thumbnail first
            thumbnailLoader.loadIntoView(
                binding.tabIcon,
                ImageLoadRequest(tab.id, iconSize, isPrivate = tab.content.private)
            )
        }
    }

    inner class UngroupedHeaderViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.header_title)

        fun bind() {
            titleText.text = itemView.context.getString(R.string.ungrouped_tabs)
        }
    }
}

/**
 * ItemTouchHelper callback for drag-and-drop functionality
 */
class TabDragCallback(
    private val adapter: TabsWithGroupsAdapter
) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // Only allow dragging for tab items
        val item = adapter.getItem(viewHolder.bindingAdapterPosition)
        if (item !is TabsWithGroupsAdapter.ListItem.TabItem) {
            return makeMovementFlags(0, 0)
        }

        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // Handle reordering within the same group or moving between groups
        val fromPosition = viewHolder.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition

        val fromItem = adapter.getItem(fromPosition)
        val toItem = adapter.getItem(toPosition)

        if (fromItem !is TabsWithGroupsAdapter.ListItem.TabItem) {
            return false
        }

        when (toItem) {
            is TabsWithGroupsAdapter.ListItem.TabItem -> {
                // Moving to another tab position
                if (fromItem.groupId != toItem.groupId) {
                    // Moving between groups or from ungrouped to grouped
                    // This will be handled on drop
                }
                return true
            }

            is TabsWithGroupsAdapter.ListItem.GroupHeader -> {
                // Dropped on a group header - add to that group
                return true
            }

            is TabsWithGroupsAdapter.ListItem.UngroupedHeader -> {
                // Dropped on ungrouped header - remove from group
                return true
            }

            else -> return false
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used - we don't support swipe to dismiss in this implementation
    }

    override fun isLongPressDragEnabled() = true

    override fun isItemViewSwipeEnabled() = false

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)

        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            val item = adapter.getItem(viewHolder.bindingAdapterPosition)
            if (item is TabsWithGroupsAdapter.ListItem.TabItem) {
                adapter.setDraggedItem(item)
                viewHolder.itemView.alpha = 0.5f
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1.0f

        val draggedItem = adapter.getDraggedItem()
        if (draggedItem != null) {
            val targetPosition = viewHolder.bindingAdapterPosition
            val targetItem = adapter.getItem(targetPosition)

            when (targetItem) {
                is TabsWithGroupsAdapter.ListItem.GroupHeader -> {
                    // Add to group - callback to handle in fragment
                }

                is TabsWithGroupsAdapter.ListItem.UngroupedHeader -> {
                    // Remove from group - callback to handle in fragment
                }

                is TabsWithGroupsAdapter.ListItem.TabItem -> {
                    // Reordered within same group or moved to different group
                }

                else -> {
                    // No action
                }
            }

            adapter.setDraggedItem(null)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            // Add elevation and scale effect during drag
            viewHolder.itemView.translationY = dY
            if (isCurrentlyActive) {
                viewHolder.itemView.scaleX = 1.05f
                viewHolder.itemView.scaleY = 1.05f
                viewHolder.itemView.elevation = 8f
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
}
