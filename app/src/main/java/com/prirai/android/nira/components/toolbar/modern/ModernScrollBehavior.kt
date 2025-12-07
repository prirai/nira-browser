package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import mozilla.components.concept.toolbar.ScrollableToolbar

/**
 * Revolutionary scroll behavior that provides buttery smooth toolbar animations
 * with intelligent snapping and momentum-based hiding/showing.
 * 
 * Works with any ScrollableToolbar implementation (ModernToolbarSystem or UnifiedToolbar).
 */
class ModernScrollBehavior(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    private var isScrollingEnabled = true
    private var isToolbarHidden = false

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
        
        val toolbarHeight = getToolbarHeight(child)
        
        if (toolbarHeight <= 0) {
            return
        }

        // Simple binary approach: scroll down = hide, scroll up = show
        val currentDirection = if (dy > 0) 1 else if (dy < 0) -1 else 0
        
        when {
            // Scrolling down - hide toolbar
            currentDirection == 1 && !isToolbarHidden -> {
                collapseToolbar(child)
                isToolbarHidden = true
            }
            // Scrolling up - show toolbar
            currentDirection == -1 && isToolbarHidden -> {
                expandToolbar(child)
                isToolbarHidden = false
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
        // Handle overscroll/fling scenarios
        if (dyUnconsumed != 0) {
            when {
                // Scrolling down past content - ensure toolbar is hidden
                dyUnconsumed > 0 && !isToolbarHidden -> {
                    collapseToolbar(child)
                    isToolbarHidden = true
                }
                // Scrolling up past content - ensure toolbar is visible
                dyUnconsumed < 0 && isToolbarHidden -> {
                    expandToolbar(child)
                    isToolbarHidden = false
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
        // No need for snapping with instant show/hide
        // State is already determined by scroll direction
    }
    
    private fun getToolbarHeight(child: View): Int {
        return when (child) {
            is ModernToolbarSystem -> child.getTotalHeight()
            else -> child.height
        }
    }
    
    private fun getCurrentOffset(child: View): Int {
        return when (child) {
            is ModernToolbarSystem -> child.getCurrentOffset()
            else -> 0
        }
    }
    
    private fun setToolbarOffset(child: View, offset: Int) {
        when (child) {
            is ModernToolbarSystem -> child.setToolbarOffset(offset)
            else -> {
                // Generic implementation using translationY
                child.translationY = offset.toFloat()
            }
        }
    }
    
    private fun expandToolbar(child: View) {
        when (child) {
            is ScrollableToolbar -> child.expand()
            else -> setToolbarOffset(child, 0)
        }
    }
    
    private fun collapseToolbar(child: View) {
        when (child) {
            is ScrollableToolbar -> child.collapse()
            else -> setToolbarOffset(child, getToolbarHeight(child))
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

    fun enableScrolling() {
        isScrollingEnabled = true
    }

    fun disableScrolling() {
        isScrollingEnabled = false
    }
}

