package com.prirai.android.nira.ui.components

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Adds a subtle press-scale animation to any composable.
 * When the user presses down, the element shrinks to [pressedScale] on the theme's fast spatial
 * spring, giving tactile depth feedback without any layout shift.
 *
 * Usage: `Modifier.pressScale(interactionSource)`
 * The interactionSource should be the same one passed to clickable/combinedClickable.
 *
 * Composable rather than `composed {}`: the latter is opaque to Modifier equality, so every
 * item in a list re-materialised its whole chain on each recomposition. The spring is read
 * inside the [graphicsLayer] block instead of the composition, which keeps its per-frame
 * updates off the composition and layout phases entirely — only the layer block re-runs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale =
        animateFloatAsState(
            targetValue = if (isPressed) pressedScale else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "pressScale",
        )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * The item animation every Nira lazy list shares: content fades with Material easings and
 * placement follows a soft spring, so reorder, insert and remove all move the same way.
 *
 * Must keep a stable `key` on the item and must not be toggled after first composition
 * (that crashes `LazyLayoutItemAnimator`).
 */
fun LazyItemScope.animateNiraListItem(): Modifier =
    Modifier.animateItem(
        fadeInSpec = tween(ITEM_FADE_IN_MILLIS, easing = EaseOutCubic),
        fadeOutSpec = tween(ITEM_FADE_OUT_MILLIS, easing = EaseInCubic),
        placementSpec = spring(dampingRatio = ITEM_PLACEMENT_DAMPING, stiffness = Spring.StiffnessLow),
    )

private const val ITEM_FADE_IN_MILLIS = 300
private const val ITEM_FADE_OUT_MILLIS = 200
private const val ITEM_PLACEMENT_DAMPING = 0.8f

/** [animateNiraListItem] for an item of a lazy grid. */
fun LazyGridItemScope.animateNiraGridItem(): Modifier =
    Modifier.animateItem(
        fadeInSpec = tween(ITEM_FADE_IN_MILLIS, easing = EaseOutCubic),
        fadeOutSpec = tween(ITEM_FADE_OUT_MILLIS, easing = EaseInCubic),
        placementSpec = spring(dampingRatio = ITEM_PLACEMENT_DAMPING, stiffness = Spring.StiffnessLow),
    )

/**
 * Scrim over the bottom of a tab thumbnail so the title overlay stays legible on light frames.
 * A legibility device, not decoration: the only gradient Nira carries, matching the scrim
 * behind Flow's duration pills.
 *
 * Built in [drawWithCache] because the brush depends only on the layout size: created per draw it
 * would allocate a Brush and its colour list for every visible card on every frame of a scroll.
 */
fun Modifier.thumbnailGradientOverlay(
    color: Color = Color.Black,
    alpha: Float = 0.75f,
    startFraction: Float = 0.45f,
): Modifier =
    this.drawWithCache {
        val brush =
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        color.copy(alpha = alpha),
                    ),
                startY = size.height * startFraction,
                endY = size.height,
            )
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush)
        }
    }

/**
 * Returns a [SheetState] configured for a smooth, polished bottom sheet experience:
 * [skipPartiallyExpanded] defaults to true, so sheets always open fully without
 * stopping at the awkward half-expanded state.
 *
 * Usage: `sheetState = rememberNiraSheetState()`
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberNiraSheetState(skipPartiallyExpanded: Boolean = true): SheetState =
    rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues =
            if (skipPartiallyExpanded) {
                setOf(SheetValue.Hidden, SheetValue.Expanded)
            } else {
                setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
            },
    )
