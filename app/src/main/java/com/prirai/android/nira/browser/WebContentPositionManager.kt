package com.prirai.android.nira.browser

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import mozilla.components.concept.engine.EngineView

/**
 * Manages web content positioning to prevent overlap with system bars and keyboard.
 * 
 * This manager:
 * - Applies padding to engine view for system bars (status bar, navigation bar)
 * - Handles keyboard (IME) appearance with smooth animations
 * - Does NOT handle toolbar margins (that's done in BaseBrowserFragment)
 */
class WebContentPositionManager(
    private val engineView: EngineView,
    private val swipeRefreshView: View,
    private val rootView: View,
    private val getTopToolbarHeight: () -> Int,
    private val getBottomToolbarHeight: () -> Int
) {
    
    private var lastTopMargin = 0
    private var lastBottomMargin = 0
    private var lastImeInset = 0
    private var statusBarHeight = 0
    private var navigationBarHeight = 0
    
    private var isDestroyed = false
    private val drawListener = android.view.ViewTreeObserver.OnDrawListener {
        if (!isDestroyed) {
            updateMargins()
        }
    }
    
    /**
     * Initialize window insets handling for web content positioning
     */
    fun initialize() {
        // Listen to window insets for system bars and keyboard (IME) changes
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            handleWindowInsets(insets)
            insets
        }
        
        // Setup IME (keyboard) animation listener for smooth transitions
        setupKeyboardAnimation()
        
        // Listen for draw events to detect toolbar position changes during scroll
        rootView.viewTreeObserver.addOnDrawListener(drawListener)
        
        // Force initial insets application
        rootView.post {
            ViewCompat.requestApplyInsets(rootView)
        }
    }
    
    /**
     * Update engine view padding to position web content correctly.
     * Apply padding to prevent web content from drawing under system bars.
     */
    private fun updateMargins() {
        val engineViewInstance = (engineView as? View) ?: return
        
        // Apply padding to engine view to prevent content under system bars
        // Status bar at top, navigation bar at bottom
        engineViewInstance.setPadding(
            0,
            statusBarHeight,  // Top padding for status bar
            0,
            navigationBarHeight  // Bottom padding for navigation bar
        )
    }
    
    /**
     * Handle window insets for keyboard (IME) and system bars
     */
    private fun handleWindowInsets(insets: WindowInsetsCompat) {
        // Get system bars insets (status bar at top, navigation bar at bottom)
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        statusBarHeight = systemBars.top
        navigationBarHeight = systemBars.bottom
        
        // Update engine view padding with system bar insets
        updateMargins()
        
        // Get keyboard (IME) insets
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        
        // Calculate visible keyboard height (IME minus navigation bar)
        val visibleImeHeight = maxOf(0, ime.bottom - navigationBarHeight)
        
        // When keyboard appears, set bottom padding on swipeRefreshView
        // to push content above keyboard without black bar
        if (visibleImeHeight > 0 && visibleImeHeight != lastImeInset) {
            val layoutParams = swipeRefreshView.layoutParams as? CoordinatorLayout.LayoutParams ?: return
            layoutParams.bottomMargin = visibleImeHeight
            swipeRefreshView.layoutParams = layoutParams
            swipeRefreshView.requestLayout()
            lastImeInset = visibleImeHeight
        } else if (visibleImeHeight == 0 && lastImeInset > 0) {
            // Keyboard hidden, restore to no margin
            val layoutParams = swipeRefreshView.layoutParams as? CoordinatorLayout.LayoutParams ?: return
            layoutParams.bottomMargin = 0
            swipeRefreshView.layoutParams = layoutParams
            swipeRefreshView.requestLayout()
            lastImeInset = 0
        }
    }
    
    /**
     * Setup smooth keyboard animation handling
     */
    private fun setupKeyboardAnimation() {
        ViewCompat.setWindowInsetsAnimationCallback(
            swipeRefreshView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    // Smooth animation during keyboard appearance
                    handleWindowInsets(insets)
                    return insets
                }
            }
        )
    }
    
    /**
     * Manually trigger margin update (useful when toolbar visibility changes)
     */
    fun requestUpdate() {
        updateMargins()
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        isDestroyed = true
        rootView.viewTreeObserver.removeOnDrawListener(drawListener)
        ViewCompat.setOnApplyWindowInsetsListener(rootView, null)
        ViewCompat.setWindowInsetsAnimationCallback(swipeRefreshView, null)
    }
}
