package com.prirai.android.nira.browser.tabs

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.prirai.android.nira.R

class MergedProfileButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    enum class Position {
        FIRST, MIDDLE, LAST, ONLY
    }

    private var textView: TextView
    private var isActive: Boolean = false
    private var currentPosition: Position = Position.MIDDLE
    private var storedName: String = ""
    
    init {
        // Create text view
        textView = TextView(context).apply {
            setPadding(20, 0, 20, 0)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.m3_primary_text))
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            isSingleLine = true
        }
        addView(textView)
        
        // Set card properties - 3x original height
        val heightDp = 48 // 3x the original ~16dp
        val heightPx = (heightDp * resources.displayMetrics.density).toInt()
        minimumHeight = heightPx
        
        cardElevation = 0f
        radius = 0f
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.m3_surface_container))
    }

    fun setActive(active: Boolean, animated: Boolean = true) {
        if (isActive == active) return
        
        isActive = active
        
        if (animated) {
            animateToState()
        } else {
            applyState()
        }
    }

    fun setPosition(position: Position) {
        currentPosition = position
        updateCornerRadius()
    }

    fun setText(emoji: String, name: String) {
        storedName = name
        // Always show both emoji and name
        textView.text = if (name.isNotBlank()) "$emoji $name" else emoji
    }

    private fun animateToState() {
        // Bezier curve interpolator for smooth animation
        val interpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
        
        // Update text - always show both emoji and name
        val emojiOnly = textView.text.toString().split(" ").firstOrNull() ?: ""
        val newText = if (storedName.isNotBlank()) {
            "$emojiOnly $storedName"
        } else {
            emojiOnly
        }
        textView.text = newText
        
        // Animate ONLY width change (not height)
        val startWidth = width
        val endWidth = textView.paint.measureText(newText).toInt() + 40 // More padding
        
        ValueAnimator.ofInt(startWidth, endWidth).apply {
            duration = 300
            this.interpolator = interpolator
            addUpdateListener { animator ->
                val animatedValue = animator.animatedValue as Int
                val heightPx = (48 * resources.displayMetrics.density).toInt()
                layoutParams = layoutParams.apply {
                    width = animatedValue
                    height = heightPx // Keep height constant
                }
                requestLayout()
            }
            start()
        }
        
        // Animate background color
        val startColor = if (!isActive) {
            ContextCompat.getColor(context, R.color.m3_surface_container)
        } else {
            ContextCompat.getColor(context, R.color.m3_primary_container)
        }
        
        val endColor = if (isActive) {
            ContextCompat.getColor(context, R.color.m3_primary_container)
        } else {
            ContextCompat.getColor(context, R.color.m3_surface_container)
        }
        
        ValueAnimator.ofArgb(startColor, endColor).apply {
            duration = 300
            this.interpolator = interpolator
            addUpdateListener { animator ->
                setCardBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun applyState() {
        // Update text to always show name
        val emojiOnly = textView.text.toString().split(" ").firstOrNull() ?: ""
        textView.text = if (storedName.isNotBlank()) {
            "$emojiOnly $storedName"
        } else {
            emojiOnly
        }
        
        // Update background color
        val color = if (isActive) {
            ContextCompat.getColor(context, R.color.m3_primary_container)
        } else {
            ContextCompat.getColor(context, R.color.m3_surface_container)
        }
        setCardBackgroundColor(color)
    }

    private fun updateCornerRadius() {
        val radiusDp = 16f
        val radiusPx = radiusDp * resources.displayMetrics.density
        
        when (currentPosition) {
            Position.FIRST -> {
                // Round left corners only
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(radiusPx)
                    .setBottomLeftCornerSize(radiusPx)
                    .setTopRightCornerSize(0f)
                    .setBottomRightCornerSize(0f)
                    .build()
            }
            Position.LAST -> {
                // Round right corners only
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(0f)
                    .setBottomLeftCornerSize(0f)
                    .setTopRightCornerSize(radiusPx)
                    .setBottomRightCornerSize(radiusPx)
                    .build()
            }
            Position.ONLY -> {
                // Round all corners
                radius = radiusPx
            }
            Position.MIDDLE -> {
                // No rounding
                radius = 0f
            }
        }
    }
}
