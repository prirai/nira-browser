package com.prirai.android.nira.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
    onSurface = Color(0xFFE0D5F0),
    background = PrivateDarkBackground,
    onBackground = Color(0xFFE0D5F0)
)

private val PrivateAmoledColorScheme = darkColorScheme(
    primary = PrivatePurple80,
    onPrimary = Color.White,
    primaryContainer = PrivatePurple60,
    onPrimaryContainer = Color.White,
    secondary = PrivatePurple40,
    surface = AmoledBlack,
    onSurface = Color(0xFFE0D5F0),
    background = AmoledBlack,
    onBackground = Color(0xFFE0D5F0),
    surfaceVariant = AmoledSurfaceVariant,
    surfaceContainer = AmoledSurfaceVariant,
    surfaceContainerLow = AmoledBlack,
    surfaceContainerHigh = AmoledSurfaceDim,
    surfaceContainerHighest = AmoledSurfaceDim
)

@Composable
fun NiraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isPrivateMode: Boolean = false,
    amoledMode: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Private mode with AMOLED
        isPrivateMode && darkTheme && amoledMode -> PrivateAmoledColorScheme
        // Private mode without AMOLED
        isPrivateMode && darkTheme -> PrivateDarkColorScheme
        isPrivateMode && !darkTheme -> PrivateLightColorScheme
        // Dynamic colors (Android 12+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                // Apply AMOLED modifications to dynamic colors
                val baseDynamic = dynamicDarkColorScheme(context)
                if (amoledMode) {
                    baseDynamic.copy(
                        surface = AmoledBlack,
                        background = AmoledBlack,
                        surfaceVariant = AmoledSurfaceVariant,
                        surfaceContainer = AmoledSurfaceVariant,
                        surfaceContainerLow = AmoledBlack,
                        surfaceContainerHigh = AmoledSurfaceDim,
                        surfaceContainerHighest = AmoledSurfaceDim
                    )
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
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
