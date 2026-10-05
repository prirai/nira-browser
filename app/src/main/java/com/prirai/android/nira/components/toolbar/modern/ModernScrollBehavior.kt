package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar

/**
 * Scroll behavior that toggles the UnifiedToolbar's **minimal state** based
 * on page scroll direction.
 *
 * - Scroll page DOWN (dy > 0) past the threshold -> enter minimal state:
 *   the auxiliary bars (tab bar + contextual bar) animate out, address
 *   bar stays visible.
 * - Scroll page UP (dy < 0) past the threshold -> exit minimal state:
 *   the auxiliary bars animate back in.
 *
 * This replaces the previous "fully collapse the toolbar" semantics. The
 * address bar now never scrolls off-screen; only the aux bars toggle.
 *
 * Direction changes reset the accumulator so a small flick back-and-forth
 * does not oscillate the state.
 */
class ModernScrollBehavior(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    private var isScrollingEnabled = true

    // Scroll distance accumulation
    private var scrollYAccumulator = 0

    // 56 dp - same threshold Chrome/Firefox use for their toolbar hide.
    private val scrollThresholdPx: Int =
        (56 * context.resources.displayMetrics.density).toInt()

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int
    ): Boolean {
        // Find and connect to the EngineView for UnifiedToolbar or ModernToolbarSystem
        findEngineView(parent)?.let { engine ->
            when (child) {
                is UnifiedToolbar -> child.setEngineView(engine)
                is ModernToolbarSystem -> child.setEngineView(engine)
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
        accumulateAndMaybeToggle(child, dy)
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
        if (!isScrollingEnabled) return
        // Covers overscroll / fling residue the target view did not consume.
        accumulateAndMaybeToggle(child, dyUnconsumed)
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        type: Int
    ) {
        // No-op: minimal state does not need to settle to a particular
        // position on gesture end the way a hide-on-scroll toolbar would.
    }

    private fun accumulateAndMaybeToggle(child: View, dy: Int) {
        val toolbar = child as? UnifiedToolbar ?: return
        when {
            dy > 0 -> {
                // Finger moving upward (reading further down the page).
                if (scrollYAccumulator < 0) scrollYAccumulator = 0
                scrollYAccumulator += dy
                if (!toolbar.isMinimal() && scrollYAccumulator >= scrollThresholdPx) {
                    toolbar.enterMinimalState()
                    scrollYAccumulator = 0
                }
            }
            dy < 0 -> {
                // Finger moving downward (scrolling back toward top).
                if (scrollYAccumulator > 0) scrollYAccumulator = 0
                scrollYAccumulator += dy
                if (toolbar.isMinimal() && scrollYAccumulator <= -scrollThresholdPx) {
                    toolbar.exitMinimalState()
                    scrollYAccumulator = 0
                }
            }
        }
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

