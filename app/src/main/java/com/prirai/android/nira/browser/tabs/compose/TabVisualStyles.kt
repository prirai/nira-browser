package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 visual constants for tabs
 */
object TabVisualConstants {
    val TAB_CORNER_RADIUS = 12.dp
    val TAB_ELEVATION_NORMAL = 1.dp
    val TAB_ELEVATION_DRAGGING = 8.dp
    val TAB_SPACING_NORMAL = 8.dp
    val TAB_HEIGHT = 72.dp
    val TAB_HEIGHT_GRID = 120.dp
    val INSERTION_LINE_WIDTH = 3.dp
    val INSERTION_LINE_LENGTH = 40.dp
}

/**
 * Get shape for tab based on position in group
 */
@Composable

fun getTabShape(): Shape {
    return RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS)
}



/**
 * Animate scale for drag feedback
 */
@Composable
fun Modifier.dragFeedbackScale(
    isTarget: Boolean,
    isDragging: Boolean
): Modifier {
    val scale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.05f
            isTarget -> 0.95f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dragScale"
    )
    return this.scale(scale)
}

/**
 * Get elevation for tab based on drag state
 */
@Composable
fun getTabElevation(isDragging: Boolean): Dp {
    return if (isDragging) {
        TabVisualConstants.TAB_ELEVATION_DRAGGING
    } else {
        TabVisualConstants.TAB_ELEVATION_NORMAL
    }
}



/**
 * Colors for drag targets with Material 3 theming
 */
@Composable
fun getDragTargetColor(feedbackType: DragFeedback): Color {
    return when (feedbackType) {
        DragFeedback.Reorder -> MaterialTheme.colorScheme.primary
        DragFeedback.None -> Color.Transparent
    }
}


