package com.prirai.android.nira.components.toolbar

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import mozilla.components.concept.toolbar.ScrollableToolbar
import mozilla.components.ui.widgets.behavior.EngineViewScrollingBehavior

/**
 * A container view that hosts the bottom toolbar components (tab group bar, address bar, contextual toolbar).
 * Facilitates hide-on-scroll behavior following Mozilla's exact pattern.
 * 
 * Based on Mozilla Firefox's BottomToolbarContainerView implementation.
 */
class BottomToolbarContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), ScrollableToolbar {
    
    override fun enableScrolling() {
        (layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            (behavior as? EngineViewScrollingBehavior)?.enableScrolling()
        }
    }

    override fun disableScrolling() {
        (layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            (behavior as? EngineViewScrollingBehavior)?.disableScrolling()
        }
    }

    override fun expand() {
        (layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            (behavior as? EngineViewScrollingBehavior)?.forceExpand(this@BottomToolbarContainerView)
        }
    }

    override fun collapse() {
        (layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            (behavior as? EngineViewScrollingBehavior)?.forceCollapse(this@BottomToolbarContainerView)
        }
    }
}