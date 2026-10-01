package com.prirai.android.nira.downloads

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID

/**
 * Routes a user-initiated download to either [DownloadService] (serial,
 * mozilla-components default) or [ParallelDownloadService]
 * (multi-threaded range downloads).
 *
 * # Routing contract
 * A download is eligible for the parallel path iff ALL of:
 *   1. URL scheme is `http(s)` (ParallelDownloadService uses raw
 *      `Request` fetches; data:, blob:, and file: cannot be range-
 *      fetched).
 *   2. Either `download.contentLength >= PARALLEL_MIN_BYTES` (8 MiB) OR
 *      a sidecar directory from an earlier attempt exists (resume
 *      case).
 *
 * `contentLength` is often null on first attempt because GeckoView only
 * fills it in after the response headers arrive, and [DownloadsFeature]
 * dispatches `AddDownloadAction` right after the request completes - at
 * which point the length may or may not be present. [start] handles the
 * `contentLength == null` case by firing an async HEAD-style
 * `Range: bytes=0-0` probe and re-routing based on the result; while
 * the probe runs we start the serial path so the user never sees a
 * stall.
 */
object DownloadController {

    private const val TAG = "DownloadController"

    const val PARALLEL_MIN_BYTES = 8L * 1024 * 1024
    const val PARALLEL_THREADS = 4

    const val ACTION_PAUSE = "com.prirai.android.nira.downloads.PAUSE"
    const val ACTION_RESUME = "com.prirai.android.nira.downloads.RESUME"
    const val ACTION_CANCEL = "com.prirai.android.nira.downloads.CANCEL"

    /**
     * Deterministic routing used when we already know
     * `download.contentLength`. Returns true when the download is a
     * good candidate for [ParallelDownloadService].
     */
    fun shouldUseParallel(context: Context, download: DownloadState): Boolean {
        val length = download.contentLength ?: 0L
        val http = download.url.startsWith("http://") || download.url.startsWith("https://")
        return http && (
            length >= PARALLEL_MIN_BYTES ||
                ParallelDownloadService.hasSidecar(context, download)
            )
    }

    /**
     * Decide whether to use parallel downloads for [download]. If the
     * content length is already known (or a sidecar exists), the
     * routing is deterministic. Otherwise we return [Deferred]-ish
     * `null` so the caller can start the serial path immediately and
     * let [start] probe the server asynchronously.
     */
    fun start(context: Context, download: DownloadState) {
        val app = context.applicationContext
        val http = download.url.startsWith("http://") || download.url.startsWith("https://")

        if (!http) {
            startService(app, DownloadService::class.java, download.id)
            return
        }

        if (shouldUseParallel(app, download)) {
            startService(app, ParallelDownloadService::class.java, download.id)
            return
        }

        // Content-Length unknown and no sidecar. GeckoView frequently
        // reports null `contentLength` here, especially for CDN links
        // and APKs served with chunked transfer encoding. Probe the
        // server with a 1-byte range request; if the server advertises
        // a total >= PARALLEL_MIN_BYTES, switch to the parallel path.
        //
        // This runs on a short-lived IO coroutine, not on the service's
        // own job graph, so a slow probe doesn't block the serial path
        // we start immediately below.
        if (download.contentLength == null) {
            CoroutineScope(Dispatchers.IO).launch {
                val probed = probeContentLength(app, download)
                if (probed != null && probed >= PARALLEL_MIN_BYTES) {
                    Log.i(TAG, "Probe found length=$probed for ${download.id}; switching to parallel")
                    // Cancel the serial job we started below. The
                    // standard AbstractFetchDownloadService ACTION_CANCEL
                    // tears down the FetchDownloadManager-owned
                    // download. Then update the state's contentLength
                    // and start the parallel service.
                    app.sendBroadcast(
                        Intent(AbstractFetchDownloadService.ACTION_CANCEL).apply {
                            setPackage(app.packageName)
                            putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
                        }
                    )
                    val current = app.components.store.state.downloads[download.id] ?: download
                    app.components.store.dispatch(
                        DownloadAction.UpdateDownloadAction(current.copy(contentLength = probed))
                    )
                    startService(app, ParallelDownloadService::class.java, download.id)
                }
            }
        }

        // Start the serial path immediately so the user sees progress
        // right away. The probe (if any) can hand off later.
        startService(app, DownloadService::class.java, download.id)
    }

    /**
     * Fired from inside [ParallelDownloadService] when the probe inside
     * it (not the one in [start]) determines parallel isn't viable
     * - e.g. the server doesn't support Range requests. Hands the
     * download to the serial path without touching broadcast fan-out.
     */
    fun startFallback(context: Context, downloadId: String) {
        startService(context.applicationContext, DownloadService::class.java, downloadId)
    }

    fun pause(context: Context, downloadId: String) {
        val app = context.applicationContext
        app.sendBroadcast(
            Intent(AbstractFetchDownloadService.ACTION_PAUSE).apply {
                setPackage(app.packageName)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, downloadId)
            }
        )
        app.sendBroadcast(
            Intent(ACTION_PAUSE).apply {
                setPackage(app.packageName)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, downloadId)
            }
        )
    }

    fun resume(context: Context, download: DownloadState) {
        val app = context.applicationContext
        if (shouldUseParallel(app, download)) {
            app.sendBroadcast(
                Intent(ACTION_RESUME).apply {
                    setPackage(app.packageName)
                    putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
                }
            )
            startService(app, ParallelDownloadService::class.java, download.id)
        } else {
            app.sendBroadcast(
                Intent(AbstractFetchDownloadService.ACTION_RESUME).apply {
                    setPackage(app.packageName)
                    putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
                }
            )
            startService(app, DownloadService::class.java, download.id)
        }
    }

    fun cancel(context: Context, download: DownloadState) {
        val app = context.applicationContext
        app.sendBroadcast(
            Intent(AbstractFetchDownloadService.ACTION_CANCEL).apply {
                setPackage(app.packageName)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
            }
        )
        app.sendBroadcast(
            Intent(ACTION_CANCEL).apply {
                setPackage(app.packageName)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, download.id)
            }
        )
        app.components.store.dispatch(DownloadAction.RemoveDownloadAction(download.id))
    }

    /**
     * HEAD-style probe used by [start] when `contentLength` is unknown.
     * Fetches `Range: bytes=0-0`; a 206 response's `Content-Range`
     * header carries the total size as `bytes 0-0/<total>`. Returns
     * null if the server does not advertise Range support, the probe
     * errors, or the total can't be parsed.
     */
    private fun probeContentLength(context: Context, download: DownloadState): Long? {
        return try {
            val headers = MutableHeaders()
            headers.append("Range", "bytes=0-0")
            val request = Request(
                url = download.url,
                headers = headers,
                private = download.private,
                referrerUrl = download.referrerUrl,
            )
            context.components.client.fetch(request).use { response ->
                if (response.status != 206) return null
                ParallelDownloadService.parseTotal(response.headers["Content-Range"])
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Content-length probe failed for ${download.url}", t)
            null
        }
    }

    private fun startService(context: Context, service: Class<*>, downloadId: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, service).apply {
                putExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, downloadId)
            }
        )
    }
}
