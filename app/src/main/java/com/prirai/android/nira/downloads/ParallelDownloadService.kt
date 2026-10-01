package com.prirai.android.nira.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID
import mozilla.components.feature.downloads.filewriter.DefaultDownloadFileWriter
import mozilla.components.support.utils.DefaultDownloadFileUtils
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Foreground service that runs multi-threaded HTTP range downloads.
 *
 * Chosen by [DownloadController.start] when the download is large enough
 * (>= [DownloadController.PARALLEL_MIN_BYTES]) and the server supports
 * Range requests (verified via a `bytes=0-0` probe at the top of
 * [runDownload]).
 *
 * # Reliability contract
 * The previous revision silently accepted truncated downloads: a
 * mid-stream HTTP/2 reset or TCP RST would make `input.read()` return
 * `-1` without throwing, the loop would exit, and the chunk was marked
 * complete even though it was short. Combined with the lack of a
 * total-length check, this produced the "APK fails at the very end when
 * fully downloaded" symptom reported in GitHub Issues: the sheet showed
 * COMPLETED, the user tapped the file, and Android's PackageManager
 * rejected the truncated archive.
 *
 * The current implementation:
 *  - [downloadChunk] tracks exactly how many bytes were written to each
 *    part file. If the count doesn't match `end - start + 1` after the
 *    HTTP body closes, it throws [IOException] so the enclosing
 *    `coroutineScope` in [runDownload] propagates the failure to
 *    `awaitAll` and the download is marked FAILED with a cause logged.
 *  - [RandomAccessFile.fd]`.sync()` is called before the chunk is
 *    considered final, so a process-kill-and-restart doesn't resurrect
 *    a part file that only has in-flight-cached tail bytes.
 *  - [assembleAndPublish] re-verifies `assembled.length() == total`
 *    before dispatching COMPLETED.
 *  - The exception handler in [startOrResume] logs the cause via
 *    `android.util.Log` instead of swallowing silently, so adb logcat
 *    actually shows what went wrong.
 *  - Sidecar lives under [filesDir], not [cacheDir], so a system cache
 *    purge during a long download does not nuke the parts.
 */
class ParallelDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val id = intent?.getStringExtra(INTENT_EXTRA_DOWNLOAD_ID) ?: return
            when (intent.action) {
                DownloadController.ACTION_PAUSE -> pause(id)
                DownloadController.ACTION_CANCEL -> cancelDownload(id)
                DownloadController.ACTION_RESUME -> {
                    val download = components.store.state.downloads[id] ?: return
                    startOrResume(download)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // Explicit foreground-service type is mandatory on Android 14 /
        // API 34; omitting it throws MissingForegroundServiceTypeException
        // at runtime. The manifest already declares
        // foregroundServiceType="dataSync"; we pass the same bit here.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Downloads"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(DownloadController.ACTION_PAUSE)
                addAction(DownloadController.ACTION_CANCEL)
                addAction(DownloadController.ACTION_RESUME)
            }
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(INTENT_EXTRA_DOWNLOAD_ID)
            ?: intent?.getStringExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID)
            ?: return START_NOT_STICKY
        val download = components.store.state.downloads[id] ?: return START_NOT_STICKY
        when (intent?.action) {
            DownloadController.ACTION_PAUSE -> pause(id)
            DownloadController.ACTION_CANCEL -> cancelDownload(id)
            else -> startOrResume(download)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startOrResume(download: DownloadState) {
        if (jobs[download.id]?.isActive == true) return
        jobs[download.id] = scope.launch {
            try {
                runDownload(download)
            } catch (ce: CancellationException) {
                // Expected on pause/cancel; let the respective handlers
                // (pause / cancelDownload) manage state. Rethrow so the
                // coroutine machinery handles unwinding correctly.
                throw ce
            } catch (t: Throwable) {
                // Log the full cause so the user / developer can see
                // WHY a download failed at the end. The previous blanket
                // `catch (_: Exception)` discarded this.
                Log.e(TAG, "Download ${download.id} (${download.fileName ?: download.url}) failed", t)
                update(download.id) { it.copy(status = DownloadState.Status.FAILED) }
            } finally {
                jobs.remove(download.id)
                if (jobs.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun pause(id: String) {
        jobs.remove(id)?.cancel()
        update(id) { it.copy(status = DownloadState.Status.PAUSED) }
        if (jobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cancelDownload(id: String) {
        jobs.remove(id)?.cancel()
        sidecarDir(this, id).deleteRecursively()
        update(id) { it.copy(status = DownloadState.Status.CANCELLED) }
        if (jobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun runDownload(initial: DownloadState) {
        val probed = probeTotal(initial)
        val total = initial.contentLength?.takeIf { it > 0 } ?: probed
        if (total == null || total < DownloadController.PARALLEL_MIN_BYTES || probed == null) {
            Log.i(TAG, "Fallback to serial download for ${initial.id}: total=$total probed=$probed")
            DownloadController.startFallback(this, initial.id)
            return
        }

        // Guarantee the DownloadState carries the correct MIME type for
        // APKs before we hand it to DefaultDownloadFileWriter. Many
        // mirrors / CDNs serve application/octet-stream for .apk, and
        // MediaStore's row inherits that; Android's PackageInstaller
        // intent filter won't match octet-stream.
        val download = initial.copy(
            status = DownloadState.Status.DOWNLOADING,
            contentLength = total,
            contentType = normaliseContentType(initial),
        )
        update(download.id) { download }

        val dir = sidecarDir(this, download.id)
        dir.mkdirs()
        val threadCount = DownloadController.PARALLEL_THREADS
        val chunkSize = total / threadCount

        coroutineScope {
            val progress = launch {
                while (isActive) {
                    val copied = partFiles(dir, threadCount).sumOf { it.length() }
                    update(download.id) {
                        it.copy(
                            status = DownloadState.Status.DOWNLOADING,
                            contentLength = total,
                            currentBytesCopied = copied.coerceAtMost(total),
                        )
                    }
                    delay(400)
                }
            }
            try {
                (0 until threadCount).map { index ->
                    async(Dispatchers.IO) {
                        val start = index * chunkSize
                        val end = if (index == threadCount - 1) total - 1 else (index + 1) * chunkSize - 1
                        downloadChunk(download, start, end, File(dir, "part$index"))
                    }
                }.awaitAll()
            } finally {
                progress.cancel()
            }
        }

        // Sanity-check assembled bytes against expected total before we
        // claim success. If any chunk silently truncated we throw here
        // and let [startOrResume]'s catch block mark the download FAILED.
        verifyParts(dir, threadCount, total)

        assembleAndPublish(download.copy(contentLength = total), dir, threadCount, total)
    }

    private suspend fun downloadChunk(
        download: DownloadState,
        start: Long,
        end: Long,
        part: File,
    ) {
        val existing = if (part.exists()) part.length() else 0L
        val expected = end - start + 1
        if (existing >= expected) {
            if (existing > expected) {
                // Resuming from a corrupt sidecar - truncate down so the
                // next copy is byte-exact.
                RandomAccessFile(part, "rw").use { it.setLength(expected) }
            }
            return
        }
        val rangeStart = start + existing
        val headers = MutableHeaders()
        headers.append("Range", "bytes=$rangeStart-$end")
        val request = Request(
            url = download.url,
            headers = headers,
            private = download.private,
            referrerUrl = download.referrerUrl,
        )
        val response = components.client.fetch(request)
        if (response.status != 206) {
            response.close()
            throw IOException("Chunk [$rangeStart-$end]: expected 206 Partial Content, got ${response.status}")
        }
        val job = currentCoroutineContext()[Job]
        // Track actual bytes written. The previous revision trusted
        // `input.read() == -1` as a sentinel for "chunk complete", which
        // is unsafe: HTTP/2 stream resets, TCP RSTs, and server-side
        // truncations also return -1 without throwing. If the server
        // closed the body before sending `expected` bytes we throw and
        // the enclosing coroutineScope cancels its siblings.
        var writtenThisPass = 0L
        response.body.useStream { input ->
            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(existing)
                val buffer = ByteArray(64 * 1024)
                while (job?.isActive != false) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    writtenThisPass += read
                }
                // Flush to the storage layer. Without this a kill-and-
                // restart can observe raf.length() == expected but with
                // uninitialised tail bytes.
                raf.fd.sync()
            }
        }
        if (job?.isActive == false) {
            throw CancellationException()
        }
        val finalLen = part.length()
        val expectedThisPass = expected - existing
        if (writtenThisPass != expectedThisPass || finalLen != expected) {
            throw IOException(
                "Chunk [$rangeStart-$end] truncated: wrote=$writtenThisPass " +
                    "expected=$expectedThisPass fileLen=$finalLen expectedLen=$expected"
            )
        }
    }

    /**
     * After `awaitAll` returns, every chunk should have reported exact
     * length. This is a belt-and-braces check against lower-level
     * filesystem shenanigans (e.g. encrypted FS reporting lies) before
     * we assemble.
     */
    private fun verifyParts(dir: File, threadCount: Int, total: Long) {
        val chunkSize = total / threadCount
        val sum = (0 until threadCount).sumOf { index ->
            val part = File(dir, "part$index")
            val expected = if (index == threadCount - 1) total - index * chunkSize else chunkSize
            if (!part.isFile || part.length() != expected) {
                throw IOException(
                    "Part $index missing or wrong size: have=${part.takeIf { it.exists() }?.length()} expected=$expected"
                )
            }
            part.length()
        }
        if (sum != total) {
            throw IOException("Assembled bytes mismatch: have=$sum expected=$total")
        }
    }

    private fun assembleAndPublish(
        download: DownloadState,
        dir: File,
        threadCount: Int,
        total: Long,
    ) {
        val assembled = File(dir, "assembled")
        assembled.outputStream().use { out ->
            partFiles(dir, threadCount).forEach { part ->
                part.inputStream().use { it.copyTo(out) }
            }
            out.flush()
            // Match the per-chunk sync so a crash between assemble and
            // writer.useFileStream cannot land us with a truncated
            // intermediate. File.outputStream() returns a FileOutputStream.
            out.fd.sync()
        }
        if (assembled.length() != total) {
            throw IOException("Assembled file truncated: have=${assembled.length()} expected=$total")
        }
        val fileUtils = DefaultDownloadFileUtils(
            context = applicationContext,
            downloadLocation = { download.directoryPath },
        )
        val writer = DefaultDownloadFileWriter(applicationContext, fileUtils)
        writer.useFileStream(
            download = download,
            append = false,
            shouldUseScopedStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            onUpdateState = { updated -> update(download.id) { updated } },
        ) { out ->
            assembled.inputStream().use { it.copyTo(out) }
            out.flush()
        }
        dir.deleteRecursively()
        update(download.id) {
            it.copy(
                status = DownloadState.Status.COMPLETED,
                contentLength = total,
                currentBytesCopied = total,
            )
        }
        Log.i(TAG, "Download ${download.id} (${download.fileName ?: download.url}) completed, $total bytes")
    }

    private fun probeTotal(download: DownloadState): Long? {
        return try {
            val headers = MutableHeaders()
            headers.append("Range", "bytes=0-0")
            val request = Request(
                url = download.url,
                headers = headers,
                private = download.private,
                referrerUrl = download.referrerUrl,
            )
            components.client.fetch(request).use { response ->
                if (response.status != 206) return null
                parseTotal(response.headers["Content-Range"])
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Range probe failed for ${download.url}", t)
            null
        }
    }

    private fun update(id: String, transform: (DownloadState) -> DownloadState) {
        val current = components.store.state.downloads[id] ?: return
        components.store.dispatch(DownloadAction.UpdateDownloadAction(transform(current)))
    }

    private fun partFiles(dir: File, count: Int): List<File> =
        (0 until count).map { File(dir, "part$it") }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_download_24)
            .setContentTitle("Downloading")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    /**
     * Return a MIME type we'd want MediaStore to record for this
     * download. If the file name suggests an APK we force the
     * well-known Android package MIME so the completion-open intent can
     * resolve to the package installer. Otherwise we trust whatever
     * GeckoView already captured from `Content-Type`.
     */
    private fun normaliseContentType(download: DownloadState): String? {
        val existing = download.contentType
        val name = download.fileName ?: return existing
        return if (name.endsWith(".apk", ignoreCase = true)) {
            APK_MIME
        } else {
            existing
        }
    }

    companion object {
        private const val TAG = "ParallelDownload"
        private const val CHANNEL_ID = "nira_parallel_downloads"
        private const val NOTIFICATION_ID = 7101

        /**
         * Canonical Android APK MIME. See
         * `android.content.pm.PackageInstaller.SessionParams` and the
         * `.apk` row of `/system/etc/mime.types`.
         */
        const val APK_MIME: String = "application/vnd.android.package-archive"

        /**
         * Sidecar location: in-progress part files for an active or
         * paused parallel download. Lives under [Context.getFilesDir]
         * (not [Context.getCacheDir]) because the OS can purge the
         * cache at any time, and a mid-download cache purge used to
         * destroy multi-gigabyte in-progress downloads with no warning.
         */
        fun sidecarDir(context: Context, id: String): File =
            File(context.filesDir, "nira-dl/$id")

        fun hasSidecar(context: Context, download: DownloadState): Boolean =
            sidecarDir(context, download.id).let { it.exists() && it.list()?.isNotEmpty() == true }

        fun parseTotal(contentRange: String?): Long? {
            val total = contentRange?.substringAfterLast('/') ?: return null
            return total.toLongOrNull()?.takeIf { it > 0 }
        }
    }
}
