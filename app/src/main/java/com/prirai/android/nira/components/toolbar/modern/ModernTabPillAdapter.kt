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
import com.google.android.material.card.MaterialCardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import mozilla.components.browser.state.state.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Enhanced adapter for beautiful tab pills.
 */
class ModernTabPillAdapter(
    private var onTabClick: (String) -> Unit,
    private var onTabClose: (String) -> Unit,
    private var onTabDuplicate: ((String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var displayItems = mutableListOf<TabPillItem>()
    private var selectedTabId: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val VIEW_TYPE_TAB = 0
        
        private fun getThemeColor(context: Context, attr: Int, fallbackColorRes: Int): Int {
            val theme = context.theme
            val typedValue = android.util.TypedValue()
            return if (theme.resolveAttribute(attr, typedValue, true)) {
                typedValue.data
            } else {
                ContextCompat.getColor(context, fallbackColorRes)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is TabPillItem.Tab -> VIEW_TYPE_TAB
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TAB -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.modern_tab_pill_item, parent, false)
                TabPillViewHolder(view)
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
            displayItems.forEachIndexed { index, item ->
                when (item) {
                    is TabPillItem.Tab -> {
                        val wasSelected = item.session.id == oldSelectedId
                        val isSelected = item.session.id == selectedId
                        if (wasSelected || isSelected) {
                            notifyItemChanged(index)
                        }
                    }

                    else -> {}
                }
            }
            return
        }

        // If nothing changed at all, skip update
        if (!itemsChanged) {

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
                notifyItemRangeChanged(0, items.size)
                notifyItemRangeRemoved(items.size, oldSize - items.size)
            }

            oldSize < items.size -> {
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
                item1.session.id == item2.session.id
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
        onTabDuplicate: ((String) -> Unit)? = null
    ) {
        this.onTabClick = onTabClick
        this.onTabClose = onTabClose
        this.onTabDuplicate = onTabDuplicate
    }

    // ViewHolder for regular tabs
    inner class TabPillViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.tabPillCard)
        private val faviconView: ImageView = itemView.findViewById(R.id.faviconImage)
        private val titleView: TextView = itemView.findViewById(R.id.tabTitle)
        private val closeButton: ImageView = itemView.findViewById(R.id.closeButton)

        private var currentTabId: String? = null
        private var isDragging = false

        fun bind(item: TabPillItem.Tab, isSelected: Boolean) {
            val tab = item.session
            currentTabId = tab.id

            // Show title with fallback to URL, and "New Tab" for homepage
            val title = when {
                !tab.content.title.isNullOrBlank() -> tab.content.title
                tab.content.url.startsWith("about:homepage") || tab.content.url.startsWith("about:privatebrowsing") -> "New Tab"
                !tab.content.url.isNullOrBlank() && !tab.content.url.startsWith("about:") -> {
                    // Use URL as fallback if no title yet
                    tab.content.url.take(30)
                }
                else -> ""
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
            // Note: Only show if isDragging flag is not set by touch handler
            cardView.setOnLongClickListener {
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
            var startX = 0f
            var localIsDragging = false
            var hasMoved = false
            var dragClone: View? = null
            var decorView: ViewGroup? = null

            cardView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        isDragging = false // Reset class-level isDragging
                        startY = event.rawY
                        startX = event.rawX
                        false // Let RecyclerView handle scrolling
                    }

                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaY = startY - event.rawY
                        val deltaX = Math.abs(event.rawX - startX)
                        
                        // Detect horizontal scrolling and mark as moved
                        if (deltaX > 5) {
                            isDragging = true // Prevent long-press menu
                            // Don't consume event - let RecyclerView scroll
                            return@setOnTouchListener false
                        }
                        
                        // Detect any movement for long-press prevention
                        if (Math.abs(deltaY) > 5) {
                            isDragging = true
                        }

                        // Only start vertical dragging if moving up, not sideways
                        if (deltaY > 20 && deltaX < 30 && !localIsDragging) {
                            localIsDragging = true
                            isDragging = true
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            
                            // Create clone and add to DecorView
                            val activity = itemView.context as? android.app.Activity
                            decorView = activity?.window?.decorView as? ViewGroup
                            
                            if (decorView != null) {
                                // Hide original
                                cardView.alpha = 0f
                                
                                // Create clone
                                dragClone = createTabClone()
                                
                                // Position clone at original location
                                val location = IntArray(2)
                                cardView.getLocationInWindow(location)
                                dragClone?.x = location[0].toFloat()
                                dragClone?.y = location[1].toFloat()
                                
                                // Add to decorView
                                decorView?.addView(dragClone, ViewGroup.LayoutParams(
                                    cardView.width,
                                    cardView.height
                                ))
                            }
                        }

                        if (localIsDragging && dragClone != null) {
                            // Update clone position to follow finger
                            val location = IntArray(2)
                            cardView.getLocationInWindow(location)
                            
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

                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (localIsDragging) {
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
                                        currentTabId?.let { onTabClose(it) }
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
                                        cardView.alpha = 1f
                                        isDragging = false
                                    }
                                    ?.start()
                            }
                            true
                        } else if (hasMoved) {
                            // Finger moved but didn't drag up - don't trigger click or long-press
                            isDragging = false
                            true
                        } else {
                            // No movement - allow click/long-press to proceed
                            isDragging = false
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
            duplicateItem.setIcon(R.drawable.control_point_duplicate_24px)
            
            val moveToProfileItem = popupMenu.menu.add(0, 2, 1, "Move to Profile")
            moveToProfileItem.setIcon(R.drawable.move_item_24px)

            val closeItem = popupMenu.menu.add(0, 3, 2, "Close Tab")
            closeItem.setIcon(R.drawable.ic_round_close)

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
                        onTabDuplicate?.invoke(tabId)
                        true
                    }
                    2 -> {
                        // Move to Profile
                        itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        showMoveToProfileDialog(listOf(tabId))
                        true
                    }
                    3 -> {
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
        
        private fun showMoveToProfileDialog(tabIds: List<String>) {
            val profileManager = com.prirai.android.nira.browser.profile.ProfileManager.getInstance(itemView.context)
            val profiles = profileManager.getAllProfiles()
            
            val items = profiles.map { "${it.emoji} ${it.name}" }.toMutableList()
            items.add("🕵️ Private")
            
            com.google.android.material.dialog.MaterialAlertDialogBuilder(itemView.context)
                .setTitle("Move tab to Profile")
                .setItems(items.toTypedArray()) { _, which ->
                    val targetProfileId = if (which == items.size - 1) {
                        "private"
                    } else {
                        profiles[which].id
                    }
                    
                    // Migrate tabs
                    val migratedCount = profileManager.migrateTabsToProfile(tabIds, targetProfileId)
                    
                    // Show confirmation
                    android.widget.Toast.makeText(
                        itemView.context,
                        "Moved $migratedCount tab to ${items[which]}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun createTabClone(): View {
            // Create a clone of the cardView with same appearance
            val clone = android.widget.FrameLayout(itemView.context).apply {
                layoutParams = ViewGroup.LayoutParams(cardView.width, cardView.height)
                elevation = 16f
            }
            
            // Clone the card appearance
            val clonedCard = CardView(itemView.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                radius = cardView.radius
                cardElevation = cardView.cardElevation
                background = cardView.background?.constantState?.newDrawable()?.mutate()
                
                // Clone content
                val content = android.widget.LinearLayout(itemView.context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val padding = (8 * context.resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                }
                
                // Clone favicon
                val clonedFavicon = ImageView(itemView.context).apply {
                    val size = (24 * context.resources.displayMetrics.density).toInt()
                    layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = (8 * context.resources.displayMetrics.density).toInt()
                    }
                    setImageDrawable(faviconView.drawable)
                }
                content.addView(clonedFavicon)
                
                // Clone title
                val clonedTitle = TextView(itemView.context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    text = titleView.text
                    textSize = titleView.textSize / context.resources.displayMetrics.scaledDensity
                    setTextColor(titleView.currentTextColor)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                content.addView(clonedTitle)
                
                addView(content)
            }
            
            clone.addView(clonedCard)
            return clone
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
                    val cachedFavicon = faviconCache.loadFavicon(tab.content.url)

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
            val isGuestTab = item.session.contextId == null
            
            // Get default background color from Material 3 theme
            val typedValue = android.util.TypedValue()
            val theme = itemView.context.theme
            
            val backgroundColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
                typedValue.data
            } else {
                // Fallback to Material 3 color resource
                getThemeColor(itemView.context, com.google.android.material.R.attr.colorSurfaceContainerHigh, R.color.m3_surface_container_background)
            }
            
            // Guest tab color: distinctive orange/amber
            val guestTabColor = 0xFFFF9800.toInt() // Orange

            if (isSelected) {
                // Selected: Show prominent border with island/guest color
                val borderColor = when {
                    isGuestTab -> guestTabColor
                    else -> {
                        // Use Material 3 primary color (supports dynamic colors)
                        val primaryTypedValue = android.util.TypedValue()
                        if (theme.resolveAttribute(android.R.attr.colorPrimary, primaryTypedValue, true)) {
                            primaryTypedValue.data
                        } else {
                            getThemeColor(itemView.context, android.R.attr.colorPrimary, R.color.m3_primary)
                        }
                    }
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

                val textColor = getThemeColor(itemView.context, com.google.android.material.R.attr.colorOnSurface, R.color.m3_primary_text)
                titleView.setTextColor(textColor)
                // Selection indicator removed

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

                val textColor = getThemeColor(itemView.context, com.google.android.material.R.attr.colorOnSurface, R.color.m3_primary_text)
                titleView.setTextColor(textColor)
                // Selection indicator removed

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
    }

    // ViewHolder for island headers
// Island ViewHolders removed.
// Helper methods removed.
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
            com.prirai.android.nira.theme.ColorConstants.Profiles.DEFAULT_COLOR,
            0xFF009688.toInt() // Teal
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

/**
 * Item types for the adapter
 */
sealed class TabPillItem {
    data class Tab(
        val session: SessionState,
        val islandId: String? = null // Keeping for compatibility, but unused
    ) : TabPillItem()
}
