package com.prirai.android.nira.browser.tabgroups

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.databinding.TabGroupBarBinding
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.lib.state.ext.flowScoped

/**
 * Horizontal tab group bar that appears above the address bar.
 * Shows current tab groups and allows switching between them.
 */
class TabGroupBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var binding: TabGroupBarBinding
    private var adapter: TabGroupAdapter? = null
    private var tabGroupManager: TabGroupManager? = null
    private var listener: TabGroupBarListener? = null
    private var isScrolling = false

    init {
        orientation = HORIZONTAL
        binding = TabGroupBarBinding.inflate(LayoutInflater.from(context), this, true)
        setupRecyclerView()
    }

    fun setup(
        tabGroupManager: TabGroupManager,
        lifecycleOwner: LifecycleOwner,
        listener: TabGroupBarListener
    ) {
        this.tabGroupManager = tabGroupManager
        this.listener = listener

        adapter = TabGroupAdapter { tabId ->
            listener.onTabSelected(tabId)
        }

        binding.tabGroupsRecyclerView.adapter = adapter

        // Observe groups and current group changes
        // Observe current group changes
        lifecycleOwner.lifecycleScope.launch {
            tabGroupManager.currentGroup.collect { currentGroup ->
                updateCurrentGroup(currentGroup)
            }
        }

        // Observe only selected tab changes to reduce excessive updates
        context.components.store.flowScoped(lifecycleOwner) { flow ->
            flow.map { state -> state.selectedTabId }
                .distinctUntilChanged()
                .collect { selectedTabId: String? ->
                    // Only update if the selected tab actually changed
                    val currentGroup = tabGroupManager?.currentGroup?.value
                    if (currentGroup != null && currentGroup.tabCount > 1 && isVisible) {
                        adapter?.updateCurrentGroup(currentGroup, selectedTabId)
                    }
                }
        }
    }

    private fun setupRecyclerView() {
        binding.tabGroupsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(false)

            // Add scroll listener to detect when user is scrolling
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
                }
            })

            // Intercept touch events to prevent clicks during scroll
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                private var startX = 0f
                private var startY = 0f

                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = e.x
                            startY = e.y
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = kotlin.math.abs(e.x - startX)
                            val deltaY = kotlin.math.abs(e.y - startY)

                            // If horizontal movement is detected, we're scrolling
                            if (deltaX > 10 && deltaX > deltaY) {
                                isScrolling = true
                            }
                        }
                    }
                    return false // Don't actually intercept, just monitor
                }
            })
        }
    }

    private fun updateCurrentGroup(currentGroup: TabGroupWithTabs?) {
        // Only show if current group has multiple tabs
        val shouldShow = currentGroup != null && currentGroup.tabCount > 1

        if (shouldShow) {
            // Get selected tab ID from the browser store
            val selectedTabId = context.components.store.state.selectedTabId
            adapter?.updateCurrentGroup(currentGroup, selectedTabId)
            isVisible = true

            // Setup scroll behavior when tab group bar becomes visible (if not already done)
            post {
                setupScrollBehaviorIfNeeded()
            }
        } else {
            isVisible = false
        }
    }

    private fun setupScrollBehaviorIfNeeded() {
        val prefs = com.prirai.android.nira.preferences.UserPreferences(context)
        if (prefs.hideBarWhileScrolling && height > 0) {
            (layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.apply {
                if (behavior == null) {
                    behavior = mozilla.components.ui.widgets.behavior.EngineViewScrollingBehavior(
                        context,
                        null,
                        mozilla.components.ui.widgets.behavior.ViewPosition.BOTTOM
                    )
                }
            }
        }
    }

    /**
     * Force refresh the current group display with updated selection state.
     */
    fun refreshSelection() {
        val currentGroup = tabGroupManager?.currentGroup?.value
        if (currentGroup != null && currentGroup.tabCount > 1) {
            val selectedTabId = context.components.store.state.selectedTabId
            adapter?.updateCurrentGroup(currentGroup, selectedTabId)
        }
    }

    /**
     * Check if the bar is currently being scrolled.
     */
    fun isScrolling(): Boolean = isScrolling

    interface TabGroupBarListener {
        fun onTabSelected(tabId: String)
    }
}
