package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.ui.components.pressScale
import com.prirai.android.nira.ui.components.thumbnailGradientOverlay
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Reusable grid card component for both grouped and ungrouped tabs.
 * Provides consistent UI with thumbnail, bottom-gradient overlay, and layered content.
 *
 * @param tab The tab session state
 * @param isSelected Whether this tab is currently selected
 * @param groupColor Optional group color for border/styling
 * @param onTabClick Callback when tab is clicked
 * @param onTabClose Callback when close button is clicked
 * @param modifier Additional modifiers to apply
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabGridCard(
    tab: TabSessionState,
    isSelected: Boolean,
    groupColor: Int?,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val closeSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    Surface(
        modifier =
            modifier
                .aspectRatio(0.75f)
                .pressScale(interactionSource)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        shape = RoundedCornerShape(12.dp),
        color = when {
            // One selection language: the focused card is always the
            // primaryContainer, grouped or not; the group color speaks
            // through the container's accent strip and badge.
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            groupColor != null -> Color(groupColor).copy(alpha = 0.05f)
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        border = when {
            // Group identity on a resting grouped card; the selected state
            // speaks through its primaryContainer, never an accent stroke.
            !isSelected && groupColor != null -> BorderStroke(1.dp, Color(groupColor).copy(alpha = 0.3f))
            else -> null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = interactionSource) { onTabClick() }
        ) {
            // Thumbnail fills the full card
            ThumbnailImageView(
                tab = tab,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom-focused scrim so the title overlay stays legible on light frames
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .thumbnailGradientOverlay()
            )

            // Close button — small, semi-transparent, top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 2.dp,
                    modifier = Modifier.size(26.dp)
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                scale.animateTo(0.8f, animationSpec = closeSpec)
                                onTabClose()
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.menu_close_tab),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Frosted-glass title overlay at the bottom — favicon + title in one row
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FaviconImage(
                            tab = tab,
                            size = 14.dp
                        )
                        Text(
                            text = getTabDisplayTitle(tab, stringResource(R.string.tab_history_new_tab)),
                            style =
                                if (isSelected) {
                                    MaterialTheme.typography.labelSmallEmphasized
                                } else {
                                    MaterialTheme.typography.labelSmall
                                },
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
