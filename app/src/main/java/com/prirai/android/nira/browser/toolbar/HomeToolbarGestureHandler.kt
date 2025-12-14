package com.prirai.android.nira.browser.toolbar

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.PointF
import android.view.View
import android.view.ViewConfiguration
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.navigation.findNavController
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.FakeTab
import com.prirai.android.nira.browser.SwipeGestureListener
import com.prirai.android.nira.browser.tabs.TabLRUManager
import com.prirai.android.nira.ext.getRectWithScreenLocation
import com.prirai.android.nira.ext.getWindowInsets
import com.prirai.android.nira.ext.isKeyboardVisible
import com.prirai.android.nira.preferences.UserPreferences
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HomeToolbarGestureHandler(
    private val activity: Activity,
    private val contentLayout: View,
    private val tabPreview: FakeTab,
    private val toolbarLayout: View,
    private val store: BrowserStore,
    private val selectTabUseCase: TabsUseCases.SelectTabUseCase
) : SwipeGestureListener {

    private val lruManager by lazy { 
        TabLRUManager.getInstance(activity) 
    }

    private enum class GestureDirection {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT
    }

    private sealed class Destination {
        data class Tab(val tab: TabSessionState) : Destination()
        data object None : Destination()
    }

    private val windowWidth: Int
        get() = activity.resources.displayMetrics.widthPixels

    private val previewOffset =
        activity.resources.getDimensionPixelSize(R.dimen.browser_fragment_gesture_preview_offset)

    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(activity).scaledMinimumFlingVelocity

    private var gestureDirection = GestureDirection.LEFT_TO_RIGHT

    override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
        val dx = next.x - start.x
        val dy = next.y - start.y
        gestureDirection = if (dx < 0) {
            GestureDirection.RIGHT_TO_LEFT
        } else {
            GestureDirection.LEFT_TO_RIGHT
        }

        @Suppress("ComplexCondition")
        return if (
            !activity.window.decorView.isKeyboardVisible() &&
            start.isInToolbar() &&
            abs(dx) > touchSlop &&
            abs(dy) < abs(dx)
        ) {
            preparePreview(getDestination())
            true
        } else {
            false
        }
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
        when (getDestination()) {
            is Destination.Tab -> {
                tabPreview.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> min(
                        windowWidth.toFloat() + previewOffset,
                        tabPreview.translationX - distanceX
                    ).coerceAtLeast(0f)
                    GestureDirection.LEFT_TO_RIGHT -> max(
                        -windowWidth.toFloat() - previewOffset,
                        tabPreview.translationX - distanceX
                    ).coerceAtMost(0f)
                }
                contentLayout.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> min(
                        0f,
                        contentLayout.translationX - distanceX
                    ).coerceAtLeast(-windowWidth.toFloat() - previewOffset)
                    GestureDirection.LEFT_TO_RIGHT -> max(
                        0f,
                        contentLayout.translationX - distanceX
                    ).coerceAtMost(windowWidth.toFloat() + previewOffset)
                }
            }
            is Destination.None -> {
                val maxContentHidden = contentLayout.width * OVERSCROLL_HIDE_PERCENT
                contentLayout.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> max(
                        -maxContentHidden.toFloat(),
                        contentLayout.translationX - distanceX
                    ).coerceAtMost(0f)
                    GestureDirection.LEFT_TO_RIGHT -> min(
                        maxContentHidden.toFloat(),
                        contentLayout.translationX - distanceX
                    ).coerceAtLeast(0f)
                }
            }
        }
    }

    override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
        val destination = getDestination()
        if (destination is Destination.Tab && isGestureComplete(velocityX)) {
            animateToNextTab(destination.tab)
        } else {
            animateCanceledGesture(velocityX)
        }
    }

    private fun getDestination(): Destination {
        val isLtr = activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR
        val currentTab = store.state.selectedTab
        val isPrivateMode = currentTab?.content?.private ?: false
        
        val nextTabId = lruManager.getMostRecentTab()
        
        if (nextTabId == null) return Destination.None
        
        val tabs = store.state.getNormalOrPrivateTabs(isPrivateMode)
        val nextTab = tabs.find { it.id == nextTabId }
        
        return if (nextTab != null) {
            Destination.Tab(nextTab)
        } else {
            Destination.None
        }
    }

    private fun preparePreview(destination: Destination) {
        val thumbnailId = when (destination) {
            is Destination.Tab -> destination.tab.id
            is Destination.None -> return
        }

        tabPreview.loadPreviewThumbnail(thumbnailId)
        tabPreview.alpha = 1f
        tabPreview.translationX = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> windowWidth.toFloat() + previewOffset
            GestureDirection.LEFT_TO_RIGHT -> -windowWidth.toFloat() - previewOffset
        }
        tabPreview.isVisible = true
    }

    private fun isGestureComplete(velocityX: Float): Boolean {
        val velocityMatchesDirection = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> velocityX <= 0
            GestureDirection.LEFT_TO_RIGHT -> velocityX >= 0
        }
        val reverseFling = abs(velocityX) >= minimumFlingVelocity && !velocityMatchesDirection

        val visibleWidth = if (tabPreview.translationX < 0) {
            windowWidth + tabPreview.translationX
        } else {
            windowWidth - tabPreview.translationX
        }

        return !reverseFling && (visibleWidth / windowWidth >= GESTURE_FINISH_PERCENT ||
            abs(velocityX) >= minimumFlingVelocity)
    }

    private fun getAnimator(finalContextX: Float, duration: Long): ValueAnimator {
        return ValueAnimator.ofFloat(contentLayout.translationX, finalContextX).apply {
            this.duration = duration
            this.interpolator = LinearOutSlowInInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                contentLayout.translationX = value
                tabPreview.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> value + windowWidth + previewOffset
                    GestureDirection.LEFT_TO_RIGHT -> value - windowWidth - previewOffset
                }
            }
        }
    }

    private fun animateToNextTab(tab: TabSessionState) {
        val browserFinalXCoordinate: Float = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> -windowWidth.toFloat() - previewOffset
            GestureDirection.LEFT_TO_RIGHT -> windowWidth.toFloat() + previewOffset
        }

        getAnimator(browserFinalXCoordinate, FINISHED_GESTURE_ANIMATION_DURATION).apply {
            doOnEnd {
                contentLayout.translationX = 0f

                if(tab.content.url != "about:homepage") {
                    activity.findNavController(R.id.container).navigate(R.id.browserFragment)
                }

                lruManager.markAsSwipeNavigation(tab.id)
                selectTabUseCase(tab.id)

                val shortAnimationDuration =
                    activity.resources.getInteger(android.R.integer.config_shortAnimTime)
                tabPreview.animate()
                    .alpha(0f)
                    .setDuration(shortAnimationDuration.toLong())
                    .withEndAction {
                        tabPreview.isVisible = false
                    }
            }
        }.start()
    }

    private fun animateCanceledGesture(velocityX: Float) {
        val duration = if (abs(velocityX) >= minimumFlingVelocity) {
            CANCELED_FLING_ANIMATION_DURATION
        } else {
            CANCELED_GESTURE_ANIMATION_DURATION
        }

        getAnimator(0f, duration).apply {
            doOnEnd {
                tabPreview.isVisible = false
            }
        }.start()
    }

    private fun PointF.isInToolbar(): Boolean {
        val toolbarLocation = toolbarLayout.getRectWithScreenLocation()
        activity.window.decorView.getWindowInsets()?.let { insets ->
            if (UserPreferences(activity).toolbarPosition == ToolbarPosition.BOTTOM.ordinal) {
                toolbarLocation.top -= (insets.mandatorySystemGestureInsets.bottom - insets.stableInsetBottom)
            }
        }
        return toolbarLocation.contains(toPoint())
    }

    companion object {
        private const val GESTURE_FINISH_PERCENT = 0.25
        private const val OVERSCROLL_HIDE_PERCENT = 0.20
        private const val FINISHED_GESTURE_ANIMATION_DURATION = 250L
        private const val CANCELED_GESTURE_ANIMATION_DURATION = 200L
        private const val CANCELED_FLING_ANIMATION_DURATION = 150L
    }
}
