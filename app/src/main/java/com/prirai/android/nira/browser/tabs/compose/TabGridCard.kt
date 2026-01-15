package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Reusable grid card component for both grouped and ungrouped tabs.
 * Provides consistent UI with thumbnail, gradient overlay, and layered content.
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
            isSelected && groupColor != null -> Color(groupColor).copy(alpha = 0.2f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            groupColor != null -> Color(groupColor).copy(alpha = 0.05f)
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        border = when {
            isSelected && groupColor != null -> BorderStroke(2.dp, Color(groupColor))
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            groupColor != null -> BorderStroke(1.dp, Color(groupColor).copy(alpha = 0.3f))
            else -> null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onTabClick() }
        ) {
            // Thumbnail preview (background)
            ThumbnailImageView(
                tab = tab,
                modifier = Modifier.fillMaxSize()
            )

            // Overlay with gradient for better text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            // Content overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header with favicon and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Favicon
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        FaviconImage(
                            tab = tab,
                            size = 20.dp,
                            modifier = Modifier.padding(2.dp)
                        )
                    }

                    // Close button - consistent style with grouped tabs
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    scale.animateTo(0.8f, animationSpec = tween(150))
                                    onTabClose()
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Title with background for readability
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tab.content.title.ifEmpty { "New Tab" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}
