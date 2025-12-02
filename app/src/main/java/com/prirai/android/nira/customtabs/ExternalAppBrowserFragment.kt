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
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.customtabs.CustomTabWindowFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
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
        
        // Set up custom Find in Page
        setupCustomFindInPage(view)
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
        val insertIndex = if (browserLayout.childCount > 0) 0 else 0
        browserLayout.addView(headerView, insertIndex)
        
        // Add top padding to EngineView to avoid content being hidden under header
        val swipeRefresh = view.findViewById<View>(R.id.swipeRefresh)
        swipeRefresh?.setPadding(0, resources.getDimensionPixelSize(R.dimen.custom_tab_header_height), 0, 0)
        
        // Initialize header views
        customTabHeader = headerView.findViewById(R.id.customTabHeader)
        customTabTitle = headerView.findViewById(R.id.customTabTitle)
        customTabUrl = headerView.findViewById(R.id.customTabUrl)
        customTabMenuButton = headerView.findViewById(R.id.customTabMenuButton)
        customTabCloseButton = headerView.findViewById(R.id.customTabCloseButton)
        
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
     * Attempts to open the current session in the main browser.
     */
    fun openInBrowser() {
        customTabSessionId?.let { sessionId ->
            val customTab = requireContext().components.store.state.findCustomTab(sessionId)
            customTab?.let { tab ->
                // Create intent to open in main browser
                val intent = Intent(requireContext(), BrowserActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = tab.content.url.toUri()
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
        return findInPageComponent?.onBackPressed() ?: false || super.onBackPressed()
    }
    
    override fun onDestroyView() {
        findInPageComponent?.destroy()
        super.onDestroyView()
    }
}
