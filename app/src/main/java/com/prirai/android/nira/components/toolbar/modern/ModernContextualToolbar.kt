package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.prirai.android.nira.R
import mozilla.components.browser.state.state.TabSessionState

/**
 * Revolutionary modern contextual toolbar with complete feature parity to the original.
 * Features context-aware button switching, sophisticated state management, and beautiful animations.
 */
class ModernContextualToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    interface ModernContextualToolbarListener {
        fun onBackClicked()
        fun onForwardClicked()
        fun onShareClicked()
        fun onSearchClicked()
        fun onNewTabClicked()
        fun onTabCountClicked()
        fun onMenuClicked()
        fun onBookmarksClicked()
        fun onRefreshClicked()
    }

    // All action buttons from original
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var shareButton: ImageButton
    private lateinit var searchButton: ImageButton
    private lateinit var newTabButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var tabCountButton: FrameLayout
    private lateinit var tabCountText: TextView
    private lateinit var menuButton: ImageButton

    var listener: ModernContextualToolbarListener? = null

    // State tracking
    private var canGoBack = false
    private var canGoForward = false
    private var isLoading = false
    private var currentTabCount = 1
    private var isHomepage = false
    private var currentTab: TabSessionState? = null

    init {
        setupModernToolbar()
    }

    private fun setupModernToolbar() {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(12, 8, 12, 8) // Increased padding for better spacing

        // CRITICAL: Increased height for better usability
        layoutParams = android.view.ViewGroup.LayoutParams(
            LayoutParams.MATCH_PARENT,
            72 // Increased height for better touch targets
        )

        // Create modern layout with original functionality
        createModernLayout()
        setupModernClickListeners()

        // Use unified ThemeManager helper (supports AMOLED + Material 3)
        setBackgroundColor(
            com.prirai.android.nira.theme.ThemeManager.getToolbarBackgroundColor(
                context, 
                useElevation = true, 
                elevationDp = 3f
            )
        )
        elevation = 0f
        outlineProvider = null
    }

    private fun createModernLayout() {
        // Create all buttons with modern styling but original functionality
        val buttonSize = 48

        // Back button (also serves as bookmarks button on homepage)
        backButton = createModernButton(R.drawable.ic_ios_back, "Go back", buttonSize)
        addView(backButton, createWeightedLayoutParams())

        // Forward button (hidden by default, context-aware)
        forwardButton = createModernButton(R.drawable.ic_ios_forward, "Go forward", buttonSize)
        addView(forwardButton, createWeightedLayoutParams())
        forwardButton.visibility = GONE

        // Share button (context-aware)
        shareButton = createModernButton(R.drawable.ios_share_24, "Share", buttonSize)
        addView(shareButton, createWeightedLayoutParams())

        // Search button (for homepage context)
        searchButton = createModernButton(android.R.drawable.ic_search_category_default, "Search", buttonSize)
        addView(searchButton, createWeightedLayoutParams())
        searchButton.visibility = GONE

        // New Tab button (context-aware)
        newTabButton = createModernButton(android.R.drawable.ic_input_add, "New tab", buttonSize)
        addView(newTabButton, createWeightedLayoutParams())
        newTabButton.visibility = GONE

        // Refresh button (additional feature)
        refreshButton = createModernButton(R.drawable.ic_refresh, "Refresh", buttonSize)
        addView(refreshButton, createWeightedLayoutParams())
        refreshButton.visibility = GONE

        // Tab count button (styled like original with background and text)
        createModernTabCountButton()

        // Menu button (always visible)
        menuButton = createModernButton(R.drawable.ic_more_vert, "More options", buttonSize)
        addView(menuButton, createWeightedLayoutParams())
    }

    private fun createModernButton(iconRes: Int, contentDescription: String, size: Int): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            this.contentDescription = contentDescription
            background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(ContextCompat.getColor(context, R.color.contextual_toolbar_icon))
            isClickable = true
            isFocusable = true
            // Ensure proper padding to prevent icon clipping
            setPadding(12, 12, 12, 12)
        }
    }

    private fun createWeightedLayoutParams(): LayoutParams {
        return LayoutParams(0, LayoutParams.MATCH_PARENT).apply {
            weight = 1f
            setMargins(2, 4, 2, 4) // Reduced margins to prevent clipping
        }
    }

    private fun createModernTabCountButton() {
        tabCountButton = FrameLayout(context).apply {
            // Tab count background (square with border like original)
            val backgroundView = android.widget.ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_view)
                setColorFilter(ContextCompat.getColor(context, R.color.contextual_toolbar_icon))
                layoutParams = FrameLayout.LayoutParams(24, 24).apply {
                    gravity = android.view.Gravity.CENTER
                }
            }
            addView(backgroundView)

            // Tab count text
            tabCountText = TextView(context).apply {
                text = "1"
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.contextual_toolbar_text))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 1
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
            }
            addView(tabCountText)

            background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            contentDescription = "Tabs"
            isClickable = true
            isFocusable = true
        }

        addView(tabCountButton, createWeightedLayoutParams())
    }

    private fun setupModernClickListeners() {
        backButton.setOnClickListener {
            // Check if it's showing bookmarks icon or back button (same logic as original)
            if (backButton.drawable.constantState ==
                ContextCompat.getDrawable(context, android.R.drawable.btn_star_big_off)?.constantState
            ) {
                listener?.onBookmarksClicked()
            } else {
                listener?.onBackClicked()
            }
            animateModernButtonPress(backButton)
        }

        forwardButton.setOnClickListener {
            listener?.onForwardClicked()
            animateModernButtonPress(forwardButton)
        }

        shareButton.setOnClickListener {
            listener?.onShareClicked()
            animateModernButtonPress(shareButton)
        }

        searchButton.setOnClickListener {
            listener?.onSearchClicked()
            animateModernButtonPress(searchButton)
        }

        newTabButton.setOnClickListener {
            listener?.onNewTabClicked()
            animateModernButtonPress(newTabButton)
        }

        refreshButton.setOnClickListener {
            listener?.onRefreshClicked()
            animateRefreshButton()
        }

        tabCountButton.setOnClickListener {
            listener?.onTabCountClicked()
            animateModernButtonPress(tabCountButton)
        }

        menuButton.setOnClickListener {
            listener?.onMenuClicked()
            animateModernButtonPress(menuButton)
        }
    }

    /**
     * Revolutionary context-aware update method - exactly like the original!
     * Updates toolbar based on current browsing context with intelligent button switching
     */
    fun updateForModernContext(
        tab: TabSessionState?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        tabCount: Int,
        isHomepage: Boolean = false
    ) {
        this.currentTab = tab
        this.canGoBack = canGoBack
        this.canGoForward = canGoForward
        this.currentTabCount = tabCount
        this.isHomepage = isHomepage


        when {
            isHomepage -> showModernHomepageContext(tabCount, canGoForward)
            canGoForward -> showModernFullNavigationContext(tabCount)
            tab != null && !isHomepage -> showModernWebsiteContext(canGoBack, tabCount)
            else -> showModernDefaultContext(tabCount)
        }

        updateModernTabCount(tabCount)
    }

    /**
     * Homepage context: bookmarks, forward(enabled/disabled), search, tabs, menu
     */
    private fun showModernHomepageContext(tabCount: Int, canGoForward: Boolean = false) {

        // Always show the toolbar on homepage
        this.visibility = VISIBLE

        // Show: bookmarks, forward(enabled/disabled), search, tabs, menu
        backButton.visibility = VISIBLE
        backButton.setImageResource(android.R.drawable.btn_star_big_off) // Bookmarks icon
        backButton.isEnabled = true
        backButton.alpha = 1.0f

        forwardButton.visibility = VISIBLE
        forwardButton.setImageResource(R.drawable.ic_ios_forward)
        forwardButton.isEnabled = canGoForward
        forwardButton.alpha = if (canGoForward) 1.0f else 0.4f

        shareButton.visibility = GONE

        searchButton.visibility = VISIBLE
        searchButton.isEnabled = true
        searchButton.alpha = 1.0f

        newTabButton.visibility = GONE
        refreshButton.visibility = GONE

        tabCountButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
    }

    /**
     * Website context: back, share, new tab, tabs, menu
     */
    private fun showModernWebsiteContext(canGoBack: Boolean, tabCount: Int) {

        // Show: back, share, new tab, tabs, menu
        backButton.visibility = VISIBLE
        backButton.setImageResource(R.drawable.ic_ios_back) // Reset to back icon
        backButton.isEnabled = canGoBack
        backButton.alpha = if (canGoBack) 1.0f else 0.4f

        forwardButton.visibility = GONE

        shareButton.visibility = VISIBLE
        shareButton.isEnabled = true
        shareButton.alpha = 1.0f

        searchButton.visibility = GONE

        newTabButton.visibility = VISIBLE
        newTabButton.isEnabled = true
        newTabButton.alpha = 1.0f

        refreshButton.visibility = GONE

        tabCountButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
    }

    /**
     * Full navigation context: back, forward, new tab, tabs, menu
     */
    private fun showModernFullNavigationContext(tabCount: Int) {

        // Show: back, forward, new tab, tabs, menu
        backButton.visibility = VISIBLE
        backButton.setImageResource(R.drawable.ic_ios_back) // Reset to back icon
        backButton.isEnabled = true
        backButton.alpha = 1.0f

        forwardButton.visibility = VISIBLE
        forwardButton.setImageResource(R.drawable.ic_ios_forward) // Reset to forward icon
        forwardButton.isEnabled = true
        forwardButton.alpha = 1.0f

        shareButton.visibility = GONE

        searchButton.visibility = GONE

        newTabButton.visibility = VISIBLE
        newTabButton.isEnabled = true
        newTabButton.alpha = 1.0f

        refreshButton.visibility = GONE

        tabCountButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
    }

    /**
     * Default/fallback context
     */
    private fun showModernDefaultContext(tabCount: Int) {

        // Show basic navigation
        backButton.visibility = VISIBLE
        backButton.setImageResource(R.drawable.ic_ios_back) // Reset to back icon
        backButton.isEnabled = true
        backButton.alpha = 1.0f

        forwardButton.visibility = GONE

        shareButton.visibility = VISIBLE
        shareButton.isEnabled = true
        shareButton.alpha = 1.0f

        searchButton.visibility = GONE

        newTabButton.visibility = VISIBLE
        refreshButton.visibility = GONE

        tabCountButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
    }

    /**
     * Update tab count display (exactly like original)
     */
    private fun updateModernTabCount(count: Int) {
        tabCountText.text = if (count > 99) "99+" else count.toString()

        // Update content description for accessibility
        tabCountButton.contentDescription = "Tabs: $count"
    }

    // Modern animations for beautiful user experience
    private fun animateModernButtonPress(button: View) {
        button.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()

        // Haptic feedback for modern experience
        button.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun animateRefreshButton() {
        refreshButton.animate()
            .rotation(refreshButton.rotation + 360f)
            .setDuration(500)
            .start()

        animateModernButtonPress(refreshButton)
    }

    /**
     * Update loading state and refresh button appearance
     */
    fun updateLoadingState(loading: Boolean) {
        isLoading = loading

        if (isLoading) {
            refreshButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            refreshButton.contentDescription = "Stop loading"
            refreshButton.animate()
                .rotation(refreshButton.rotation + 180f)
                .setDuration(300)
                .start()
        } else {
            refreshButton.setImageResource(R.drawable.ic_refresh)
            refreshButton.contentDescription = "Refresh"
        }
    }

    /**
     * Public API for updating navigation states (similar to original)
     */
    fun updateNavigationState(canBack: Boolean, canForward: Boolean) {
        canGoBack = canBack
        canGoForward = canForward

        // Update the current context with new navigation state
        updateForModernContext(currentTab, canBack, canForward, currentTabCount, isHomepage)
    }

    companion object {
        const val ANIMATION_DURATION = 150L
    }
}
