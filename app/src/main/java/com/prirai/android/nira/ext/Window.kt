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
 * - Uses fully transparent system bars
 * - Content extends behind system bars
 * - System bar icon colors adapt based on theme
 */
fun ComponentActivity.enableEdgeToEdgeMode() {
    val isDark = isAppInDarkTheme()
    
    // Use Android's modern edge-to-edge API with transparent bars
    enableEdgeToEdge(
        statusBarStyle = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        },
        navigationBarStyle = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
    )
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
