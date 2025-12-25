package com.prirai.android.nira.customtabs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.FragmentBrowserBinding
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.integration.FindInPageIntegration
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.customtabs.CustomTabWindowFeature
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.session.SwipeRefreshFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.base.feature.UserInteractionHandler
import kotlin.math.pow

/**
 * Fragment for external app browsing with custom header UI but using Mozilla's core features.
 * Combines custom branding/UI with Mozilla's intent processing and session management.
 */
class ExternalAppBrowserFragment : Fragment(), UserInteractionHandler {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    
    var customTabSessionId: String? = null
    
    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()
    private val customTabWindowFeature = ViewBoundFeatureWrapper<CustomTabWindowFeature>()
    private val swipeRefreshFeature = ViewBoundFeatureWrapper<SwipeRefreshFeature>()
    private val findInPageIntegration = ViewBoundFeatureWrapper<FindInPageIntegration>()
    private val downloadsFeature = ViewBoundFeatureWrapper<mozilla.components.feature.downloads.DownloadsFeature>()
    
    private var customTabHeader: LinearLayout? = null
    private var customTabTitle: TextView? = null
    private var customTabUrl: TextView? = null
    private var customTabMenuButton: ImageButton? = null
    private var customTabCloseButton: ImageButton? = null
    
    private var engineView: EngineView? = null
    
    // Custom tab theming colors
    private var toolbarColor: Int = -1
    private var textColor: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        customTabSessionId = arguments?.getString("activeSessionId")
        
        toolbarColor = arguments?.getInt(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, -1) ?: -1
        
        if (toolbarColor != -1) {
            textColor = if (isColorLight(toolbarColor)) {
                "#000000".toColorInt()
            } else {
                "#FFFFFF".toColorInt()
            }
        }
        
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        customTabSessionId = customTabSessionId ?: arguments?.getString("activeSessionId")
        val sessionId = customTabSessionId ?: return
        val tab = requireContext().components.store.state.findCustomTab(sessionId) ?: return
        
        // Hide fullscreen buttons (only for main browser)
        binding.fullscreenExitButton.visibility = View.GONE
        binding.fullscreenToggleButton.visibility = View.GONE
        
        // Setup edge-to-edge
        setupEdgeToEdge()
        
        engineView = binding.engineView
        
        // Add custom header UI
        addCustomHeader(view, tab)
        
        // Adjust content padding for header
        setupCustomTabContentPadding(view)
        
        // Initialize features
        initializeFeatures(sessionId)
        
        // Setup Find in Page
        setupCustomFindInPage(sessionId)
        
        // Observe tab changes
        observeTabChanges(sessionId)
    }
    
    private fun setupEdgeToEdge() {
        // Let header handle its own insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.browserLayout) { _, insets ->
            insets
        }
    }
    
    private fun initializeFeatures(sessionId: String) {
        val components = requireContext().components
        
        // Session feature - connects engine to tab
        sessionFeature.set(
            feature = SessionFeature(
                components.store,
                components.sessionUseCases.goBack,
                components.sessionUseCases.goForward,
                binding.engineView,
                sessionId
            ),
            owner = this,
            view = binding.root
        )
        
        // Custom tab window feature - handles opening links in same window
        customTabWindowFeature.set(
            feature = CustomTabWindowFeature(
                activity = requireActivity(),
                store = components.store,
                sessionId = sessionId
            ),
            owner = this,
            view = binding.root
        )
        
        // Swipe refresh feature - enables pull to reload
        swipeRefreshFeature.set(
            feature = SwipeRefreshFeature(
                components.store,
                components.sessionUseCases.reload,
                binding.swipeRefresh,
                { },
                sessionId
            ),
            owner = this,
            view = binding.root
        )
        
        // Downloads feature - CRITICAL for handling downloads
        downloadsFeature.set(
            feature = mozilla.components.feature.downloads.DownloadsFeature(
                requireContext().applicationContext,
                store = components.store,
                useCases = components.downloadsUseCases,
                fragmentManager = childFragmentManager,
                shouldForwardToThirdParties = { 
                    com.prirai.android.nira.preferences.UserPreferences(requireContext()).promptExternalDownloader 
                },
                downloadManager = mozilla.components.feature.downloads.manager.FetchDownloadManager(
                    requireContext().applicationContext,
                    components.store,
                    com.prirai.android.nira.downloads.DownloadService::class,
                    notificationsDelegate = components.notificationsDelegate
                ),
                tabId = sessionId,
                onNeedToRequestPermissions = { permissions ->
                    requestPermissions(permissions, REQUEST_CODE_DOWNLOAD_PERMISSIONS)
                }
            ),
            owner = this,
            view = binding.root
        )
    }
    
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
        headerView.id = View.generateViewId()
        
        // Add header to the browserWindow (sibling to browserLayout)
        browserWindow.addView(headerView)
        
        // Setup window insets for the header wrapper
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(headerView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
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
        
        // Set up close button
        customTabCloseButton?.setOnClickListener {
            customTabSessionId?.let { sessionId ->
                requireContext().components.customTabsUseCases.remove(sessionId)
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
    
    private fun observeTabChanges(sessionId: String) {
        requireContext().components.store.flowScoped(viewLifecycleOwner) { flow ->
            flow.mapNotNull { state -> state.findCustomTab(sessionId) }
                .collect { tab ->
                    updateHeaderDisplay(tab)
                }
        }
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
                    findInPageIntegration.get()?.launch()
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
    
    private fun isColorLight(color: Int): Boolean {
        val red = android.graphics.Color.red(color) / 255.0
        val green = android.graphics.Color.green(color) / 255.0
        val blue = android.graphics.Color.blue(color) / 255.0
        
        val r = if (red <= 0.03928) red / 12.92 else ((red + 0.055) / 1.055).pow(2.4)
        val g = if (green <= 0.03928) green / 12.92 else ((green + 0.055) / 1.055).pow(2.4)
        val b = if (blue <= 0.03928) blue / 12.92 else ((blue + 0.055) / 1.055).pow(2.4)
        
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance > 0.5
    }
    
    private fun setupCustomFindInPage(sessionId: String) {
        val stub = view?.findViewById<ViewStub>(R.id.stubFindInPage) ?: return
        
        findInPageIntegration.set(
            feature = FindInPageIntegration(
                store = requireContext().components.store,
                sessionId = sessionId,
                stub = stub,
                engineView = binding.engineView,
                toolbarInfo = FindInPageIntegration.ToolbarInfo(
                    toolbar = customTabHeader?.parent as? mozilla.components.browser.toolbar.BrowserToolbar
                        ?: return, // Not a BrowserToolbar, can't use
                    isToolbarDynamic = false,
                    isToolbarPlacedAtTop = true
                ),
                prepareLayout = {},
                restorePreviousLayout = {}
            ),
            owner = this,
            view = view!!
        )
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

    private fun openInBrowser() {
        customTabSessionId?.let { sessionId ->
            val customTab = requireContext().components.store.state.findCustomTab(sessionId)
            customTab?.let { tab ->
                // Create guest tab
                val newTab = mozilla.components.browser.state.state.TabSessionState(
                    id = java.util.UUID.randomUUID().toString(),
                    content = mozilla.components.browser.state.state.ContentState(
                        url = tab.content.url,
                        private = false
                    ),
                    contextId = null,
                    source = SessionState.Source.Internal.NewTab
                )
                
                // Mark as guest tab
                requireContext().components.profileMiddleware.markAsGuestTab(newTab.id)
                
                // Add to store
                requireContext().components.store.dispatch(
                    mozilla.components.browser.state.action.TabListAction.AddTabAction(newTab, select = true)
                )
                
                // Remove custom tab
                requireContext().components.customTabsUseCases.remove(sessionId)
                
                // Launch main browser
                val intent = Intent(requireContext(), BrowserActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("activeSessionId", newTab.id)
                }
                startActivity(intent)
                requireActivity().finish()
            }
        }
    }

    override fun onBackPressed(): Boolean {
        // Check if find in page is open
        if (findInPageIntegration.onBackPressed()) {
            return true
        }
        
        // Check if we can go back in history
        if (sessionFeature.onBackPressed()) {
            return true
        }
        
        // If nothing handled it, finish activity
        requireActivity().finish()
        return true
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_DOWNLOAD_PERMISSIONS -> {
                downloadsFeature.get()?.onPermissionsResult(permissions, grantResults)
            }
        }
    }
    
    override fun onDestroyView() {
        // Lifecycle-aware features cleaned up automatically
        _binding = null
        super.onDestroyView()
    }
    
    companion object {
        private const val REQUEST_CODE_DOWNLOAD_PERMISSIONS = 1
    }
}
