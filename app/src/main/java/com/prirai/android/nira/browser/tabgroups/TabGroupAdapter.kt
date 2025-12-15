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

            // Add swipe-up gesture for delete with animation
            setupSwipeToCloseGesture(pillView, tab.id, context)

            container.addView(pillView)
        }

        private fun setupSwipeToCloseGesture(
            pillView: com.google.android.material.card.MaterialCardView,
            tabId: String,
            context: android.content.Context
        ) {
            var startY = 0f
            var startX = 0f
            var isDragging = false
            var hasMoved = false
            var dragClone: View? = null
            var decorView: ViewGroup? = null

            pillView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        startX = event.rawX
                        isDragging = false
                        hasMoved = false
                        // Return false to let RecyclerView handle scrolling, but we still get MOVE events
                        false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = startY - event.rawY
                        val deltaX = abs(event.rawX - startX)
                        
                        // Detect horizontal scrolling and mark as moved
                        if (deltaX > 5) {
                            hasMoved = true
                            // Don't consume event - let RecyclerView scroll
                            return@setOnTouchListener false
                        }
                        
                        // Detect any movement for long-press prevention
                        if (abs(deltaY) > 5) {
                            hasMoved = true
                        }

                        // Only start vertical dragging if moving up, not sideways
                        if (deltaY > 20 && deltaX < 30 && !isDragging) {
                            isDragging = true
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            
                            // Create clone and add to DecorView
                            val activity = context as? android.app.Activity
                            decorView = activity?.window?.decorView as? ViewGroup
                            
                            if (decorView != null) {
                                // Hide original
                                pillView.alpha = 0f
                                
                                // Create clone
                                dragClone = createTabClone(pillView, context)
                                
                                // Position clone at original location
                                val location = IntArray(2)
                                pillView.getLocationInWindow(location)
                                dragClone?.x = location[0].toFloat()
                                dragClone?.y = location[1].toFloat()
                                
                                // Add to decorView
                                decorView?.addView(dragClone, ViewGroup.LayoutParams(
                                    pillView.width,
                                    pillView.height
                                ))
                            }
                        }

                        if (isDragging && dragClone != null) {
                            // Update clone position to follow finger
                            val location = IntArray(2)
                            pillView.getLocationInWindow(location)
                            
                            dragClone?.y = location[1].toFloat() - deltaY
                            
                            // Visual feedback during drag
                            val progress = (deltaY / 100f).coerceIn(0f, 1f)
                            dragClone?.scaleX = 1f - (progress * 0.2f)
                            dragClone?.scaleY = 1f - (progress * 0.2f)
                            dragClone?.rotation = -progress * 10f
                            dragClone?.alpha = 1f - (progress * 0.3f)
                            true
                        } else {
                            false
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            val deltaY = startY - event.rawY
                            if (deltaY > 100) {
                                // Animate clone flying away
                                dragClone?.animate()
                                    ?.translationY(-500f)
                                    ?.rotation(-30f)
                                    ?.scaleX(0.3f)
                                    ?.scaleY(0.3f)
                                    ?.alpha(0f)
                                    ?.setDuration(250)
                                    ?.withEndAction {
                                        decorView?.removeView(dragClone)
                                        dragClone = null
                                        // Trigger actual delete
                                        val tabsUseCases = context.components.tabsUseCases
                                        tabsUseCases.removeTab(tabId)
                                    }
                                    ?.start()
                            } else {
                                // Spring back - animate clone back and show original
                                dragClone?.animate()
                                    ?.scaleX(1f)
                                    ?.scaleY(1f)
                                    ?.rotation(0f)
                                    ?.alpha(0f)
                                    ?.setDuration(200)
                                    ?.withEndAction {
                                        decorView?.removeView(dragClone)
                                        dragClone = null
                                        pillView.alpha = 1f
                                    }
                                    ?.start()
                            }
                            isDragging = false
                            hasMoved = false
                            true
                        } else if (hasMoved) {
                            // Finger moved but didn't drag up - don't trigger click
                            hasMoved = false
                            true
                        } else {
                            // No movement - allow click to proceed
                            false
                        }
                    }

                    else -> false
                }
            }
        }

        private fun createTabClone(
            pillView: com.google.android.material.card.MaterialCardView,
            context: android.content.Context
        ): View {
            // Create a clone of the pill with same appearance
            val clone = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(pillView.width, pillView.height)
                elevation = 16f
            }
            
            // Clone the card appearance
            val clonedCard = com.google.android.material.card.MaterialCardView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                radius = pillView.radius
                cardElevation = pillView.cardElevation
                background = pillView.background?.constantState?.newDrawable()?.mutate()
                
                // Clone content layout
                val content = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val padding = (12 * context.resources.displayMetrics.density).toInt()
                    setPadding(padding, (8 * context.resources.displayMetrics.density).toInt(), 
                              (8 * context.resources.displayMetrics.density).toInt(), 
                              (8 * context.resources.displayMetrics.density).toInt())
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
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
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
