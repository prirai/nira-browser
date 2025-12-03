package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat

/**
 * Revolutionary scroll behavior that provides buttery smooth toolbar animations
 * with intelligent snapping and momentum-based hiding/showing.
 */
class ModernScrollBehavior(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<ModernToolbarSystem>(context, attrs) {

    private var totalScrolled = 0
    private var lastScrollDirection = 0
    private var snapThreshold = 0.3f
    private var isScrollingEnabled = true
    private var isToolbarHidden = false
    private var consecutiveDownwardScrolls = 0

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: ModernToolbarSystem,
        layoutDirection: Int
    ): Boolean {
        // Find and connect to the EngineView
        findEngineView(parent)?.let { engine ->
            child.setEngineView(engine)
        }
        
        return super.onLayoutChild(parent, child, layoutDirection)
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: ModernToolbarSystem,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return isScrollingEnabled && axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: ModernToolbarSystem,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        if (!isScrollingEnabled) return
        
        val toolbarHeight = child.getTotalHeight()
        
        if (toolbarHeight <= 0) {
            return
        }

        // Track scroll direction changes
        val currentDirection = if (dy > 0) 1 else if (dy < 0) -1 else 0
        
        // Reset totalScrolled when direction changes from down to up while toolbar is visible
        if (currentDirection == -1 && lastScrollDirection == 1 && !isToolbarHidden) {
            // User scrolled down then immediately up while toolbar still visible
            // Reset to current position to prevent toolbar from reappearing from bottom
            totalScrolled = child.getCurrentOffset()
        }
        
        // Count consecutive downward scrolls to prevent toolbar flashing
        if (currentDirection == 1) {
            consecutiveDownwardScrolls++
        } else if (currentDirection == -1) {
            consecutiveDownwardScrolls = 0
        }
        
        lastScrollDirection = currentDirection
        
        totalScrolled += dy
        val newOffset = totalScrolled.coerceIn(0, toolbarHeight)
        
        // Update toolbar state tracking
        isToolbarHidden = (newOffset >= toolbarHeight)
        
        if (newOffset != child.getCurrentOffset()) {
            child.setToolbarOffset(newOffset)
        }
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: ModernToolbarSystem,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        // Only apply intelligent snapping when scroll momentum changes or reaches end
        if (dyUnconsumed != 0) {
            val toolbarHeight = child.getTotalHeight()
            val currentOffset = child.getCurrentOffset()
            val threshold = toolbarHeight * snapThreshold
            
            // Only trigger if we have a valid toolbar height
            if (toolbarHeight > 0) {
                when {
                    // Scrolling down and toolbar should be hidden
                    dyUnconsumed > 0 && currentOffset > threshold && !isToolbarHidden -> {
                        child.collapse()
                        isToolbarHidden = true
                    }
                    // Scrolling up and toolbar is currently hidden - show it
                    dyUnconsumed < 0 && isToolbarHidden -> {
                        child.expand()
                        isToolbarHidden = false
                        consecutiveDownwardScrolls = 0
                    }
                }
            }
        }
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: ModernToolbarSystem,
        target: View,
        type: Int
    ) {
        // Final snap decision when scrolling stops
        val toolbarHeight = child.getTotalHeight()
        val currentOffset = child.getCurrentOffset()
        val midpoint = toolbarHeight / 2
        
        // Only snap if toolbar is partially visible
        if (currentOffset in 1 until toolbarHeight) {
            if (currentOffset < midpoint) {
                child.expand()
                isToolbarHidden = false
            } else {
                child.collapse()
                isToolbarHidden = true
            }
        } else {
            // Update state based on current position
            isToolbarHidden = (currentOffset >= toolbarHeight)
        }
    }

    private fun findEngineView(coordinatorLayout: CoordinatorLayout): mozilla.components.concept.engine.EngineView? {
        for (i in 0 until coordinatorLayout.childCount) {
            val child = coordinatorLayout.getChildAt(i)
            
            // Direct EngineView
            if (child is mozilla.components.concept.engine.EngineView) {
                return child
            }
            
            // EngineView in ViewPager2 or Fragment
            if (child is androidx.viewpager2.widget.ViewPager2) continue
            if (child is androidx.fragment.app.FragmentContainerView) {
                return searchForEngineView(child)
            }
        }
        return null
    }

    private fun searchForEngineView(view: View): mozilla.components.concept.engine.EngineView? {
        if (view is mozilla.components.concept.engine.EngineView) return view
        if (view is androidx.viewpager2.widget.ViewPager2) return null
        
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val engineView = searchForEngineView(view.getChildAt(i))
                if (engineView != null) return engineView
            }
        }
        return null
    }

    fun setScrollingEnabled(enabled: Boolean) {
        isScrollingEnabled = enabled
    }

    fun setSnapThreshold(threshold: Float) {
        snapThreshold = threshold.coerceIn(0.1f, 0.9f)
    }
}