package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                    scope.launch {
                        viewModel.closeOtherTabs(tab.id)
                        onDismiss()
                    }
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
    var showRenameDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showProfilePicker by remember { mutableStateOf(false) }
    
    val groups by viewModel.groups.collectAsState()
    val currentGroup = groups.find { it.id == groupId }
    
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
                    scope.launch {
                        viewModel.createNewTabInGroup(groupId)
                        onDismiss()
                    }
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_edit), "Rename") },
                text = "Rename Island",
                onClick = {
                    showRenameDialog = true
                }
            )
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_palette), "Change Color") },
                text = "Change Color",
                onClick = {
                    showColorPicker = true
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
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_pin_outline), "Pin Island") },
                text = "Pin Island",
                onClick = {
                    scope.launch {
                        viewModel.pinGroup(groupId)
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
            
            TabMenuItem(
                icon = { Icon(painterResource(R.drawable.ic_profile), "Move to Profile") },
                text = "Move to Profile",
                onClick = {
                    showProfilePicker = true
                }
            )
        }
    }
    
    // Show dialogs
    if (showRenameDialog && currentGroup != null) {
        RenameGroupDialog(
            currentName = currentGroup.name,
            onConfirm = { newName ->
                scope.launch {
                    viewModel.renameGroup(groupId, newName)
                    showRenameDialog = false
                    onDismiss()
                }
            },
            onDismiss = {
                showRenameDialog = false
                onDismiss()
            }
        )
    }
    
    if (showColorPicker && currentGroup != null) {
        ColorPickerDialog(
            currentColor = currentGroup.color,
            onConfirm = { newColor ->
                scope.launch {
                    viewModel.changeGroupColor(groupId, newColor)
                    showColorPicker = false
                    onDismiss()
                }
            },
            onDismiss = {
                showColorPicker = false
                onDismiss()
            }
        )
    }
    
    if (showProfilePicker) {
        ProfilePickerDialog(
            onConfirm = { profileId ->
                scope.launch {
                    viewModel.moveGroupToProfile(groupId, profileId)
                    showProfilePicker = false
                    onDismiss()
                }
            },
            onDismiss = {
                showProfilePicker = false
                onDismiss()
            }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameGroupDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Island") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Island Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    currentColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        0xFFE57373.toInt(), // Red
        0xFFF06292.toInt(), // Pink
        0xFFBA68C8.toInt(), // Purple
        0xFF9575CD.toInt(), // Deep Purple
        0xFF7986CB.toInt(), // Indigo
        0xFF64B5F6.toInt(), // Blue
        0xFF4FC3F7.toInt(), // Light Blue
        0xFF4DD0E1.toInt(), // Cyan
        0xFF4DB6AC.toInt(), // Teal
        0xFF81C784.toInt(), // Green
        0xFFAED581.toInt(), // Light Green
        0xFFDCE775.toInt(), // Lime
        0xFFFFF176.toInt(), // Yellow
        0xFFFFD54F.toInt(), // Amber
        0xFFFFB74D.toInt(), // Orange
        0xFFFF8A65.toInt()  // Deep Orange
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Color") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(300.dp)
            ) {
                items(colors) { color ->
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .clickable { onConfirm(color) }
                            .border(
                                width = if (color == currentColor) 3.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // TODO: Get actual profiles from ProfileManager
    val profiles = remember {
        listOf(
            "default" to "Default Profile",
            "work" to "Work Profile",
            "personal" to "Personal Profile"
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Profile") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                profiles.forEach { (profileId, profileName) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(profileId) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profileName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
