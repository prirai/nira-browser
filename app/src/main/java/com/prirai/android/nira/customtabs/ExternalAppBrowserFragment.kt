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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.feature.customtabs.CustomTabWindowFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components

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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set customTabSessionId from arguments before calling super
        customTabSessionId = arguments?.getString("activeSessionId")
        super.onCreate(savedInstanceState)
    }

    override fun onInitializeUI(view: View, tab: SessionState) {
        // Hide unnecessary UI elements for custom tabs
        binding.contextualBottomToolbar.isVisible = false
        binding.tabGroupBar?.isVisible = false
        
        // Add custom minimal header
        addCustomHeader(view, tab)
    }
    
    private fun addCustomHeader(view: View, tab: SessionState) {
        // Inflate custom header
        val headerView = LayoutInflater.from(requireContext())
            .inflate(R.layout.custom_tab_header, null)
        
        // Find the browser layout container (CoordinatorLayout)
        val browserLayout = view.findViewById<ViewGroup>(R.id.browserLayout) ?: return
        
        // Set layout params for the header
        val layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP
        }
        headerView.layoutParams = layoutParams
        
        // Add header at the top with proper z-index
        headerView.elevation = 8f
        browserLayout.addView(headerView, browserLayout.childCount)
        
        // Initialize header views
        customTabHeader = headerView.findViewById(R.id.customTabHeader)
        customTabTitle = headerView.findViewById(R.id.customTabTitle)
        customTabUrl = headerView.findViewById(R.id.customTabUrl)
        customTabMenuButton = headerView.findViewById(R.id.customTabMenuButton)
        customTabCloseButton = headerView.findViewById(R.id.customTabCloseButton)
        
        // Set up close button
        customTabCloseButton?.setOnClickListener {
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
                    // Handle find in page
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
     * Attempts to open the current session in the main browser.
     */
    fun openInBrowser() {
        customTabSessionId?.let { sessionId ->
            val customTab = requireContext().components.store.state.findCustomTab(sessionId)
            customTab?.let { tab ->
                // Create intent to open in main browser
                val intent = Intent(requireContext(), BrowserActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = android.net.Uri.parse(tab.content.url)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                // Remove the custom tab
                requireContext().components.tabsUseCases.removeTab(sessionId)
                
                // Start the browser activity
                startActivity(intent)
                
                // Finish the custom tab activity
                activity?.finish()
            }
        }
    }

    override fun onBackPressed(): Boolean {
        return super.onBackPressed()
    }
}
