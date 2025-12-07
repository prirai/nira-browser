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
 * Provides instant show/hide: scroll down hides toolbar, scroll up shows toolbar.
 * Works with any ScrollableToolbar implementation (UnifiedToolbar or ModernToolbarSystem).
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

        // Instant show/hide based on scroll direction
        when {
            dy > 0 && !isToolbarHidden -> {
                // Scrolling down - hide toolbar
                collapseToolbar(child)
                isToolbarHidden = true
            }
            dy < 0 && isToolbarHidden -> {
                // Scrolling up - show toolbar
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
        when {
            dyUnconsumed > 0 && !isToolbarHidden -> {
                // Scrolling down past content - hide toolbar
                collapseToolbar(child)
                isToolbarHidden = true
            }
            dyUnconsumed < 0 && isToolbarHidden -> {
                // Scrolling up past content - show toolbar
                expandToolbar(child)
                isToolbarHidden = false
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

