package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    Surface(
        modifier = modifier
            .aspectRatio(0.75f)
            .scale(scale.value),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected && groupColor != null -> Color(groupColor).copy(alpha = 0.15f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            groupColor != null -> Color(groupColor).copy(alpha = 0.05f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        border = when {
            isSelected && groupColor != null -> BorderStroke(2.5.dp, Color(groupColor))
            isSelected -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
            groupColor != null -> BorderStroke(1.dp, Color(groupColor).copy(alpha = 0.3f))
            else -> null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onTabClick() }
        ) {
            // Thumbnail fills the full card
            ThumbnailImageView(
                tab = tab,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom-focused gradient so the thumbnail is fully visible at the top
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
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
                                scale.animateTo(0.8f, animationSpec = tween(150))
                                onTabClose()
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        FaviconImage(
                            tab = tab,
                            size = 14.dp
                        )
                        Text(
                            text = getTabDisplayTitle(tab),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
