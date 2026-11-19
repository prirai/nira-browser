package com.prirai.android.nira.downloads

import androidx.compose.runtime.Composable
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.components.NiraWindowDialogFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.lib.state.ext.flow

/** Floating downloads window, shown over both the main browser and custom tabs. */
class DownloadsDialogFragment : NiraWindowDialogFragment() {

    @Composable
    override fun WindowContent() {
        DownloadsScreen(onDismiss = { dismiss() })
    }

    companion object {
        const val TAG = "DownloadsDialog"

        fun show(fragmentManager: FragmentManager) {
            showOnce(fragmentManager, TAG) { DownloadsDialogFragment() }
        }

        /**
         * Opens the window when a download starts. Downloads wait in INITIATED state for the
         * user's confirmation (see [DownloadConfirmationMiddleware]), so every surface that can
         * start a download - including custom tabs - must call this.
         */
        fun showOnNewDownloads(fragment: Fragment) {
            val shown = mutableSetOf<String>()
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                fragment.requireContext().components.store.flow()
                    .map { state ->
                        state.downloads.values.filter {
                            it.status == DownloadState.Status.INITIATED ||
                                it.status == DownloadState.Status.DOWNLOADING
                        }
                    }
                    .distinctUntilChanged()
                    .collect { active ->
                        val fresh = active.filter { it.id !in shown }
                        if (fresh.isNotEmpty()) {
                            fresh.forEach { shown.add(it.id) }
                            if (fragment.isAdded && fragment.activity != null) {
                                show(fragment.parentFragmentManager)
                            }
                        }
                    }
            }
        }
    }
}
