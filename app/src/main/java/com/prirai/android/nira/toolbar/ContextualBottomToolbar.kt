package com.prirai.android.nira.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
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
    }

    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var shareButton: ImageButton
    private lateinit var searchButton: ImageButton
    private lateinit var newTabButton: ImageButton
    private lateinit var tabCountButton: FrameLayout
    private lateinit var tabCountText: TextView
    private lateinit var menuButton: ImageButton

    var listener: ContextualToolbarListener? = null
    
    // Track bookmark state like BrowserMenu does
    private var isShowingBookmarkIcon = false

    init {
        orientation = HORIZONTAL
        setupView()
    }

    private fun setupView() {
        LayoutInflater.from(context).inflate(R.layout.contextual_bottom_toolbar, this, true)
        
        backButton = findViewById(R.id.back_button)
        forwardButton = findViewById(R.id.forward_button)
        shareButton = findViewById(R.id.share_button)
        searchButton = findViewById(R.id.search_button)
        newTabButton = findViewById(R.id.new_tab_button)
        tabCountButton = findViewById(R.id.tab_count_button)
        tabCountText = findViewById(R.id.tab_count_text)
        menuButton = findViewById(R.id.menu_button)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { 
            // Simple approach: Use the same pattern as BrowserMenu
            // Check context state instead of drawable comparison
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
        tabCountButton.setOnClickListener { listener?.onTabCountClicked() }
        menuButton.setOnClickListener { listener?.onMenuClicked() }
    }

    /**
     * Update toolbar based on current browsing context
     */
    fun updateForContext(
        tab: TabSessionState?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        tabCount: Int,
        isHomepage: Boolean
    ) {
        when {
            isHomepage -> showHomepageContext(tabCount, canGoForward)
            canGoForward -> showFullNavigationContext(tabCount)
            tab != null && !isHomepage -> showWebsiteContext(canGoBack, tabCount)
            else -> showDefaultContext(tabCount)
        }
        
        updateTabCount(tabCount)
    }

    /**
     * Homepage context: bookmarks, forward(enabled/disabled), search, tabs, menu
     */
    private fun showHomepageContext(tabCount: Int, canGoForward: Boolean = false) {
        // Always show the toolbar on homepage
        this.visibility = View.VISIBLE
        
        // Show: bookmarks, forward(enabled/disabled), search, tabs, menu
        backButton.visibility = View.VISIBLE
        backButton.setImageResource(R.drawable.ic_baseline_bookmark)
        backButton.isEnabled = true
        backButton.alpha = 1.0f
        
        // Set bookmark state flag - this is the key fix!
        isShowingBookmarkIcon = true
        
        forwardButton.visibility = View.VISIBLE
        forwardButton.setImageResource(R.drawable.ic_ios_forward)
        forwardButton.isEnabled = canGoForward
        forwardButton.alpha = if (canGoForward) 1.0f else 0.4f
        
        shareButton.visibility = View.GONE
        
        searchButton.visibility = View.VISIBLE
        searchButton.isEnabled = true
        searchButton.alpha = 1.0f
        
        newTabButton.visibility = View.GONE
        
        tabCountButton.visibility = View.VISIBLE
        menuButton.visibility = View.VISIBLE
        
        updateTabCount(tabCount)
    }

    /**
     * Website context: back, share, new tab, tabs, menu
     */
    private fun showWebsiteContext(canGoBack: Boolean, tabCount: Int) {
        // Show: back, share, new tab, tabs, menu
        backButton.visibility = View.VISIBLE
        backButton.setImageResource(R.drawable.ic_ios_back) // Reset to back icon
        backButton.isEnabled = canGoBack
        backButton.alpha = if (canGoBack) 1.0f else 0.4f
        
        // Reset bookmark state flag
        isShowingBookmarkIcon = false
        
        forwardButton.visibility = View.GONE
        
        shareButton.visibility = View.VISIBLE
        shareButton.isEnabled = true
        shareButton.alpha = 1.0f
        
        searchButton.visibility = View.GONE
        
        newTabButton.visibility = View.VISIBLE
        newTabButton.isEnabled = true
        newTabButton.alpha = 1.0f
        
        tabCountButton.visibility = View.VISIBLE
        menuButton.visibility = View.VISIBLE
        
        updateTabCount(tabCount)
    }

    /**
     * Full navigation context: back, forward, new tab, tabs, menu
     */
    private fun showFullNavigationContext(tabCount: Int) {
        // Show: back, forward, new tab, tabs, menu
        backButton.visibility = View.VISIBLE
        backButton.setImageResource(R.drawable.ic_ios_back) // Reset to back icon
        backButton.isEnabled = true
        backButton.alpha = 1.0f
        
        // Reset bookmark state flag
        isShowingBookmarkIcon = false
        
        forwardButton.visibility = View.VISIBLE
        forwardButton.setImageResource(R.drawable.ic_ios_forward) // Reset to forward icon
        forwardButton.isEnabled = true
        forwardButton.alpha = 1.0f
        
        shareButton.visibility = View.GONE
        
        searchButton.visibility = View.GONE
        
        newTabButton.visibility = View.VISIBLE
        newTabButton.isEnabled = true
        newTabButton.alpha = 1.0f
        
        tabCountButton.visibility = View.VISIBLE
        menuButton.visibility = View.VISIBLE
        
        updateTabCount(tabCount)
    }

    /**
     * Default/fallback context
     */
    private fun showDefaultContext(tabCount: Int) {
        // Show basic navigation
        backButton.visibility = View.VISIBLE
        backButton.setImageResource(R.drawable.ic_ios_back) // Reset to back icon
        backButton.isEnabled = true
        backButton.alpha = 1.0f
        
        // Reset bookmark state flag
        isShowingBookmarkIcon = false
        
        forwardButton.visibility = View.GONE
        
        shareButton.visibility = View.VISIBLE
        shareButton.isEnabled = true
        shareButton.alpha = 1.0f
        
        searchButton.visibility = View.GONE
        
        newTabButton.visibility = View.VISIBLE
        
        tabCountButton.visibility = View.VISIBLE
        menuButton.visibility = View.VISIBLE
        
        updateTabCount(tabCount)
    }

    /**
     * Update tab count display
     */
    private fun updateTabCount(count: Int) {
        tabCountText.text = if (count > 99) "99+" else count.toString()
        
        // Update content description for accessibility
        tabCountButton.contentDescription = context.getString(
            R.string.tabs_count_description, 
            count
        )
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