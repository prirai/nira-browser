package com.prirai.android.nira.browser.tabs.compose

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
import androidx.compose.ui.unit.dp

/**
 * Reusable Dialog Components for Tab Menu System
 *
 * This file contains all dialog components used in the tab menu system:
 * - RenameGroupDialog: For renaming groups/islands
 * - ColorPickerDialog: For changing group colors
 * - ProfilePickerDialog: For moving tabs/groups between profiles
 *
 * These dialogs are designed to be reusable across different contexts
 * and maintain consistent styling with Material 3 guidelines.
 */

// ============================================================================
// RENAME GROUP DIALOG
// ============================================================================

/**
 * Dialog for renaming a group/island.
 * Validates that the name is not blank before allowing confirmation.
 *
 * Features:
 * - Pre-fills with current name
 * - Single-line text input
 * - Validates non-blank input
 * - Confirm/Cancel actions
 *
 * @param currentName The current name of the group
 * @param onConfirm Callback with new name when user confirms
 * @param onDismiss Callback when dialog is dismissed without confirming
 */
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

// ============================================================================
// COLOR PICKER DIALOG
// ============================================================================

/**
 * Dialog for picking a color for a group/island.
 * Shows a grid of 16 predefined Material Design colors.
 * Highlights the currently selected color with a white border.
 *
 * Features:
 * - 4x4 grid of colors
 * - Visual indication of current selection
 * - Immediate selection on tap
 * - Predefined Material Design color palette
 *
 * Color Palette:
 * - Red, Pink, Purple, Deep Purple
 * - Indigo, Blue, Light Blue, Cyan
 * - Teal, Green, Light Green, Lime
 * - Yellow, Amber, Orange, Deep Orange
 *
 * @param currentColor The current color of the group (as Int)
 * @param onConfirm Callback with selected color (as Int) when user picks a color
 * @param onDismiss Callback when dialog is dismissed without picking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    currentColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Material Design color palette - 16 colors
    val colors = remember {
        listOf(
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
    }

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

// ============================================================================
// PROFILE PICKER DIALOG
// ============================================================================

/**
 * Dialog for selecting a profile to move tabs/groups to.
 * Shows a list of available profiles for the user to choose from.
 *
 * Features:
 * - Lists all available profiles
 * - Simple tap-to-select interaction
 * - Cancel option
 *
 * TODO: Integration Points
 * - Connect to actual ProfileManager to get real profile list
 * - Show current profile indicator
 * - Add profile icons/avatars
 * - Handle profile creation if needed
 *
 * @param onConfirm Callback with selected profile ID when user picks a profile
 * @param onDismiss Callback when dialog is dismissed without picking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // TODO: Get actual profiles from ProfileManager
    // This is a placeholder implementation with hardcoded profiles
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
                            .clickable {
                                onConfirm(profileId)
                                onDismiss()
                            }
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

// ============================================================================
// FUTURE DIALOG COMPONENTS
// ============================================================================

/**
 * Potential future dialogs to implement:
 *
 * 1. AddToGroupDialog:
 *    - Shows list of existing groups to add a tab to
 *    - Option to create new group
 *
 * 2. MergeGroupsDialog:
 *    - Confirmation dialog when merging two groups
 *    - Shows preview of combined tabs
 *
 * 3. CloseAllTabsDialog:
 *    - Confirmation dialog for destructive actions
 *    - Shows count of tabs to be closed
 *
 * 4. ExportGroupDialog:
 *    - Options for exporting group (bookmarks, sharing, etc.)
 *    - Format selection
 *
 * 5. GroupDetailsDialog:
 *    - Detailed view of group statistics
 *    - Tab list, memory usage, etc.
 */
