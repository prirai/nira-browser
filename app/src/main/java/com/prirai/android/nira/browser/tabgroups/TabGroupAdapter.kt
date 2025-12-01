package com.prirai.android.nira.browser.tabgroups

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.TabGroupItemBinding
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Adapter for displaying tab groups as circular favicons.
 */
class TabGroupAdapter(
    private val onTabClick: (String) -> Unit  // Changed to tab click instead of group click
) : RecyclerView.Adapter<TabGroupAdapter.TabGroupViewHolder>() {

    private var currentGroupWithTabs: TabGroupWithTabs? = null
    private var selectedTabId: String? = null

    fun updateCurrentGroup(groupWithTabs: TabGroupWithTabs?, selectedTabId: String?) {
        // Only update if something actually changed to reduce flickering
        if (this.currentGroupWithTabs != groupWithTabs || this.selectedTabId != selectedTabId) {
            this.currentGroupWithTabs = groupWithTabs
            this.selectedTabId = selectedTabId
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabGroupViewHolder {
        val binding = TabGroupItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TabGroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabGroupViewHolder, position: Int) {
        // We only show one item - the current group's tabs
        currentGroupWithTabs?.let { groupWithTabs ->
            holder.bind(groupWithTabs, selectedTabId)
        }
    }

    override fun getItemCount() = if (currentGroupWithTabs != null) 1 else 0

    inner class TabGroupViewHolder(
        private val binding: TabGroupItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(groupWithTabs: TabGroupWithTabs, selectedTabId: String?) {
            val faviconContainer = binding.faviconContainer
            faviconContainer.removeAllViews()

            // Get tab details from browser store
            CoroutineScope(Dispatchers.Main).launch {
                val store = binding.root.context.components.store
                val faviconCache = binding.root.context.components.faviconCache

                // Create pill-shaped tab for each tab in the group (maintain original order)
                groupWithTabs.tabIds.forEachIndexed { index, tabId ->
                    val tab = store.state.tabs.find { it.id == tabId }
                    if (tab != null) {
                        createTabPill(faviconContainer, tab, tabId == selectedTabId, faviconCache)
                    }
                }
            }
        }

        private fun createTabPill(
            container: LinearLayout,
            tab: mozilla.components.browser.state.state.TabSessionState,
            isSelected: Boolean,
            faviconCache: com.prirai.android.nira.utils.FaviconCache
        ) {
            val context = container.context
            val pillView = LayoutInflater.from(context)
                .inflate(R.layout.tab_pill_item, container, false) as com.google.android.material.card.MaterialCardView

            val faviconImage = pillView.findViewById<ImageView>(R.id.faviconImage)
            val tabTitle = pillView.findViewById<android.widget.TextView>(R.id.tabTitle)
            val closeButton = pillView.findViewById<ImageView>(R.id.closeButton)

            // Set selection state
            pillView.isSelected = isSelected

            // Set tab title
            val title = tab.content.title.ifBlank { tab.content.url }
            tabTitle.text = if (title.length > 20) {
                title.substring(0, 20) + "..."
            } else {
                title
            }

            // Set favicon
            if (tab.content.icon != null) {
                faviconImage.setImageBitmap(tab.content.icon)
            } else {
                // Try to load from cache
                CoroutineScope(Dispatchers.Main).launch {
                    val cachedIcon = faviconCache.loadFavicon(tab.content.url)
                    if (cachedIcon != null) {
                        faviconImage.setImageBitmap(cachedIcon)
                    } else {
                        // Fallback to default icon
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
                // Handle close tab
                context.components.store
                val tabsUseCases = context.components.tabsUseCases
                tabsUseCases.removeTab(tab.id)
            }

            container.addView(pillView)
        }

        /**
         * Creates a touch listener that only triggers on tap, not during drag/scroll.
         */
        private fun createTapOnlyTouchListener(tabId: String): View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var downTime = 0L
            val tapTimeout = 200L // Max time for a tap in milliseconds
            val tapSlop = 10f // Max movement in pixels to still be considered a tap

            return View.OnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        downTime = System.currentTimeMillis()
                        v.isPressed = true
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = abs(event.x - downX)
                        val deltaY = abs(event.y - downY)

                        // If moved too much, cancel the tap
                        if (deltaX > tapSlop || deltaY > tapSlop) {
                            v.isPressed = false
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val deltaX = abs(event.x - downX)
                        val deltaY = abs(event.y - downY)
                        val deltaTime = System.currentTimeMillis() - downTime

                        // Only trigger click if it's a tap (minimal movement and quick)
                        if (deltaX <= tapSlop && deltaY <= tapSlop && deltaTime <= tapTimeout && v.isPressed) {
                            v.isPressed = false
                            v.performClick()
                            onTabClick(tabId)
                        } else {
                            v.isPressed = false
                        }
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        true
                    }

                    else -> false
                }
            }
        }

    }
}
