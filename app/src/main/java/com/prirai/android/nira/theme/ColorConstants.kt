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
    
    // Tab group colors - Material 3 based with expanded palette
    object TabGroups {
        // Primary Material 3 colors
        const val BLUE = 0xFF2196F3.toInt()
        const val RED = 0xFFF44336.toInt()
        const val GREEN = 0xFF4CAF50.toInt()
        const val ORANGE = 0xFFFF9800.toInt()
        const val PURPLE = 0xFF9C27B0.toInt()
        const val PINK = 0xFFE91E63.toInt()
        const val TEAL = 0xFF009688.toInt()
        const val YELLOW = 0xFFFFC107.toInt()
        
        // Extended Material 3 colors
        const val INDIGO = 0xFF3F51B5.toInt()
        const val CYAN = 0xFF00BCD4.toInt()
        const val LIME = 0xFFCDDC39.toInt()
        const val AMBER = 0xFFFFC107.toInt()
        const val DEEP_ORANGE = 0xFFFF5722.toInt()
        const val LIGHT_GREEN = 0xFF8BC34A.toInt()
        const val DEEP_PURPLE = 0xFF673AB7.toInt()
        const val BROWN = 0xFF795548.toInt()
        
        fun parseColor(colorString: String): Int {
            return when (colorString.lowercase()) {
                "blue" -> BLUE
                "red" -> RED
                "green" -> GREEN
                "orange" -> ORANGE
                "purple" -> PURPLE
                "pink" -> PINK
                "teal" -> TEAL
                "yellow" -> YELLOW
                "indigo" -> INDIGO
                "cyan" -> CYAN
                "lime" -> LIME
                "amber" -> AMBER
                "deep_orange" -> DEEP_ORANGE
                "light_green" -> LIGHT_GREEN
                "deep_purple" -> DEEP_PURPLE
                "brown" -> BROWN
                else -> BLUE // Default
            }
        }
        
        fun getColorName(@ColorInt color: Int): String {
            return when (color) {
                BLUE -> "blue"
                RED -> "red"
                GREEN -> "green"
                ORANGE -> "orange"
                PURPLE -> "purple"
                PINK -> "pink"
                TEAL -> "teal"
                YELLOW -> "yellow"
                INDIGO -> "indigo"
                CYAN -> "cyan"
                LIME -> "lime"
                AMBER -> "amber"
                DEEP_ORANGE -> "deep_orange"
                LIGHT_GREEN -> "light_green"
                DEEP_PURPLE -> "deep_purple"
                BROWN -> "brown"
                else -> "blue"
            }
        }
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
