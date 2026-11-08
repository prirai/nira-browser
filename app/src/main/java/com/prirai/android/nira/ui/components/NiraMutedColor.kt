package com.prirai.android.nira.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Muted, theme-adaptive variants of the app's fixed identity palettes
 * (profile colors, settings category tints).
 *
 * The raw palette entries are bright saturated accent colors that read as
 * harsh in both themes. This helper softens them the way M3 tonal palettes
 * do: saturation is capped, and lightness is steered toward the tone that
 * keeps ~3:1 contrast on the current surface - deepened on light surfaces,
 * lifted on dark ones - so one stored color adapts to light, dark and
 * AMOLED themes without duplicates.
 */
private const val MAX_SATURATION = 0.52f
private const val LIGHT_MODE_LIGHTNESS = 0.42f
private const val DARK_MODE_LIGHTNESS = 0.74f

/** [mutedAdaptive] resolved against the current theme's surface. */
@Composable
fun Color.mutedAdaptive(): Color =
    mutedAdaptive(darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f)

/**
 * The muted variant of [this] color for the given theme: desaturated, with
 * lightness steered to the theme's muted tone. Hue is untouched, so each
 * palette entry keeps its identity.
 */
fun Color.mutedAdaptive(darkSurface: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtMost(MAX_SATURATION)
    hsv[2] = if (darkSurface) {
        (hsv[2].coerceAtLeast(DARK_MODE_LIGHTNESS) + DARK_MODE_LIGHTNESS) / 2f
    } else {
        (hsv[2].coerceAtMost(LIGHT_MODE_LIGHTNESS) + LIGHT_MODE_LIGHTNESS) / 2f
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}
