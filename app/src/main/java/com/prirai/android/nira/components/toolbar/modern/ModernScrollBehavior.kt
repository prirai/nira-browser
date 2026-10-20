package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat

/**
 * CoordinatorLayout behavior that wires the active EngineView into
 * [UnifiedToolbar] / [ModernToolbarSystem] on layout.
 *
 * # History
 * Earlier revisions used this behavior to **toggle the toolbar's minimal
 * state** based on page scroll direction (scroll down -> hide aux bars,
 * scroll up -> show). That behaviour was removed in favour of a single
 * explicit affordance: the chevron button on the address bar (see
 * [UnifiedToolbar.toggleMinimalState]). Scroll-driven minimising
 * produced false positives on short pages, websites with internal
 * scroll containers, and media galleries, and the oscillation was
 * disliked.
 *
 * The behavior is retained because:
 *   - `UnifiedToolbar.layoutParams.behavior` is read back at
 *     [enableScrolling] / [disableScrolling] sites.
 *   - [onLayoutChild] still has to discover the EngineView and attach
 *     it to the toolbar for the toolbar's own scroll-position
 *     observations (download notifier, URL updates, etc.).
 *
 * [enableScrolling] / [disableScrolling] no longer influence toolbar
 * minimising; they are kept as a plumbed-through gate for any future
 * scroll-coupled behaviour so callers do not have to be rewritten.
 */
class ModernScrollBehavior(
    context: Context,
    attrs: AttributeSet? = null,
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    @Suppress("unused")
    private var isScrollingEnabled = true

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int,
    ): Boolean {
        // Find and connect to the EngineView for UnifiedToolbar or ModernToolbarSystem.
        findEngineView(parent)?.let { engine ->
            when (child) {
                is com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar ->
                    child.setEngineView(engine)
                is ModernToolbarSystem ->
                    child.setEngineView(engine)
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
        type: Int,
    ): Boolean {
        // We still return true for vertical axes so the CoordinatorLayout
        // routes nested scrolls to us - some child views rely on the
        // behavior being an active participant in the dispatch chain
        // (e.g. for inset propagation). We simply do not act on dy
        // anymore.
        return axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        // No-op: scroll-driven minimising removed. See class kdoc.
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
        consumed: IntArray,
    ) {
        // No-op: scroll-driven minimising removed. See class kdoc.
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        type: Int,
    ) {
        // No-op: minimal state does not need to settle on gesture end.
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

    /**
     * Legacy no-op kept so callers that previously flipped scroll on/off
     * (e.g. during pull-to-refresh, picture-in-picture) do not have to be
     * rewritten. See class kdoc for context.
     */
    fun enableScrolling() {
        isScrollingEnabled = true
    }

    /** @see enableScrolling */
    fun disableScrolling() {
        isScrollingEnabled = false
    }
}
