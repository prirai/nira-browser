package com.prirai.android.nira.browser

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import mozilla.components.concept.engine.EngineView

/**
 * Manages web content positioning to prevent overlap with toolbars and keyboard.
 * 
 * Responsibilities:
 * - Measure all visible toolbars (top and bottom)
 * - Apply appropriate padding to EngineView
 * - Handle keyboard (IME) appearance
 * - Adjust for system bars (status bar, navigation bar)
 * - Support both top and bottom toolbar modes
 * - Handle toolbar auto-hide/show animations
 */
class WebContentPositionManager(
    private val engineView: EngineView,
    private val rootView: View,
    private val unifiedToolbar: View?
) {
    
    private var lastTopInset = 0
    private var lastBottomInset = 0
    private var lastKeyboardInset = 0
    
    /**
     * Initialize window insets handling for web content positioning
     */
    fun initialize() {
        val view = engineView.asView()
        
        // Listen to all insets changes
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            updateWebContentPadding(v, insets)
            insets
        }
        
        // Setup IME (keyboard) animation listener for smooth transitions
        setupKeyboardAnimation(view)
        
        // Force initial insets application
        view.post {
            ViewCompat.requestApplyInsets(view)
        }
        
        // Listen to toolbar visibility changes
        unifiedToolbar?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            view.post {
                ViewCompat.requestApplyInsets(view)
            }
        }
    }
    
    /**
     * Update web content padding based on all insets and toolbar positions
     */
    private fun updateWebContentPadding(view: View, insets: WindowInsetsCompat) {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        
        // Calculate toolbar heights
        val (topToolbarHeight, bottomToolbarHeight) = measureToolbarHeights()
        
        // Top padding: status bar + top toolbars
        val topPadding = systemBars.top + topToolbarHeight
        
        // Bottom padding: max of (navigation bar + bottom toolbars, keyboard)
        // When keyboard is shown, it replaces bottom toolbars
        val bottomNavAndToolbars = systemBars.bottom + bottomToolbarHeight
        val bottomPadding = maxOf(bottomNavAndToolbars, ime.bottom)
        
        // Only update if changed to avoid unnecessary relayout
        if (topPadding != lastTopInset || bottomPadding != lastBottomInset) {
            view.setPadding(0, topPadding, 0, bottomPadding)
            lastTopInset = topPadding
            lastBottomInset = bottomPadding
        }
        
        lastKeyboardInset = ime.bottom
    }
    
    /**
     * Measure the combined height of all visible toolbars at top and bottom
     * Returns Pair<topHeight, bottomHeight>
     */
    private fun measureToolbarHeights(): Pair<Int, Int> {
        var topHeight = 0
        var bottomHeight = 0
        
        unifiedToolbar?.let { toolbar ->
            if (toolbar.isVisible && toolbar.height > 0) {
                // Check if toolbar is positioned at top or bottom
                // This is determined by the toolbar's layout params or parent position
                val toolbarParent = toolbar.parent as? ViewGroup
                val isAtTop = isToolbarAtTop(toolbar, toolbarParent)
                
                if (isAtTop) {
                    topHeight += toolbar.height
                } else {
                    bottomHeight += toolbar.height
                }
            }
        }
        
        return Pair(topHeight, bottomHeight)
    }
    
    /**
     * Determine if a toolbar is positioned at the top of the screen
     */
    private fun isToolbarAtTop(toolbar: View, parent: ViewGroup?): Boolean {
        parent ?: return false
        
        // Check layout gravity or position
        val layoutParams = toolbar.layoutParams
        if (layoutParams is androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
            val gravity = layoutParams.gravity
            return (gravity and android.view.Gravity.TOP) == android.view.Gravity.TOP
        }
        
        if (layoutParams is android.widget.FrameLayout.LayoutParams) {
            val gravity = layoutParams.gravity
            return (gravity and android.view.Gravity.TOP) == android.view.Gravity.TOP
        }
        
        // Default: check if toolbar Y position is near top
        return toolbar.y < parent.height / 2
    }
    
    /**
     * Setup smooth keyboard animation handling
     */
    private fun setupKeyboardAnimation(view: View) {
        ViewCompat.setWindowInsetsAnimationCallback(
            view,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    // Update padding during animation for smooth keyboard appearance
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    
                    val (topToolbarHeight, bottomToolbarHeight) = measureToolbarHeights()
                    val topPadding = systemBars.top + topToolbarHeight
                    val bottomNavAndToolbars = systemBars.bottom + bottomToolbarHeight
                    val bottomPadding = maxOf(bottomNavAndToolbars, ime.bottom)
                    
                    view.setPadding(0, topPadding, 0, bottomPadding)
                    
                    return insets
                }
            }
        )
    }
    
    /**
     * Manually trigger padding update (useful when toolbar visibility changes)
     */
    fun requestUpdate() {
        val view = engineView.asView()
        view.post {
            ViewCompat.requestApplyInsets(view)
        }
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        val view = engineView.asView()
        ViewCompat.setOnApplyWindowInsetsListener(view, null)
        ViewCompat.setWindowInsetsAnimationCallback(view, null)
    }
}
