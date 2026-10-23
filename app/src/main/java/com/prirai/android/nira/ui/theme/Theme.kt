package com.prirai.android.nira.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    surface = AmoledBlack,
    background = AmoledBlack,
    surfaceVariant = AmoledSurfaceVariant,
    surfaceContainer = AmoledSurfaceVariant,
    surfaceContainerLow = AmoledBlack,
    surfaceContainerHigh = AmoledSurfaceDim,
    surfaceContainerHighest = AmoledSurfaceDim
)

private val PrivateLightColorScheme = lightColorScheme(
    primary = PrivatePurple60,
    onPrimary = Color.White,
    primaryContainer = PrivatePurple80,
    onPrimaryContainer = Color.White,
    secondary = PrivatePurple40,
    surface = PrivateLightSurface,
    onSurface = PrivatePurple10,
    background = PrivateLightBackground,
    onBackground = PrivatePurple10
)

private val PrivateDarkColorScheme = darkColorScheme(
    primary = PrivatePurple80,
    onPrimary = Color.White,
    primaryContainer = PrivatePurple60,
    onPrimaryContainer = Color.White,
    secondary = PrivatePurple40,
    surface = PrivateDarkSurface,
    onSurface = PrivateOnDarkSurface,
    background = PrivateDarkBackground,
    onBackground = PrivateOnDarkSurface
)

private val PrivateAmoledColorScheme = darkColorScheme(
    primary = PrivatePurple80,
    onPrimary = Color.White,
    primaryContainer = PrivatePurple60,
    onPrimaryContainer = Color.White,
    secondary = PrivatePurple40,
    surface = AmoledBlack,
    onSurface = PrivateOnDarkSurface,
    background = AmoledBlack,
    onBackground = PrivateOnDarkSurface,
    surfaceVariant = AmoledSurfaceVariant,
    surfaceContainer = AmoledSurfaceVariant,
    surfaceContainerLow = AmoledBlack,
    surfaceContainerHigh = AmoledSurfaceDim,
    surfaceContainerHighest = AmoledSurfaceDim
)

/**
 * The single source of truth for the color scheme the app shows, shared by every
 * [NiraTheme] so all surfaces render in the exact palette the user selected.
 */
@Composable
private fun resolveNiraColorScheme(
    darkTheme: Boolean,
    isPrivateMode: Boolean,
    amoledMode: Boolean,
    dynamicColor: Boolean
) = when {
    // Private mode with AMOLED
    isPrivateMode && darkTheme && amoledMode -> PrivateAmoledColorScheme
    // Private mode without AMOLED
    isPrivateMode && darkTheme -> PrivateDarkColorScheme
    isPrivateMode && !darkTheme -> PrivateLightColorScheme
    // Dynamic colors (Android 12+)
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) {
            val baseDynamic = dynamicDarkColorScheme(context)
            if (amoledMode) {
                baseDynamic.withBlackSurfaces()
            } else {
                baseDynamic
            }
        } else {
            dynamicLightColorScheme(context)
        }
    }
    // Static colors with AMOLED
    darkTheme && amoledMode -> AmoledDarkColorScheme
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
}

/**
 * The dynamic scheme is already a complete, tuned Material 3 scheme, so it is used
 * as the system delivers it. Only the AMOLED variant changes it, taking the surface
 * ladder to the same near-black steps every static AMOLED palette uses, while
 * keeping the dynamic accents and containers.
 */
private fun ColorScheme.withBlackSurfaces() =
    copy(
        background = AmoledBlack,
        surface = AmoledBlack,
        surfaceContainerLowest = AmoledBlack,
        surfaceContainerLow = AmoledSurfaceVariant,
        surfaceContainer = AmoledSurfaceVariant,
        surfaceContainerHigh = AmoledSurfaceDim,
        surfaceContainerHighest = AmoledSurfaceDim,
    )

/**
 * Nira's Material 3 Expressive theme: [MaterialExpressiveTheme] with the expressive
 * motion scheme, so every component that reads [MotionScheme] specs moves the way
 * Material 3 Expressive defines. All UI surfaces must be wrapped in this theme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NiraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isPrivateMode: Boolean = false,
    amoledMode: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = resolveNiraColorScheme(darkTheme, isPrivateMode, amoledMode, dynamicColor)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            // Set status bar and navigation bar colors
            if (isPrivateMode) {
                // Purple for private mode
                window.statusBarColor = colorScheme.primary.toArgb()
                window.navigationBarColor = colorScheme.primary.toArgb()
            } else if (darkTheme && amoledMode) {
                // Pure black for AMOLED mode
                window.statusBarColor = AmoledBlack.toArgb()
                window.navigationBarColor = AmoledBlack.toArgb()
            } else if (darkTheme) {
                // Dark surface for normal dark mode
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
            }
            // Set light/dark status bar icons
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme && !isPrivateMode
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
