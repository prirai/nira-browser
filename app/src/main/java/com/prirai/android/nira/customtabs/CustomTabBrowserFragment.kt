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
abstract class CustomTabBrowserFragment : Fragment(), mozilla.components.support.base.feature.UserInteractionHandler {

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
        
        // Setup edge-to-edge for custom tabs
        setupEdgeToEdgeForCustomTab()
        
        // Initialize custom tab session
        initializeCustomTab()
    }
    
    /**
     * Setup edge-to-edge for custom tab fragment.
     * Same as BaseBrowserFragment but for custom tabs.
     */
    private fun setupEdgeToEdgeForCustomTab() {
        // Setup insets for the engineView (web content container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.engineView.asView()) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Apply padding to prevent web content from going behind system bars
            view.setPadding(
                0,
                systemBars.top,      // Status bar
                0,
                systemBars.bottom    // Navigation bar
            )
            
            insets
        }
        
        // Make browserLayout pass insets through to children
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.browserLayout) { _, insets ->
            insets
        }
        
        // Request insets to be applied immediately
        binding.engineView.asView().post {
            androidx.core.view.ViewCompat.requestApplyInsets(binding.engineView.asView())
        }
    }
    
    private fun initializeCustomTab() {
        val sessionId = customTabSessionId ?: return
        val tab = requireContext().components.store.state.findCustomTab(sessionId) ?: return
        
        engineView = binding.engineView
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
    
    override fun onBackPressed(): Boolean {
        // If the session can go back, let it handle the back press
        if (sessionFeature.onBackPressed()) {
            return true
        }
        
        // If no back history, finish the activity to return to calling app
        requireActivity().finish()
        return true
    }
}
