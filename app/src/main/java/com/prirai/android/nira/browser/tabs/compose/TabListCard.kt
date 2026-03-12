package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState

/**
 * Reusable list card component for both grouped and ungrouped tabs.
 *
 * Selection is shown via a 4dp left accent strip instead of a border, which is more
 * visually distinctive and leaves the thumbnail area unobstructed.
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
    val accentColor = when {
        isSelected && groupColor != null -> Color(groupColor)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected && groupColor != null -> Color(groupColor).copy(alpha = 0.08f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isSelected) 2.dp else 1.dp
    ) {
        // IntrinsicSize.Min lets the left strip fill the Row's actual height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left accent strip — primary/group color for selected, transparent otherwise
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        accentColor,
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )

            // Main content
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onTabClick() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Favicon in rounded surface container + title/URL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // 40dp rounded container for favicon
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            FaviconImage(
                                tab = tab,
                                size = 22.dp
                            )
                        }
                    }

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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
}
