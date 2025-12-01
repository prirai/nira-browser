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
    private var lifecycleOwner: LifecycleOwner? = null

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
        this.lifecycleOwner = lifecycleOwner

        adapter = TabGroupAdapter { tabId ->
            listener.onTabSelected(tabId)
        }

        binding.tabGroupsRecyclerView.adapter = adapter

        // Observe browser store for tabs and groups
        context.components.store.flowScoped(lifecycleOwner) { flow ->
            flow.map { state -> 
                val selectedTab = state.selectedTab
                Triple(state.tabs.map { it.id }, selectedTab?.id, selectedTab?.content?.private)
            }
            .distinctUntilChanged()
            .collect { (allTabIds, selectedTabId, isPrivate) ->
                lifecycleOwner.lifecycleScope.launch {
                    android.util.Log.d("TabGroupBar", "===== TAB BAR UPDATE =====")
                    android.util.Log.d("TabGroupBar", "Total tabs in store: ${allTabIds.size}, selected: $selectedTabId")
                    
                    // Get browsing mode from BrowserActivity
                    val activity = context as? com.prirai.android.nira.BrowserActivity
                    val currentBrowsingMode = activity?.browsingModeManager?.mode?.isPrivate ?: false
                    val currentProfile = activity?.browsingModeManager?.currentProfile
                    
                    android.util.Log.d("TabGroupBar", "Current profile: ${currentProfile?.name}, private mode: $currentBrowsingMode")
                    
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
                    
                    android.util.Log.d("TabGroupBar", "Filtered ${filteredTabIds.size} tabs for current profile")
                    
                    if (filteredTabIds.isEmpty()) {
                        android.util.Log.d("TabGroupBar", "No tabs for current profile, clearing tab bar")
                        updateCurrentGroup(null)
                        return@launch
                    }
                    
                    // If selected tab is in a group, show that group
                    // Otherwise show all ungrouped tabs as a virtual group
                    val selectedTabGroupId = selectedTabId?.let { tabGroupManager?.getGroupIdForTab(it) }
                    android.util.Log.d("TabGroupBar", "Selected tab $selectedTabId is in group: $selectedTabGroupId")
                    
                    val groupToShow = if (selectedTabGroupId != null) {
                        // Show the actual group from database
                        android.util.Log.d("TabGroupBar", "Loading group $selectedTabGroupId from database")
                        tabGroupManager?.getAllGroups()?.find { it.id == selectedTabGroupId }?.let { group ->
                            val groupTabIds = tabGroupManager?.getTabIdsInGroup(group.id) ?: emptyList()
                            android.util.Log.d("TabGroupBar", "Group '${group.name}' has ${groupTabIds.size} tabs in DB")
                            
                            // Only show tabs that are in filteredTabIds (matching profile/mode)
                            val visibleGroupTabIds = groupTabIds.filter { it in filteredTabIds }
                            android.util.Log.d("TabGroupBar", "Of those, ${visibleGroupTabIds.size} are visible in current profile")
                            
                            if (visibleGroupTabIds.isNotEmpty()) {
                                TabGroupWithTabs(
                                    group = group,
                                    tabIds = visibleGroupTabIds
                                )
                            } else {
                                android.util.Log.d("TabGroupBar", "No visible tabs in group, not showing")
                                null
                            }
                        }
                    } else {
                        android.util.Log.d("TabGroupBar", "Selected tab not in group, showing ungrouped tabs")
                        // Show ungrouped tabs as virtual group
                        val allGroupedTabIds = tabGroupManager?.getAllGroups()?.flatMap { group ->
                            tabGroupManager?.getTabIdsInGroup(group.id) ?: emptyList()
                        }?.toSet() ?: emptySet()
                        
                        android.util.Log.d("TabGroupBar", "Total ${allGroupedTabIds.size} tabs are in groups")
                        
                        val ungroupedTabIds = filteredTabIds.filter { it !in allGroupedTabIds }
                        android.util.Log.d("TabGroupBar", "${ungroupedTabIds.size} ungrouped tabs")
                        
                        if (ungroupedTabIds.isNotEmpty()) {
                            TabGroupWithTabs(
                                group = TabGroup(id = "ungrouped", name = "Tabs", color = "blue", createdAt = 0, isActive = true),
                                tabIds = ungroupedTabIds
                            )
                        } else {
                            android.util.Log.d("TabGroupBar", "No ungrouped tabs to show")
                            null
                        }
                    }
                    
                    android.util.Log.d("TabGroupBar", "Updating tab bar with group: ${groupToShow?.group?.name} (${groupToShow?.tabIds?.size} tabs)")
                    updateCurrentGroup(groupToShow)
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
