package com.prirai.android.nira.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.prirai.android.nira.R
import mozilla.components.browser.state.state.TabSessionState

/**
 * Context-aware bottom toolbar that shows different quick actions based on browsing state
 */
class ContextualBottomToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface ContextualToolbarListener {
        fun onBackClicked()
        fun onForwardClicked()
        fun onShareClicked()
        fun onSearchClicked()
        fun onNewTabClicked()
        fun onTabCountClicked()
        fun onMenuClicked()
        fun onBookmarksClicked()

        /**
         * Extensions button on the contextual toolbar - opens the
         * extensions (add-ons) bottom sheet.
         */
        fun onExtensionsClicked()

        /**
         * Long-press on the back button. Only called when the button is
         * actually showing the back glyph - on the homepage the back slot
         * is the bookmarks button and the long-press is swallowed.
         */
        fun onBackLongClicked() {}

        /** Long-press on the forward button. */
        fun onForwardLongClicked() {}
    }

    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var shareButton: ImageView
    private lateinit var searchButton: ImageButton
    private lateinit var newTabButton: ImageButton
    private lateinit var extensionsButton: ImageButton
    private lateinit var menuButton: ImageButton

    var listener: ContextualToolbarListener? = null
    
    // Track bookmark state like BrowserMenu does
    private var isShowingBookmarkIcon = false

    init {
        setupView()
    }

    private fun setupView() {
        // Inflate the layout - this merges the XML's LinearLayout into this view
        LayoutInflater.from(context).inflate(R.layout.contextual_bottom_toolbar, this, true)
        
        // Use unified ThemeManager helper (supports AMOLED + Material 3)
        setBackgroundColor(
            com.prirai.android.nira.theme.ThemeManager.getToolbarBackgroundColor(
                context, 
                useElevation = true, 
                elevationDp = 3f
            )
        )
        
        // Remove any elevation/shadow that creates gradient effect
        elevation = 0f
        outlineProvider = null
        
        backButton = findViewById(R.id.back_button)
        forwardButton = findViewById(R.id.forward_button)
        shareButton = findViewById(R.id.share_button)
        searchButton = findViewById(R.id.search_button)
        newTabButton = findViewById(R.id.new_tab_button)
        extensionsButton = findViewById(R.id.extensions_button)
        menuButton = findViewById(R.id.menu_button)

        // Apply icon size scale
        applyIconScale()
        
        setupClickListeners()
    }
    
    private fun applyIconScale() {
        val iconScale = com.prirai.android.nira.preferences.UserPreferences(context).toolbarIconSize

        val baseHeight = (48 * context.resources.displayMetrics.density).toInt()
        val scaledHeight = (baseHeight * iconScale).toInt()

        val basePadding = (10 * context.resources.displayMetrics.density).toInt()
        val scaledPadding = (basePadding / iconScale).toInt()

        val buttons = listOf(
            backButton, forwardButton, shareButton, searchButton,
            newTabButton, extensionsButton, menuButton
        )
        buttons.forEach { button ->
            val params = button.layoutParams
            params.height = scaledHeight
            button.layoutParams = params
            button.setPadding(scaledPadding, scaledPadding, scaledPadding, scaledPadding)
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { 
            if (isShowingBookmarkIcon) {
                listener?.onBookmarksClicked()
            } else {
                listener?.onBackClicked()
            }
        }
        forwardButton.setOnClickListener { listener?.onForwardClicked() }
        shareButton.setOnClickListener { listener?.onShareClicked() }
        searchButton.setOnClickListener { listener?.onSearchClicked() }
        newTabButton.setOnClickListener { listener?.onNewTabClicked() }
        extensionsButton.setOnClickListener { listener?.onExtensionsClicked() }
        menuButton.setOnClickListener { listener?.onMenuClicked() }

        // Long-press on back opens the tab-history popup (Chrome/Fenix
        // style). Swallowed when the back slot is showing the bookmark
        // glyph (homepage context) - that button has no history to show.
        backButton.setOnLongClickListener { view ->
            if (!isShowingBookmarkIcon) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                listener?.onBackLongClicked()
            }
            true
        }
        forwardButton.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onForwardLongClicked()
            true
        }
    }

    fun updateForContext(
        tab: TabSessionState?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        isHomepage: Boolean
    ) {
        val showContextualToolbar =
            com.prirai.android.nira.preferences.UserPreferences(context).showContextualToolbar

        if (!showContextualToolbar) {
            this.visibility = GONE
            return
        }

        when {
            isHomepage -> showHomepageContext(canGoForward)
            canGoForward -> showFullNavigationContext()
            tab != null && !isHomepage -> showWebsiteContext(canGoBack)
            else -> showDefaultContext()
        }
    }

    private fun showHomepageContext(canGoForward: Boolean = false) {
        this.visibility = VISIBLE

        backButton.visibility = VISIBLE
        backButton.setImageResource(mozilla.components.ui.icons.R.drawable.mozac_ic_bookmark_24)
        backButton.isEnabled = true
        backButton.alpha = 1.0f

        isShowingBookmarkIcon = true

        forwardButton.visibility = VISIBLE
        forwardButton.setImageResource(mozilla.components.ui.icons.R.drawable.mozac_ic_forward_24)
        forwardButton.isEnabled = canGoForward
        forwardButton.alpha = if (canGoForward) 1.0f else 0.4f

        shareButton.visibility = GONE

        searchButton.visibility = VISIBLE
        searchButton.isEnabled = true
        searchButton.alpha = 1.0f

        newTabButton.visibility = GONE

        extensionsButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
    }

    private fun showWebsiteContext(canGoBack: Boolean) {
        backButton.visibility = VISIBLE
        backButton.setImageResource(mozilla.components.ui.icons.R.drawable.mozac_ic_back_24)
        backButton.isEnabled = canGoBack
        backButton.alpha = if (canGoBack) 1.0f else 0.4f

        isShowingBookmarkIcon = false

        forwardButton.visibility = GONE

        shareButton.visibility = VISIBLE
        shareButton.isEnabled = true
        shareButton.alpha = 1.0f

        searchButton.visibility = GONE

        newTabButton.visibility = VISIBLE
        newTabButton.isEnabled = true
        newTabButton.alpha = 1.0f

        extensionsButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
    }

    private fun showFullNavigationContext() {
        backButton.visibility = VISIBLE
        backButton.setImageResource(mozilla.components.ui.icons.R.drawable.mozac_ic_back_24)
        backButton.isEnabled = true
        backButton.alpha = 1.0f

        isShowingBookmarkIcon = false

        forwardButton.visibility = VISIBLE
        forwardButton.setImageResource(mozilla.components.ui.icons.R.drawable.mozac_ic_forward_24)
        forwardButton.isEnabled = true
        forwardButton.alpha = 1.0f

        shareButton.visibility = GONE

        searchButton.visibility = GONE

        newTabButton.visibility = VISIBLE
        newTabButton.isEnabled = true
        newTabButton.alpha = 1.0f

        extensionsButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
    }

    private fun showDefaultContext() {
        backButton.visibility = VISIBLE
        backButton.setImageResource(mozilla.components.ui.icons.R.drawable.mozac_ic_back_24)
        backButton.isEnabled = true
        backButton.alpha = 1.0f

        isShowingBookmarkIcon = false

        forwardButton.visibility = GONE

        shareButton.visibility = VISIBLE
        shareButton.isEnabled = true
        shareButton.alpha = 1.0f

        searchButton.visibility = GONE

        newTabButton.visibility = VISIBLE

        extensionsButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
    }

    /**
     * Check if current URL is homepage
     */
    private fun isHomepageUrl(url: String): Boolean {
        return url == "about:homepage" || url == "about:blank" || url.isEmpty()
    }

    companion object {
        const val ANIMATION_DURATION = 150L
    }
}
