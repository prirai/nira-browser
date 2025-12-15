package com.prirai.android.nira.browser.tabgroups

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
                groupWithTabs.tabIds.forEachIndexed { _, tabId ->
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
            val pillView = LayoutInflater.from(context).inflate(
                R.layout.tab_pill_item, container, false
            ) as com.google.android.material.card.MaterialCardView

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

            // Long-press for context menu
            pillView.setOnLongClickListener { view ->
                showTabPillContextMenu(view, tab.id, context)
                true
            }

            // Close button click listener
            closeButton.setOnClickListener {
                // Handle close tab
                context.components.store
                val tabsUseCases = context.components.tabsUseCases
                tabsUseCases.removeTab(tab.id)
            }

            // Add swipe-up gesture for delete with animation
            setupSwipeToCloseGesture(pillView, tab.id, context)

            container.addView(pillView)
        }

        private fun showTabPillContextMenu(
            view: View, tabId: String, context: android.content.Context
        ) {
            val popup = androidx.appcompat.widget.PopupMenu(context, view)
            popup.menu.add(0, 1, 0, "Move to Profile")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        showMoveToProfileDialog(context, listOf(tabId))
                        true
                    }

                    else -> false
                }
            }

            popup.show()
        }

        private fun showMoveToProfileDialog(
            context: android.content.Context, tabIds: List<String>
        ) {
            val profileManager =
                com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
            val profiles = profileManager.getAllProfiles()

            val items = profiles.map { "${it.emoji} ${it.name}" }.toMutableList()
            items.add("🕵️ Private")

            com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Move tab to Profile").setItems(items.toTypedArray()) { _, which ->
                    val targetProfileId = if (which == items.size - 1) {
                        "private"
                    } else {
                        profiles[which].id
                    }

                    // Migrate tabs
                    val migratedCount = profileManager.migrateTabsToProfile(tabIds, targetProfileId)

                    // Show confirmation
                    android.widget.Toast.makeText(
                        context,
                        "Moved $migratedCount tab to ${items[which]}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }.setNegativeButton("Cancel", null).show()
        }

        private fun setupSwipeToCloseGesture(
            pillView: com.google.android.material.card.MaterialCardView,
            tabId: String,
            context: android.content.Context
        ) {
            val startY = 0f
            val startX = 0f
            var isDragging = false
            var shadowClone: View? = null
            var decorView: ViewGroup? = null
            val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
            var longPressRunnable: Runnable? = null

            pillView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = false

                        // Start long-press timer
                        longPressRunnable = Runnable {
                            if (!isDragging) {
                                // Trigger long-press for context menu
                                v.performLongClick()
                            }
                        }
                        longPressHandler.postDelayed(
                            longPressRunnable,
                            android.view.ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = startY - event.rawY
                        val deltaX = abs(event.rawX - startX)

                        // Cancel long-press if moved
                        if (abs(deltaY) > 10 || deltaX > 10) {
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        }

                        // Only start vertical dragging if clearly moving up
                        if (deltaY > 30 && deltaX < 20 && !isDragging) {
                            isDragging = true
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

                            // Request parent to not intercept
                            var parent = v.parent
                            while (parent != null && parent !is RecyclerView) {
                                parent = parent.parent
                            }
                            parent?.requestDisallowInterceptTouchEvent(true)

                            // Create shadow clone visible above the pill
                            val activity = context as? android.app.Activity
                            decorView = activity?.window?.decorView as? ViewGroup

                            if (decorView != null) {
                                shadowClone = createShadowClone(pillView, context)

                                val location = IntArray(2)
                                pillView.getLocationInWindow(location)
                                shadowClone.x = location[0].toFloat()
                                shadowClone.y = location[1].toFloat()

                                decorView?.addView(
                                    shadowClone, ViewGroup.LayoutParams(
                                        pillView.width, pillView.height
                                    )
                                )
                            }
                        }

                        if (isDragging && shadowClone != null) {
                            val location = IntArray(2)
                            pillView.getLocationInWindow(location)
                            shadowClone.y = location[1].toFloat() - deltaY

                            // Visual feedback - stronger animation
                            val progress = (deltaY / 120f).coerceIn(0f, 1f)
                            shadowClone.scaleX = 1f - (progress * 0.15f)
                            shadowClone.scaleY = 1f - (progress * 0.15f)
                            shadowClone.rotation = -progress * 8f
                            shadowClone.alpha = 1f - (progress * 0.2f)
                            true
                        } else {
                            false
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Cancel long-press timer
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

                        // Restore parent interception
                        var parent = v.parent
                        while (parent != null && parent !is RecyclerView) {
                            parent = parent.parent
                        }
                        parent?.requestDisallowInterceptTouchEvent(false)

                        if (isDragging) {
                            val deltaY = startY - event.rawY
                            if (deltaY > 120) {
                                // Threshold reached - delete tab
                                shadowClone?.animate()?.translationY(-600f)?.rotation(-35f)
                                    ?.scaleX(0.2f)?.scaleY(0.2f)?.alpha(0f)?.setDuration(300)
                                    ?.withEndAction {
                                        decorView?.removeView(shadowClone)
                                        context.components.tabsUseCases.removeTab(tabId)
                                    }?.start()
                            } else {
                                // Spring back
                                shadowClone?.animate()?.scaleX(1f)?.scaleY(1f)?.rotation(0f)
                                    ?.alpha(0f)?.setDuration(200)?.withEndAction {
                                        decorView?.removeView(shadowClone)
                                    }?.start()
                            }
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }

        private fun createShadowClone(
            pillView: com.google.android.material.card.MaterialCardView,
            context: android.content.Context
        ): View {
            // Create a shadow clone that looks like an ungrouped tab pill
            val clone = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(pillView.width, pillView.height)
                elevation = 20f // Higher elevation for shadow effect
            }

            val clonedCard = com.google.android.material.card.MaterialCardView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                radius = 20f * context.resources.displayMetrics.density // Same as ungrouped pills
                cardElevation = 8f * context.resources.displayMetrics.density

                // Use primary color for border like ungrouped tabs
                val theme = context.theme
                val primaryTypedValue = android.util.TypedValue()
                val primaryColor = if (theme.resolveAttribute(
                        android.R.attr.colorPrimary, primaryTypedValue, true
                    )
                ) {
                    primaryTypedValue.data
                } else {
                    androidx.core.content.ContextCompat.getColor(context, R.color.m3_primary)
                }

                val borderWidth = (3 * context.resources.displayMetrics.density).toInt()
                setCardBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(
                        context, R.color.m3_surface_container_background
                    )
                )
                strokeColor = primaryColor
                strokeWidth = borderWidth

                // Clone content layout
                val content = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val padding = (12 * context.resources.displayMetrics.density).toInt()
                    setPadding(
                        padding,
                        (8 * context.resources.displayMetrics.density).toInt(),
                        (8 * context.resources.displayMetrics.density).toInt(),
                        (8 * context.resources.displayMetrics.density).toInt()
                    )
                }

                // Clone favicon
                val originalFavicon = pillView.findViewById<ImageView>(R.id.faviconImage)
                val clonedFavicon = ImageView(context).apply {
                    val size = (20 * context.resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = (8 * context.resources.displayMetrics.density).toInt()
                    }
                    setImageDrawable(originalFavicon.drawable)
                }
                content.addView(clonedFavicon)

                // Clone title
                val originalTitle = pillView.findViewById<android.widget.TextView>(R.id.tabTitle)
                val clonedTitle = android.widget.TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                    text = originalTitle.text
                    textSize = 14f
                    setTextColor(originalTitle.currentTextColor)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                content.addView(clonedTitle)

                addView(content)
            }

            clone.addView(clonedCard)
            return clone
        }
    }
}
