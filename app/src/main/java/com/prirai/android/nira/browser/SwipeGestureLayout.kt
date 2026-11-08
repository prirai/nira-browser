package com.prirai.android.nira.browser

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.GestureDetectorCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout

interface SwipeGestureListener {
    fun onSwipeStarted(start: PointF, next: PointF): Boolean
    fun onSwipeUpdate(distanceX: Float, distanceY: Float)
    fun onSwipeFinished(velocityX: Float, velocityY: Float)
}

/**
 * The touch-interception behavior every swipe host shares: a scroll that a
 * registered listener claims (via [SwipeGestureListener.onSwipeStarted])
 * is intercepted away from the children - which is what lets a swipe start
 * on the toolbar's own clickable children (the URL display opens edit mode
 * on tap) and still reach the tab-switch gesture.
 */
internal class SwipeGestureHost(context: Context) {

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val start = e1.let { event -> PointF(event!!.rawX, event.rawY) }
            val next = e2.let { event -> PointF(event.rawX, event.rawY) }

            if (activeListener == null && !handledInitialScroll) {
                activeListener = listeners.firstOrNull { listener ->
                    listener.onSwipeStarted(start, next)
                }
                handledInitialScroll = true
            }
            activeListener?.onSwipeUpdate(distanceX, distanceY)
            return activeListener != null
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            activeListener?.onSwipeFinished(velocityX, velocityY)
            return if (activeListener != null) {
                activeListener = null
                true
            } else {
                false
            }
        }
    }

    private val gestureDetector = GestureDetectorCompat(context, gestureListener)

    private val listeners = mutableListOf<SwipeGestureListener>()
    private var activeListener: SwipeGestureListener? = null
    private var handledInitialScroll = false

    fun addGestureListener(listener: SwipeGestureListener) {
        listeners.add(listener)
    }

    fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handledInitialScroll = false
                activeListener = null
                gestureDetector.onTouchEvent(event)
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val shouldIntercept = gestureDetector.onTouchEvent(event)
                // Return true to intercept if we have an active listener
                shouldIntercept && activeListener != null
            }
            else -> gestureDetector.onTouchEvent(event)
        }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                gestureDetector.onTouchEvent(event)
                activeListener?.onSwipeFinished(
                    velocityX = 0f,
                    velocityY = 0f
                )
                activeListener = null
                false
            }
            else -> gestureDetector.onTouchEvent(event)
        }
    }
}

class SwipeGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var isSwipeEnabled = true

    private val host = SwipeGestureHost(context)

    fun addGestureListener(listener: SwipeGestureListener) {
        host.addGestureListener(listener)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isSwipeEnabled) {
            return false
        }
        return host.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return host.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/**
 * A [CoordinatorLayout] with [SwipeGestureLayout]'s interception behavior.
 * The home fragment builds its view tree programmatically with a
 * CoordinatorLayout root; hosting the interception at that root lets the
 * tab-switch swipe start on the toolbar's address bar, whose tap opens
 * edit mode and would otherwise consume the gesture.
 */
class SwipeGestureCoordinatorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    private val host = SwipeGestureHost(context)

    fun addGestureListener(listener: SwipeGestureListener) {
        host.addGestureListener(listener)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return host.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return host.onTouchEvent(event)
    }
}
