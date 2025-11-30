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
import mozilla.components.browser.state.selector.selectedTab
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

        // Observe browser store for ALL tabs (not just grouped ones)
        context.components.store.flowScoped(lifecycleOwner) { flow ->
            flow.map { state -> 
                val selectedTab = state.selectedTab
                Triple(state.tabs.map { it.id }, selectedTab?.id, selectedTab?.content?.private)
            }
            .distinctUntilChanged()
            .collect { (allTabIds, selectedTabId, isPrivate) ->
                // Get browsing mode from BrowserActivity
                val activity = context as? com.prirai.android.nira.BrowserActivity
                val currentBrowsingMode = activity?.browsingModeManager?.mode?.isPrivate ?: false
                val currentProfile = activity?.browsingModeManager?.currentProfile
                
                // Filter tabs by current profile and privacy mode
                val store = context.components.store.state
                val filteredTabIds = allTabIds.filter { tabId ->
                    val tab = store.tabs.find { it.id == tabId }
                    val tabIsPrivate = tab?.content?.private ?: false
                    
                    // Check privacy mode AND profile contextId
                    if (tabIsPrivate != currentBrowsingMode) {
                        false
                    } else if (currentBrowsingMode) {
                        // Private tabs
                        tab?.contextId == "private"
                    } else {
                        // Normal tabs: check profile contextId
                        val expectedContextId = "profile_${currentProfile?.id ?: "default"}"
                        tab?.contextId == expectedContextId
                    }
                }
                
                // Create a virtual group with all filtered tabs
                if (filteredTabIds.isNotEmpty()) {
                    val virtualGroup = TabGroupWithTabs(
                        group = TabGroup(id = "virtual", name = "All Tabs", color = "blue", createdAt = 0, isActive = true),
                        tabIds = filteredTabIds
                    )
                    updateCurrentGroup(virtualGroup)
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
        // Filter tabs to only show those matching current browsing mode AND profile
        val filteredGroup = if (currentGroup != null) {
            val browserState = context.components.store.state
            
            // Get browsing mode from BrowserActivity
            val activity = context as? com.prirai.android.nira.BrowserActivity
            val isPrivateMode = activity?.browsingModeManager?.mode?.isPrivate ?: false
            val currentProfile = activity?.browsingModeManager?.currentProfile
            
            // Filter tab IDs to only include those from current browsing mode AND profile
            val filteredTabIds = currentGroup.tabIds.filter { tabId ->
                val tab = browserState.tabs.find { it.id == tabId }
                val tabIsPrivate = tab?.content?.private ?: false
                
                // Check privacy mode AND profile contextId
                if (tabIsPrivate != isPrivateMode) {
                    false
                } else if (isPrivateMode) {
                    // Private tabs always use contextId = "private"
                    tab?.contextId == "private"
                } else {
                    // Normal tabs: check profile contextId
                    val expectedContextId = "profile_${currentProfile?.id ?: "default"}"
                    tab?.contextId == expectedContextId
                }
            }
            
            // Show group even with just one tab (changed from >= 1 instead of > 1)
            if (filteredTabIds.isNotEmpty()) {
                TabGroupWithTabs(currentGroup.group, filteredTabIds)
            } else {
                null
            }
        } else {
            null
        }
        
        // Show if filtered group has any tabs (changed from > 1 to isNotEmpty)
        val shouldShow = filteredGroup != null && filteredGroup.tabIds.isNotEmpty()

        if (shouldShow) {
            // Get selected tab ID from the browser store
            val selectedTabId = context.components.store.state.selectedTabId
            adapter?.updateCurrentGroup(filteredGroup, selectedTabId)
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
        // Use updateCurrentGroup to ensure filtering is applied
        updateCurrentGroup(currentGroup)
    }

    /**
     * Check if the bar is currently being scrolled.
     */
    fun isScrolling(): Boolean = isScrolling

    interface TabGroupBarListener {
        fun onTabSelected(tabId: String)
    }
}
