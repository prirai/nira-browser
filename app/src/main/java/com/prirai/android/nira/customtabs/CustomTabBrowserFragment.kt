package com.prirai.android.nira.customtabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.customtabs.CustomTabWindowFeature
import mozilla.components.feature.session.SessionFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import com.prirai.android.nira.databinding.FragmentBrowserBinding
import com.prirai.android.nira.ext.components

/**
 * Minimal base fragment for custom tabs that skips heavy toolbar initialization.
 * Unlike BaseBrowserFragment, this only initializes essential features needed for custom tabs.
 */
abstract class CustomTabBrowserFragment : Fragment() {

    protected lateinit var binding: FragmentBrowserBinding
    
    var customTabSessionId: String? = null
    
    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()
    private val customTabWindowFeature = ViewBoundFeatureWrapper<CustomTabWindowFeature>()
    
    protected var engineView: EngineView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize custom tab session
        initializeCustomTab()
    }
    
    private fun initializeCustomTab() {
        val sessionId = customTabSessionId ?: return
        val tab = requireContext().components.store.state.findCustomTab(sessionId) ?: return
        
        // Get the engine view
        engineView = binding.engineView
        
        // Initialize UI
        onInitializeUI(binding.root, tab)
        
        // Set up session feature to connect engine view to tab
        sessionFeature.set(
            feature = SessionFeature(
                requireContext().components.store,
                requireContext().components.sessionUseCases.goBack,
                requireContext().components.sessionUseCases.goForward,
                binding.engineView,
                sessionId
            ),
            owner = this,
            view = binding.root
        )
        
        // Set up custom tab window feature
        customTabWindowFeature.set(
            feature = CustomTabWindowFeature(
                activity = requireActivity(),
                store = requireContext().components.store,
                sessionId = sessionId
            ),
            owner = this,
            view = binding.root
        )
        
        // Observe tab changes
        requireContext().components.store.flowScoped(viewLifecycleOwner) { flow ->
            flow.mapNotNull { state -> state.findCustomTab(sessionId) }
                .collect { tab ->
                    onTabUpdated(tab)
                }
        }
    }
    
    /**
     * Called when UI should be initialized. Override this to set up custom UI.
     */
    protected abstract fun onInitializeUI(view: View, tab: SessionState)
    
    /**
     * Called when the tab is updated. Override this to update custom UI.
     */
    protected open fun onTabUpdated(tab: SessionState) {
        // Override in subclass if needed
    }
    
    open fun onBackPressed(): Boolean {
        return sessionFeature.onBackPressed()
    }
}
