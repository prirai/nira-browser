package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import mozilla.components.browser.state.state.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced adapter for Tab Islands with beautiful tab pills, island headers,
 * colored indicators, and collapse/expand functionality.
 */
class ModernTabPillAdapter(
    private var onTabClick: (String) -> Unit,
    private var onTabClose: (String) -> Unit,
    private var onIslandHeaderClick: (String) -> Unit = {},
    private var onIslandLongPress: (String) -> Unit = {},
    private var onIslandPlusClick: (String) -> Unit = {},
    private var onTabUngroupFromIsland: ((String, String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var displayItems = mutableListOf<TabPillItem>()
    private var selectedTabId: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val VIEW_TYPE_TAB = 0
        private const val VIEW_TYPE_ISLAND_HEADER = 1
        private const val VIEW_TYPE_COLLAPSED_ISLAND = 2
        private const val VIEW_TYPE_EXPANDED_ISLAND_GROUP = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is TabPillItem.Tab -> VIEW_TYPE_TAB
            is TabPillItem.IslandHeader -> VIEW_TYPE_ISLAND_HEADER
            is TabPillItem.CollapsedIsland -> VIEW_TYPE_COLLAPSED_ISLAND
            is TabPillItem.ExpandedIslandGroup -> VIEW_TYPE_EXPANDED_ISLAND_GROUP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TAB -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.modern_tab_pill_item, parent, false)
                TabPillViewHolder(view)
            }

            VIEW_TYPE_ISLAND_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.tab_island_header_item, parent, false)
                IslandHeaderViewHolder(view)
            }

            VIEW_TYPE_COLLAPSED_ISLAND -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.tab_island_collapsed_item, parent, false)
                CollapsedIslandViewHolder(view)
            }

            VIEW_TYPE_EXPANDED_ISLAND_GROUP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.tab_island_group_item, parent, false)
                ExpandedIslandGroupViewHolder(view)
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is TabPillItem.Tab -> {
                val isSelected = item.session.id == selectedTabId
                (holder as TabPillViewHolder).bind(item, isSelected)
            }

            is TabPillItem.IslandHeader -> {
                (holder as IslandHeaderViewHolder).bind(item.island)
            }

            is TabPillItem.CollapsedIsland -> {
                (holder as CollapsedIslandViewHolder).bind(item.island, item.tabCount)
            }

            is TabPillItem.ExpandedIslandGroup -> {
                val viewHolder = holder as ExpandedIslandGroupViewHolder
                viewHolder.bind(item.island, item.tabs)
                // Update selection state for tabs in the group
                if (selectedTabId != null) {
                    viewHolder.updateTabSelection(selectedTabId!!, item.island)
                }
            }
        }
    }

    override fun getItemCount(): Int = displayItems.size

    fun updateDisplayItems(items: List<TabPillItem>, selectedId: String?) {

        val oldSelectedId = selectedTabId
        selectedTabId = selectedId

        // Check if items actually changed
        val itemsChanged = items.size != displayItems.size ||
                items.zip(displayItems).any { (new, old) -> !areItemsSame(new, old) }

        // If only selection changed, just update affected items
        if (!itemsChanged && oldSelectedId != selectedId) {
            android.util.Log.d(
                "ModernTabPillAdapter",
                "updateDisplayItems: Only selection changed, updating affected items"
            )
            // Find and update only the selected/deselected items
            displayItems.forEachIndexed { index, item ->
                when (item) {
                    is TabPillItem.Tab -> {
                        val wasSelected = item.session.id == oldSelectedId
                        val isSelected = item.session.id == selectedId
                        if (wasSelected || isSelected) {
                            notifyItemChanged(index)
                        }
                    }

                    is TabPillItem.ExpandedIslandGroup -> {
                        // Check if any tab in the group was selected/deselected
                        val hasSelectedTab = item.tabs.any { it.id == oldSelectedId || it.id == selectedId }
                        if (hasSelectedTab) {
                            notifyItemChanged(index)
                        }
                    }

                    else -> {}
                }
            }
            return
        }

        // If nothing changed at all, skip update
        if (!itemsChanged && oldSelectedId == selectedId) {

            return
        }

        val oldSize = displayItems.size

        displayItems.clear()
        displayItems.addAll(items)

        when {
            oldSize == 0 && items.isNotEmpty() -> {

                notifyItemRangeInserted(0, items.size)
            }

            oldSize > items.size -> {
                android.util.Log.d(
                    "ModernTabPillAdapter",
                    "updateDisplayItems: Items removed, notifying range changed and removed"
                )
                notifyItemRangeChanged(0, items.size)
                notifyItemRangeRemoved(items.size, oldSize - items.size)
            }

            oldSize < items.size -> {
                android.util.Log.d(
                    "ModernTabPillAdapter",
                    "updateDisplayItems: Items added, notifying range changed and inserted"
                )
                notifyItemRangeChanged(0, oldSize)
                notifyItemRangeInserted(oldSize, items.size - oldSize)
            }

            else -> {

                notifyItemRangeChanged(0, items.size)
            }
        }


    }

    private fun areItemsSame(item1: TabPillItem, item2: TabPillItem): Boolean {
        // Items are different if they're different types (e.g., Header vs CollapsedIsland)
        if (item1::class != item2::class) return false

        return when {
            item1 is TabPillItem.Tab && item2 is TabPillItem.Tab ->
                item1.session.id == item2.session.id &&
                        item1.islandId == item2.islandId &&
                        item1.islandColor == item2.islandColor &&
                        item1.session.content.title == item2.session.content.title &&
                        item1.session.content.url == item2.session.content.url &&
                        item1.session.content.loading == item2.session.content.loading

            item1 is TabPillItem.IslandHeader && item2 is TabPillItem.IslandHeader ->
                item1.island.id == item2.island.id &&
                        item1.island.isCollapsed == item2.island.isCollapsed &&
                        item1.island.name == item2.island.name &&
                        item1.island.color == item2.island.color

            item1 is TabPillItem.CollapsedIsland && item2 is TabPillItem.CollapsedIsland ->
                item1.island.id == item2.island.id &&
                        item1.tabCount == item2.tabCount &&
                        item1.island.name == item2.island.name &&
                        item1.island.color == item2.island.color

            item1 is TabPillItem.ExpandedIslandGroup && item2 is TabPillItem.ExpandedIslandGroup ->
                item1.island.id == item2.island.id &&
                        item1.tabs.map { it.id } == item2.tabs.map { it.id } &&
                        item1.island.name == item2.island.name &&
                        item1.island.color == item2.island.color &&
                        item1.tabs.zip(item2.tabs).all { (tab1, tab2) ->
                            tab1.content.title == tab2.content.title &&
                                    tab1.content.url == tab2.content.url &&
                                    tab1.content.loading == tab2.content.loading
                        }

            else -> false
        }
    }

    fun moveTab(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                displayItems[i] = displayItems[i + 1].also { displayItems[i + 1] = displayItems[i] }
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                displayItems[i] = displayItems[i - 1].also { displayItems[i - 1] = displayItems[i] }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun updateCallbacks(
        onTabClick: (String) -> Unit,
        onTabClose: (String) -> Unit,
        onIslandHeaderClick: (String) -> Unit = {},
        onIslandLongPress: (String) -> Unit = {},
        onIslandPlusClick: (String) -> Unit = {},
        onTabUngroupFromIsland: ((String, String) -> Unit)? = null
    ) {
        this.onTabClick = onTabClick
        this.onTabClose = onTabClose
        this.onIslandHeaderClick = onIslandHeaderClick
        this.onIslandLongPress = onIslandLongPress
        this.onIslandPlusClick = onIslandPlusClick
        this.onTabUngroupFromIsland = onTabUngroupFromIsland
    }

    // ViewHolder for regular tabs
    inner class TabPillViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.tabPillCard)
        private val faviconView: ImageView = itemView.findViewById(R.id.tabFavicon)
        private val titleView: TextView = itemView.findViewById(R.id.tabTitle)
        private val closeButton: ImageView = itemView.findViewById(R.id.tabCloseButton)
        private val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)

        private var currentTabId: String? = null
        private var isDragging = false

        fun bind(item: TabPillItem.Tab, isSelected: Boolean) {
            val tab = item.session
            currentTabId = tab.id

            // Set title with smart truncation
            // Show title if available, only show "Loading..." when actively loading a real URL
            val isRealUrl = !tab.content.url.isNullOrBlank() &&
                    !tab.content.url.startsWith("about:")
            val title = when {
                !tab.content.title.isNullOrBlank() -> tab.content.title
                tab.content.loading && isRealUrl -> "Loading..."
                !tab.content.url.isNullOrBlank() && !tab.content.url.startsWith("about:") -> tab.content.url
                else -> "New Tab"
            }
            titleView.text = title

            // Load favicon
            loadFavicon(tab)

            // Apply styling with island color if applicable
            applyPillStyling(isSelected, item)

            // Setup interactions
            cardView.setOnClickListener {
                onTabClick(tab.id)
                animateClick()
                vibrateHaptic()
            }

            // Add swipe-up gesture for delete
            setupStandaloneTabSwipeGesture(tab.id)

            // Add long-press for context menu
            cardView.setOnLongClickListener {
                // Don't show menu if we're dragging
                if (!isDragging) {
                    vibrateHaptic()
                    showStandaloneTabContextMenu(cardView, tab.id)
                }
                true
            }

            closeButton.setOnClickListener {
                onTabClose(tab.id)
                animateClose()
                vibrateHaptic()
            }
        }

        private fun setupStandaloneTabSwipeGesture(tabId: String) {
            var startY = 0f
            var isDragging = false

            cardView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        false
                    }

                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaY = startY - event.rawY

                        if (deltaY > 20 && !isDragging) {
                            isDragging = true
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }

                        if (isDragging) {
                            // Visual feedback during drag
                            val progress = (deltaY / 100f).coerceIn(0f, 1f)
                            cardView.scaleX = 1f - (progress * 0.2f)
                            cardView.scaleY = 1f - (progress * 0.2f)
                            cardView.rotation = -progress * 10f
                            cardView.alpha = 1f - (progress * 0.3f)
                            true
                        } else {
                            false
                        }
                    }

                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            val deltaY = startY - event.rawY
                            if (deltaY > 100) {
                                // Trigger delete animation
                                v.performClick()
                                animateStandaloneTabDelete()
                            } else {
                                // Spring back
                                cardView.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .rotation(0f)
                                    .alpha(1f)
                                    .setDuration(200)
                                    .start()
                            }
                            isDragging = false
                            true
                        } else {
                            v.performClick()
                            false
                        }
                    }

                    else -> false
                }
            }
        }

        private fun showStandaloneTabContextMenu(anchorView: View, tabId: String) {
            val wrapper = android.view.ContextThemeWrapper(itemView.context, R.style.RoundedPopupMenu)
            val popupMenu = android.widget.PopupMenu(wrapper, anchorView, android.view.Gravity.NO_GRAVITY, 
                0, R.style.RoundedPopupMenu)
            
            // Add menu items for standalone tabs with icons
            val duplicateItem = popupMenu.menu.add(0, 1, 0, "Duplicate Tab")
            duplicateItem.setIcon(android.R.drawable.ic_menu_add)
            
            val closeItem = popupMenu.menu.add(0, 2, 1, "Close Tab")
            closeItem.setIcon(android.R.drawable.ic_menu_close_clear_cancel)
            
            // Force icons to show
            try {
                val popup = android.widget.PopupMenu::class.java.getDeclaredField("mPopup")
                popup.isAccessible = true
                val menu = popup.get(popupMenu)
                menu.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                    .invoke(menu, true)
            } catch (e: Exception) {
                // Ignore if reflection fails
            }
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        // Duplicate tab
                        itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        // TODO: Implement duplicate tab functionality via callback
                        true
                    }
                    2 -> {
                        // Close tab with animation
                        itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        animateStandaloneTabDelete()
                        true
                    }
                    else -> false
                }
            }
            
            // Show menu above the anchor view
            popupMenu.gravity = android.view.Gravity.TOP
            popupMenu.show()
        }

        private fun animateStandaloneTabDelete() {
            // Multi-stage breaking apart animation for standalone tabs
            // Stage 1: Shake and lift
            cardView.animate()
                .translationY(-20f)
                .rotationBy(5f)
                .setDuration(100)
                .withEndAction {
                    // Stage 2: Shake the other way
                    cardView.animate()
                        .rotationBy(-10f)
                        .setDuration(100)
                        .withEndAction {
                            // Stage 3: Break apart - fly up with rotation
                            cardView.animate()
                                .translationY(-500f)
                                .rotation(-30f)
                                .scaleX(0.4f)
                                .scaleY(0.4f)
                                .alpha(0f)
                                .setDuration(300)
                                .setInterpolator(android.view.animation.AccelerateInterpolator())
                                .withEndAction {
                                    currentTabId?.let { onTabClose(it) }
                                }
                                .start()
                        }
                        .start()
                }
                .start()
        }

        private fun loadFavicon(tab: SessionState) {
            // Try to get favicon from tab content first
            val existingIcon = tab.content.icon
            if (existingIcon != null) {
                faviconView.setImageBitmap(existingIcon)
                return
            }

            // Load from cache (memory and disk) or generate
            scope.launch {
                try {
                    val context = itemView.context
                    val faviconCache = com.prirai.android.nira.utils.FaviconCache.getInstance(context)

                    // This will check memory cache first, then disk cache
                    val cachedFavicon = faviconCache.loadFavicon(tab.content.url ?: "")

                    if (cachedFavicon != null) {
                        faviconView.setImageBitmap(cachedFavicon)
                    } else {
                        // Generate favicon if not in cache
                        val favicon = withContext(Dispatchers.IO) {
                            generateBeautifulFavicon(tab.content.url ?: "", itemView.context)
                        }
                        faviconView.setImageBitmap(favicon)
                    }
                } catch (e: Exception) {
                    faviconView.setImageResource(R.drawable.ic_language)
                }
            }
        }

        private fun applyPillStyling(isSelected: Boolean, item: TabPillItem.Tab) {
            val islandColor = item.islandColor
            val isGuestTab = item.session.contextId == null
            
            // Get default background color
            val backgroundColor = if (isDarkMode()) {
                ContextCompat.getColor(itemView.context, android.R.color.background_dark)
            } else {
                ContextCompat.getColor(itemView.context, android.R.color.background_light)
            }
            
            // Guest tab color: distinctive orange/amber
            val guestTabColor = 0xFFFF6B35.toInt()

            if (isSelected) {
                // Selected: Show prominent border with island/guest color
                val borderColor = when {
                    isGuestTab -> guestTabColor
                    islandColor != null -> islandColor
                    else -> 0xFF6200EE.toInt()
                }
                val gradient = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(backgroundColor)
                    // Consistent border width for all selected tabs (3dp)
                    val strokeWidth = (3 * itemView.resources.displayMetrics.density).toInt()
                    setStroke(strokeWidth, borderColor)
                }
                cardView.background = gradient
                cardView.elevation = 8f
                cardView.scaleX = 1.0f
                cardView.scaleY = 1.0f

                val textColor = if (isDarkMode()) {
                    ContextCompat.getColor(itemView.context, android.R.color.primary_text_dark_nodisable)
                } else {
                    ContextCompat.getColor(itemView.context, android.R.color.primary_text_light_nodisable)
                }
                titleView.setTextColor(textColor)
                selectionIndicator.visibility = View.GONE

                cardView.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                cardView.clipToOutline = true

            } else {
                // Unselected: Subtle pill with guest/island color hint
                val gradient = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(backgroundColor)
                    when {
                        isGuestTab -> {
                            // Guest tabs: orange border (2dp, same as island tabs)
                            val strokeWidth = (2 * itemView.resources.displayMetrics.density).toInt()
                            setStroke(strokeWidth, guestTabColor)
                        }
                        islandColor != null -> {
                            // Island tabs: subtle colored border (2dp)
                            val strokeWidth = (2 * itemView.resources.displayMetrics.density).toInt()
                            setStroke(strokeWidth, islandColor)
                        }
                        else -> {
                            // Regular tabs: very subtle border (1dp)
                            val strokeWidth = (1 * itemView.resources.displayMetrics.density).toInt()
                            setStroke(strokeWidth, 0x30FFFFFF)
                        }
                    }
                }
                cardView.background = gradient
                cardView.elevation = 4f
                cardView.scaleX = 1f
                cardView.scaleY = 1f

                val textColor = if (isDarkMode()) {
                    ContextCompat.getColor(itemView.context, android.R.color.primary_text_dark_nodisable)
                } else {
                    ContextCompat.getColor(itemView.context, android.R.color.primary_text_light_nodisable)
                }
                titleView.setTextColor(textColor)
                selectionIndicator.visibility = View.GONE

                cardView.clipToOutline = true
            }
        }

        private fun animateClick() {
            // Quick, subtle click animation
            cardView.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(50)
                .withEndAction {
                    cardView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(50)
                        .start()
                }
                .start()
        }

        private fun animateClose() {
            cardView.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .rotationY(90f)
                .setDuration(300)
                .start()
        }

        private fun vibrateHaptic() {
            itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }

        fun isTabId(tabId: String): Boolean = currentTabId == tabId

        private fun isDarkMode(): Boolean {
            return when (itemView.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
                else -> false
            }
        }
    }

    // ViewHolder for island headers
    inner class IslandHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.islandName)
        private val colorIndicator: View = itemView.findViewById(R.id.islandColorIndicator)
        private val collapseButton: ImageView = itemView.findViewById(R.id.islandCollapseButton)
        private val containerCard: CardView = itemView.findViewById(R.id.islandHeaderCard)

        fun bind(island: TabIsland) {
            // Keep name blank if not set
            nameText.text = island.name
            colorIndicator.setBackgroundColor(island.color)

            // Update collapse button icon
            collapseButton.setImageResource(
                if (island.isCollapsed) R.drawable.ic_expand_more
                else R.drawable.ic_expand_less
            )

            // Click to collapse/expand
            containerCard.setOnClickListener {
                onIslandHeaderClick(island.id)
                animateHeaderClick()
            }

            // Long press for rename/options
            containerCard.setOnLongClickListener {
                onIslandLongPress(island.id)
                itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                true
            }
        }

        private fun animateHeaderClick() {
            containerCard.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    containerCard.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    // ViewHolder for collapsed islands
    inner class CollapsedIslandViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.collapsedIslandName)
        private val tabCountText: TextView = itemView.findViewById(R.id.collapsedIslandTabCount)
        private val containerCard: CardView = itemView.findViewById(R.id.collapsedIslandCard)

        fun bind(island: TabIsland, tabCount: Int) {
            // Keep name blank if not set
            nameText.text = island.name
            tabCountText.text = "$tabCount"

            // Apply island color
            val gradient = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(island.color)
                alpha = 255
            }
            containerCard.background = gradient
            containerCard.elevation = 8f

            // Click to expand
            containerCard.setOnClickListener {
                onIslandHeaderClick(island.id)
                animateExpand()
            }

            // Long press for options
            containerCard.setOnLongClickListener {
                onIslandLongPress(island.id)
                itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                true
            }
        }

        private fun animateExpand() {
            containerCard.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(100)
                .withEndAction {
                    containerCard.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    // ViewHolder for expanded island groups (header + tabs as one unit)
    inner class ExpandedIslandGroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.islandName)
        private val colorIndicator: View = itemView.findViewById(R.id.islandColorIndicator)
        private val collapseButton: ImageView = itemView.findViewById(R.id.islandCollapseButton)
        private val plusButton: ImageView = itemView.findViewById(R.id.islandPlusButton)
        private val headerSection: ViewGroup = itemView.findViewById(R.id.islandHeaderSection)
        private val tabsContainer: ViewGroup = itemView.findViewById(R.id.islandTabsContainer)
        private val groupCard: CardView = itemView.findViewById(R.id.islandGroupCard)

        fun bind(island: TabIsland, tabs: List<SessionState>) {
            // Keep name blank if not set
            nameText.text = island.name
            colorIndicator.setBackgroundColor(island.color)

            // Update collapse button icon
            collapseButton.setImageResource(R.drawable.ic_expand_less)

            // Setup header click for collapse
            headerSection.setOnClickListener {
                onIslandHeaderClick(island.id)
                animateHeaderClick()
            }

            // Long press for rename/options
            headerSection.setOnLongClickListener {
                onIslandLongPress(island.id)
                itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                true
            }

            // Setup plus button to add new tab to island
            plusButton.setOnClickListener {
                onIslandPlusClick(island.id)
                animatePlusButtonClick()
                itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }

            // Clear previous tabs
            tabsContainer.removeAllViews()

            // Add each tab to the container
            tabs.forEachIndexed { index, tab ->
                val isLastTab = index == tabs.size - 1
                val tabView = createTabPillView(tab, island, index, isLastTab)
                // Store tab ID as tag for later updates
                tabView.tag = tab.id
                tabsContainer.addView(tabView)
            }
        }

        fun updateTabSelection(tabId: String, island: TabIsland) {
            // Update selection state for tabs in this group
            val tabCount = tabsContainer.childCount
            for (i in 0 until tabCount) {
                val tabView = tabsContainer.getChildAt(i)
                val storedTabId = tabView.tag as? String
                if (storedTabId != null) {
                    val tabContent: ViewGroup = tabView.findViewById(R.id.tabPillContent)
                    val titleView: TextView = tabView.findViewById(R.id.tabTitle)
                    val faviconView: ImageView = tabView.findViewById(R.id.tabFavicon)

                    val isSelected = storedTabId == tabId
                    val isLastTab = i == tabCount - 1
                    
                    if (isSelected) {
                        // Selected tab: show border with rounded corners on last tab
                        val gradient = GradientDrawable().apply {
                            setColor(Color.TRANSPARENT)
                            // Prominent border for selected state (3dp stroke width)
                            val strokeWidth = (3 * itemView.resources.displayMetrics.density).toInt()
                            setStroke(strokeWidth, island.color)
                            // Only round top-right and bottom-right corners for last tab
                            if (isLastTab) {
                                val radius = 12f * itemView.resources.displayMetrics.density
                                cornerRadii = floatArrayOf(
                                    0f, 0f,              // top-left
                                    radius, radius,      // top-right (rounded)
                                    radius, radius,      // bottom-right (rounded)
                                    0f, 0f               // bottom-left
                                )
                            }
                        }
                        tabContent.background = gradient
                        val textColor = if (isDarkMode()) {
                            ContextCompat.getColor(itemView.context, android.R.color.primary_text_dark_nodisable)
                        } else {
                            ContextCompat.getColor(itemView.context, android.R.color.primary_text_light_nodisable)
                        }
                        titleView.setTextColor(textColor)
                        titleView.setTypeface(null, android.graphics.Typeface.BOLD)
                        faviconView.alpha = 1.0f
                    } else {
                        // Unselected tab: transparent background
                        val gradient = GradientDrawable().apply {
                            setColor(Color.TRANSPARENT)
                            // Only round top-right and bottom-right corners for last tab
                            if (isLastTab) {
                                val radius = 12f * itemView.resources.displayMetrics.density
                                cornerRadii = floatArrayOf(
                                    0f, 0f,              // top-left
                                    radius, radius,      // top-right (rounded)
                                    radius, radius,      // bottom-right (rounded)
                                    0f, 0f               // bottom-left
                                )
                            }
                        }
                        tabContent.background = gradient
                        val textColor = if (isDarkMode()) {
                            ContextCompat.getColor(itemView.context, android.R.color.primary_text_dark_nodisable)
                        } else {
                            ContextCompat.getColor(itemView.context, android.R.color.primary_text_light_nodisable)
                        }
                        titleView.setTextColor(textColor)
                        titleView.setTypeface(null, android.graphics.Typeface.NORMAL)
                        faviconView.alpha = 0.8f
                    }
                }
            }
        }

        private fun createTabPillView(tab: SessionState, island: TabIsland, index: Int, isLastTab: Boolean): View {
            val tabView = LayoutInflater.from(itemView.context)
                .inflate(R.layout.tab_pill_in_group, tabsContainer, false)

            val separator: View = tabView.findViewById(R.id.tabSeparator)
            val tabContent: ViewGroup = tabView.findViewById(R.id.tabPillContent)
            val faviconView: ImageView = tabView.findViewById(R.id.tabFavicon)
            val titleView: TextView = tabView.findViewById(R.id.tabTitle)

            // Show separator for all tabs (acts as divider from header/previous tab)
            separator.visibility = View.VISIBLE

            // Set title
            // Show title if available, only show "Loading..." when actively loading a real URL
            val isRealUrl = !tab.content.url.isNullOrBlank() &&
                    !tab.content.url.startsWith("about:")
            val title = when {
                !tab.content.title.isNullOrBlank() -> tab.content.title
                tab.content.loading && isRealUrl -> "Loading..."
                !tab.content.url.isNullOrBlank() && !tab.content.url.startsWith("about:") -> tab.content.url
                else -> "New Tab"
            }
            titleView.text = title

            // Load favicon - check content icon, then cache, then generate
            loadFaviconForGroupTab(tab, faviconView)

            // Apply styling based on selection
            // NOTE: Last tab is NO LONGER rounded because plus button is at the end
            val isSelected = tab.id == selectedTabId
            if (isSelected) {
                // Selected tab: show border but DON'T round last tab anymore
                val gradient = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    // Prominent border for selected state (3dp stroke width)
                    val strokeWidth = (3 * itemView.resources.displayMetrics.density).toInt()
                    setStroke(strokeWidth, island.color)
                    // No rounding needed - plus button is now at the end
                }
                tabContent.background = gradient
                val textColor = if (isDarkMode()) {
                    ContextCompat.getColor(itemView.context, android.R.color.primary_text_dark_nodisable)
                } else {
                    ContextCompat.getColor(itemView.context, android.R.color.primary_text_light_nodisable)
                }
                titleView.setTextColor(textColor)
                titleView.setTypeface(null, android.graphics.Typeface.BOLD)
                faviconView.alpha = 1.0f
            } else {
                // Unselected tab: transparent background
                val gradient = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    // No rounding needed - plus button is now at the end
                }
                tabContent.background = gradient
                val textColor = if (isDarkMode()) {
                    ContextCompat.getColor(itemView.context, android.R.color.primary_text_dark_nodisable)
                } else {
                    ContextCompat.getColor(itemView.context, android.R.color.primary_text_light_nodisable)
                }
                titleView.setTextColor(textColor)
                titleView.setTypeface(null, android.graphics.Typeface.NORMAL)
                faviconView.alpha = 0.8f
            }

            // Make view clickable
            tabContent.isClickable = true
            tabContent.isFocusable = true

            // Regular click handler - simpler approach
            tabContent.setOnClickListener {
                onTabClick(tab.id)
                itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }

            // Add swipe-up gesture for delete OR long-press drag to ungroup
            setupTabGestures(tabView, tabContent, tab.id, island.id)

            return tabView
        }

        private fun loadFaviconForGroupTab(tab: SessionState, faviconView: ImageView) {
            // Try to get favicon from tab content first
            val existingIcon = tab.content.icon
            if (existingIcon != null) {
                faviconView.setImageBitmap(existingIcon)
                return
            }

            // Load from cache (memory and disk) or generate
            scope.launch {
                try {
                    val context = itemView.context
                    val faviconCache = com.prirai.android.nira.utils.FaviconCache.getInstance(context)

                    // This will check memory cache first, then disk cache
                    val cachedFavicon = faviconCache.loadFavicon(tab.content.url ?: "")

                    if (cachedFavicon != null) {
                        faviconView.setImageBitmap(cachedFavicon)
                    } else {
                        // Generate favicon if not in cache
                        val favicon = withContext(Dispatchers.IO) {
                            generateBeautifulFavicon(tab.content.url ?: "", itemView.context)
                        }
                        faviconView.setImageBitmap(favicon)
                    }
                } catch (e: Exception) {
                    faviconView.setImageResource(R.drawable.ic_language)
                }
            }
        }

        private fun setupTabGestures(tabView: View, tabContent: ViewGroup, tabId: String, islandId: String) {
            var startY = 0f
            var startX = 0f
            var isDragging = false
            var longPressTimer: android.os.Handler? = null

            tabContent.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        startX = event.rawX
                        isDragging = false
                        
                        // Start long press timer for context menu
                        longPressTimer = android.os.Handler(android.os.Looper.getMainLooper())
                        longPressTimer?.postDelayed({
                            if (!isDragging) {
                                // Show context menu
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                showTabContextMenu(tabView, tabId, islandId)
                            }
                        }, 1000)  // Increased to 1000ms to avoid conflict with swipe
                        false
                    }

                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaY = startY - event.rawY
                        val deltaX = Math.abs(event.rawX - startX)

                        // Check if user is trying to swipe up for delete
                        if (deltaY > 20 && deltaX < 50 && !isDragging) {
                            isDragging = true
                            longPressTimer?.removeCallbacksAndMessages(null)
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }

                        if (isDragging) {
                            // Visual feedback during drag - shake and scale
                            val progress = (deltaY / 100f).coerceIn(0f, 1f)
                            tabView.scaleX = 1f - (progress * 0.2f)
                            tabView.scaleY = 1f - (progress * 0.2f)
                            tabView.rotation = -progress * 10f
                            tabView.alpha = 1f - (progress * 0.3f)
                            true
                        } else {
                            // Cancel long press if moved too much
                            if (deltaX > 20 || Math.abs(deltaY) > 20) {
                                longPressTimer?.removeCallbacksAndMessages(null)
                            }
                            false
                        }
                    }

                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        longPressTimer?.removeCallbacksAndMessages(null)
                        
                        if (isDragging) {
                            val deltaY = startY - event.rawY
                            if (deltaY > 100) {
                                // Delete with animation
                                animateTabDelete(tabView, tabId)
                            } else {
                                resetTabVisualState(tabView)
                            }
                            true
                        } else {
                            v.performClick()
                            false
                        }
                    }

                    else -> false
                }
            }
        }

        private fun showTabContextMenu(anchorView: View, tabId: String, islandId: String) {
            val wrapper = android.view.ContextThemeWrapper(itemView.context, R.style.RoundedPopupMenu)
            val popupMenu = android.widget.PopupMenu(wrapper, anchorView, android.view.Gravity.NO_GRAVITY,
                0, R.style.RoundedPopupMenu)
            
            // Add menu items with icons
            val duplicateItem = popupMenu.menu.add(0, 1, 0, "Duplicate Tab")
            duplicateItem.setIcon(android.R.drawable.ic_menu_add)
            
            val removeItem = popupMenu.menu.add(0, 2, 1, "Remove from Group")
            removeItem.setIcon(android.R.drawable.ic_menu_revert)
            
            val closeItem = popupMenu.menu.add(0, 3, 2, "Close Tab")
            closeItem.setIcon(android.R.drawable.ic_menu_close_clear_cancel)
            
            // Force icons to show
            try {
                val popup = android.widget.PopupMenu::class.java.getDeclaredField("mPopup")
                popup.isAccessible = true
                val menu = popup.get(popupMenu)
                menu.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                    .invoke(menu, true)
            } catch (e: Exception) {
                // Ignore if reflection fails
            }
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        // Duplicate tab
                        itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        // TODO: Implement duplicate tab functionality via callback
                        true
                    }
                    2 -> {
                        // Remove from group
                        itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        onTabUngroupFromIsland?.invoke(tabId, islandId)
                        true
                    }
                    3 -> {
                        // Close tab
                        itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        onTabClose(tabId)
                        true
                    }
                    else -> false
                }
            }
            
            // Show menu above the anchor view
            popupMenu.gravity = android.view.Gravity.TOP
            popupMenu.show()
        }

        private fun animateTabDelete(tabView: View, tabId: String) {
            // Multi-stage "breaking apart" animation optimized for visibility
            // Even if clipped by EngineView, the shake and scale are visible
            
            // Stage 1: Shake left with scale
            tabView.animate()
                .translationX(-15f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .rotation(-8f)
                .setDuration(80)
                .withEndAction {
                    // Stage 2: Shake right harder
                    tabView.animate()
                        .translationX(15f)
                        .rotation(8f)
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(80)
                        .withEndAction {
                            // Stage 3: Break apart - dramatic scale down with rotation
                            tabView.animate()
                                .translationY(-400f)
                                .translationX(0f)
                                .rotation(-45f)
                                .scaleX(0.2f)
                                .scaleY(0.2f)
                                .alpha(0f)
                                .setDuration(250)
                                .setInterpolator(android.view.animation.AccelerateInterpolator())
                                .withEndAction {
                                    onTabClose(tabId)
                                }
                                .start()
                        }
                        .start()
                }
                .start()
        }

        private fun resetTabVisualState(tabView: View) {
            // Properly reset all visual properties to default state
            tabView.animate()
                .translationY(0f)
                .translationX(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .withEndAction {
                    // Reset elevation to default
                    tabView.elevation = 4f * tabView.resources.displayMetrics.density
                }
                .start()
        }

        private fun animateHeaderClick() {
            headerSection.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    headerSection.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }

        private fun animatePlusButtonClick() {
            plusButton.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(100)
                .withEndAction {
                    plusButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }

        private fun isDarkMode(): Boolean {
            return when (itemView.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
                else -> false
            }
        }


    }

    /**
     * Generates a beautiful Material Design favicon with gradient and letter
     */
    private fun generateBeautifulFavicon(url: String, context: Context): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val domain = try {
            java.net.URL(url).host?.removePrefix("www.") ?: url
        } catch (e: Exception) {
            url
        }

        val colors = intArrayOf(
            0xFF6200EE.toInt(),
            0xFF03DAC6.toInt()
        )

        val gradient = RadialGradient(
            size / 2f, size / 2f, size / 2f,
            colors, null, Shader.TileMode.CLAMP
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val letter = domain.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        paint.apply {
            shader = null
            color = Color.WHITE
            textSize = size * 0.45f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }

        val textBounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, textBounds)

        canvas.drawText(
            letter,
            size / 2f,
            size / 2f - textBounds.exactCenterY(),
            paint
        )

        return bitmap
    }
}
