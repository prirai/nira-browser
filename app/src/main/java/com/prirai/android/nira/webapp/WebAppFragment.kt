package com.prirai.android.nira.webapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.GeckoEngineView
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.window.WindowRequest

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
    private var navigationHistorySize: Int = 0

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
        
        // Create a session with the specified profile context
        // This ensures cookies and data are isolated per profile
        // Browser uses "profile_${profileId}" format for contextId
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
                
                // Register this session with the request interceptor to handle external links
                requireContext().components.appRequestInterceptor.registerWebAppSession(
                    engineSession!!,
                    webappDomain,
                    profileId
                )
                
                // Register as observer to track navigation state and history
                engineSession?.register(this@WebAppFragment)
                
                // Link the engine session to the view
                engineView.render(engineSession!!)
                
                // Load the URL
                engineSession?.loadUrl(url)
                
            } catch (e: Exception) {
                // Failed to create session
                activity?.finish()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister from request interceptor
        engineSession?.let {
            try {
                requireContext().components.appRequestInterceptor.unregisterWebAppSession(it)
            } catch (e: Exception) {
                // Context might be detached
            }
        }
        // Unregister observer
        engineSession?.unregister(this)
        // Release the engine view
        engineView.release()
        engineSession?.close()
        engineSession = null
    }
    
    override fun onLoadRequest(
        url: String,
        triggeredByRedirect: Boolean,
        triggeredByWebContent: Boolean
    ) {
        // Track navigation events - when a new page is being loaded
        if (triggeredByWebContent && !triggeredByRedirect) {
            // User clicked a link or navigated within the webapp
            navigationHistorySize++
        }
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

    fun handleBackPressed(): Boolean {
        // Handle back button - go back in web history if we have navigated within the app
        // Only close if we're at the initial start URL (no history to go back to)
        val session = engineSession ?: return false
        
        // If we have navigation history (navigated away from start URL), go back
        if (navigationHistorySize > 0) {
            session.goBack()
            navigationHistorySize = maxOf(0, navigationHistorySize - 1)
            return true
        }
        
        // No history to go back to - let the activity close the webapp
        return false
    }
    
    private fun openInCustomTab(url: String, profileId: String) {
        try {
            // Open in custom tab with the same profile context
            val customTabIntent = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
            customTabIntent.intent.putExtra("PROFILE_ID", profileId)
            customTabIntent.launchUrl(requireContext(), android.net.Uri.parse(url))
        } catch (e: Exception) {
            // Fallback: open in default system browser
            try {
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
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
            intent.data = android.net.Uri.parse(url)
            intent.putExtra("PROFILE_ID", profileId)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open in default system browser
            try {
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                // Ignore if no browser available
            }
        }
    }
}
