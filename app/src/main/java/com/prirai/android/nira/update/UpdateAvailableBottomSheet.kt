package com.prirai.android.nira.update

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Material 3 bottom sheet shown when the auto-updater has a newer
 * release ready. Owned by [com.prirai.android.nira.BrowserActivity];
 * see its `onResume` for the show-once-per-pending-notify logic.
 *
 * # Flow
 *   1. Fragment inflates with the pending manifest rehydrated from
 *      [UserPreferences.updatePendingManifestJson].
 *   2. User taps **Install** -> we flip the "notify consumed" flag,
 *      run [UpdateDownloader], then hand the resulting APK to
 *      [UpdateInstaller].
 *   3. User taps **Later** -> dismiss without touching prefs; the
 *      next worker tick or the next cold start will re-show.
 *   4. User taps **Skip this version** -> write
 *      [UserPreferences.updateSkippedVersionCode] to this manifest's
 *      versionCode so the worker stops notifying for it; also clear
 *      the pending flag so the sheet doesn't come back on resume.
 *
 * # Why not download in a Worker
 * The user has just said "yes install" so UI foreground is already
 * acceptable; wrapping in another CoroutineWorker would add a
 * notification channel + system-service overhead for a 30s task
 * that already has a foreground sheet to render progress in. Keep
 * it simple: lifecycleScope + Dispatchers.IO.
 */
internal class UpdateAvailableBottomSheet : BottomSheetDialogFragment() {

    private var downloader: UpdateDownloader? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(
            R.layout.fragment_update_available_bottom_sheet,
            container,
            false,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = UserPreferences(requireContext())
        val manifestJson = prefs.updatePendingManifestJson
        val manifest = UpdateManifest.fromCachedJson(manifestJson)
        val asset = manifest?.let {
            pickAssetForCurrentDevice(it)
        }

        if (manifest == null || asset == null) {
            // Nothing to show; dismiss and clear the pending flag.
            prefs.updatePendingNotify = false
            prefs.updatePendingManifestJson = ""
            dismissAllowingStateLoss()
            return
        }

        val versionChip = view.findViewById<Chip>(R.id.update_version_chip)
        val sizeLabel = view.findViewById<TextView>(R.id.update_size_label)
        val notes = view.findViewById<TextView>(R.id.update_release_notes)
        val progress = view.findViewById<LinearProgressIndicator>(R.id.update_progress)
        val statusLabel = view.findViewById<TextView>(R.id.update_status_label)
        val installBtn = view.findViewById<MaterialButton>(R.id.update_install_button)
        val laterBtn = view.findViewById<MaterialButton>(R.id.update_later_button)
        val skipBtn = view.findViewById<MaterialButton>(R.id.update_skip_button)

        versionChip.text = manifest.tagName
        sizeLabel.text = humanReadableBytes(asset.sizeBytes)
        notes.text = manifest.body.ifBlank { getString(R.string.update_sheet_no_release_notes) }

        installBtn.setOnClickListener {
            // Flip the notify flag now so swipe-dismiss during download
            // does not loop back to the sheet on next resume.
            prefs.updatePendingNotify = false
            startInstallFlow(
                manifest = manifest,
                asset = asset,
                installBtn = installBtn,
                laterBtn = laterBtn,
                skipBtn = skipBtn,
                progress = progress,
                statusLabel = statusLabel,
            )
        }

        laterBtn.setOnClickListener {
            // Keep updatePendingNotify = true so a later cold start
            // still re-surfaces this update, but dismiss the sheet
            // for now.
            dismiss()
        }

        skipBtn.setOnClickListener {
            manifest.versionCode?.let { v -> prefs.updateSkippedVersionCode = v }
            prefs.updatePendingNotify = false
            prefs.updatePendingManifestJson = ""
            dismiss()
        }
    }

    override fun onDestroyView() {
        downloader = null
        super.onDestroyView()
    }

    private fun startInstallFlow(
        manifest: UpdateManifest,
        asset: UpdateAsset,
        installBtn: MaterialButton,
        laterBtn: MaterialButton,
        skipBtn: MaterialButton,
        progress: LinearProgressIndicator,
        statusLabel: TextView,
    ) {
        installBtn.isEnabled = false
        laterBtn.isEnabled = false
        skipBtn.isEnabled = false
        progress.visibility = View.VISIBLE
        statusLabel.visibility = View.VISIBLE
        statusLabel.text = getString(R.string.update_sheet_status_starting)
        isCancelable = false

        val dl = UpdateDownloader()
        downloader = dl

        // Live progress -> status text + progress bar.
        dl.progress.onEach { fraction ->
            val pct = (fraction * 100f).toInt().coerceIn(0, 100)
            progress.progress = pct
            statusLabel.text = getString(R.string.update_sheet_status_downloading, pct)
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                dl.download(
                    context = requireContext().applicationContext,
                    asset = asset,
                    tagName = manifest.tagName,
                )
            }
            when (result) {
                is UpdateDownloader.DownloadResult.Success -> {
                    statusLabel.text = getString(R.string.update_sheet_status_installing)
                    val ok = UpdateInstaller.installApk(
                        requireContext(),
                        result.apk,
                    )
                    if (!ok) {
                        resetButtons(installBtn, laterBtn, skipBtn, progress, statusLabel)
                        statusLabel.visibility = View.VISIBLE
                        statusLabel.text = getString(R.string.update_sheet_status_install_failed)
                        Toast.makeText(
                            requireContext(),
                            R.string.update_sheet_status_install_failed,
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        // Keep the sheet visible briefly so the user
                        // can see progress completed before the system
                        // installer UI takes over.
                        dismiss()
                    }
                }
                is UpdateDownloader.DownloadResult.Error -> {
                    resetButtons(installBtn, laterBtn, skipBtn, progress, statusLabel)
                    statusLabel.visibility = View.VISIBLE
                    statusLabel.text = getString(
                        R.string.update_sheet_status_download_error,
                        result.reason,
                    )
                }
            }
        }
    }

    private fun resetButtons(
        installBtn: MaterialButton,
        laterBtn: MaterialButton,
        skipBtn: MaterialButton,
        progress: LinearProgressIndicator,
        statusLabel: TextView,
    ) {
        installBtn.isEnabled = true
        laterBtn.isEnabled = true
        skipBtn.isEnabled = true
        progress.visibility = View.GONE
        isCancelable = true
    }

    private fun pickAssetForCurrentDevice(manifest: UpdateManifest): UpdateAsset? {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val wanted = when (abi) {
            "arm64-v8a" -> "arm64-v8a"
            "armeabi-v7a" -> "armeabi-v7a"
            "x86_64" -> "x86_64"
            else -> return null
        }
        return manifest.assets.firstOrNull {
            it.name.contains(wanted) && it.name.endsWith(".apk", ignoreCase = true)
        }
    }

    /**
     * Human-readable byte count. Decimal SI (1 MB = 1,000,000 B)
     * because that matches what GitHub shows on the release page
     * and avoids an off-by-megabyte mismatch between the sheet and
     * the browser-rendered release.
     */
    private fun humanReadableBytes(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var idx = 0
        while (value >= 1000.0 && idx < units.lastIndex) {
            value /= 1000.0
            idx++
        }
        return if (idx == 0) {
            "$bytes B"
        } else {
            String.format(java.util.Locale.US, "%.1f %s", value, units[idx])
        }
    }

    companion object {
        const val TAG = "UpdateAvailableBottomSheet"
        fun newInstance(): UpdateAvailableBottomSheet = UpdateAvailableBottomSheet()
    }
}
