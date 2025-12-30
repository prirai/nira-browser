package com.prirai.android.nira.browser.tabs.modern

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import mozilla.components.browser.state.state.TabSessionState

@Composable
fun TabSheet(
    tabManager: TabManager,
    isGridView: Boolean,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState) -> Unit,
    onGroupLongPress: (com.prirai.android.nira.browser.tabgroups.TabGroupData) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by tabManager.state.collectAsState()
    
    Box(modifier = modifier.fillMaxSize()) {
        if (isGridView) {
            TabGrid(
                tabs = state.tabs,
                groups = state.groups,
                expandedGroupIds = state.expandedGroupIds,
                selectedTabId = state.selectedTabId,
                onTabClick = onTabClick,
                onTabClose = onTabClose,
                onTabLongPress = onTabLongPress,
                onGroupToggle = { tabManager.toggleGroup(it) },
                onGroupLongPress = onGroupLongPress
            )
        } else {
            TabList(
                tabs = state.tabs,
                groups = state.groups,
                expandedGroupIds = state.expandedGroupIds,
                selectedTabId = state.selectedTabId,
                onTabClick = onTabClick,
                onTabClose = onTabClose,
                onTabLongPress = onTabLongPress,
                onGroupToggle = { tabManager.toggleGroup(it) },
                onGroupLongPress = onGroupLongPress
            )
        }
        
        if (state.tabs.isEmpty()) {
            EmptyState(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabList(
    tabs: List<TabSessionState>,
    groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>,
    expandedGroupIds: Set<String>,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState) -> Unit,
    onGroupToggle: (String) -> Unit,
    onGroupLongPress: (com.prirai.android.nira.browser.tabgroups.TabGroupData) -> Unit
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val grouped = tabs.groupBy { tab ->
            groups.find { tab.id in it.tabIds }
        }
        
        grouped.forEach { (group, groupTabs) ->
            if (group != null) {
                item(key = "group_${group.id}") {
                    GroupHeader(
                        group = group,
                        isExpanded = group.id in expandedGroupIds,
                        onToggle = { onGroupToggle(group.id) },
                        onLongPress = { onGroupLongPress(group) },
                        modifier = Modifier
                    )
                }
                
                if (group.id in expandedGroupIds) {
                    items(
                        items = groupTabs,
                        key = { "group_${group.id}_tab_${it.id}" }
                    ) { tab ->
                        TabCard(
                            tab = tab,
                            isSelected = tab.id == selectedTabId,
                            onTabClick = { onTabClick(tab.id) },
                            onTabClose = { onTabClose(tab.id) },
                            onTabLongPress = { onTabLongPress(tab) },
                            modifier = Modifier
                                
                                .padding(start = 16.dp)
                        )
                    }
                }
            } else {
                items(
                    items = groupTabs,
                    key = { "tab_${it.id}" }
                ) { tab ->
                    TabCard(
                        tab = tab,
                        isSelected = tab.id == selectedTabId,
                        onTabClick = { onTabClick(tab.id) },
                        onTabClose = { onTabClose(tab.id) },
                        onTabLongPress = { onTabLongPress(tab) },
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabGrid(
    tabs: List<TabSessionState>,
    groups: List<com.prirai.android.nira.browser.tabgroups.TabGroupData>,
    expandedGroupIds: Set<String>,
    selectedTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onTabLongPress: (TabSessionState) -> Unit,
    onGroupToggle: (String) -> Unit,
    onGroupLongPress: (com.prirai.android.nira.browser.tabgroups.TabGroupData) -> Unit
) {
    val gridState = rememberLazyGridState()
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val grouped = tabs.groupBy { tab ->
            groups.find { tab.id in it.tabIds }
        }
        
        grouped.forEach { (group, groupTabs) ->
            if (group != null) {
                item(
                    key = "group_${group.id}",
                    span = { GridItemSpan(3) }
                ) {
                    GroupHeader(
                        group = group,
                        isExpanded = group.id in expandedGroupIds,
                        onToggle = { onGroupToggle(group.id) },
                        onLongPress = { onGroupLongPress(group) },
                        modifier = Modifier
                    )
                }
                
                if (group.id in expandedGroupIds) {
                    items(
                        items = groupTabs,
                        key = { "group_${group.id}_tab_${it.id}" }
                    ) { tab ->
                        GridTabCard(
                            tab = tab,
                            isSelected = tab.id == selectedTabId,
                            onTabClick = { onTabClick(tab.id) },
                            onTabClose = { onTabClose(tab.id) },
                            onTabLongPress = { onTabLongPress(tab) },
                            modifier = Modifier
                        )
                    }
                }
            } else {
                items(
                    items = groupTabs,
                    key = { "tab_${it.id}" }
                ) { tab ->
                    GridTabCard(
                        tab = tab,
                        isSelected = tab.id == selectedTabId,
                        onTabClick = { onTabClick(tab.id) },
                        onTabClose = { onTabClose(tab.id) },
                        onTabLongPress = { onTabLongPress(tab) },
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(
    tab: TabSessionState,
    isSelected: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tab_scale"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                onClick = onTabClick,
                onLongClick = onTabLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Favicon
            AsyncImage(
                model = tab.content.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            )
            
            // Title and URL
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = tab.content.title.ifEmpty { "New Tab" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Text(
                    text = tab.content.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
            
            // Close button
            IconButton(onClick = onTabClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTabCard(
    tab: TabSessionState,
    isSelected: Boolean,
    onTabClick: () -> Unit,
    onTabClose: () -> Unit,
    onTabLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "grid_scale"
    )
    
    Card(
        modifier = modifier
            .aspectRatio(0.75f)
            .scale(scale)
            .combinedClickable(
                onClick = onTabClick,
                onLongClick = onTabLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Preview area (placeholder)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = tab.content.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Title
                Text(
                    text = tab.content.title.ifEmpty { "New Tab" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // Close button
            IconButton(
                onClick = onTabClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    modifier = Modifier.size(16.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(
    group: com.prirai.android.nira.browser.tabgroups.TabGroupData,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggle,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(group.color).copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(group.color)
            )
            
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(group.color),
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = "${group.tabIds.size} tabs",
                style = MaterialTheme.typography.bodySmall,
                color = Color(group.color).copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Text(
            text = "No tabs open",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Tap + to create a new tab",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
