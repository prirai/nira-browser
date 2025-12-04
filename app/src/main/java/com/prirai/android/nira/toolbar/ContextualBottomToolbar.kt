package com.prirai.android.nira.toolbar

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
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
    }

    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var shareButton: ImageView
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

        // Apply icon size scale
        applyIconScale()
        
        setupClickListeners()
    }
    
    private fun applyIconScale() {
        val userPrefs = context.getSharedPreferences("scw_preferences", Context.MODE_PRIVATE)
        val iconScale = userPrefs.getFloat("toolbar_icon_size", 1.0f)
        
        val baseHeight = (48 * context.resources.displayMetrics.density).toInt()
        val scaledHeight = (baseHeight * iconScale).toInt()
        
        val basePadding = (10 * context.resources.displayMetrics.density).toInt()
        val scaledPadding = (basePadding / iconScale).toInt()
        
        val buttons = listOf(backButton, forwardButton, shareButton, searchButton, newTabButton, menuButton)
        buttons.forEach { button ->
            val params = button.layoutParams
            params.height = scaledHeight
            button.layoutParams = params
            button.setPadding(scaledPadding, scaledPadding, scaledPadding, scaledPadding)
        }
        
        // Also update tab count button height
        val tabCountParams = tabCountButton.layoutParams
        tabCountParams.height = scaledHeight
        tabCountButton.layoutParams = tabCountParams
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
        tabCountButton.setOnClickListener { listener?.onTabCountClicked() }
        menuButton.setOnClickListener { listener?.onMenuClicked() }
    }

    fun updateForContext(
        tab: TabSessionState?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        tabCount: Int,
        isHomepage: Boolean
    ) {
        val userPrefs = context.getSharedPreferences("scw_preferences", Context.MODE_PRIVATE)
        val showContextualToolbar = userPrefs.getBoolean("show_contextual_toolbar", true)
        
        if (!showContextualToolbar) {
            this.visibility = GONE
            return
        }
        
        when {
            isHomepage -> showHomepageContext(tabCount, canGoForward)
            canGoForward -> showFullNavigationContext(tabCount)
            tab != null && !isHomepage -> showWebsiteContext(canGoBack, tabCount)
            else -> showDefaultContext(tabCount)
        }
        
        updateTabCount(tabCount)
    }

    private fun showHomepageContext(tabCount: Int, canGoForward: Boolean = false) {
        this.visibility = VISIBLE
        
        backButton.visibility = VISIBLE
        backButton.setImageResource(R.drawable.ic_baseline_bookmark)
        backButton.isEnabled = true
        backButton.alpha = 1.0f
        
        isShowingBookmarkIcon = true
        
        forwardButton.visibility = VISIBLE
        forwardButton.setImageResource(R.drawable.ic_ios_forward)
        forwardButton.isEnabled = canGoForward
        forwardButton.alpha = if (canGoForward) 1.0f else 0.4f
        
        shareButton.visibility = GONE
        
        searchButton.visibility = VISIBLE
        searchButton.isEnabled = true
        searchButton.alpha = 1.0f
        
        newTabButton.visibility = GONE
        
        tabCountButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
        
        updateTabCount(tabCount)
    }

    private fun showWebsiteContext(canGoBack: Boolean, tabCount: Int) {
        backButton.visibility = VISIBLE
        backButton.setImageResource(R.drawable.ic_ios_back)
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
        
        tabCountButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
        
        updateTabCount(tabCount)
    }

    private fun showFullNavigationContext(tabCount: Int) {
        backButton.visibility = VISIBLE
        backButton.setImageResource(R.drawable.ic_ios_back)
        backButton.isEnabled = true
        backButton.alpha = 1.0f
        
        isShowingBookmarkIcon = false
        
        forwardButton.visibility = VISIBLE
        forwardButton.setImageResource(R.drawable.ic_ios_forward)
        forwardButton.isEnabled = true
        forwardButton.alpha = 1.0f
        
        shareButton.visibility = GONE
        
        searchButton.visibility = GONE
        
        newTabButton.visibility = VISIBLE
        newTabButton.isEnabled = true
        newTabButton.alpha = 1.0f
        
        tabCountButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
        
        updateTabCount(tabCount)
    }

    private fun showDefaultContext(tabCount: Int) {
        backButton.visibility = VISIBLE
        backButton.setImageResource(R.drawable.ic_ios_back)
        backButton.isEnabled = true
        backButton.alpha = 1.0f
        
        isShowingBookmarkIcon = false
        
        forwardButton.visibility = GONE
        
        shareButton.visibility = VISIBLE
        shareButton.isEnabled = true
        shareButton.alpha = 1.0f
        
        searchButton.visibility = GONE
        
        newTabButton.visibility = VISIBLE
        
        tabCountButton.visibility = VISIBLE
        menuButton.visibility = VISIBLE
        
        updateTabCount(tabCount)
    }

    private fun updateTabCount(count: Int) {
        tabCountText.text = if (count > 99) "99+" else count.toString()
        tabCountButton.contentDescription = context.getString(R.string.tabs_count_description, count)
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