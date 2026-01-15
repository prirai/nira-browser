package com.prirai.android.nira.components.toolbar.modern

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    
    // Toolbar offset callback for smooth margin adjustment
    private var onOffsetChangedListener: ((Int, Int) -> Unit)? = null

    enum class ToolbarPosition {
        TOP, BOTTOM
    }

    init {
        orientation = VERTICAL
        clipToPadding = false
        clipChildren = false
        
        // Use unified ThemeManager helper (supports AMOLED + Material 3)
        setBackgroundColor(
            com.prirai.android.nira.theme.ThemeManager.getToolbarBackgroundColor(
                context, 
                useElevation = true, 
                elevationDp = 3f
            )
        )
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

        // No need to update clipping - we use margins instead
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

        // No need to update clipping - we use margins instead
    }

    fun setEngineView(engine: EngineView) {
        engineView = engine
        
        // Always disable dynamic toolbar - we use margins for top toolbar instead
        engine.setDynamicToolbarMaxHeight(0)
    }

    fun setToolbarPosition(position: ToolbarPosition) {
        toolbarPosition = position
        
        // Setup window insets handling for edge-to-edge
        // This allows toolbars to add padding for system bars without affecting fullscreen
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Only apply padding when visible (not in fullscreen)
            if (view.isVisible && view.alpha > 0f) {
                when (toolbarPosition) {
                    ToolbarPosition.BOTTOM -> {
                        view.setPadding(0, 0, 0, systemBars.bottom)
                    }
                    ToolbarPosition.TOP -> {
                        view.setPadding(0, systemBars.top, 0, 0)
                    }
                }
            } else {
                // In fullscreen or hidden - no padding
                view.setPadding(0, 0, 0, 0)
            }
            
            insets
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
        animateToOffset(0)
    }

    override fun collapse() {
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
            duration = 200
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

        // Notify listener for smooth margin adjustment (for both TOP and BOTTOM modes)
        onOffsetChangedListener?.invoke(currentOffset, totalHeight)

        // No clipping needed - we use margins instead
        if (toolbarPosition == ToolbarPosition.TOP) {
            // Keep toolbar visible but translated for smooth animation
            visibility = VISIBLE
            alpha = 1f
        } else {
            // Bottom toolbar - normal fade behavior
            alpha = if (totalHeight > 0) {
                if (currentOffset >= totalHeight) 0f else 1f
            } else 1f
            
            visibility = VISIBLE
        }
    }

    fun getCurrentOffset(): Int = currentOffset
    
    /**
     * Set listener for offset changes (for smooth margin adjustment)
     */
    fun setOnOffsetChangedListener(listener: (Int, Int) -> Unit) {
        onOffsetChangedListener = listener
    }

    enum class ComponentType {
        TAB_GROUP,
        ADDRESS_BAR,
        CONTEXTUAL
    }
}
