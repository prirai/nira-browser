package com.prirai.android.nira.browser.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.prirai.android.nira.R
import com.prirai.android.nira.theme.ColorConstants
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

private const val MIN_CONTRAST = 3.0

/** Pushes [color] lighter (dark surface) or darker (light surface) until it reaches [MIN_CONTRAST]. */
internal fun ensureContrast(color: Int, surface: Int): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    // Neutral white / black are an explicit choice and stay as picked.
    if (hsv[1] < 0.04f) return color
    val lighten = ColorUtils.calculateLuminance(surface) < 0.5
    var out = color
    var steps = 0
    while (ColorUtils.calculateContrast(out, surface) < MIN_CONTRAST && steps < 50) {
        hsv[2] = (hsv[2] + if (lighten) 0.02f else -0.02f).coerceIn(0f, 1f)
        out = android.graphics.Color.HSVToColor(hsv)
        steps++
    }
    return out
}

/**
 * Icon colour for a profile avatar: the profile's primary colour when it reads against
 * [background], a nudged variant otherwise, and plain black or white as a last resort.
 */
fun profileIconTint(primary: Color, background: Color): Color {
    val bg = background.toArgb()
    if (ColorUtils.calculateContrast(primary.toArgb(), bg) >= MIN_CONTRAST) return primary
    val nudged = Color(ensureContrast(primary.toArgb(), bg))
    if (ColorUtils.calculateContrast(nudged.toArgb(), bg) >= MIN_CONTRAST) return nudged
    return if (ColorUtils.calculateLuminance(bg) > 0.5) Color.Black else Color.White
}

/**
 * Profile name colour, derived from the secondary (background) colour: same hue, pushed dark or
 * light until it reaches 4.5:1 contrast. Neutral backgrounds get plain black or white.
 */
fun profileLabelColor(background: Color): Color {
    val bg = background.toArgb()
    val dark = ColorUtils.calculateLuminance(bg) > 0.5
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(bg, hsv)
    if (hsv[1] < 0.04f) return if (dark) Color.Black else Color.White
    hsv[1] = (hsv[1] * 0.8f).coerceAtMost(0.9f)
    var out = bg
    var steps = 0
    while (ColorUtils.calculateContrast(out, bg) < 4.5 && steps < 60) {
        hsv[2] = (hsv[2] + if (dark) -0.02f else 0.02f).coerceIn(0f, 1f)
        out = android.graphics.Color.HSVToColor(hsv)
        steps++
    }
    if (ColorUtils.calculateContrast(out, bg) < 4.5) return if (dark) Color.Black else Color.White
    return Color(out)
}

/** A profile colour pair: [icon] is the primary (foreground) and [background] the secondary. */
private data class ColorPair(val icon: Long, val background: Long)

// Every pair keeps at least 3:1 contrast between icon and background.
private val COLOR_PAIRS = listOf(
    ColorPair(0xFF202124, 0xFFFFFFFF),
    ColorPair(0xFFFFFFFF, 0xFF202124),
    ColorPair(0xFF0B3D91, 0xFFD2E3FC),
    ColorPair(0xFFD2E3FC, 0xFF1A56C4),
    ColorPair(0xFF0D5C3A, 0xFFC8F0DA),
    ColorPair(0xFFC8F0DA, 0xFF1E7B4F),
    ColorPair(0xFF7A1F2B, 0xFFFFD9DE),
    ColorPair(0xFFFFD9DE, 0xFFB3263E),
    ColorPair(0xFF5A2C00, 0xFFFFE0B2),
    ColorPair(0xFFFFE0B2, 0xFF9A4A00),
    ColorPair(0xFF4A1E7A, 0xFFE9D7FF),
    ColorPair(0xFFE9D7FF, 0xFF6B3FB0),
    ColorPair(0xFF004F58, 0xFFC4F1F5),
    ColorPair(0xFFC4F1F5, 0xFF00707C),
    ColorPair(0xFF6B1E50, 0xFFFFD6EE),
    ColorPair(0xFFFFD6EE, 0xFF9C2A74),
    ColorPair(0xFF3B3B00, 0xFFF3F0A6),
    ColorPair(0xFF1A1A1A, 0xFFFFD54F),
    ColorPair(0xFF1B2A41, 0xFF9FB8D6),
    ColorPair(0xFFFFF4CC, 0xFF2E3A59),
)

/**
 * Preset dual-colour picker. Each option is a circle split by a slanted line: primary (icon) on
 * one half and secondary (background) on the other. All pairs are pre-checked for contrast.
 */
@Composable
fun ProfileColorPicker(
    primary: Int,
    secondary: Int,
    onPrimaryChange: (Int) -> Unit,
    onSecondaryChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        COLOR_PAIRS.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                row.forEach { pair ->
                    val icon = pair.icon.toInt()
                    val bg = pair.background.toInt()
                    PairSwatch(
                        icon = Color(icon),
                        background = Color(bg),
                        selected = primary == icon && secondary == bg,
                        onClick = {
                            onPrimaryChange(icon)
                            onSecondaryChange(bg)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PairSwatch(icon: Color, background: Color, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        val w = size.width
        val h = size.height
        drawCircle(background)
        val slant = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.68f, 0f)
            lineTo(w * 0.32f, h)
            lineTo(0f, h)
            close()
        }
        clipPath(androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)) }) {
            drawPath(slant, icon)
        }
        drawCircle(outline, style = Stroke(1.dp.toPx()))
        if (selected) {
            drawCircle(ring, radius = w / 2f - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
        }
    }
}
