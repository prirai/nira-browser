package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabContextMenu(
    tab: TabSessionState,
    isInGroup: Boolean,
    onDismiss: () -> Unit,
    viewModel: TabViewModel,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            TabMenuHeader(tab)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.mozac_ic_tab_new_24), "New Tab") },
                text = "New Tab",
                onClick = {
                    scope.launch {
                        viewModel.createNewTab()
                        onDismiss()
                    }
                }
            )
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.control_point_duplicate_24px), "Duplicate") },
                text = "Duplicate Tab",
                onClick = {
                    scope.launch {
                        viewModel.duplicateTab(tab.id)
                        onDismiss()
                    }
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            if (isInGroup) {
                TabMenuItem(
                    icon = { Icon(painterResource(R.drawable.ungroup_24px), "Remove from Island") },
                    text = "Remove from Island",
                    onClick = {
                        scope.launch {
                            viewModel.removeTabFromGroup(tab.id)
                            onDismiss()
                        }
                    }
                )
            } else {
                TabMenuItem(
                    icon = { Icon(painterResource(R.drawable.ic_tab_group), "Add to Island") },
                    text = "Add to Island",
                    onClick = {
                        scope.launch {
                            viewModel.showAddToGroupDialog(tab.id)
                            onDismiss()
                        }
                    }
                )
            }
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_pin_outline), "Pin Tab") },
                text = "Pin Tab",
                onClick = {
                    scope.launch {
                        viewModel.togglePinTab(tab.id)
                        onDismiss()
                    }
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_share), "Share") },
                text = "Share Tab",
                onClick = {
                    onDismiss()
                    // Share functionality will be handled by the caller
                }
            )
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_select_all_24), "Select Tabs") },
                text = "Select Tabs",
                onClick = {
                    scope.launch {
                        viewModel.enterMultiSelectMode()
                        onDismiss()
                    }
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_close_small), "Close Tab") },
                text = "Close Tab",
                onClick = {
                    scope.launch {
                        viewModel.closeTab(tab.id)
                        onDismiss()
                    }
                }
            )
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_round_close), "Close Other Tabs") },
                text = "Close Other Tabs",
                onClick = {
                    onDismiss()
                    // Close other tabs functionality will be handled by the caller
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupContextMenu(
    groupId: String,
    groupName: String,
    onDismiss: () -> Unit,
    viewModel: TabViewModel,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = groupName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.mozac_ic_tab_new_24), "New Tab in Group") },
                text = "New Tab in Island",
                onClick = {
                    onDismiss()
                    // New tab in group will be handled by the caller
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_edit), "Rename") },
                text = "Rename Island",
                onClick = {
                    onDismiss()
                    // Show rename dialog - handled by caller
                }
            )
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_palette), "Change Color") },
                text = "Change Color",
                onClick = {
                    onDismiss()
                    // Show color picker - handled by caller
                }
            )
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_expand_more), "Collapse") },
                text = "Collapse/Expand",
                onClick = {
                    scope.launch {
                        viewModel.toggleGroupExpanded(groupId)
                        onDismiss()
                    }
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ungroup_24px), "Ungroup") },
                text = "Ungroup All Tabs",
                onClick = {
                    scope.launch {
                        viewModel.ungroupAllTabs(groupId)
                        onDismiss()
                    }
                }
            )
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_close_small), "Close All") },
                text = "Close All Tabs",
                onClick = {
                    scope.launch {
                        viewModel.closeAllTabsInGroup(groupId)
                        onDismiss()
                    }
                }
            )
        }
    }
}

@Composable
private fun TabMenuHeader(tab: TabSessionState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tab.content.icon?.let { icon ->
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tab.content.title.ifEmpty { "New Tab" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = tab.content.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TabMenuItem(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
