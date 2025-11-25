package com.prirai.android.nira.components.toolbar.modern

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import mozilla.components.concept.toolbar.ScrollableToolbar
import mozilla.components.concept.engine.EngineView

/**
 * Revolutionary unified toolbar system that combines all toolbar components
 * into one seamless, beautifully animated container with perfect scroll behavior.
 */
class ModernToolbarSystem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), ScrollableToolbar {

    private var currentOffset = 0
    private var scrollingEnabled = true
    private val hideAnimator = ValueAnimator()
    private var engineView: EngineView? = null

    // Position tracking for proper scroll direction
    private var toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM

    // Components
    private var tabGroupComponent: View? = null
    private var addressBarComponent: View? = null
    private var contextualComponent: View? = null

    enum class ToolbarPosition {
        TOP, BOTTOM
    }

    init {
        orientation = VERTICAL
        clipToPadding = false
        clipChildren = false
    }

    fun addComponent(component: View, type: ComponentType) {

        val layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )

        when (type) {
            ComponentType.TAB_GROUP -> {
                tabGroupComponent = component
                addView(component, 0, layoutParams) // Top position
            }

            ComponentType.ADDRESS_BAR -> {
                addressBarComponent = component
                val index = if (tabGroupComponent != null) 1 else 0
                addView(component, index, layoutParams)
            }

            ComponentType.CONTEXTUAL -> {
                contextualComponent = component
                addView(component, layoutParams) // Bottom position
            }
        }

        // Update engine view about our new height
        updateDynamicToolbarHeight()
    }

    fun removeComponent(type: ComponentType) {
        val component = when (type) {
            ComponentType.TAB_GROUP -> tabGroupComponent
            ComponentType.ADDRESS_BAR -> addressBarComponent
            ComponentType.CONTEXTUAL -> contextualComponent
        }

        component?.let {
            removeView(it)
            when (type) {
                ComponentType.TAB_GROUP -> tabGroupComponent = null
                ComponentType.ADDRESS_BAR -> addressBarComponent = null
                ComponentType.CONTEXTUAL -> contextualComponent = null
            }
        }

        updateDynamicToolbarHeight()
    }

    fun setEngineView(engine: EngineView) {
        engineView = engine

        // CRITICAL: For bottom toolbar, immediately set clipping to 0
        // to prevent black bar at top before any scroll events
        if (toolbarPosition == ToolbarPosition.BOTTOM) {
            engine.setDynamicToolbarMaxHeight(0)
            engine.setVerticalClipping(0)
        }

        updateDynamicToolbarHeight()
    }

    fun setToolbarPosition(position: ToolbarPosition) {
        toolbarPosition = position
    }

    private fun updateDynamicToolbarHeight() {
        // CRITICAL: Don't set dynamic toolbar height for bottom toolbar
        // Gecko reserves space at the TOP by default, causing black bar at top
        // Bottom toolbar doesn't need dynamic toolbar behavior
        if (toolbarPosition == ToolbarPosition.BOTTOM) {
            engineView?.setDynamicToolbarMaxHeight(0)
            return
        }

        val totalHeight = getTotalHeight()
        if (totalHeight > 0) {
            engineView?.setDynamicToolbarMaxHeight(totalHeight)
        }
    }

    fun getTotalHeight(): Int {
        var totalHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isVisible) {
                totalHeight += child.height
            }
        }

        return totalHeight
    }

    override fun enableScrolling() {
        scrollingEnabled = true
    }

    override fun disableScrolling() {
        scrollingEnabled = false
        expand()
    }

    override fun expand() {
        if (!scrollingEnabled) return
        animateToOffset(0)
    }

    override fun collapse() {
        if (!scrollingEnabled) return
        val totalHeight = getTotalHeight()
        if (totalHeight > 0) {
            animateToOffset(totalHeight)
        }
    }

    private fun animateToOffset(targetOffset: Int) {
        hideAnimator.cancel()
        hideAnimator.removeAllUpdateListeners()

        hideAnimator.apply {
            setIntValues(currentOffset, targetOffset)
            duration = 800
            addUpdateListener { animation ->
                val offset = animation.animatedValue as Int
                setToolbarOffset(offset)
            }
            start()
        }
    }

    fun setToolbarOffset(offset: Int) {
        val totalHeight = getTotalHeight()
        currentOffset = offset.coerceIn(0, totalHeight)

        // Position-aware translation:
        // TOP toolbar: Negative Y to hide upward, 0 Y to show
        // BOTTOM toolbar: Positive Y to hide downward, 0 Y to show
        translationY = when (toolbarPosition) {
            ToolbarPosition.TOP -> -currentOffset.toFloat()  // Negative moves UP (hiding)
            ToolbarPosition.BOTTOM -> currentOffset.toFloat()  // Positive moves DOWN (hiding)
        }

        alpha = if (totalHeight > 0) {
            1f - (currentOffset.toFloat() / totalHeight * 0.3f) // Subtle fade
        } else 1f

        // Apply clipping to engine view
        // CRITICAL: Don't clip for bottom toolbar - clipping removes content from TOP
        if (toolbarPosition == ToolbarPosition.TOP) {
            engineView?.setVerticalClipping(currentOffset)
        } else {
            engineView?.setVerticalClipping(0)
        }
    }

    fun getCurrentOffset(): Int = currentOffset

    enum class ComponentType {
        TAB_GROUP,
        ADDRESS_BAR,
        CONTEXTUAL
    }
}
