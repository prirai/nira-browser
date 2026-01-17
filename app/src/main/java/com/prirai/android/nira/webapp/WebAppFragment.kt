package com.prirai.android.nira.webapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.integration.ContextMenuIntegration
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.GeckoEngineView
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.window.WindowRequest
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import androidx.core.net.toUri

/**
 * Fragment for displaying PWA content in fullscreen mode
 * No toolbar, no URL bar, just pure web content
 */
class WebAppFragment : Fragment(), EngineSession.Observer {

    companion object {
        private const val ARG_WEB_APP_URL = "web_app_url"
        private const val ARG_PROFILE_ID = "profile_id"

        fun newInstance(url: String, profileId: String = "default"): WebAppFragment {
            return WebAppFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WEB_APP_URL, url)
                    putString(ARG_PROFILE_ID, profileId)
                }
            }
        }
    }

    private lateinit var engineView: GeckoEngineView
    private var engineSession: EngineSession? = null
    private var profileId: String = "default"
    private var startUrl: String? = null
    private var canGoBack: Boolean = false
    private val contextMenuIntegration = ViewBoundFeatureWrapper<ContextMenuIntegration>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_webapp, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        engineView = view.findViewById(R.id.engineView)
        
        // Get URL and profile from arguments
        val url = arguments?.getString(ARG_WEB_APP_URL)
        profileId = arguments?.getString(ARG_PROFILE_ID) ?: "default"
        startUrl = url
        
        if (url.isNullOrEmpty()) {
            activity?.finish()
            return
        }
        
        // Setup context menu integration
        setupContextMenu(view)
        
        // Setup keyboard adjustment
        setupKeyboardAdjustment()
        
        // Create a session with the specified profile context
        lifecycleScope.launch {
            try {
                val engine = requireContext().components.engine
                val contextId = "profile_$profileId"
                
                engineSession = engine.createSession(private = false, contextId = contextId)
                
                // Extract domain from URL
                val webappDomain = try {
                    java.net.URL(url).host
                } catch (e: Exception) {
                    url
                }
                
                // Register this session with the request interceptor
                requireContext().components.appRequestInterceptor.registerWebAppSession(
                    engineSession!!,
                    webappDomain,
                    profileId
                )
                
                // Register as observer to track navigation
                engineSession?.register(this@WebAppFragment)
                
                // Link the engine session to the view
                engineView.render(engineSession!!)
                
                // Load the URL
                engineSession?.loadUrl(url)
                
            } catch (e: Exception) {
                activity?.finish()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        engineSession?.let {
            try {
                requireContext().components.appRequestInterceptor.unregisterWebAppSession(it)
            } catch (e: Exception) {
                // Context might be detached
            }
        }
        engineSession?.unregister(this)
        engineView.release()
        engineSession?.close()
        engineSession = null
    }
    
    override fun onWindowRequest(windowRequest: WindowRequest) {
        // Handle new window requests (e.g., target="_blank" links)
        when (windowRequest.type) {
            WindowRequest.Type.OPEN -> {
                // External link - open in custom tab with same profile
                openInCustomTab(windowRequest.url, profileId)
                // Consume the request
                windowRequest.prepare()
                windowRequest.start()
            }
            WindowRequest.Type.CLOSE -> {
                // Close request - ignore for webapps
            }
        }
    }

    override fun onNavigationStateChange(canGoBack: Boolean?, canGoForward: Boolean?) {
        // Track navigation state
        canGoBack?.let { this.canGoBack = it }
    }

    fun handleBackPressed(): Boolean {
        // Check if we can go back in navigation history
        val session = engineSession ?: return false
        
        // Use tracked canGoBack state
        return if (this.canGoBack) {
            session.goBack()
            true
        } else {
            // No history to go back to - let the activity close the webapp
            false
        }
    }
    
    /**
     * Initializes context menu integration for PWA to allow long-press actions on links.
     * Provides options like copy link, share, open in new tab, save image, etc.
     */
    private fun setupContextMenu(view: View) {
        val components = requireContext().components
        
        contextMenuIntegration.set(
            feature = ContextMenuIntegration(
                context = requireContext(),
                fragmentManager = parentFragmentManager,
                browserStore = components.store,
                tabsUseCases = components.tabsUseCases,
                contextMenuUseCases = components.contextMenuUseCases,
                parentView = view,
                sessionId = null, // PWAs don't use session IDs from store
                engineView = engineView
            ),
            owner = this,
            view = view
        )
    }
    
    /**
     * Adjusts engine view bottom padding when keyboard appears to prevent content overlap.
     * Uses WindowInsets to detect keyboard visibility and automatically adjust layout.
     */
    private fun setupKeyboardAdjustment() {
        // Adjust engine view position when keyboard appears
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(engineView as View) { view, insets ->
            val imeInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val systemBarsInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Adjust bottom padding when keyboard appears
            if (imeInsets.bottom > systemBarsInsets.bottom) {
                // Keyboard is showing, add bottom padding
                val keyboardHeight = imeInsets.bottom - systemBarsInsets.bottom
                view.setPadding(0, 0, 0, keyboardHeight)
            } else {
                // Keyboard is hidden, reset padding
                view.setPadding(0, 0, 0, 0)
            }
            
            insets
        }
        
        // Request initial insets
        androidx.core.view.ViewCompat.requestApplyInsets(engineView as View)
    }
    
    private fun openInCustomTab(url: String, profileId: String) {
        try {
            val customTabIntent = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
            customTabIntent.intent.putExtra("PROFILE_ID", profileId)
            customTabIntent.launchUrl(requireContext(), url.toUri())
        } catch (e: Exception) {
            // Fallback: open in default system browser
            try {
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                // Ignore if no browser available
            }
        }
    }
    
    private fun openInBrowser(url: String, profileId: String) {
        try {
            val intent = android.content.Intent(requireContext(), com.prirai.android.nira.BrowserActivity::class.java)
            intent.action = android.content.Intent.ACTION_VIEW
            intent.data = url.toUri()
            intent.putExtra("PROFILE_ID", profileId)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open in default system browser
            try {
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                    url.toUri())
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                // Ignore if no browser available
            }
        }
    }

    /**
     * Request notification permission for this PWA
     */
    fun requestNotificationPermissionIfNeeded(callback: (Boolean) -> Unit) {
        val notificationManager = WebAppNotificationManager(requireContext())
        
        if (notificationManager.hasNotificationPermission()) {
            callback(true)
            return
        }
        
        val webAppActivity = activity as? WebAppActivity
        if (webAppActivity != null) {
            webAppActivity.requestNotificationPermission { granted ->
                callback(granted)
            }
        } else {
            callback(false)
        }
    }
}
