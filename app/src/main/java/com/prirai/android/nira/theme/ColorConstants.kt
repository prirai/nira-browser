package com.prirai.android.nira.theme

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import com.prirai.android.nira.R

/**
 * Centralized color constants for the entire app.
 * All hardcoded colors should be defined here and use theme attributes where possible.
 */
object ColorConstants {
    
    // Security status colors
    @ColorInt
    fun getSecureColor(context: Context): Int {
        return context.getColor(R.color.secure_icon_color)
    }
    
    @ColorInt
    fun getInsecureColor(context: Context): Int {
        return context.getColor(R.color.insecure_icon_color)
    }
    

    
    // Profile colors - Material 3 based
    object Profiles {
        val COLORS = listOf(
            0xFF6200EE.toInt(), // Purple
            0xFF03DAC5.toInt(), // Teal
            0xFFFF6F00.toInt(), // Orange
            0xFFC51162.toInt(), // Pink
            0xFF00C853.toInt(), // Green
            0xFF2979FF.toInt(), // Blue
            0xFFD50000.toInt(), // Red
            0xFFFFD600.toInt(), // Yellow
        )
        
        val DEFAULT_COLOR = COLORS[0]
    }
    
    // Private mode colors
    object PrivateMode {
        const val PURPLE = 0xFF6A1B9A.toInt()
        
        @ColorInt
        fun getPurpleColor(context: Context): Int {
            // Try to get from theme first
            val typedValue = android.util.TypedValue()
            return if (context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)) {
                typedValue.data
            } else {
                PURPLE
            }
        }
    }
    
    // Drag and drop colors
    object DragDrop {
        const val UNGROUP_COLOR = 0xFFFF5722.toInt() // Orange-red
        const val VALID_DROP_COLOR = 0xFF4CAF50.toInt() // Green
    }
    
    /**
     * Get color from theme attribute
     */
    @ColorInt
    fun getColorFromAttr(context: Context, attr: Int, fallback: Int = Color.BLACK): Int {
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            fallback
        }
    }
}
