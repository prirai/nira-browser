package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import mozilla.components.browser.state.state.TabSessionState

/**
 * Reusable tab card with all features:
 * - Favicon display
 * - Orange border for null contextId
 * - Drag handle indicator
 * - Group visual integration
 * - Material 3 theming
 */
@Composable
fun TabCard(
    tab: TabSessionState,
    isSelected: Boolean,
    isDragging: Boolean,
    isInGroup: Boolean = false,
    isFirstInGroup: Boolean = false,
    isLastInGroup: Boolean = false,
    groupColor: Color? = null,
    showDragHandle: Boolean = true,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val hasNullContext = tab.contextId == null
    val isTarget = false // Managed by parent
    
    val borderColor = when {
        hasNullContext -> Color(0xFFFF9800) // Orange for null context
        groupColor != null -> groupColor
        else -> Color.Transparent
    }
    
    val borderWidth = when {
        hasNullContext -> 2.dp
        isInGroup -> 0.dp
        else -> 0.dp
    }
    
    Card(
        modifier = modifier
            .dragFeedbackScale(isTarget, isDragging)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTabClick() },
                    onLongPress = { onLongPress() }
                )
            },
        shape = getTabShape(isInGroup, isFirstInGroup, isLastInGroup),
        elevation = CardDefaults.cardElevation(
            defaultElevation = getTabElevation(isDragging)
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isInGroup && groupColor != null -> groupColor.copy(alpha = 0.05f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle indicator (subtle dots)
            if (showDragHandle && !isCompact) {
                Column(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                    }
                }
            }
            
            // Favicon
            FaviconImage(
                tab = tab,
                size = if (isCompact) 32.dp else 40.dp,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            // Tab info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tab.content.title.takeIf { it.isNotBlank() } ?: "New Tab",
                    style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (!isCompact) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.content.url.takeIf { it.isNotBlank() } ?: "about:blank",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Close button
            IconButton(
                onClick = onTabClose,
                modifier = Modifier.size(if (isCompact) 32.dp else 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    modifier = Modifier.size(if (isCompact) 18.dp else 20.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Favicon image with placeholder
 */
@Composable
fun FaviconImage(
    tab: TabSessionState,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        // Try to load favicon
        tab.content.icon?.let { iconBitmap ->
            Image(
                bitmap = iconBitmap.asImageBitmap(),
                contentDescription = "Favicon",
                modifier = Modifier.size(size * 0.75f)
            )
        } ?: run {
            // Placeholder icon
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "No favicon",
                modifier = Modifier.size(size * 0.6f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Thumbnail image for grid view - uses Coil to load from thumbnail storage
 */
@Composable
fun ThumbnailImage(
    tab: TabSessionState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        // Use Coil AsyncImage to load thumbnail from storage
        val imageRequest = remember(tab.id) {
            ImageRequest.Builder(context)
                .data("moz-tab-thumbnail://${tab.id}")
                .crossfade(true)
                .build()
        }
        
        AsyncImage(
            model = imageRequest,
            contentDescription = "Tab thumbnail",
            modifier = Modifier.fillMaxSize()
        )
        
        // Fallback content when no thumbnail available
        if (tab.content.icon == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "No thumbnail",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = tab.content.title.takeIf { it.isNotBlank() } ?: "New Tab",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/**
 * Group header card
 */
@Composable
fun GroupHeaderCard(
    group: UnifiedTabOrder.OrderItem.TabGroup,
    isExpanded: Boolean,
    isTarget: Boolean,
    onHeaderClick: () -> Unit,
    onMoreClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .dragFeedbackScale(isTarget, false)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onHeaderClick() },
                    onLongPress = { onLongPress() }
                )
            },
        shape = getGroupHeaderShape(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget) {
                getDragTargetColor(DragFeedback.MoveToGroup)
            } else {
                Color(group.color).copy(alpha = 0.15f)
            }
        ),
        border = BorderStroke(1.dp, Color(group.color).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabVisualConstants.GROUP_HEADER_HEIGHT)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group color indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(group.color))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Expand/collapse icon
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Default.ExpandMore
                } else {
                    Icons.Default.ChevronRight
                },
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Group name
            Text(
                text = group.groupName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Tab count badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(group.color).copy(alpha = 0.2f),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "${group.tabIds.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(group.color),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            // More options button
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Group options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
