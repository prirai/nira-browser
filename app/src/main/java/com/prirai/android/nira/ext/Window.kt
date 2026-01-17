package com.prirai.android.nira.ext

import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Enables edge-to-edge display for the activity following Mozilla's reference browser pattern.
 * This ensures consistent behavior across all Android versions.
 * 
 * - Uses fully transparent system bars (or custom color if provided)
 * - Content extends behind system bars
 * - System bar icon colors adapt based on theme or color
 * 
 * @param statusBarColor Optional custom color for status bar (used by custom tabs)
 */
fun ComponentActivity.enableEdgeToEdgeMode(statusBarColor: Int? = null) {
    val isDark = if (statusBarColor != null) {
        // For custom tabs with specific color, check if that color is dark
        isColorDark(statusBarColor)
    } else {
        // For normal browser, use app theme
        isAppInDarkTheme()
    }
    
    val barColor = statusBarColor ?: Color.TRANSPARENT
    
    // Use Android's modern edge-to-edge API
    enableEdgeToEdge(
        statusBarStyle = if (isDark) {
            SystemBarStyle.dark(barColor)
        } else {
            SystemBarStyle.light(barColor, barColor)
        },
        navigationBarStyle = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
    )
    
    // For custom tabs with specific color, also set window.statusBarColor
    // This ensures the color is actually applied (SystemBarStyle alone may not be enough)
    if (statusBarColor != null) {
        window.statusBarColor = statusBarColor
    }
}

/**
 * Determines if a color is dark based on its luminance.
 * Used to decide whether to use light or dark status bar icons.
 */
private fun isColorDark(color: Int): Boolean {
    val red = Color.red(color) / 255.0
    val green = Color.green(color) / 255.0
    val blue = Color.blue(color) / 255.0
    
    // Calculate relative luminance
    val r = if (red <= 0.03928) red / 12.92 else Math.pow((red + 0.055) / 1.055, 2.4)
    val g = if (green <= 0.03928) green / 12.92 else Math.pow((green + 0.055) / 1.055, 2.4)
    val b = if (blue <= 0.03928) blue / 12.92 else Math.pow((blue + 0.055) / 1.055, 2.4)
    
    val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return luminance <= 0.5
}

/**
 * Applies persistent window insets to a view following Mozilla's pattern.
 * The view will maintain its padding adjustments for system bars across configuration changes.
 *
 * @param applyTop Whether to apply top insets (status bar). Default is true.
 * @param applyBottom Whether to apply bottom insets (navigation bar). Default is true.
 * @param applyLeft Whether to apply left insets (display cutouts). Default is true.
 * @param applyRight Whether to apply right insets (display cutouts). Default is true.
 */
fun View.applyPersistentInsets(
    applyTop: Boolean = true,
    applyBottom: Boolean = true,
    applyLeft: Boolean = true,
    applyRight: Boolean = true
) {
    val initialPadding = recordInitialPaddingForView(this)
    
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val systemBars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        
        v.updatePadding(
            left = if (applyLeft) initialPadding.left + systemBars.left else initialPadding.left,
            top = if (applyTop) initialPadding.top + systemBars.top else initialPadding.top,
            right = if (applyRight) initialPadding.right + systemBars.right else initialPadding.right,
            bottom = if (applyBottom) initialPadding.bottom + systemBars.bottom else initialPadding.bottom
        )
        
        WindowInsetsCompat.CONSUMED
    }
}

/**
 * Records initial padding of a view for use with persistent insets.
 */
private fun recordInitialPaddingForView(view: View): InitialPadding {
    return InitialPadding(
        view.paddingLeft,
        view.paddingTop,
        view.paddingRight,
        view.paddingBottom
    )
}

private data class InitialPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
