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

@Composable
fun NiraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isPrivateMode: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isPrivateMode && darkTheme -> PrivateDarkColorScheme
        isPrivateMode && !darkTheme -> PrivateLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme && !isPrivateMode
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
