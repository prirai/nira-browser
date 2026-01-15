package com.prirai.android.nira.browser.tabs.modern

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import mozilla.components.browser.state.state.TabSessionState

/**
 * Horizontal tab bar showing active tabs (modern browser style)
 */
@Composable
fun TabBar(
    tabManager: TabManager,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTabClick: () -> Unit,
    onShowAllTabsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by tabManager.state.collectAsState()
    val scrollState = rememberScrollState()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scrollable tabs
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.tabs.take(10).forEach { tab ->
                    TabBarItem(
                        tab = tab,
                        isSelected = tab.id == state.selectedTabId,
                        onTabClick = { onTabClick(tab.id) },
                        onTabClose = { onTabClose(tab.id) }
                    )
                }
                
                // Show "..." if more than 10 tabs
                if (state.tabs.size > 10) {
                    TextButton(
                        onClick = onShowAllTabsClick,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            text = "+${state.tabs.size - 10}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            // New tab button
            IconButton(onClick = onNewTabClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New tab"
                )
            }
            
            // Show all tabs button
            IconButton(onClick = onShowAllTabsClick) {
                Badge(
                    modifier = Modifier.offset(x = 8.dp, y = (-8).dp)
                ) {
                    Text(
                        text = "${state.tabs.size}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Show all tabs"
                )
            }
        }
    }
}

@Composable
private fun TabBarItem(
    tab: TabSessionState,
    isSelected: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "tab_bg_color"
    )
    
    Surface(
        modifier = modifier
            .height(40.dp)
            .widthIn(min = 120.dp, max = 200.dp)
            .clickable(onClick = onTabClick),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        tonalElevation = if (isSelected) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Favicon
            AsyncImage(
                model = tab.content.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
            )
            
            // Title
            Text(
                text = tab.content.title.ifEmpty { "New Tab" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            // Close button
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close tab",
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onTabClose),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
