package com.prirai.android.nira.ui.components

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView

/**
 * Detaches a ComposeView that was added directly to a view hierarchy and
 * disposes its composition. Removing the view alone is not enough with
 * `DisposeOnViewTreeLifecycleDestroyed`: the composition (and any Compose
 * `Dialog` window it owns) stays alive and keeps swallowing touches.
 */
fun ComposeView.removeAndDispose() {
    disposeComposition()
    (parent as? ViewGroup)?.removeView(this)
}
