package com.prirai.android.nira.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.ThemeChoice

/**
 * Manages application theme configuration including Material 3 dynamic colors,
 * AMOLED mode, and theme switching.
 */
object ThemeManager {
    
    /**
     * Apply the user's theme preference to the app.
     */
    fun applyTheme(context: Context) {
        val prefs = UserPreferences(context)
        when (prefs.appThemeChoice) {
            ThemeChoice.LIGHT.ordinal -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            ThemeChoice.DARK.ordinal -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            ThemeChoice.SYSTEM.ordinal -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }
    
    /**
     * Check if the device is currently in dark mode.
     */
    fun isDarkMode(context: Context): Boolean {
        val prefs = UserPreferences(context)
        return when (prefs.appThemeChoice) {
            ThemeChoice.LIGHT.ordinal -> false
            ThemeChoice.DARK.ordinal -> true
            ThemeChoice.SYSTEM.ordinal -> {
                val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
            else -> {
                val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
    
    /**
     * Check if AMOLED mode is enabled and device is in dark mode.
     */
    fun isAmoledActive(context: Context): Boolean {
        val prefs = UserPreferences(context)
        return prefs.amoledMode && isDarkMode(context)
    }
    
    /**
     * Check if dynamic colors (Material You) should be used.
     * Only available on Android 12+ (API 31+).
     */
    fun shouldUseDynamicColors(context: Context): Boolean {
        val prefs = UserPreferences(context)
        return prefs.dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    
    /**
     * Get the appropriate background color based on theme settings.
     * This is useful for programmatically setting backgrounds.
     */
    fun getBackgroundColor(context: Context): Int {
        return if (isAmoledActive(context)) {
            android.graphics.Color.BLACK
        } else {
            // Get from theme attribute
            val typedValue = android.util.TypedValue()
            val theme = context.theme
            theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurface,
                typedValue,
                true
            )
            typedValue.data
        }
    }
    
    /**
     * Get the appropriate surface color based on theme settings.
     */
    fun getSurfaceColor(context: Context): Int {
        return if (isAmoledActive(context)) {
            android.graphics.Color.BLACK
        } else {
            val typedValue = android.util.TypedValue()
            val theme = context.theme
            theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurface,
                typedValue,
                true
            )
            typedValue.data
        }
    }
    
    /**
     * Get surface variant color (slightly elevated surface).
     */
    fun getSurfaceVariantColor(context: Context): Int {
        return if (isAmoledActive(context)) {
            0xFF0A0A0A.toInt() // Near black for AMOLED
        } else {
            val typedValue = android.util.TypedValue()
            val theme = context.theme
            theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceVariant,
                typedValue,
                true
            )
            typedValue.data
        }
    }
    
    /**
     * Apply Material 3 colors to status bar and navigation bar.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    fun applySystemBarsTheme(activity: android.app.Activity, isPrivateMode: Boolean = false) {
        val context = activity as Context
        val window = activity.window
        
        if (isPrivateMode) {
            // Purple theme for private mode
            val purpleColor = ColorConstants.PrivateMode.PURPLE
            window.statusBarColor = purpleColor
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } else if (isAmoledActive(context)) {
            // Pure black for AMOLED
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } else {
            // Surface color for normal mode
            window.statusBarColor = getSurfaceColor(context)
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        
        // Set light/dark status bar icons
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isDark = isDarkMode(context)
            window.insetsController?.setSystemBarsAppearance(
                if (isDark || isPrivateMode) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            val isDark = isDarkMode(context)
            if (isDark || isPrivateMode) {
                window.decorView.systemUiVisibility = 
                    window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                window.decorView.systemUiVisibility = 
                    window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }
}
