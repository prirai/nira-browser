package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import kotlinx.coroutines.launch
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
 * - Smooth release animation on the theme's fast spatial spring
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val maxOffset = 80f
    val scope = rememberCoroutineScope()
    val releaseSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    // Reset when the tab id changes so the state does not leak between
    // adjacent slots when Compose reuses this call site for a different tab
    // (e.g. after a neighbouring pill is closed).
    val offsetY = remember(tab.id) { Animatable(0f) }

    Box(
        modifier = modifier
            .height(32.dp)
            .width(100.dp)
    ) {
        // Background indicator based on swipe direction
        if (abs(offsetY.value) > 5f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            offsetY.value < -5f -> MaterialTheme.colorScheme.errorContainer // Swipe up - delete
                            offsetY.value > 5f -> MaterialTheme.colorScheme.primaryContainer // Swipe down - menu
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (offsetY.value < 0) Icons.Default.Delete else Icons.Default.MoreVert,
                    contentDescription = stringResource(if (offsetY.value < 0) R.string.delete else R.string.more_options),
                    tint = if (offsetY.value < 0)
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
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
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
                                    scope.launch {
                                        offsetY.snapTo((offsetY.value + deltaY).coerceIn(-maxOffset, maxOffset))
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // On release, trigger action if vertical was dominant
                        if (totalVerticalDrag > totalHorizontalDrag && totalVerticalDrag > 10f) {
                            when {
                                offsetY.value < -swipeThreshold -> {
                                    // Swipe up - close tab. Do NOT hide the pill locally;
                                    // the tab list flowing down from the store will remove
                                    // it on the next recomposition. Hiding it locally used
                                    // to leak state to the adjacent pill when Compose
                                    // reused the slot.
                                    onTabClose()
                                }

                                offsetY.value > swipeThreshold -> {
                                    // Swipe down - show menu
                                    onShowMenu()
                                }
                            }
                        }

                        // Reset position on the theme's spring so the pill settles instead of snapping
                        scope.launch { offsetY.animateTo(0f, animationSpec = releaseSpec) }
                    }
                },
            shape = RoundedCornerShape(12.dp),
            color = when {
                isSelected && groupColor != null -> Color(groupColor).copy(alpha = 0.3f)
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else -> Color.Transparent
            },
            border = if (isSelected) {
                BorderStroke(
                    1.dp,
                    groupColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                )
            } else {
                null
            }
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
                    text = getTabDisplayTitle(tab, stringResource(R.string.tab_history_new_tab)),
                    style =
                        if (isSelected) {
                            MaterialTheme.typography.labelSmallEmphasized
                        } else {
                            MaterialTheme.typography.labelSmall
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Get display title for a tab, avoiding the new tab title for homepage.
 *
 * @param newTabTitle localized fallback for the synthetic new tab page
 */
fun getTabDisplayTitle(tab: TabSessionState, newTabTitle: String): String {
    return when {
        // For homepage with no title, show the new tab title
        tab.content.url == "about:homepage" && tab.content.title.isEmpty() -> newTabTitle
        // For other pages, show title if available
        tab.content.title.isNotEmpty() -> tab.content.title
        // If no title but has URL (not homepage), show URL
        tab.content.url.isNotEmpty() && tab.content.url != "about:homepage" -> tab.content.url
        // Last resort fallback
        else -> newTabTitle
    }
}
