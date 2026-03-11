package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import mozilla.components.concept.toolbar.ScrollableToolbar

/**
 * Scroll behavior for toolbar auto-hide on scroll.
 * 
 * Uses scroll distance accumulation (modern browser behavior):
 * - Accumulates scroll distance in each direction
 * - Hides toolbar after scrolling down a threshold distance
 * - Shows toolbar after scrolling up a threshold distance
 * 
 * Works with any ScrollableToolbar implementation (UnifiedToolbar or ModernToolbarSystem).
 */
class ModernScrollBehavior(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    private var isScrollingEnabled = true
    private var isToolbarHidden = false
    
    // Scroll distance accumulation
    private var scrollYAccumulator = 0
    
    // Threshold in pixels to trigger show/hide (similar to Chrome/Firefox)
    private val scrollThreshold = 56 // dp converted to pixels below
    private val scrollThresholdPx: Int
    
    init {
        scrollThresholdPx = (scrollThreshold * context.resources.displayMetrics.density).toInt()
    }

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int
    ): Boolean {
        // Find and connect to the EngineView for UnifiedToolbar or ModernToolbarSystem
        findEngineView(parent)?.let { engine ->
            when (child) {
                is com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar -> {
                    child.setEngineView(engine)
                }
                is ModernToolbarSystem -> {
                    child.setEngineView(engine)
                }
            }
        }
        
        return super.onLayoutChild(parent, child, layoutDirection)
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return isScrollingEnabled && axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        if (!isScrollingEnabled) return

        // Accumulate scroll distance based on direction
        when {
            dy > 0 -> {
                // Scrolling down - accumulate downward scroll
                if (scrollYAccumulator > 0) {
                    // Change of direction - reset accumulator
                    scrollYAccumulator = 0
                }
                scrollYAccumulator += dy
                
                // Hide toolbar if threshold reached and not already hidden
                if (!isToolbarHidden && scrollYAccumulator >= scrollThresholdPx) {
                    collapseToolbar(child)
                    isToolbarHidden = true
                    scrollYAccumulator = 0 // Reset after action
                }
            }
            dy < 0 -> {
                // Scrolling up - accumulate upward scroll
                if (scrollYAccumulator < 0) {
                    // Change of direction - reset accumulator
                    scrollYAccumulator = 0
                }
                scrollYAccumulator += dy
                
                // Show toolbar if threshold reached and currently hidden
                if (isToolbarHidden && scrollYAccumulator <= -scrollThresholdPx) {
                    expandToolbar(child)
                    isToolbarHidden = false
                    scrollYAccumulator = 0 // Reset after action
                }
            }
        }
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        // Handle overscroll/fling scenarios with same scroll distance logic
        when {
            dyUnconsumed > 0 -> {
                // Scrolling down past content
                if (scrollYAccumulator > 0) {
                    scrollYAccumulator = 0
                }
                scrollYAccumulator += dyUnconsumed
                
                if (!isToolbarHidden && scrollYAccumulator >= scrollThresholdPx) {
                    collapseToolbar(child)
                    isToolbarHidden = true
                    scrollYAccumulator = 0
                }
            }
            dyUnconsumed < 0 -> {
                // Scrolling up past content
                if (scrollYAccumulator < 0) {
                    scrollYAccumulator = 0
                }
                scrollYAccumulator += dyUnconsumed
                
                if (isToolbarHidden && scrollYAccumulator <= -scrollThresholdPx) {
                    expandToolbar(child)
                    isToolbarHidden = false
                    scrollYAccumulator = 0
                }
            }
        }
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        type: Int
    ) {
        // Nothing to do - state is already determined by scroll direction
    }
    
    private fun expandToolbar(child: View) {
        (child as? ScrollableToolbar)?.expand()
    }
    
    private fun collapseToolbar(child: View) {
        (child as? ScrollableToolbar)?.collapse()
    }

    private fun findEngineView(coordinatorLayout: CoordinatorLayout): mozilla.components.concept.engine.EngineView? {
        for (i in 0 until coordinatorLayout.childCount) {
            val child = coordinatorLayout.getChildAt(i)
            
            if (child is mozilla.components.concept.engine.EngineView) {
                return child
            }
            
            if (child is androidx.fragment.app.FragmentContainerView) {
                return searchForEngineView(child)
            }
        }
        return null
    }

    private fun searchForEngineView(view: View): mozilla.components.concept.engine.EngineView? {
        if (view is mozilla.components.concept.engine.EngineView) return view
        
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                searchForEngineView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    fun enableScrolling() {
        isScrollingEnabled = true
    }

    fun disableScrolling() {
        isScrollingEnabled = false
    }
}

