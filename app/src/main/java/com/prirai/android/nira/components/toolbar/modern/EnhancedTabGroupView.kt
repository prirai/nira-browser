package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.SessionState

/**
 * Enhanced tab group view with Tab Islands support - automatic and manual grouping,
 * colored pills, drag-to-reorder, collapse/expand, and beautiful animations.
 */
class EnhancedTabGroupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    private lateinit var tabAdapter: ModernTabPillAdapter
    private lateinit var islandManager: TabIslandManager

    private var onTabSelected: ((String) -> Unit)? = null
    private var onTabClosed: ((String) -> Unit)? = null
    private var onIslandRenamed: ((String, String) -> Unit)? = null
    private var onNewTabInIsland: ((String) -> Unit)? = null
    private var onTabDuplicated: ((String) -> Unit)? = null

    private var currentTabs = mutableListOf<SessionState>()
    private var selectedTabId: String? = null

    // Track last update to prevent unnecessary refreshes
    private var lastTabIds = emptyList<String>()
    private var lastSelectedId: String? = null
    
    /**
     * Tracks whether this is the initial setup of the view.
     * 
     * Used to prevent visible scrolling animation when the view is first created
     * during fragment transitions. Set to false after the first updateTabs() call.
     */
    private var isInitialSetup = true
    private var lastDisplayItemsCount = 0

    // Track parent-child relationships for automatic grouping
    private val pendingAutoGroups = mutableMapOf<String, String>()

    // Cache Paint and ElevationOverlayProvider for onDraw to avoid allocation on every frame
    private val backgroundPaint by lazy { Paint() }
    private val elevationOverlayProvider by lazy {
        com.google.android.material.elevation.ElevationOverlayProvider(context)
    }

    init {
        setupRecyclerView()
        setupItemTouchHelper()
        setupIslandManager()
    }

    private fun setupRecyclerView() {
        layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
        tabAdapter = ModernTabPillAdapter(
            onTabClick = { tabId ->
                onTabSelected?.invoke(tabId)
                animateSelection(tabId)
            },
            onTabClose = { tabId ->
                handleTabClose(tabId)
            },
            onIslandHeaderClick = { islandId ->
                handleIslandHeaderClick(islandId)
            },
            onIslandLongPress = { islandId ->
                handleIslandLongPress(islandId)
            }
        )
        adapter = tabAdapter

        // Layout configuration - CRITICAL: Allow children to draw outside bounds
        clipToPadding = false
        clipChildren = false
        overScrollMode = OVER_SCROLL_NEVER
        setPadding(4, 2, 4, 2)

        // Background is handled by parent TabGroupWithProfileSwitcher
        // Make this transparent so parent background shows through
        setBackgroundColor(Color.TRANSPARENT)

        elevation = 0f
    }

    private fun isDarkMode(): Boolean {
        return when (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Force height to 60dp for compact appearance
        val heightInPx = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            40f,
            resources.displayMetrics
        ).toInt()
        val newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(heightInPx, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }

    // Debounce refresh calls to prevent flickering
    private var refreshJob: kotlinx.coroutines.Job? = null
    private val refreshScope = CoroutineScope(Dispatchers.Main)
    
    private fun setupIslandManager() {
        islandManager = TabIslandManager.getInstance(context)
        
        // Register listener to refresh display when islands change
        // Use debouncing to prevent multiple rapid refreshes
        islandManager.addChangeListener {
            scheduleRefresh()
        }
    }
    
    /**
     * Schedule a refresh with debouncing to prevent flickering
     */
    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = refreshScope.launch {
            kotlinx.coroutines.delay(150) // 150ms debounce to reduce flickering
            refreshDisplay()
        }
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, // Disable reordering to prevent position changes
            ItemTouchHelper.UP
        ) {
            private var draggedTabId: String? = null
            private var lastTargetTabId: String? = null
            private var lastTargetView: View? = null
            private var dragStartX = 0f
            private var dragStartY = 0f

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: ViewHolder,
                target: ViewHolder
            ): Boolean {
                // Don't allow reordering - we only want grouping
                return false
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                    // Get dragged tab
                    val draggedTab = draggedTabId?.let { id -> currentTabs.find { it.id == id } }
                    if (draggedTab == null) {
                        return
                    }

                    // Calculate center position of dragged item
                    val draggedView = viewHolder.itemView
                    val draggedCenterX = draggedView.left + dX + draggedView.width / 2f
                    val draggedCenterY = draggedView.top + dY + draggedView.height / 2f

                    // Find which view is under the dragged item
                    var targetView: View? = null
                    var targetTabId: String? = null
                    var foundTarget = false

                    for (i in 0 until recyclerView.childCount) {
                        val child = recyclerView.getChildAt(i)
                        if (child == draggedView) continue

                        val childLeft = child.left.toFloat()
                        val childTop = child.top.toFloat()
                        val childRight = child.right.toFloat()
                        val childBottom = child.bottom.toFloat()

                        // Check if dragged item center is over this child
                        if (draggedCenterX >= childLeft && draggedCenterX <= childRight &&
                            draggedCenterY >= childTop && draggedCenterY <= childBottom
                        ) {
                            val childHolder = recyclerView.getChildViewHolder(child)
                            val displayItems = islandManager.createDisplayItems(currentTabs)
                            val position = childHolder.adapterPosition

                            if (position >= 0 && position < displayItems.size) {
                                when (val item = displayItems[position]) {
                                    is TabPillItem.Tab -> {
                                        if (item.session.id != draggedTab.id) {
                                            targetView = child
                                            targetTabId = item.session.id
                                            foundTarget = true
                                        }
                                    }

                                    is TabPillItem.IslandHeader -> {
                                        targetView = child
                                        targetTabId = "island_${item.island.id}"
                                        foundTarget = true
                                    }

                                    is TabPillItem.CollapsedIsland -> {
                                        targetView = child
                                        targetTabId = "collapsed_${item.island.id}"
                                        foundTarget = true
                                    }

                                    is TabPillItem.ExpandedIslandGroup -> {
                                        targetView = child
                                        targetTabId = "island_${item.island.id}"
                                        foundTarget = true
                                    }
                                }
                            }
                            break
                        }
                    }

                    // If no target found and tab is in an island, allow ungrouping
                    if (!foundTarget && islandManager.getIslandForTab(draggedTab.id) != null) {
                        // Check if dragged outside reasonable bounds for ungrouping
                        val recyclerViewBounds = recyclerView.let { rv ->
                            intArrayOf(0, 0).also { rv.getLocationOnScreen(it) }
                        }
                        // Sensitive drag-out detection
                        val isOutsideBounds = draggedCenterY < recyclerViewBounds[1] - 30 ||
                                draggedCenterY > recyclerViewBounds[1] + recyclerView.height + 30 ||
                                dY < -100 || dY > 100 // Also check relative drag distance



                        if (isOutsideBounds) {
                            targetTabId = "ungroup"

                            // Visual feedback for ungroup
                            draggedView.animate()
                                .scaleX(0.9f)
                                .scaleY(0.9f)
                                .alpha(0.6f)
                                .setDuration(100)
                                .start()
                        }
                    }

                    // Update visual feedback if target changed
                    if (targetTabId != lastTargetTabId) {
                        // Remove previous highlight
                        lastTargetView?.animate()?.scaleX(1f)?.scaleY(1f)?.alpha(1f)?.setDuration(100)?.start()

                        // Add new highlight
                        if (targetView != null) {
                            targetView.animate()?.scaleX(1.08f)?.scaleY(1.08f)?.alpha(0.7f)?.setDuration(100)?.start()
                            lastTargetView = targetView
                            lastTargetTabId = targetTabId
                        } else {
                            lastTargetView = null
                            lastTargetTabId = targetTabId // ✅ NEW: Keep ungroup target
                        }
                    }
                }
            }

            override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
                if (direction == ItemTouchHelper.UP) {
                    if (viewHolder is ModernTabPillAdapter.TabPillViewHolder) {
                        val displayItems = islandManager.createDisplayItems(currentTabs)
                        val position = viewHolder.adapterPosition
                        if (position in displayItems.indices) {
                            val item = displayItems[position]
                            if (item is TabPillItem.Tab) {
                                handleTabClose(item.session.id)
                            }
                        }
                    }
                }
            }

            override fun isLongPressDragEnabled(): Boolean = true
            override fun isItemViewSwipeEnabled(): Boolean = true

            override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                when (actionState) {
                    ItemTouchHelper.ACTION_STATE_DRAG -> {
                        // Visual feedback for dragged item
                        viewHolder?.itemView?.animate()
                            ?.scaleX(1.15f)
                            ?.scaleY(1.15f)
                            ?.alpha(0.85f)
                            ?.setDuration(150)
                            ?.start()

                        // Store dragged tab ID and start position
                        if (viewHolder is ModernTabPillAdapter.TabPillViewHolder) {
                            val displayItems = islandManager.createDisplayItems(currentTabs)
                            val position = viewHolder.adapterPosition
                            if (position in displayItems.indices) {
                                val item = displayItems[position]
                                if (item is TabPillItem.Tab) {
                                    draggedTabId = item.session.id
                                    dragStartX = viewHolder.itemView.x
                                    dragStartY = viewHolder.itemView.y
                                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                }
                            }
                        }
                    }

                    ItemTouchHelper.ACTION_STATE_IDLE -> {
                        // Check if we should create an island based on final position
                        val draggedTab = draggedTabId?.let { id -> currentTabs.find { it.id == id } }

                        if (draggedTab != null && lastTargetTabId != null) {

                            handleDrop(draggedTab.id, lastTargetTabId!!)
                        }

                        // Clear drag state
                        draggedTabId = null
                        lastTargetTabId = null
                        lastTargetView = null
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                // Reset all visual states
                viewHolder.itemView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()

                lastTargetView?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.alpha(1f)
                    ?.setDuration(200)
                    ?.start()
            }

            private fun handleDrop(draggedTabId: String, targetId: String) {

                // First remove the tab from its current island if it's in one
                val currentIsland = islandManager.getIslandForTab(draggedTabId)


                when {
                    targetId.startsWith("island_") -> {
                        // Dropped on island header - add to island
                        val islandId = targetId.removePrefix("island_")
                        if (currentIsland?.id != islandId) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            if (currentIsland != null) {
                                islandManager.removeTabFromIsland(draggedTabId, currentIsland.id)
                            }
                            islandManager.addTabToIsland(draggedTabId, islandId)
                            // Refresh is handled by debounced change listener
                        }
                    }

                    targetId.startsWith("collapsed_") -> {
                        // Dropped on collapsed island - add to island
                        val islandId = targetId.removePrefix("collapsed_")
                        if (currentIsland?.id != islandId) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            if (currentIsland != null) {
                                islandManager.removeTabFromIsland(draggedTabId, currentIsland.id)
                            }
                            islandManager.addTabToIsland(draggedTabId, islandId)
                            // Refresh is handled by debounced change listener
                        }
                    }

                    targetId == "ungroup" -> {
                        // Dropped in empty space - remove from island
                        if (currentIsland != null) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            islandManager.removeTabFromIsland(draggedTabId, currentIsland.id)
                            // Refresh is handled by debounced change listener
                        }
                    }

                    else -> {
                        // Dropped on another tab - create island or merge
                        val targetTab = currentTabs.find { it.id == targetId }
                        val draggedTab = currentTabs.find { it.id == draggedTabId }

                        if (targetTab != null && draggedTab != null) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            if (currentIsland != null) {
                                islandManager.removeTabFromIsland(draggedTabId, currentIsland.id)
                            }
                            createIslandAtPosition(draggedTabId, targetId)
                        }
                    }
                }
            }

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: ViewHolder): Int {
                // Only allow dragging tab pills, not island headers or collapsed islands
                val dragFlags = if (viewHolder is ModernTabPillAdapter.TabPillViewHolder) {
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN
                } else {
                    0
                }

                val swipeFlags = if (viewHolder is ModernTabPillAdapter.TabPillViewHolder) {
                    ItemTouchHelper.UP
                } else {
                    0
                }

                return makeMovementFlags(dragFlags, swipeFlags)
            }
        })

        itemTouchHelper.attachToRecyclerView(this)
    }

    private fun createIslandAtPosition(tabId1: String, tabId2: String) {

        // Check if either tab is already in an island
        val island1 = islandManager.getIslandForTab(tabId1)
        val island2 = islandManager.getIslandForTab(tabId2)


        // Get the positions of both tabs in the current tabs list
        val pos1 = currentTabs.indexOfFirst { it.id == tabId1 }
        val pos2 = currentTabs.indexOfFirst { it.id == tabId2 }

        // Determine the insert position (where the target tab is)
        minOf(pos1, pos2)

        when {
            island1 != null && island2 == null -> {

                // Tab1 in island, add tab2 to it at the correct position
                islandManager.addTabToIsland(tabId2, island1.id)
            }

            island2 != null && island1 == null -> {

                // Tab2 in island, add tab1 to it
                islandManager.addTabToIsland(tabId1, island2.id)
            }

            island1 == null -> {

                // Neither in island, create new one maintaining order
                // Create island with tabs in their current order
                val orderedTabs = if (pos1 < pos2) listOf(tabId1, tabId2) else listOf(tabId2, tabId1)

                CoroutineScope(Dispatchers.Main).launch {
                    islandManager.createIsland(orderedTabs)
                    // Refresh is handled by debounced change listener
                }
            }

            island2 != null && island1.id != island2.id -> {
                // Both in different islands, merge into island2
                islandManager.addTabToIsland(tabId1, island2.id)
                // Refresh is handled by debounced change listener
            }
            // If both in same island, do nothing
            else -> {
            }
        }

        showIslandCreatedFeedback()
    }

    fun setup(
        onTabSelected: (String) -> Unit,
        onTabClosed: (String) -> Unit,
        onIslandRenamed: ((String, String) -> Unit)? = null,
        onNewTabInIsland: ((String) -> Unit)? = null,
        onTabDuplicated: ((String) -> Unit)? = null
    ) {
        this.onTabSelected = onTabSelected
        this.onTabClosed = onTabClosed
        this.onIslandRenamed = onIslandRenamed
        this.onNewTabInIsland = onNewTabInIsland
        this.onTabDuplicated = onTabDuplicated
        tabAdapter.updateCallbacks(
            onTabSelected,
            { tabId -> handleTabClose(tabId) },
            { islandId -> handleIslandHeaderClick(islandId) },
            { islandId -> handleIslandLongPress(islandId) },
            { islandId -> handleIslandPlusClick(islandId) },
            { tabId, islandId -> handleTabUngroupFromIsland(tabId, islandId) },
            { tabId -> handleTabDuplicate(tabId) }
        )
    }

    fun updateTabs(tabs: List<SessionState>, selectedId: String?) {
        // Check if anything actually changed to prevent flickering
        val currentTabIds = tabs.map { it.id }
        val hasTabsChanged = currentTabIds != lastTabIds
        val hasSelectionChanged = selectedId != lastSelectedId

        // Check if tab content (title, URL, icon) has changed
        val hasContentChanged = tabs.any { newTab ->
            val oldTab = currentTabs.find { it.id == newTab.id }
            oldTab == null ||
                    oldTab.content.title != newTab.content.title ||
                    oldTab.content.url != newTab.content.url ||
                    oldTab.content.icon != newTab.content.icon
        }

        // If nothing changed, don't update
        if (!hasTabsChanged && !hasSelectionChanged && !hasContentChanged) {
            return
        }

        selectedTabId = selectedId
        lastTabIds = currentTabIds
        lastSelectedId = selectedId

        // Auto-expand collapsed island if selected tab is inside it
        if (hasSelectionChanged && selectedId != null) {
            expandIslandIfTabInside(selectedId)
        }

        val shouldShow = tabs.isNotEmpty()

        if (shouldShow) {
            // Update if tabs changed, content changed, or selection changed
            if (hasTabsChanged || hasContentChanged || currentTabs.isEmpty()) {
                currentTabs.clear()
                currentTabs.addAll(tabs)

                // Create display items with island information
                val displayItems = islandManager.createDisplayItems(tabs)

                // Update adapter with fresh display items
                lastDisplayItemsCount = displayItems.size
                tabAdapter.updateDisplayItems(displayItems, selectedId)
            } else {
                // Only selection changed, just update that
                val displayItems = islandManager.createDisplayItems(tabs)
                tabAdapter.updateDisplayItems(displayItems, selectedId)
            }

            // Scroll to selected tab if selection changed.
            // Important: We differentiate between initial setup (fragment creation) and
            // actual tab selection to avoid jarring scroll animations during navigation.
            if (hasSelectionChanged && selectedId != null) {
                // On initial setup: instant scroll (no animation) - tab bar appears already positioned
                // On tab switch: smooth scroll (animated) - provides visual feedback to user
                scrollToSelectedTab(selectedId, animate = !isInitialSetup)
            }
            
            // Mark initial setup as complete after first update
            isInitialSetup = false

            animateVisibility(true)
        } else {
            animateVisibility(false)
        }
    }

    /**
     * Scrolls the RecyclerView to show the selected tab.
     *
     * This method intelligently handles scrolling to avoid jarring animations during
     * fragment transitions:
     * - On initial setup (fragment creation), scrolls instantly without animation
     * - On subsequent tab selections, uses smooth scroll animation for visual feedback
     *
     * The distinction is important because when navigating between fragments
     * (e.g., ComposeHomeFragment to BrowserFragment), the toolbar is recreated and
     * we want the tab bar to already be positioned at the selected tab without
     * showing a visible scrolling animation.
     *
     * @param selectedId The ID of the tab to scroll to
     * @param animate If true, uses smoothScrollToPosition (animated). If false, uses
     *                scrollToPosition (instant). Defaults to true for normal tab switches.
     */
    private fun scrollToSelectedTab(selectedId: String, animate: Boolean = true) {
        post {
            val displayItems = islandManager.createDisplayItems(currentTabs)
            var position = -1
            var isInGroup = false
            var tabIndexInGroup = -1
            
            // Find the position - could be a standalone tab or inside a group
            for (i in displayItems.indices) {
                when (val item = displayItems[i]) {
                    is TabPillItem.Tab -> {
                        if (item.session.id == selectedId) {
                            position = i
                            break
                        }
                    }
                    is TabPillItem.ExpandedIslandGroup -> {
                        val tabIndex = item.tabs.indexOfFirst { it.id == selectedId }
                        if (tabIndex >= 0) {
                            position = i
                            isInGroup = true
                            tabIndexInGroup = tabIndex
                            break
                        }
                    }
                    else -> {}
                }
            }

            if (position >= 0) {
                val layoutMgr = layoutManager as? LinearLayoutManager ?: return@post
                val viewWidth = width
                
                // Get the group container view if tab is in a group
                val containerView = layoutMgr.findViewByPosition(position)
                
                if (isInGroup && containerView != null) {
                    // Tab is inside an expanded group - need to find the specific tab view
                    // The group container has a LinearLayout with tab pills
                    val tabsContainer = containerView.findViewById<android.view.ViewGroup>(
                        R.id.islandTabsContainer
                    )
                    
                    if (tabsContainer != null && tabIndexInGroup < tabsContainer.childCount) {
                        // Get the specific tab view within the group
                        val tabView = tabsContainer.getChildAt(tabIndexInGroup)
                        
                        if (tabView != null) {
                            // Calculate the offset to center the specific tab
                            val tabViewLocation = IntArray(2)
                            tabView.getLocationInWindow(tabViewLocation)
                            
                            val containerLocation = IntArray(2)
                            containerView.getLocationInWindow(containerLocation)
                            
                            // Offset of the tab within the container
                            val tabOffsetInContainer = tabViewLocation[0] - containerLocation[0]
                            val tabWidth = tabView.width
                            
                            // Calculate centering offset
                            val centerOffset = (viewWidth - tabWidth) / 2
                            val totalOffset = centerOffset - tabOffsetInContainer
                            
                            if (animate) {
                                smoothScrollToPosition(position)
                                postDelayed({
                                    layoutMgr.scrollToPositionWithOffset(position, totalOffset)
                                }, 300)
                            } else {
                                layoutMgr.scrollToPositionWithOffset(position, totalOffset)
                            }
                            return@post
                        }
                    }
                }
                
                // Fallback: Regular tab or group container (if we couldn't find the specific tab)
                val itemView = containerView
                val itemWidth = itemView?.width ?: (viewWidth / 3)
                
                val centerOffset = (viewWidth - itemWidth) / 2
                val isFirstItem = position == 0
                val isLastItem = position == displayItems.size - 1
                
                val offset = when {
                    isFirstItem -> 0
                    isLastItem -> viewWidth - itemWidth
                    else -> centerOffset
                }
                
                if (animate) {
                    smoothScrollToPosition(position)
                    postDelayed({
                        layoutMgr.scrollToPositionWithOffset(position, offset)
                    }, 300)
                } else {
                    layoutMgr.scrollToPositionWithOffset(position, offset)
                }
            }
        }
    }

    /**
     * Expands a collapsed island if the given tab is inside it
     */
    private fun expandIslandIfTabInside(tabId: String) {
        val island = islandManager.getIslandForTab(tabId)
        if (island != null && island.isCollapsed) {
            islandManager.toggleIslandCollapse(island.id)
            refreshDisplay()
        }
    }

    /**
     * Records a parent-child relationship for potential automatic grouping
     */
    fun recordTabParent(childTabId: String, parentTabId: String) {
        pendingAutoGroups[childTabId] = parentTabId
    }

    /**
     * Attempts to automatically group a new tab with its parent
     */
    fun autoGroupNewTab(newTabId: String) {
        val parentTabId = pendingAutoGroups.remove(newTabId) ?: return

        // Try to add to parent's island
        val parentIsland = islandManager.getIslandForTab(parentTabId)
        if (parentIsland != null) {
            // Parent is already in an island, add child to same island
            islandManager.addTabToIsland(newTabId, parentIsland.id)
            // Refresh is handled by debounced change listener
        } else {
            // Parent is not in an island, create a new island for both
            // This implements feature #3: auto-group new tabs with parent
            CoroutineScope(Dispatchers.Main).launch {
                islandManager.createIsland(
                    tabIds = listOf(parentTabId, newTabId),
                    name = null
                )
                // Refresh is handled by debounced change listener
                showIslandCreatedFeedback()
            }
        }
    }

    /**
     * Manually creates an island from selected tabs
     */
    fun createIslandFromTabs(tabIds: List<String>, name: String? = null) {
        if (tabIds.size < 2) return

        CoroutineScope(Dispatchers.Main).launch {
            islandManager.createIsland(tabIds, name)
            // Refresh is handled by debounced change listener
            showIslandCreatedFeedback()
        }
    }

    /**
     * Groups tabs by domain
     */
    fun groupTabsByDomain() {
        val islands = islandManager.groupTabsByDomain(currentTabs)
        if (islands.isNotEmpty()) {
            // Refresh is handled by debounced change listener
            showIslandCreatedFeedback()
        }
    }

    private fun handleTabClose(tabId: String) {
        // Notify island manager to clean up
        islandManager.onTabClosed(tabId)

        // Notify parent component
        onTabClosed?.invoke(tabId)

        // Refresh display handled by debounced listener
        animateTabRemoval(tabId)
    }

    private fun handleIslandHeaderClick(islandId: String) {
        // Toggle collapse/expand
        islandManager.toggleIslandCollapse(islandId)

        // Force full refresh by clearing state and rebuilding display items
        lastTabIds = emptyList()
        lastDisplayItemsCount = 0

        // Create fresh display items with updated island state
        val displayItems = islandManager.createDisplayItems(currentTabs)
        lastDisplayItemsCount = displayItems.size
        lastTabIds = currentTabs.map { it.id }

        // Update adapter with new display items
        tabAdapter.updateDisplayItems(displayItems, selectedTabId)
    }

    private fun handleIslandLongPress(islandId: String) {
        // Show island options dialog (rename, ungroup, close all)
        showIslandOptionsDialog(islandId)
    }

    private fun handleIslandPlusClick(islandId: String) {
        // Notify parent to create a new tab and add it to this island
        onNewTabInIsland?.invoke(islandId)
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun handleTabUngroupFromIsland(tabId: String, islandId: String) {
        // Remove tab from island
        islandManager.removeTabFromIsland(tabId, islandId)
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        // Refresh is handled by debounced change listener
    }

    /**
     * Handles tab duplication request from context menu.
     *
     * Creates an exact duplicate of the specified tab with the same URL, profile,
     * and privacy mode. If the original tab is in a group, the duplicate is
     * automatically added to the same group right next to the original.
     *
     * Visual positioning:
     * - Duplicate appears immediately after the original in the tab bar
     * - This works because grouped tabs are displayed in the order specified
     *   by the group's tabIds array (see TabIslandManager.createDisplayItems)
     * - Position is set via UnifiedTabGroupManager.addTabToGroup(position = originalPos + 1)
     *
     * Tab properties copied:
     * - URL (content.url)
     * - Profile context (contextId)
     * - Privacy mode (content.private)
     *
     * The duplicate tab:
     * - Is NOT auto-selected (selectTab = false)
     * - Preserves the same profile as original
     * - Joins the same group if original is grouped
     *
     * @param tabId ID of the tab to duplicate
     */
    private fun handleTabDuplicate(tabId: String) {
        // Find the tab to duplicate
        val tabToDuplicate = currentTabs.find { it.id == tabId } ?: return

        // Create a new tab with the same URL and context (profile)
        val newTabId = context.components.tabsUseCases.addTab(
            url = tabToDuplicate.content.url,
            selectTab = false,  // Don't switch to the new tab
            private = tabToDuplicate.content.private,
            contextId = tabToDuplicate.contextId,
            parentId = null
        )

        // If the original tab is in a group, add the duplicate to the same group at the position right after the original
        // The tab bar displays grouped tabs in the order specified by the group's tabIds, so this will position it correctly
        CoroutineScope(Dispatchers.Main).launch {
            val unifiedManager = com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager.getInstance(context)
            val groupData = unifiedManager.getGroupForTab(tabId)
            if (groupData != null) {
                // Find the position of the original tab in the group
                val originalPosition = groupData.tabIds.indexOf(tabId)
                if (originalPosition != -1) {
                    // Add the duplicate right after the original (position + 1)
                    // This ensures it appears right next to the original in the tab bar
                    unifiedManager.addTabToGroup(newTabId, groupData.id, position = originalPosition + 1)
                } else {
                    // Fallback: add at the end if position not found
                    unifiedManager.addTabToGroup(newTabId, groupData.id)
                }
            }
        }

        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

        // Delegate to external callback if set
        onTabDuplicated?.invoke(newTabId)
    }

    private fun showIslandOptionsDialog(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle(island.name.ifBlank { " " })
            .setItems(
                arrayOf(
                    "Rename Island",
                    "Change Color",
                    "Move Group to Profile",
                    "Ungroup All Tabs",
                    "Close All Tabs"
                )
            ) { _, which ->
                when (which) {
                    0 -> showRenameIslandDialog(islandId)
                    1 -> showChangeColorDialog(islandId)
                    2 -> showMoveGroupToProfileDialog(islandId)
                    3 -> ungroupIsland(islandId)
                    4 -> closeAllTabsInIsland(islandId)
                }
            }
            .create()

        dialog.show()
    }

    private fun showRenameIslandDialog(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        val input = android.widget.EditText(context).apply {
            setText(if (island.name.isBlank()) "" else island.name)
            hint = "Enter group name"
            selectAll()
        }

        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle("Rename Island")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    islandManager.renameIsland(islandId, newName)
                    onIslandRenamed?.invoke(islandId, newName)
                    // Refresh is handled by debounced change listener
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Show keyboard
        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showChangeColorDialog(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return
        val currentColor = island.color

        val dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.colorRecyclerView)
        
        // Use expanded Material 3 color palette
        val colors = listOf(
            com.prirai.android.nira.theme.ColorConstants.TabGroups.RED,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.PINK,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.PURPLE,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.DEEP_PURPLE,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.INDIGO,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.BLUE,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.CYAN,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.TEAL,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.GREEN,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.LIGHT_GREEN,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.LIME,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.YELLOW,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.AMBER,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.ORANGE,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.DEEP_ORANGE,
            com.prirai.android.nira.theme.ColorConstants.TabGroups.BROWN
        )
        
        var selectedColorIndex = colors.indexOfFirst { it == currentColor }.takeIf { it >= 0 } ?: 0
        
        val colorAdapter = object : Adapter<ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
                val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_color_chip, parent, false)
                return object : ViewHolder(view) {}
            }
            
            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val card = holder.itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorCard)
                val colorView = holder.itemView.findViewById<View>(R.id.colorView)
                
                val colorInt = colors[position]
                colorView.setBackgroundColor(colorInt)
                card.isChecked = position == selectedColorIndex
                
                card.setOnClickListener {
                    val oldSelection = selectedColorIndex
                    selectedColorIndex = position
                    notifyItemChanged(oldSelection)
                    notifyItemChanged(selectedColorIndex)
                }
            }
            
            override fun getItemCount() = colors.size
        }
        
        recyclerView.adapter = colorAdapter
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Choose Island Color")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                islandManager.changeIslandColor(islandId, colors[selectedColorIndex])
                // Refresh is handled by debounced change listener
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun ungroupIsland(islandId: String) {
        islandManager.deleteIsland(islandId)
        // Refresh is handled by debounced change listener
    }

    private fun closeAllTabsInIsland(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        // Close all tabs in the island
        island.tabIds.forEach { tabId ->
            onTabClosed?.invoke(tabId)
        }

        // Clean up island
        islandManager.deleteIsland(islandId)
        // Refresh is handled by debounced change listener
    }
    
    private fun showMoveGroupToProfileDialog(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return
        val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(context)
        val profiles = profileManager.getAllProfiles()
        
        val items = profiles.map { "${it.emoji} ${it.name}" }.toMutableList()
        items.add("🕵️ Private")
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Move group to Profile")
            .setItems(items.toTypedArray()) { _, which ->
                val targetProfileId = if (which == items.size - 1) {
                    "private"
                } else {
                    profiles[which].id
                }
                
                // Migrate all tabs in the group
                val migratedCount = profileManager.migrateTabsToProfile(island.tabIds, targetProfileId)
                
                // Show confirmation
                android.widget.Toast.makeText(
                    context,
                    "Moved $migratedCount tabs to ${items[which]}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
                // Refresh is handled by debounced change listener
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun refreshDisplay() {
        android.util.Log.d(
            "EnhancedTabGroupView",
            "refreshDisplay: Starting refresh"
        )
        // Force refresh by clearing last state and recreating display items
        lastTabIds = emptyList()
        lastDisplayItemsCount = 0

        // Recreate display items from current state
        val displayItems = islandManager.createDisplayItems(currentTabs)

        lastDisplayItemsCount = displayItems.size
        lastTabIds = currentTabs.map { it.id }

        // Clear RecyclerView view pool to prevent ghost tabs
        recycledViewPool.clear()

        // Update adapter with notifyDataSetChanged to force complete refresh
        tabAdapter.updateDisplayItems(displayItems, selectedTabId)
        tabAdapter.notifyDataSetChanged()

    }

    private fun showIslandCreatedFeedback() {
        // Haptic feedback
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    private fun animateVisibility(shouldShow: Boolean) {
        if (shouldShow == (isVisible)) return

        if (shouldShow) {
            visibility = VISIBLE
            alpha = 0f
            translationY = -height.toFloat()
            scaleX = 0.95f
            scaleY = 0.95f

            animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        } else {
            animate()
                .alpha(0f)
                .translationY(-height.toFloat())
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(300)
                .withEndAction { visibility = GONE }
                .start()
        }
    }

    private fun animateSelection(tabId: String) {
        for (i in 0 until childCount) {
            val childView = getChildAt(i)
            val viewHolder = getChildViewHolder(childView)
            if (viewHolder is ModernTabPillAdapter.TabPillViewHolder) {
                if (viewHolder.isTabId(tabId)) {
                    childView.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(100)
                        .withEndAction {
                            childView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                    break
                }
            }
        }
    }

    private fun animateTabRemoval(tabId: String) {
        for (i in 0 until childCount) {
            val childView = getChildAt(i)
            val viewHolder = getChildViewHolder(childView)
            if (viewHolder is ModernTabPillAdapter.TabPillViewHolder) {
                if (viewHolder.isTabId(tabId)) {
                    childView.animate()
                        .alpha(0f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .translationX(childView.width.toFloat())
                        .setDuration(250)
                        .start()
                    break
                }
            }
        }
    }

    fun getCurrentTabCount(): Int = currentTabs.size

    fun getSelectedTabPosition(): Int {
        return currentTabs.indexOfFirst { it.id == selectedTabId }
    }

    fun getAllIslands(): List<TabIsland> {
        return islandManager.getAllIslands()
    }

    fun collapseAllIslands() {
        getAllIslands().forEach { island ->
            islandManager.collapseIsland(island.id)
        }
        refreshDisplay()
    }

    fun expandAllIslands() {
        getAllIslands().forEach { island ->
            islandManager.expandIsland(island.id)
        }
        refreshDisplay()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background using Material 3 surface color with tonal elevation overlay (3dp)
        val elevationDp = 3f * resources.displayMetrics.density
        val elevatedColor = elevationOverlayProvider
            .compositeOverlayWithThemeSurfaceColorIfNeeded(elevationDp)
        backgroundPaint.color = elevatedColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
    }
}
