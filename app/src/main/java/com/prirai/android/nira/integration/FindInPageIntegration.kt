package com.prirai.android.nira.integration

import android.view.View
import android.view.ViewStub
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.findinpage.FindInPageFeature
import mozilla.components.feature.findinpage.view.FindInPageView
import mozilla.components.support.base.feature.LifecycleAwareFeature

class FindInPageIntegration(
    private val store: BrowserStore,
    private val sessionId: String? = null,
    stub: ViewStub,
    private val engineView: EngineView,
    private val toolbarInfo: ToolbarInfo,
    private val prepareLayout: () -> Unit,
    private val restorePreviousLayout: () -> Unit
) : InflationAwareFeature(stub) {
    
    private var insetsListener: ((WindowInsetsCompat) -> Unit)? = null
    
    override fun onViewInflated(view: View): LifecycleAwareFeature {
        return FindInPageFeature(store, view as FindInPageView, engineView) {
            restorePreviousLayout()
            view.visibility = View.GONE
            
            // Remove insets listener when closing
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            insetsListener = null
        }
    }

    override fun onLaunch(view: View, feature: LifecycleAwareFeature) {
        store.state.findCustomTabOrSelectedTab(sessionId)?.let { tab ->
            prepareLayout()

            view.visibility = View.VISIBLE
            (feature as FindInPageFeature).bind(tab)
            view.layoutParams.height = toolbarInfo.toolbar.height
            
            // Listen for keyboard insets to adjust position
            insetsListener = { insets ->
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                
                // Adjust translationY to move above keyboard with extra padding
                if (imeInsets.bottom > systemBarsInsets.bottom) {
                    // Keyboard is showing, move view up with 16dp extra padding
                    val extraPadding = (16 * view.context.resources.displayMetrics.density).toInt()
                    view.translationY = -(imeInsets.bottom - systemBarsInsets.bottom + extraPadding).toFloat()
                } else {
                    // Keyboard is hidden, reset position
                    view.translationY = 0f
                }
            }
            
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                insetsListener?.invoke(insets)
                insets
            }
            
            // Request initial insets
            ViewCompat.requestApplyInsets(view)
        }
    }

    /**
     * Holder of all details needed about the Toolbar.
     * Used to modify the layout of BrowserToolbar while the find in page bar is shown.
     */
    data class ToolbarInfo(
        val toolbar: BrowserToolbar,
        val isToolbarDynamic: Boolean,
        val isToolbarPlacedAtTop: Boolean
    )
}
