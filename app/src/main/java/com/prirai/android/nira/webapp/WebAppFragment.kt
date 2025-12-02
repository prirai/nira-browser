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
    private var canGoBack: Boolean = false

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
        val profileId = arguments?.getString(ARG_PROFILE_ID) ?: "default"
        
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
                
                // Register as observer to track navigation state
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
        // Unregister observer
        engineSession?.unregister(this)
        // Release the engine view
        engineView.release()
        engineSession?.close()
        engineSession = null
    }

    override fun onNavigationStateChange(canGoBack: Boolean?, canGoForward: Boolean?) {
        this.canGoBack = canGoBack ?: false
    }

    fun handleBackPressed(): Boolean {
        // Handle back button - go back in web history
        if (canGoBack) {
            engineSession?.goBack()
            return true
        }
        return false
    }
}
