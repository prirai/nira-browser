package com.prirai.android.nira.components.toolbar.unified

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.android.material.elevation.ElevationOverlayProvider
import com.prirai.android.nira.R

/**
 * UnifiedToolbarTheme - Centralized theming for all toolbar components.
 *
 * Provides consistent styling across:
 * - Tab Group Bar
 * - Address Bar
 * - Contextual Toolbar
 *
 * This ensures all toolbar components share the same:
 * - Background colors (normal and private mode)
 * - Text colors
 * - Icon colors
 * - Elevation and shadows
 * - Material 3 design tokens
 */
class UnifiedToolbarTheme(private val context: Context) {

    // Elevation overlay provider for Material 3 tonal elevation
    private val elevationOverlayProvider = ElevationOverlayProvider(context)

    /**
     * Standard toolbar elevation (3dp as per Material 3 guidelines)
     */
    val toolbarElevation: Float = 3f * context.resources.displayMetrics.density

    /**
     * Toolbar background color with Material 3 tonal elevation
     * Uses colorSurface with 3dp elevation overlay
     */
    val toolbarBackgroundColor: Int
        get() {
            val elevationDp = 3f * context.resources.displayMetrics.density
            return elevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(elevationDp)
        }

    /**
     * Private mode toolbar background color
     * Deep purple to indicate private browsing
     */
    val privateToolbarBackgroundColor: Int
        get() = com.prirai.android.nira.theme.ColorConstants.PrivateMode.PURPLE

    /**
     * Primary text color for toolbar
     * Used for URL text, tab titles, etc.
     */
    val toolbarTextColor: Int
        get() = ContextCompat.getColor(context, R.color.primary_text_color)

    /**
     * Hint text color for toolbar
     * Used for placeholder text like "Search or enter address"
     */
    val toolbarHintColor: Int
        get() = ContextCompat.getColor(context, R.color.secondary_text_color)

    /**
     * Icon color for toolbar buttons and indicators
     * Used for all toolbar icons (navigation, menu, security indicators)
     */
    val toolbarIconColor: Int
        get() = ContextCompat.getColor(context, R.color.primary_icon)

    /**
     * Secondary icon color for disabled or less prominent icons
     */
    val toolbarIconSecondaryColor: Int
        get() = ContextCompat.getColor(context, R.color.secondary_icon)

    /**
     * Separator color for toolbar dividers
     */
    val toolbarSeparatorColor: Int
        get() = ContextCompat.getColor(context, R.color.separator_color)

    /**
     * Tab count badge background color
     */
    val tabCountBadgeColor: Int
        get() = ContextCompat.getColor(context, R.color.tab_count_badge_background)

    /**
     * Tab count badge text color
     */
    val tabCountBadgeTextColor: Int
        get() = ContextCompat.getColor(context, R.color.tab_count_badge_text)

    /**
     * Contextual toolbar button size (scaled based on user preference)
     */
    fun getContextualButtonSize(iconSizeMultiplier: Float = 1.0f): Int {
        val baseSize = 48 // Base size in dp
        return (baseSize * context.resources.displayMetrics.density * iconSizeMultiplier).toInt()
    }

    /**
     * Address bar height
     */
    val addressBarHeight: Int
        get() = context.resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)

    /**
     * Contextual toolbar height
     */
    val contextualToolbarHeight: Int
        get() = context.resources.getDimensionPixelSize(R.dimen.contextual_toolbar_height)

    /**
     * Tab group bar height
     */
    val tabGroupBarHeight: Int
        get() = context.resources.getDimensionPixelSize(R.dimen.tab_group_bar_height)

    /**
     * Toolbar corner radius for Material 3 design
     */
    val toolbarCornerRadius: Float
        get() = context.resources.getDimension(R.dimen.toolbar_corner_radius)

    /**
     * Apply Material 3 ripple effect to buttons
     */
    fun getRippleDrawable(): Int {
        return android.R.attr.selectableItemBackgroundBorderless
    }

    /**
     * Get toolbar padding (horizontal)
     */
    val toolbarHorizontalPadding: Int
        get() = (12 * context.resources.displayMetrics.density).toInt()

    /**
     * Get toolbar padding (vertical)
     */
    val toolbarVerticalPadding: Int
        get() = (8 * context.resources.displayMetrics.density).toInt()

    /**
     * Button padding for contextual toolbar
     */
    val buttonPadding: Int
        get() = (12 * context.resources.displayMetrics.density).toInt()

    /**
     * Button margin for contextual toolbar
     */
    val buttonMargin: Int
        get() = (2 * context.resources.displayMetrics.density).toInt()

    /**
     * Progress bar color
     */
    val progressBarColor: Int
        get() = ContextCompat.getColor(context, R.color.progress_bar_color)

    /**
     * Security indicator colors
     */
    val secureColor: Int
        get() = ContextCompat.getColor(context, R.color.secure_icon_color)

    val insecureColor: Int
        get() = ContextCompat.getColor(context, R.color.insecure_icon_color)

    /**
     * Get tint color for icons based on theme
     */
    fun getIconTint(isPrivateMode: Boolean = false): Int {
        return if (isPrivateMode) {
            // On-primary color for private mode
            ContextCompat.getColor(context, R.color.m3_on_primary)
        } else {
            toolbarIconColor
        }
    }

    /**
     * Get text color based on theme
     */
    fun getTextColor(isPrivateMode: Boolean = false): Int {
        return if (isPrivateMode) {
            // On-primary color for private mode
            ContextCompat.getColor(context, R.color.m3_on_primary)
        } else {
            toolbarTextColor
        }
    }

    companion object {
        /**
         * Standard toolbar elevation in dp (Material 3 guideline)
         */
        const val TOOLBAR_ELEVATION_DP = 3f

        /**
         * Animation duration for toolbar transitions
         */
        const val ANIMATION_DURATION_MS = 200L

        /**
         * Animation duration for button press feedback
         */
        const val BUTTON_ANIMATION_DURATION_MS = 100L

        /**
         * Alpha value for disabled buttons
         */
        const val DISABLED_ALPHA = 0.4f

        /**
         * Alpha value for enabled buttons
         */
        const val ENABLED_ALPHA = 1.0f
    }
}
