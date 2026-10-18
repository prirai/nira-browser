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
 * Centralized, action-based snackbar system. Appears above all UI
 * elements regardless of which composable requested it, which is useful
 * for destructive-action feedback (undo tab close, etc.).
 *
 * Thread-safe singleton. The host composable observes [snackbarHostState]
 * and the Activity/Fragment lifetime scope is injected via
 * [coroutineScope].
 *
 * The only call-site today is `TabViewModel.showUndoSnackbar(...)` for
 * the tab-close undo flow. Dead helpers (`showInfo`, `dismiss()`,
 * `clearCallbacks()`, companion `reset()`, top-level
 * `showGlobalSnackbar` / `showUndoSnackbar` extensions) were removed
 * - every one of them had zero callers outside this file.
 */
class GlobalSnackbarManager private constructor() {

    /** SnackbarHostState the UI layer binds to a `SnackbarHost`. */
    val snackbarHostState = SnackbarHostState()

    /**
     * Coroutine scope for snackbar operations. Must be set by the UI
     * layer (Activity/Fragment) before the first [showUndoSnackbar]
     * call; setting it to `null` cancels any in-flight snackbar.
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
     * Internal: show a snackbar with optional action. Kept private
     * because the only public shape used today is [showUndoSnackbar].
     */
    private fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Long,
        onAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
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
                    withDismissAction = true, // Show X button
                )

                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        currentOnDismiss = null
                        onAction?.invoke()
                    }
                    SnackbarResult.Dismissed -> {
                        val callback = currentOnDismiss
                        currentOnDismiss = null
                        callback?.invoke()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d(TAG, "Snackbar cancelled")
                throw e
            } catch (e: Exception) {
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
     * Show an undo snackbar (used for destructive actions like tab-close).
     *
     * @param message e.g. "Tab closed"
     * @param onUndo Callback when the user taps "Undo"
     * @param onConfirm Callback when the snackbar is dismissed without undo
     */
    fun showUndoSnackbar(
        message: String,
        onUndo: () -> Unit,
        onConfirm: () -> Unit,
        duration: SnackbarDuration = SnackbarDuration.Long,
    ) {
        showSnackbar(
            message = message,
            actionLabel = "Undo",
            duration = duration,
            onAction = onUndo,
            onDismiss = onConfirm,
        )
    }

    companion object {
        private const val TAG = "GlobalSnackbarManager"

        @Volatile
        private var instance: GlobalSnackbarManager? = null

        fun getInstance(): GlobalSnackbarManager {
            return instance ?: synchronized(this) {
                instance ?: GlobalSnackbarManager().also { instance = it }
            }
        }
    }
}
