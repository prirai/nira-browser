package com.prirai.android.nira.update

import android.content.Context
import android.util.Log
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import java.io.File

/**
 * Streams an update [UpdateAsset] into Nira's cache directory and
 * reports progress via a [StateFlow] so the UI can draw a bar.
 *
 * One instance per download. Not thread-safe to invoke [download]
 * twice concurrently on the same instance; callers should create a
 * fresh instance per attempt (the sheet UI does this - "retry"
 * creates a new downloader).
 *
 * # Storage layout
 * `cacheDir/updates/<tag>-<abi>.apk`
 *
 * We use cacheDir rather than filesDir because:
 *   - Android may evict it under pressure; the user can always
 *     re-trigger the download. filesDir would persist a stale APK
 *     forever if the install flow was never completed.
 *   - cacheDir is scoped per-app, so the APK is only readable by
 *     Nira until we grant URI permission to the package installer
 *     via FileProvider (see UpdateInstaller).
 *
 * # Progress
 * [progress] emits values in `[0f, 1f]`. On start the flow is `0f`;
 * on successful completion it reaches `1f`. On failure it stays
 * wherever it was when the exception fired - callers compare
 * against the sealed [DownloadResult] from [download] instead.
 */
internal class UpdateDownloader {

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    /**
     * Download [asset] into Nira's cache. Returns a sealed result;
     * never throws. All IO happens on [Dispatchers.IO] regardless
     * of the dispatcher the caller is on.
     */
    suspend fun download(
        context: Context,
        asset: UpdateAsset,
        tagName: String,
    ): DownloadResult = withContext(Dispatchers.IO) {
        _progress.value = 0f

        val targetDir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        val abiSuffix = asset.name
            .removeSuffix(".apk")
            .removeSuffix(".APK")
        val targetFile = File(targetDir, "$tagName-$abiSuffix.apk")

        // Clean any prior partial download for the same filename to
        // guarantee a fresh stream (we are not doing resumable
        // downloads here - the asset is small enough that redoing
        // the full fetch is simpler than tracking Range + sidecars).
        if (targetFile.exists()) {
            if (!targetFile.delete()) {
                Log.w(TAG, "could not delete prior partial file ${targetFile.path}")
            }
        }

        val headers = MutableHeaders().apply {
            append("Accept", "application/octet-stream")
            append("User-Agent", "Nira-browser-update-downloader")
        }
        val request = Request(
            url = asset.downloadUrl,
            headers = headers,
            method = Request.Method.GET,
            useCaches = false,
        )

        try {
            context.components.client.fetch(request).use { response ->
                if (response.status !in 200..299) {
                    return@withContext DownloadResult.Error("HTTP ${response.status}")
                }
                response.body.useStream { input: java.io.InputStream ->
                    targetFile.outputStream().use { out ->
                        val buf = ByteArray(BUFFER_BYTES)
                        var written = 0L
                        var lastReported = 0f
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n == 0) continue
                            out.write(buf, 0, n)
                            written += n
                            if (asset.sizeBytes > 0) {
                                val fraction = (written.toFloat() / asset.sizeBytes.toFloat())
                                    .coerceIn(0f, 1f)
                                // Dedupe rapid progress emits that
                                // the UI cannot distinguish anyway;
                                // compose will skip-work on the same
                                // value but allocating a Float on
                                // every ~64 KiB read is wasteful.
                                if (fraction - lastReported >= PROGRESS_EMIT_STEP) {
                                    _progress.value = fraction
                                    lastReported = fraction
                                }
                            }
                        }
                        out.flush()
                        out.fd.sync()
                    }
                }
            }
            _progress.value = 1f
        } catch (t: Throwable) {
            targetFile.delete()
            Log.w(TAG, "download failed", t)
            return@withContext DownloadResult.Error(t.message ?: "download error")
        }

        if (asset.sizeBytes > 0 && targetFile.length() != asset.sizeBytes) {
            targetFile.delete()
            return@withContext DownloadResult.Error(
                "size mismatch: expected ${asset.sizeBytes}, got ${targetFile.length()}",
            )
        }

        Log.i(TAG, "downloaded ${targetFile.path} (${targetFile.length()}B)")
        DownloadResult.Success(targetFile)
    }

    /** Thin result type so the UI can [when]-dispatch cleanly. */
    sealed class DownloadResult {
        data class Success(val apk: File) : DownloadResult()
        data class Error(val reason: String) : DownloadResult()
    }

    companion object {
        private const val TAG = "NiraUpdateDownload"
        private const val UPDATES_DIR = "updates"
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_EMIT_STEP = 0.01f

        /**
         * Scrub any `updates/` files that aren't currently in use.
         * Call from a non-UI path (e.g. the periodic worker) if the
         * subsystem has been disabled or after a successful install
         * so we don't leak APK blobs in the cache.
         */
        fun cleanAll(context: Context) {
            val dir = File(context.cacheDir, UPDATES_DIR)
            if (!dir.exists()) return
            try {
                dir.listFiles()?.forEach { f ->
                    try {
                        f.delete()
                    } catch (t: Throwable) {
                        Log.w(TAG, "cleanAll: could not delete ${f.path}", t)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "cleanAll failed", t)
            }
        }
    }
}


