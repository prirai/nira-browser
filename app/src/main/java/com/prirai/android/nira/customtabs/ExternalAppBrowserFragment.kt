package com.prirai.android.nira.customtabs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.customtabs.CustomTabWindowFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.utils.SafeIntent
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.components.FindInPageComponent
import com.prirai.android.nira.ext.components
import androidx.core.net.toUri

/**
 * Fragment used for browsing the web within external apps (custom tabs).
 * Uses CustomTabBrowserFragment as base to avoid heavy toolbar initialization.
 */
class ExternalAppBrowserFragment : CustomTabBrowserFragment() {

    private val customTabWindowFeature = ViewBoundFeatureWrapper<CustomTabWindowFeature>()
    
    // Custom header views
    private var customTabHeader: LinearLayout? = null
    private var customTabTitle: TextView? = null
    private var customTabUrl: TextView? = null
    private var customTabMenuButton: ImageButton? = null
    private var customTabCloseButton: ImageButton? = null
    
    // Find in Page component
    private var findInPageComponent: FindInPageComponent? = null
    
    // Custom tab theming colors
    private var toolbarColor: Int = -1
    private var textColor: Int = -1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set customTabSessionId from arguments before calling super
        customTabSessionId = arguments?.getString("activeSessionId")
        
        // Extract toolbar color from arguments (passed from activity intent)
        toolbarColor = arguments?.getInt(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, -1) ?: -1
        
        // Calculate appropriate text color based on toolbar color
        if (toolbarColor != -1) {
            textColor = if (isColorLight(toolbarColor)) {
                // Light background needs dark text
                android.graphics.Color.parseColor("#000000")
            } else {
                // Dark background needs light text
                android.graphics.Color.parseColor("#FFFFFF")
            }
        }
        
        super.onCreate(savedInstanceState)
    }

    override fun onInitializeUI(view: View, tab: SessionState) {
        // Hide unnecessary UI elements for custom tabs
        // Contextual toolbar and tab group bar are now part of UnifiedToolbar
        // binding.contextualBottomToolbar.isVisible = false // Removed with legacy toolbar
        // binding.tabGroupBar?.isVisible = false // Removed with legacy toolbar
        
        // Add custom minimal header
        addCustomHeader(view, tab)
        
        // Adjust swipeRefresh padding to account for custom header
        // The parent class already applies status bar padding, we need to add header height on top
        setupCustomTabContentPadding(view)
        
        // Set up custom Find in Page
        setupCustomFindInPage(view)
    }
    
    /**
     * Setup content padding for custom tabs to account for the custom header.
     * Simplified version that avoids complex inset calculations.
     */
    private fun setupCustomTabContentPadding(view: View) {
        val browserWindow = view.findViewById<ViewGroup>(R.id.browserWindow) ?: return
        val browserLayout = binding.browserLayout
        
        // Apply padding to browserLayout once header is measured
        browserWindow.post {
            val headerWrapper = browserWindow.findViewWithTag<View>("customTabHeaderWrapper")
            if (headerWrapper != null) {
                val headerHeight = headerWrapper.height
                browserLayout.setPadding(0, headerHeight, 0, 0)
            }
        }
    }
    
    private fun setupCustomFindInPage(view: View) {
        val sessionId = customTabSessionId ?: return
        val rootLayout = view.findViewById<ViewGroup>(R.id.gestureLayout) ?: return
        
        // Create and attach the Find in Page component
        findInPageComponent = FindInPageComponent(
            context = requireContext(),
            store = requireContext().components.store,
            sessionId = sessionId,
            lifecycleOwner = viewLifecycleOwner,
            isCustomTab = true
        )
        findInPageComponent?.attach(rootLayout)
    }
    
    private fun showFindInPage() {
        findInPageComponent?.show()
    }
    
    private fun addCustomHeader(view: View, tab: SessionState) {
        // Inflate custom header
        val headerView = LayoutInflater.from(requireContext())
            .inflate(R.layout.custom_tab_header, null)
        
        // Find the main constraint layout (browserWindow), not browserLayout
        val browserWindow = view.findViewById<ViewGroup>(R.id.browserWindow) ?: return
        
        // Set layout params to overlay at the top with proper z-index
        val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        }
        headerView.layoutParams = layoutParams
        headerView.elevation = 8f
        headerView.tag = "customTabHeaderWrapper"
        headerView.id = View.generateViewId()  // Generate ID for constraint reference
        
        // Add header to the browserWindow (sibling to browserLayout)
        browserWindow.addView(headerView)
        
        // Setup window insets for the header wrapper
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(headerView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Apply top padding for status bar to the wrapper
            v.setPadding(0, systemBars.top, 0, 0)
            
            insets
        }
        
        // Force apply insets
        headerView.post {
            androidx.core.view.ViewCompat.requestApplyInsets(headerView)
        }
        
        // Initialize header views
        customTabHeader = headerView.findViewById(R.id.customTabHeader)
        customTabTitle = headerView.findViewById(R.id.customTabTitle)
        customTabUrl = headerView.findViewById(R.id.customTabUrl)
        customTabMenuButton = headerView.findViewById(R.id.customTabMenuButton)
        customTabCloseButton = headerView.findViewById(R.id.customTabCloseButton)
        
        // Apply custom tab theming if provided
        applyCustomTabTheming()
        
        // Set up close button - just finish the activity without opening main app
        customTabCloseButton?.setOnClickListener {
            // Remove the custom tab session
            customTabSessionId?.let { sessionId ->
                requireContext().components.tabsUseCases.removeTab(sessionId)
            }
            requireActivity().finish()
        }
        
        // Set up menu button
        customTabMenuButton?.setOnClickListener { anchor ->
            showCustomTabMenu(anchor)
        }
        
        // Update initial content
        updateHeaderDisplay(tab)
    }
    
    override fun onTabUpdated(tab: SessionState) {
        updateHeaderDisplay(tab)
    }
    
    private fun updateHeaderDisplay(tab: SessionState) {
        // Update title
        customTabTitle?.text = if (tab.content.title.isNotEmpty()) {
            tab.content.title
        } else {
            "Loading..."
        }
        
        // Update URL with "Powered by Nira" branding
        customTabUrl?.text = if (tab.content.url.isNotEmpty()) {
            // Extract domain from URL for cleaner display
            val domain = try {
                java.net.URL(tab.content.url).host
            } catch (e: Exception) {
                tab.content.url
            }
            "Powered by Nira  •  $domain"
        } else {
            "Powered by Nira"
        }
    }
    
    private fun showCustomTabMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.custom_tab_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_find_in_page -> {
                    showFindInPage()
                    true
                }
                R.id.menu_share -> {
                    shareCurrentUrl()
                    true
                }
                R.id.menu_open_in_browser -> {
                    openInBrowser()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    
    private fun shareCurrentUrl() {
        customTabSessionId?.let { sessionId ->
            val customTab = requireContext().components.store.state.findCustomTab(sessionId)
            customTab?.let { tab ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, tab.content.url)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }
        }
    }

    /**
     * Opens the current custom tab in the main browser by creating a new tab without profile context.
     * This creates a "guest" tab that is isolated from all profiles, shown with distinct borders.
     */
    fun openInBrowser() {
        customTabSessionId?.let { sessionId ->
            val customTab = requireContext().components.store.state.findCustomTab(sessionId)
            customTab?.let { tab ->
                // Create tab using TabSessionState directly
                val newTab = mozilla.components.browser.state.state.TabSessionState(
                    id = java.util.UUID.randomUUID().toString(),
                    content = mozilla.components.browser.state.state.ContentState(
                        url = tab.content.url,
                        private = false
                    ),
                    contextId = null,  // Critical: null for guest tab
                    source = SessionState.Source.Internal.NewTab
                )
                
                // Mark this tab as a guest tab in ProfileMiddleware BEFORE dispatching
                requireContext().components.profileMiddleware.markAsGuestTab(newTab.id)
                
                // Add the tab via store dispatch
                requireContext().components.store.dispatch(
                    mozilla.components.browser.state.action.TabListAction.AddTabAction(newTab, select = true)
                )
                
                // Remove the custom tab
                requireContext().components.tabsUseCases.removeTab(sessionId)
                
                // Start the main browser activity
                val intent = Intent(requireContext(), BrowserActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("activeSessionId", newTab.id)
                }
                startActivity(intent)
                
                // Finish the custom tab activity
                activity?.finish()
            }
        }
    }

    override fun onBackPressed(): Boolean {
        return findInPageComponent?.onBackPressed() ?: false || super.onBackPressed()
    }
    
    override fun onDestroyView() {
        findInPageComponent?.destroy()
        super.onDestroyView()
    }
    
    /**
     * Applies custom tab theming to the toolbar based on intent colors.
     */
    private fun applyCustomTabTheming() {
        if (toolbarColor == -1) return
        
        // Apply background color to header
        customTabHeader?.setBackgroundColor(toolbarColor)
        
        // Apply text colors
        customTabTitle?.setTextColor(textColor)
        customTabUrl?.setTextColor(textColor)
        
        // Apply icon tints
        customTabCloseButton?.setColorFilter(textColor)
        customTabMenuButton?.setColorFilter(textColor)
    }
    
    /**
     * Determines if a color is light (requires dark text/icons) or dark (requires light text/icons).
     * Uses the WCAG formula for relative luminance.
     */
    private fun isColorLight(color: Int): Boolean {
        val red = android.graphics.Color.red(color) / 255.0
        val green = android.graphics.Color.green(color) / 255.0
        val blue = android.graphics.Color.blue(color) / 255.0
        
        // Calculate relative luminance
        val r = if (red <= 0.03928) red / 12.92 else Math.pow((red + 0.055) / 1.055, 2.4)
        val g = if (green <= 0.03928) green / 12.92 else Math.pow((green + 0.055) / 1.055, 2.4)
        val b = if (blue <= 0.03928) blue / 12.92 else Math.pow((blue + 0.055) / 1.055, 2.4)
        
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        
        // If luminance > 0.5, it's a light color
        return luminance > 0.5
    }
}
