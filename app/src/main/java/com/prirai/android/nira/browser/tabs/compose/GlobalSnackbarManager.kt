package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Global Snackbar Manager
 *
 * Provides a centralized, action-based snackbar system that can be accessed from anywhere
 * in the application (Activity, Fragment, ViewModel, Composables).
 *
 * Features:
 * - Single snackbar instance across the entire app
 * - Action-based (not context-based) - appears above all UI elements
 * - Supports undo actions with callbacks
 * - Automatic timeout handling
 * - Thread-safe singleton pattern
 *
 * Usage:
 * ```kotlin
 * // In your root Composable (e.g., BrowserActivity or TabsBottomSheetFragment)
 * val snackbarManager = GlobalSnackbarManager.getInstance()
 * val snackbarHostState = snackbarManager.snackbarHostState
 *
 * Scaffold(
 *     snackbarHost = { SnackbarHost(snackbarHostState) }
 * ) {
 *     // Your content
 * }
 *
 * // From anywhere in the app:
 * GlobalSnackbarManager.getInstance().showSnackbar(
 *     message = "Tab closed",
 *     actionLabel = "Undo",
 *     onAction = { /* undo logic */ },
 *     onDismiss = { /* confirm deletion */ }
 * )
 * ```
 */
class GlobalSnackbarManager private constructor() {

    /**
     * The SnackbarHostState that should be observed by the UI
     */
    val snackbarHostState = SnackbarHostState()

    /**
     * Coroutine scope for snackbar operations
     * Should be set by the UI layer (Activity/Fragment)
     */
    var coroutineScope: CoroutineScope? = null
        set(value) {
            field = value
            if (value == null) {
                currentSnackbarJob?.cancel()
                currentSnackbarJob = null
            }
        }

    private var currentSnackbarJob: Job? = null
    private var currentOnDismiss: (() -> Unit)? = null

    /**
     * Show a snackbar with optional action
     *
     * @param message The message to display
     * @param actionLabel Optional action button label (e.g., "Undo")
     * @param duration How long to show the snackbar
     * @param onAction Callback when action button is clicked
     * @param onDismiss Callback when snackbar is dismissed (timeout or swipe)
     */
    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Long,
        onAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val scope = coroutineScope ?: run {
            android.util.Log.w(TAG, "Cannot show snackbar: coroutineScope not set")
            return
        }

        // Cancel any existing snackbar
        currentSnackbarJob?.cancel()
        currentOnDismiss = onDismiss

        currentSnackbarJob = scope.launch {
            try {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    duration = duration,
                    withDismissAction = true // Show X button
                )

                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        // User clicked the action button (e.g., "Undo")
                        val callback = currentOnDismiss
                        currentOnDismiss = null // Clear before invoking to prevent re-entry
                        onAction?.invoke()
                        // Don't call onDismiss since action was performed
                    }

                    SnackbarResult.Dismissed -> {
                        // User dismissed or timeout occurred
                        val callback = currentOnDismiss
                        currentOnDismiss = null // Clear before invoking
                        callback?.invoke()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job was cancelled (e.g., new snackbar shown) - don't invoke dismiss callback
                android.util.Log.d(TAG, "Snackbar cancelled")
                throw e // Re-throw to propagate cancellation
            } catch (e: Exception) {
                // Other errors - invoke dismiss callback
                android.util.Log.e(TAG, "Error showing snackbar", e)
                val callback = currentOnDismiss
                currentOnDismiss = null
                callback?.invoke()
            } finally {
                currentSnackbarJob = null
            }
        }
    }

    /**
     * Show a simple informational snackbar (no action)
     */
    fun showInfo(message: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        showSnackbar(
            message = message,
            actionLabel = null,
            duration = duration,
            onAction = null,
            onDismiss = null
        )
    }

    /**
     * Show an undo snackbar (common pattern for destructive actions)
     *
     * @param message The message to display (e.g., "Tab closed")
     * @param onUndo Callback when user clicks "Undo"
     * @param onConfirm Callback when action is confirmed (timeout or dismiss)
     * @param duration How long to show before auto-confirming
     */
    fun showUndoSnackbar(
        message: String,
        onUndo: () -> Unit,
        onConfirm: () -> Unit,
        duration: SnackbarDuration = SnackbarDuration.Long
    ) {
        showSnackbar(
            message = message,
            actionLabel = "Undo",
            duration = duration,
            onAction = onUndo,
            onDismiss = onConfirm
        )
    }

    /**
     * Dismiss the current snackbar
     */
    fun dismiss() {
        currentSnackbarJob?.cancel()
        currentSnackbarJob = null
        currentOnDismiss?.invoke()
        currentOnDismiss = null

        coroutineScope?.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    /**
     * Clear any pending callbacks without showing dismiss animation
     */
    fun clearCallbacks() {
        currentOnDismiss = null
    }

    companion object {
        private const val TAG = "GlobalSnackbarManager"

        @Volatile
        private var instance: GlobalSnackbarManager? = null

        /**
         * Get the singleton instance
         */
        fun getInstance(): GlobalSnackbarManager {
            return instance ?: synchronized(this) {
                instance ?: GlobalSnackbarManager().also { instance = it }
            }
        }

        /**
         * Reset the singleton (mainly for testing)
         */
        @Suppress("unused")
        fun reset() {
            synchronized(this) {
                instance?.dismiss()
                instance = null
            }
        }
    }
}

/**
 * Extension function for easy access from ViewModels
 */
fun showGlobalSnackbar(
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Long,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    GlobalSnackbarManager.getInstance().showSnackbar(
        message = message,
        actionLabel = actionLabel,
        duration = duration,
        onAction = onAction,
        onDismiss = onDismiss
    )
}

/**
 * Extension function for undo pattern
 */
fun showUndoSnackbar(
    message: String,
    onUndo: () -> Unit,
    onConfirm: () -> Unit
) {
    GlobalSnackbarManager.getInstance().showUndoSnackbar(
        message = message,
        onUndo = onUndo,
        onConfirm = onConfirm
    )
}
