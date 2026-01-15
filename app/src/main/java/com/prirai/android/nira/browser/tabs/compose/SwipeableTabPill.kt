package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reusable Swipeable Tab Pill Component for Tab Bar
 *
 * This component provides a tab pill with vertical swipe gestures:
 * - Swipe DOWN: Show menu
 * - Swipe UP: Delete/close tab
 *
 * Features:
 * - Visual feedback during swipe (background color + icon)
 * - Configurable swipe threshold
 * - Smooth animations
 * - Group color theming support
 * - Selection state support
 *
 * Usage:
 * ```
 * SwipeableTabPill(
 *     tab = tabSession,
 *     isSelected = currentTab == tabSession.id,
 *     groupColor = 0xFFE57373.toInt(),
 *     onTabClick = { handleClick(tab.id) },
 *     onTabClose = { handleClose(tab.id) },
 *     onShowMenu = { showMenu(tab) }
 * )
 * ```
 */

/**
 * Swipeable tab pill with vertical gesture support
 *
 * @param tab The tab session state to display
 * @param isSelected Whether this tab is currently selected
 * @param groupColor Optional group color for theming (as Int)
 * @param onTabClick Callback when tab is clicked
 * @param onTabClose Callback when tab should be closed (swipe up)
 * @param onShowMenu Callback when menu should be shown (swipe down)
 * @param modifier Optional modifier
 * @param swipeThreshold Vertical distance in pixels required to trigger action (default: 40f)
 */
@Composable
fun SwipeableTabPill(
    tab: TabSessionState,
    isSelected: Boolean,
    groupColor: Int? = null,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onShowMenu: () -> Unit,
    modifier: Modifier = Modifier,
    swipeThreshold: Float = 40f
) {
    var offsetY by remember { mutableStateOf(0f) }
    val maxOffset = 80f

    Box(
        modifier = modifier
            .height(32.dp)
            .width(100.dp)
    ) {
        // Background indicator based on swipe direction
        if (abs(offsetY) > 5f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            offsetY < -5f -> MaterialTheme.colorScheme.errorContainer // Swipe up - delete
                            offsetY > 5f -> MaterialTheme.colorScheme.primaryContainer // Swipe down - menu
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (offsetY < 0) Icons.Default.Delete else Icons.Default.MoreVert,
                    contentDescription = if (offsetY < 0) "Delete" else "Menu",
                    tint = if (offsetY < 0)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Tab content with swipe gesture
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .pointerInput(tab.id) {
                    var totalHorizontalDrag = 0f
                    var totalVerticalDrag = 0f

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        totalHorizontalDrag = 0f
                        totalVerticalDrag = 0f

                        do {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull()

                            if (drag != null && drag.pressed) {
                                val deltaX = drag.position.x - drag.previousPosition.x
                                val deltaY = drag.position.y - drag.previousPosition.y

                                totalHorizontalDrag += abs(deltaX)
                                totalVerticalDrag += abs(deltaY)

                                // Only handle vertical gestures if vertical movement dominates
                                if (totalVerticalDrag > totalHorizontalDrag && totalVerticalDrag > 10f) {
                                    // This is a vertical gesture - consume it
                                    drag.consume()
                                    offsetY = (offsetY + deltaY).coerceIn(-maxOffset, maxOffset)
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // On release, trigger action if vertical was dominant
                        if (totalVerticalDrag > totalHorizontalDrag && totalVerticalDrag > 10f) {
                            when {
                                offsetY < -swipeThreshold -> {
                                    // Swipe up - close tab
                                    onTabClose()
                                }

                                offsetY > swipeThreshold -> {
                                    // Swipe down - show menu
                                    onShowMenu()
                                }
                            }
                        }

                        // Reset position
                        offsetY = 0f
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = when {
                isSelected && groupColor != null -> Color(groupColor).copy(alpha = 0.2f)
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> Color.Transparent
            },
            border = if (isSelected) {
                BorderStroke(
                    1.5.dp,
                    groupColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                )
            } else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onTabClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Favicon
                FaviconImage(
                    tab = tab,
                    size = 14.dp
                )

                // Title
                Text(
                    text = tab.content.title.ifEmpty { "New Tab" },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Example usage in GroupPill:
 *
 * ```kotlin
 * group.tabs.forEachIndexed { index, tab ->
 *     if (index > 0) {
 *         // Divider
 *     }
 *
 *     SwipeableTabPill(
 *         tab = tab,
 *         isSelected = selectedTabId == tab.id,
 *         groupColor = group.color,
 *         onTabClick = { onTabClick(tab.id) },
 *         onTabClose = { onTabClose(tab.id) },
 *         onShowMenu = {
 *             menuTab = tab
 *             showTabMenu = true
 *         }
 *     )
 * }
 * ```
 */
