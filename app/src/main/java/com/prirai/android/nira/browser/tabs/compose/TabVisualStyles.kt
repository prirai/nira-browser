package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 visual constants for tabs
 */
object TabVisualConstants {
    val TAB_CORNER_RADIUS = 12.dp
    val GROUP_CORNER_RADIUS = 16.dp
    val TAB_ELEVATION_NORMAL = 1.dp
    val TAB_ELEVATION_DRAGGING = 8.dp
    val TAB_SPACING_NORMAL = 8.dp
    val TAB_SPACING_IN_GROUP = 2.dp
    val GROUP_HEADER_HEIGHT = 48.dp
    val TAB_HEIGHT = 72.dp
    val TAB_HEIGHT_GRID = 120.dp
    val INSERTION_LINE_WIDTH = 3.dp
    val INSERTION_LINE_LENGTH = 40.dp
}

/**
 * Get shape for tab based on position in group
 */
@Composable
fun getTabShape(
    isInGroup: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean
): Shape {
    return when {
        !isInGroup -> RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS)
        isFirstInGroup && isLastInGroup -> RoundedCornerShape(TabVisualConstants.TAB_CORNER_RADIUS)
        isFirstInGroup -> RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
        isLastInGroup -> RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = TabVisualConstants.TAB_CORNER_RADIUS,
            bottomEnd = TabVisualConstants.TAB_CORNER_RADIUS
        )
        else -> RoundedCornerShape(0.dp)
    }
}

/**
 * Get shape for group header
 */
@Composable
fun getGroupHeaderShape(): Shape {
    return RoundedCornerShape(
        topStart = TabVisualConstants.GROUP_CORNER_RADIUS,
        topEnd = TabVisualConstants.GROUP_CORNER_RADIUS,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
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
            isDragging -> 1.08f  // Increased from 1.05f - dragged item is bigger
            isTarget -> 1.10f     // Changed from 0.95f - target zooms IN to show it's ready
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium  // Faster response
        ),
        label = "dragScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
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
 * Get spacing between items
 */
@Composable
fun getTabSpacing(isInGroup: Boolean): Dp {
    return if (isInGroup) {
        TabVisualConstants.TAB_SPACING_IN_GROUP
    } else {
        TabVisualConstants.TAB_SPACING_NORMAL
    }
}

/**
 * Colors for drag targets with Material 3 theming
 */
@Composable
fun getDragTargetColor(feedbackType: DragFeedback): Color {
    return when (feedbackType) {
        DragFeedback.GroupWith -> MaterialTheme.colorScheme.primaryContainer
        DragFeedback.MoveToGroup -> MaterialTheme.colorScheme.secondaryContainer
        DragFeedback.Reorder -> MaterialTheme.colorScheme.primary
        DragFeedback.Ungroup -> MaterialTheme.colorScheme.tertiaryContainer
        DragFeedback.None -> Color.Transparent
    }
}

/**
 * Generate random color for new groups
 */
fun generateRandomGroupColor(): Int {
    val colors = listOf(
        0xFFE53935.toInt(), // Red
        0xFF1E88E5.toInt(), // Blue
        0xFF43A047.toInt(), // Green
        0xFFFB8C00.toInt(), // Orange
        0xFF8E24AA.toInt(), // Purple
        0xFFD81B60.toInt(), // Pink
        0xFF00897B.toInt(), // Teal
        0xFFFDD835.toInt()  // Yellow
    )
    return colors.random()
}
