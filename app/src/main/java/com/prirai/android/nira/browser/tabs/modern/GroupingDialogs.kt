package com.prirai.android.nira.browser.tabs.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import mozilla.components.browser.state.state.TabSessionState

/**
 * Dialog for adding tabs to groups
 */
@Composable
fun AddToGroupDialog(
    tab: TabSessionState,
    existingGroups: List<TabGroupData>,
    onDismiss: () -> Unit,
    onAddToExistingGroup: (String) -> Unit,
    onCreateNewGroup: (String, Int) -> Unit
) {
    var showNewGroupDialog by remember { mutableStateOf(false) }
    
    if (showNewGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showNewGroupDialog = false },
            onCreate = { name, color ->
                onCreateNewGroup(name, color)
                showNewGroupDialog = false
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add to Group") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNewGroupDialog = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Create New Group",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    
                    items(existingGroups) { group ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddToExistingGroup(group.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(group.color).copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(group.color))
                                )
                                Column {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${group.tabIds.size} tabs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
}

/**
 * Dialog for creating a new group
 */
@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, color: Int) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0xFFE57373.toInt()) }
    
    val availableColors = remember {
        listOf(
            0xFFE57373.toInt(), // Red
            0xFF81C784.toInt(), // Green
            0xFF64B5F6.toInt(), // Blue
            0xFFFFB74D.toInt(), // Orange
            0xFFBA68C8.toInt(), // Purple
            0xFFF06292.toInt(), // Pink
            0xFF4DD0E1.toInt(), // Cyan
            0xFFFFF176.toInt()  // Yellow
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Group") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    placeholder = { Text("e.g., Shopping, Work, Research") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelLarge
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { selectedColor = color }
                                .then(
                                    if (selectedColor == color) {
                                        Modifier.padding(4.dp)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.isNotBlank()) {
                        onCreate(groupName.trim(), selectedColor)
                    }
                },
                enabled = groupName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for group operations (rename, recolor, ungroup, close all)
 */
@Composable
fun GroupOptionsDialog(
    group: TabGroupData,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRecolor: (Int) -> Unit,
    onUngroup: () -> Unit,
    onCloseAll: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showRecolorDialog by remember { mutableStateOf(false) }
    
    when {
        showRenameDialog -> {
            RenameGroupDialog(
                currentName = group.name,
                onDismiss = { showRenameDialog = false },
                onRename = { newName ->
                    onRename(newName)
                    showRenameDialog = false
                }
            )
        }
        showRecolorDialog -> {
            RecolorGroupDialog(
                currentColor = group.color,
                onDismiss = { showRecolorDialog = false },
                onRecolor = { newColor ->
                    onRecolor(newColor)
                    showRecolorDialog = false
                }
            )
        }
        else -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Group: ${group.name}") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { showRenameDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Rename Group")
                        }
                        
                        TextButton(
                            onClick = { showRecolorDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Change Color")
                        }
                        
                        TextButton(
                            onClick = {
                                onUngroup()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ungroup Tabs")
                        }
                        
                        TextButton(
                            onClick = {
                                onCloseAll()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Close All Tabs")
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
    }
}

@Composable
private fun RenameGroupDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Group") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Group Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onRename(newName.trim()) },
                enabled = newName.isNotBlank()
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

@Composable
private fun RecolorGroupDialog(
    currentColor: Int,
    onDismiss: () -> Unit,
    onRecolor: (Int) -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }
    
    val availableColors = remember {
        listOf(
            0xFFE57373.toInt(),
            0xFF81C784.toInt(),
            0xFF64B5F6.toInt(),
            0xFFFFB74D.toInt(),
            0xFFBA68C8.toInt(),
            0xFFF06292.toInt(),
            0xFF4DD0E1.toInt(),
            0xFFFFF176.toInt()
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Group Color") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .clickable { selectedColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedColor == color) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onRecolor(selectedColor) }) {
                Text("Change")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
