package com.prirai.android.nira.components.toolbar

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    
    init {
        // Set Material 3 background color
        val typedArray = context.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorSurface))
        val backgroundColor = typedArray.getColor(0, 0)
        typedArray.recycle()
        if (backgroundColor != 0) {
            setBackgroundColor(backgroundColor)
        }
        
        // Handle window insets for navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            // Add bottom padding for navigation bar
            view.setPadding(
                paddingLeft,
                paddingTop,
                paddingRight,
                navigationBars.bottom
            )
            
            insets
        }
    }
    
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