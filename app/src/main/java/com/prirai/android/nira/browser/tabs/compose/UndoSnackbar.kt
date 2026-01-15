package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable Undo Snackbar Component
 *
 * This component provides a Material 3 snackbar with undo functionality,
 * designed for tab deletion but reusable for any undo-able action.
 *
 * Features:
 * - Automatic dismissal after timeout
 * - Manual undo action
 * - Manual dismiss action
 * - Clean Material 3 design
 *
 * Usage:
 * ```
 * val undoState by viewModel.showUndoSnackbar.collectAsState()
 *
 * undoState?.let {
 *     UndoSnackbar(
 *         state = it,
 *         modifier = Modifier.align(Alignment.BottomCenter)
 *     )
 * }
 * ```
 */

/**
 * State for undo snackbar
 */
data class UndoSnackbarState(
    val message: String,
    val onUndo: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * Undo snackbar composable
 *
 * Displays a snackbar with message, undo button, and dismiss action.
 * Positioned at the bottom of the screen with appropriate padding.
 *
 * @param state The snackbar state containing message and callbacks
 * @param modifier Optional modifier for positioning
 */
@Composable
fun UndoSnackbar(
    state: UndoSnackbarState,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier.padding(16.dp),
        action = {
            TextButton(onClick = state.onUndo) {
                Text(
                    text = "UNDO",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissAction = {
            IconButton(
                onClick = state.onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionContentColor = MaterialTheme.colorScheme.inversePrimary
    ) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Undo snackbar host for managing multiple snackbars
 *
 * Use this when you need to show snackbars in a specific area of your UI.
 * It provides a SnackbarHost that automatically shows/dismisses based on state.
 *
 * @param undoState The current undo state (null if no snackbar to show)
 * @param modifier Optional modifier for positioning
 */
@Composable
fun UndoSnackbarHost(
    undoState: UndoSnackbarState?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        undoState?.let { state ->
            UndoSnackbar(
                state = state,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

/**
 * Example usage in a composable:
 *
 * ```kotlin
 * @Composable
 * fun MyTabScreen(viewModel: TabViewModel) {
 *     val undoState by viewModel.showUndoSnackbar.collectAsState()
 *
 *     Box(modifier = Modifier.fillMaxSize()) {
 *         // Your tab content here
 *         TabContent()
 *
 *         // Snackbar overlay
 *         undoState?.let {
 *             UndoSnackbar(
 *                 state = it,
 *                 modifier = Modifier.align(Alignment.BottomCenter)
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * Or use the host version:
 *
 * ```kotlin
 * Box(modifier = Modifier.fillMaxSize()) {
 *     TabContent()
 *     UndoSnackbarHost(undoState = undoState)
 * }
 * ```
 */
