package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState

/**
 * Reusable list card component for both grouped and ungrouped tabs.
 * Provides consistent UI without background colors or extra borders.
 * Uses only transparent background with content in a Row layout.
 *
 * @param tab The tab session state
 * @param isSelected Whether this tab is currently selected
 * @param groupColor Optional group color for selection indicator
 * @param onTabClick Callback when tab is clicked
 * @param onTabClose Callback when close button is clicked
 * @param modifier Additional modifiers to apply
 * @param showDragHandle Whether to show the drag handle indicator
 */
@Composable
fun TabListCard(
    tab: TabSessionState,
    isSelected: Boolean,
    groupColor: Int? = null,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected && groupColor != null -> Color(groupColor).copy(alpha = 0.15f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        border = when {
            isSelected && groupColor != null -> BorderStroke(2.dp, Color(groupColor))
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else -> null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onTabClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Main content area
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Favicon
                FaviconImage(
                    tab = tab,
                    size = 20.dp
                )

                // Title and URL
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = tab.content.title.ifEmpty { "New Tab" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (tab.content.url.isNotEmpty()) {
                        Text(
                            text = tab.content.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right side actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button
                IconButton(
                    onClick = onTabClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Drag handle indicator (optional)
                if (showDragHandle) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Long press to drag",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
