@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.update

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraActionButton
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Material 3 Expressive bottom sheet shown when the auto-updater has a
 * newer release ready. Owned by [com.prirai.android.nira.BrowserActivity];
 * see its `onResume` for the show-once-per-pending-notify logic.
 *
 * # Flow
 *   1. Fragment resolves the pending manifest rehydrated from
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

    private data class SheetContent(
        val manifest: UpdateManifest,
        val asset: UpdateAsset,
        val sizeLabel: String,
        val notes: String
    )

    private var downloader: UpdateDownloader? = null

    private val contentState = mutableStateOf<SheetContent?>(null)
    private val buttonsEnabledState = mutableStateOf(true)

    /** Downloaded fraction, or null while no download is in flight (the bar is hidden then). */
    private val progressState = mutableStateOf<Float?>(null)
    private val statusTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val context = LocalContext.current
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors
            ) {
                val content = contentState.value
                if (content != null) {
                    UpdateAvailableSheetContent(
                        tagName = content.manifest.tagName,
                        sizeLabel = content.sizeLabel,
                        notes = content.notes,
                        installEnabled = buttonsEnabledState.value,
                        progress = progressState.value,
                        statusText = statusTextState.value,
                        onInstall = { onInstallClicked(content) },
                        onLater = { dismiss() },
                        onSkip = { onSkipClicked(content.manifest) },
                        onClose = { dismiss() }
                    )
                }
            }
        }
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

        contentState.value = SheetContent(
            manifest = manifest,
            asset = asset,
            sizeLabel = humanReadableBytes(asset.sizeBytes),
            notes = manifest.body.ifBlank { getString(R.string.update_sheet_no_release_notes) }
        )
    }

    override fun onDestroyView() {
        downloader = null
        super.onDestroyView()
    }

    private fun onInstallClicked(content: SheetContent) {
        // Flip the notify flag now so swipe-dismiss during download
        // does not loop back to the sheet on next resume.
        UserPreferences(requireContext()).updatePendingNotify = false
        startInstallFlow(
            manifest = content.manifest,
            asset = content.asset
        )
    }

    private fun onSkipClicked(manifest: UpdateManifest) {
        val prefs = UserPreferences(requireContext())
        manifest.versionCode?.let { v -> prefs.updateSkippedVersionCode = v }
        prefs.updatePendingNotify = false
        prefs.updatePendingManifestJson = ""
        dismiss()
    }

    private fun startInstallFlow(
        manifest: UpdateManifest,
        asset: UpdateAsset
    ) {
        buttonsEnabledState.value = false
        progressState.value = 0f
        statusTextState.value = getString(R.string.update_sheet_status_starting)
        isCancelable = false

        val dl = UpdateDownloader()
        downloader = dl

        // Live progress -> status text + progress bar.
        dl.progress.onEach { fraction ->
            val pct = (fraction * 100f).toInt().coerceIn(0, 100)
            progressState.value = fraction.coerceIn(0f, 1f)
            statusTextState.value = getString(R.string.update_sheet_status_downloading, pct)
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                dl.download(
                    context = requireContext().applicationContext,
                    asset = asset,
                    tagName = manifest.tagName
                )
            }
            when (result) {
                is UpdateDownloader.DownloadResult.Success -> {
                    statusTextState.value = getString(R.string.update_sheet_status_installing)
                    val ok = UpdateInstaller.installApk(
                        requireContext(),
                        result.apk
                    )
                    if (!ok) {
                        resetAfterFailure()
                        statusTextState.value = getString(R.string.update_sheet_status_install_failed)
                        Toast.makeText(
                            requireContext(),
                            R.string.update_sheet_status_install_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // Keep the sheet visible briefly so the user
                        // can see progress completed before the system
                        // installer UI takes over.
                        dismiss()
                    }
                }
                is UpdateDownloader.DownloadResult.Error -> {
                    resetAfterFailure()
                    statusTextState.value = getString(
                        R.string.update_sheet_status_download_error,
                        result.reason
                    )
                }
            }
        }
    }

    private fun resetAfterFailure() {
        buttonsEnabledState.value = true
        progressState.value = null
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

@Composable
private fun UpdateAvailableSheetContent(
    tagName: String,
    sizeLabel: String,
    notes: String,
    installEnabled: Boolean,
    progress: Float?,
    statusText: String?,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        NiraSheetHeader(
            title = stringResource(R.string.update_sheet_title),
            onClose = onClose,
            dividerAlpha = null
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                ) {
                    Text(
                        text = tagName,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (sizeLabel.isNotBlank()) {
                    Text(
                        text = sizeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.update_sheet_release_notes_label),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (progress != null) {
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            statusText?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            NiraActionButton(
                text = stringResource(R.string.update_sheet_install),
                onClick = onInstall,
                enabled = installEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onLater,
                enabled = installEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.update_sheet_later))
            }
            TextButton(
                onClick = onSkip,
                enabled = installEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.update_sheet_skip_this_version))
            }
        }
    }
}
