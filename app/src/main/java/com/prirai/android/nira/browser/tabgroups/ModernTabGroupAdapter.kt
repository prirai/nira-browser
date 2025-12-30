package com.prirai.android.nira.browser.tabgroups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.ModernTabGroupItemBinding
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Modern adapter for displaying tab groups with improved grouping behavior.
 * Includes a '+' button to add new tabs to the current group.
 */
class ModernTabGroupAdapter(
    private val onTabClick: (String) -> Unit,
    private val onAddTabToGroup: (String?) -> Unit  // groupId, null for ungrouped
) : RecyclerView.Adapter<ModernTabGroupAdapter.TabGroupViewHolder>() {

    private var currentGroupWithTabs: TabGroupWithTabs? = null
    private var selectedTabId: String? = null

    fun updateCurrentGroup(groupWithTabs: TabGroupWithTabs?, selectedTabId: String?) {
        if (this.currentGroupWithTabs != groupWithTabs || this.selectedTabId != selectedTabId) {
            this.currentGroupWithTabs = groupWithTabs
            this.selectedTabId = selectedTabId
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabGroupViewHolder {
        val binding = ModernTabGroupItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TabGroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabGroupViewHolder, position: Int) {
        currentGroupWithTabs?.let { groupWithTabs ->
            holder.bind(groupWithTabs, selectedTabId, onTabClick, onAddTabToGroup)
        }
    }

    override fun getItemCount() = if (currentGroupWithTabs != null) 1 else 0

    class TabGroupViewHolder(
        private val binding: ModernTabGroupItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            groupWithTabs: TabGroupWithTabs,
            selectedTabId: String?,
            onTabClick: (String) -> Unit,
            onAddTabToGroup: (String?) -> Unit
        ) {
            val faviconContainer = binding.faviconContainer
            faviconContainer.removeAllViews()

            val context = binding.root.context
            val store = context.components.store
            val faviconCache = context.components.faviconCache

            // Create tab pills for each tab in the group
            CoroutineScope(Dispatchers.Main).launch {
                groupWithTabs.tabIds.forEach { tabId ->
                    val tab = store.state.tabs.find { it.id == tabId }
                    if (tab != null) {
                        createModernTabPill(
                            faviconContainer,
                            tab,
                            tabId == selectedTabId,
                            faviconCache,
                            onTabClick,
                            context
                        )
                    }
                }
            }

            // Setup add button - passes the group ID (or null for ungrouped)
            val groupId = if (groupWithTabs.group.id == "ungrouped") null else groupWithTabs.group.id
            binding.addTabButton.setOnClickListener {
                onAddTabToGroup(groupId)
            }
        }

        private fun createModernTabPill(
            container: LinearLayout,
            tab: TabSessionState,
            isSelected: Boolean,
            faviconCache: com.prirai.android.nira.utils.FaviconCache,
            onTabClick: (String) -> Unit,
            context: android.content.Context
        ) {
            val pillView = LayoutInflater.from(context).inflate(
                R.layout.modern_tab_pill_item, container, false
            ) as MaterialCardView

            val faviconImage = pillView.findViewById<ImageView>(R.id.faviconImage)
            val tabTitle = pillView.findViewById<android.widget.TextView>(R.id.tabTitle)
            val closeButton = pillView.findViewById<ImageView>(R.id.closeButton)

            // Set selection state with modern styling
            pillView.isSelected = isSelected
            if (isSelected) {
                pillView.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.tab_pill_selected_stroke)
            } else {
                pillView.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.tab_pill_stroke)
            }

            // Set tab title with better truncation
            val title = tab.content.title.ifBlank { 
                val url = tab.content.url
                when {
                    url.startsWith("https://") -> url.removePrefix("https://")
                    url.startsWith("http://") -> url.removePrefix("http://")
                    else -> url
                }.split("/").first()
            }
            tabTitle.text = if (title.length > 25) {
                title.substring(0, 22) + "..."
            } else {
                title
            }

            // Set favicon with better fallback
            if (tab.content.icon != null) {
                faviconImage.setImageBitmap(tab.content.icon)
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    val cachedIcon = faviconCache.loadFavicon(tab.content.url)
                    if (cachedIcon != null) {
                        faviconImage.setImageBitmap(cachedIcon)
                    } else {
                        // Use first letter of domain as fallback
                        val domain = tab.content.url.split("/").getOrNull(2) ?: "?"
                        faviconImage.setImageResource(R.drawable.ic_baseline_link)
                    }
                }
            }

            // Tab click listener
            pillView.setOnClickListener {
                onTabClick(tab.id)
            }

            // Close button click listener
            closeButton.setOnClickListener {
                context.components.tabsUseCases.removeTab(tab.id)
            }

            // Long-press for context menu
            pillView.setOnLongClickListener { view ->
                showTabContextMenu(view, tab, context)
                true
            }

            container.addView(pillView)
        }

        private fun showTabContextMenu(
            view: View,
            tab: TabSessionState,
            context: android.content.Context
        ) {
            val popup = androidx.appcompat.widget.PopupMenu(context, view)
            popup.menu.add(0, 1, 0, "Close Tab")
            popup.menu.add(0, 2, 0, "Close Other Tabs")
            popup.menu.add(0, 3, 0, "Move to Profile")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        context.components.tabsUseCases.removeTab(tab.id)
                        true
                    }
                    2 -> {
                        // Close all except this one
                        val store = context.components.store
                        store.state.tabs.filter { it.id != tab.id }.forEach {
                            context.components.tabsUseCases.removeTab(it.id)
                        }
                        true
                    }
                    3 -> {
                        // TODO: Show profile selection dialog
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
