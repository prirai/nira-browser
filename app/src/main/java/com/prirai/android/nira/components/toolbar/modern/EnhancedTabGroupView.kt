package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private var currentTabs = mutableListOf<SessionState>()
    private var selectedTabId: String? = null

    // Track last update to prevent unnecessary refreshes
    private var lastTabIds = emptyList<String>()
    private var lastSelectedId: String? = null
    private var lastDisplayItemsCount = 0

    // Track parent-child relationships for automatic grouping
    private val pendingAutoGroups = mutableMapOf<String, String>()

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

        // Layout configuration
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        setPadding(4, 2, 4, 2)

        // Set theme-aware background color
        val backgroundColor = if (isDarkMode()) {
            androidx.core.content.ContextCompat.getColor(context, android.R.color.background_dark)
        } else {
            androidx.core.content.ContextCompat.getColor(context, android.R.color.background_light)
        }
        setBackgroundColor(backgroundColor)

        elevation = 2f
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

    private fun setupIslandManager() {
        islandManager = TabIslandManager.getInstance(context)
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
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // Don't allow reordering - we only want grouping
                return false
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
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

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
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

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
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

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
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

                            post {
                                lastTabIds = emptyList()
                                refreshDisplay()
                            }
                        }
                    }

                    targetId.startsWith("collapsed_") -> {
                        // Dropped on collapsed island - add to island
                        val islandId = targetId.removePrefix("collapsed_")
                        if (currentIsland?.id != islandId) {
                            android.util.Log.d(
                                "EnhancedTabGroupView",
                                "handleDrop: Adding to collapsed island $islandId"
                            )
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            if (currentIsland != null) {
                                android.util.Log.d(
                                    "EnhancedTabGroupView",
                                    "handleDrop: Removing from current island ${currentIsland.id}"
                                )
                                islandManager.removeTabFromIsland(draggedTabId, currentIsland.id)
                            }
                            islandManager.addTabToIsland(draggedTabId, islandId)

                            post {
                                lastTabIds = emptyList()
                                refreshDisplay()
                            }
                        }
                    }

                    targetId == "ungroup" -> {
                        // Dropped in empty space - remove from island
                        if (currentIsland != null) {

                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            islandManager.removeTabFromIsland(draggedTabId, currentIsland.id)

                            post {
                                lastTabIds = emptyList()
                                refreshDisplay()
                            }
                        }
                    }

                    else -> {
                        // Dropped on another tab - create island or merge
                        val targetTab = currentTabs.find { it.id == targetId }
                        val draggedTab = currentTabs.find { it.id == draggedTabId }

                        if (targetTab != null && draggedTab != null) {
                            android.util.Log.d(
                                "EnhancedTabGroupView",
                                "handleDrop: Creating island with tabs $draggedTabId and $targetId"
                            )
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            if (currentIsland != null) {
                                android.util.Log.d(
                                    "EnhancedTabGroupView",
                                    "handleDrop: Removing from current island ${currentIsland.id}"
                                )
                                islandManager.removeTabFromIsland(draggedTabId, currentIsland.id)
                            }
                            createIslandAtPosition(draggedTabId, targetId)

                        }
                    }
                }
            }

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
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
        val insertPosition = minOf(pos1, pos2)

        when {
            island1 != null && island2 == null -> {

                // Tab1 in island, add tab2 to it at the correct position
                islandManager.addTabToIsland(tabId2, island1.id)
            }

            island2 != null && island1 == null -> {

                // Tab2 in island, add tab1 to it
                islandManager.addTabToIsland(tabId1, island2.id)
            }

            island1 == null && island2 == null -> {

                // Neither in island, create new one maintaining order
                // Create island with tabs in their current order
                val orderedTabs = if (pos1 < pos2) listOf(tabId1, tabId2) else listOf(tabId2, tabId1)

                islandManager.createIsland(orderedTabs)
            }

            island1 != null && island2 != null && island1.id != island2.id -> {

                // Both in different islands, merge into island2
                islandManager.addTabToIsland(tabId1, island2.id)
            }
            // If both in same island, do nothing
            else -> {

            }
        }


        refreshDisplay()
        showIslandCreatedFeedback()
    }

    fun setup(
        onTabSelected: (String) -> Unit,
        onTabClosed: (String) -> Unit,
        onIslandRenamed: ((String, String) -> Unit)? = null
    ) {
        this.onTabSelected = onTabSelected
        this.onTabClosed = onTabClosed
        this.onIslandRenamed = onIslandRenamed
        tabAdapter.updateCallbacks(
            onTabSelected,
            { tabId -> handleTabClose(tabId) },
            { islandId -> handleIslandHeaderClick(islandId) },
            { islandId -> handleIslandLongPress(islandId) }
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
            val titleChanged = oldTab?.content?.title != newTab.content.title
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

        val shouldShow = tabs.size > 1

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
            } else if (hasSelectionChanged) {
                // Only selection changed, just update that
                val displayItems = islandManager.createDisplayItems(tabs)
                tabAdapter.updateDisplayItems(displayItems, selectedId)
            }

            // Scroll to selected tab if selection changed
            if (hasSelectionChanged && selectedId != null) {
                scrollToSelectedTab(selectedId)
            }

            animateVisibility(true)
        } else {
            animateVisibility(false)
        }
    }

    /**
     * Scrolls to show the selected tab in the RecyclerView
     */
    private fun scrollToSelectedTab(selectedId: String) {
        post {
            val displayItems = islandManager.createDisplayItems(currentTabs)
            val position = displayItems.indexOfFirst { item ->
                when (item) {
                    is TabPillItem.Tab -> item.session.id == selectedId
                    is TabPillItem.ExpandedIslandGroup -> item.tabs.any { it.id == selectedId }
                    else -> false
                }
            }

            if (position >= 0) {
                smoothScrollToPosition(position)
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
            islandManager.addTabToIsland(newTabId, parentIsland.id)
            refreshDisplay()
        }
    }

    /**
     * Manually creates an island from selected tabs
     */
    fun createIslandFromTabs(tabIds: List<String>, name: String? = null) {
        if (tabIds.size < 2) return

        islandManager.createIsland(tabIds, name)
        refreshDisplay()
        showIslandCreatedFeedback()
    }

    /**
     * Groups tabs by domain
     */
    fun groupTabsByDomain() {
        val islands = islandManager.groupTabsByDomain(currentTabs)
        if (islands.isNotEmpty()) {
            refreshDisplay()
            showIslandCreatedFeedback()
        }
    }

    private fun handleTabClose(tabId: String) {
        // Notify island manager to clean up
        islandManager.onTabClosed(tabId)

        // Notify parent component
        onTabClosed?.invoke(tabId)

        // Refresh display
        refreshDisplay()
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

    private fun showIslandOptionsDialog(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle(island.name.ifBlank { " " })
            .setItems(
                arrayOf(
                    "Rename Island",
                    "Change Color",
                    "Ungroup All Tabs",
                    "Close All Tabs"
                )
            ) { _, which ->
                when (which) {
                    0 -> showRenameIslandDialog(islandId)
                    1 -> showChangeColorDialog(islandId)
                    2 -> ungroupIsland(islandId)
                    3 -> closeAllTabsInIsland(islandId)
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
                    refreshDisplay()
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

        val colors = TabIsland.DEFAULT_COLORS
        val colorNames = arrayOf(
            "Red", "Green", "Blue", "Orange", "Light Green",
            "Yellow", "Grey", "Pink", "Purple", "Cyan", "Lime", "Deep Orange"
        )

        // Create a custom adapter to show colored circles
        val adapter = object : android.widget.ArrayAdapter<String>(
            context,
            android.R.layout.select_dialog_item,
            colorNames
        ) {
            override fun getView(
                position: Int,
                convertView: android.view.View?,
                parent: android.view.ViewGroup
            ): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)

                // Create colored indicator
                val colorCircle = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(colors[position])
                    setSize(48, 48)
                }

                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(colorCircle, null, null, null)
                textView.compoundDrawablePadding = 32

                return view
            }
        }

        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle("Choose Island Color")
            .setAdapter(adapter) { _, which ->
                islandManager.changeIslandColor(islandId, colors[which])
                refreshDisplay()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun ungroupIsland(islandId: String) {
        islandManager.deleteIsland(islandId)
        refreshDisplay()
    }

    private fun closeAllTabsInIsland(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        // Close all tabs in the island
        island.tabIds.forEach { tabId ->
            onTabClosed?.invoke(tabId)
        }

        // Clean up island
        islandManager.deleteIsland(islandId)
        refreshDisplay()
    }


    private fun refreshDisplay() {
        android.util.Log.d(
            "EnhancedTabGroupView",
            "refreshDisplay: Starting refresh, currentTabs.size=${currentTabs.size}"
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
        if (shouldShow == (visibility == VISIBLE)) return

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

        // Draw subtle background gradient
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            intArrayOf(
                ContextCompat.getColor(context, android.R.color.background_light),
                ContextCompat.getColor(context, android.R.color.background_light)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            shader = gradient
            alpha = 50
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
