package com.prirai.android.nira.webapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.engine.gecko.GeckoEngineView
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.feature.session.SessionFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper

/**
 * Fragment for displaying PWA content in fullscreen mode
 * No toolbar, no URL bar, just pure web content
 */
class WebAppFragment : Fragment() {

    companion object {
        private const val ARG_WEB_APP_URL = "web_app_url"

        fun newInstance(url: String): WebAppFragment {
            return WebAppFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WEB_APP_URL, url)
                }
            }
        }
    }

    private lateinit var engineView: GeckoEngineView
    private var sessionId: String? = null
    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()

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
        
        // Get URL from arguments
        val url = arguments?.getString(ARG_WEB_APP_URL)
        
        if (url.isNullOrEmpty()) {
            activity?.finish()
            return
        }
        
        // Create or load session
        val store = requireContext().components.store
        val existingTab = store.state.tabs.find { it.content.url == url }
        
        sessionId = if (existingTab != null) {
            existingTab.id
        } else {
            // Create new tab for this PWA
            requireContext().components.tabsUseCases.addTab(
                url = url,
                private = false,
                selectTab = true
            )
            store.state.selectedTabId
        }
        
        sessionId?.let { id ->
            // Set up session feature to connect engine view to tab
            sessionFeature.set(
                feature = SessionFeature(
                    store,
                    requireContext().components.sessionUseCases.goBack,
                    requireContext().components.sessionUseCases.goForward,
                    engineView,
                    id
                ),
                owner = this,
                view = view
            )
            
            // Observe tab state
            store.flowScoped(viewLifecycleOwner) { flow ->
                flow.mapNotNull { state -> state.findTab(id) }
                    .collect { tab ->
                        // Tab state updated
                    }
            }
        }
    }

    fun handleBackPressed(): Boolean {
        // Handle back button - go back in web history
        return sessionFeature.get()?.onBackPressed() ?: false
    }
}
