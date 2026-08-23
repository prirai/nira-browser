package com.prirai.android.nira.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
        startForeground(NOTIFICATION_ID, buildNotification("Downloads"))
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
            } catch (_: CancellationException) {
            } catch (_: Exception) {
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
        sidecarDir(id).deleteRecursively()
        update(id) { it.copy(status = DownloadState.Status.CANCELLED) }
        if (jobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun runDownload(initial: DownloadState) {
        val client = components.client
        val probed = probeTotal(initial)
        val total = initial.contentLength?.takeIf { it > 0 } ?: probed
        if (total == null || total < DownloadController.PARALLEL_MIN_BYTES || probed == null) {
            DownloadController.startFallback(this, initial.id)
            return
        }

        val download = initial.copy(
            status = DownloadState.Status.DOWNLOADING,
            contentLength = total,
        )
        update(download.id) { download }

        val dir = sidecarDir(download.id)
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
        if (existing >= expected) return
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
            throw IOException("Unexpected status ${response.status}")
        }
        val job = currentCoroutineContext()[Job]
        response.body.useStream { input ->
            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(existing)
                val buffer = ByteArray(64 * 1024)
                while (job?.isActive != false) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                }
            }
        }
        if (job?.isActive == false) {
            throw CancellationException()
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
        }
        dir.deleteRecursively()
        update(download.id) {
            it.copy(
                status = DownloadState.Status.COMPLETED,
                contentLength = total,
                currentBytesCopied = total,
            )
        }
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
        } catch (_: Exception) {
            null
        }
    }

    private fun update(id: String, transform: (DownloadState) -> DownloadState) {
        val current = components.store.state.downloads[id] ?: return
        components.store.dispatch(DownloadAction.UpdateDownloadAction(transform(current)))
    }

    private fun sidecarDir(id: String): File = File(cacheDir, "nira-dl/$id")

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

    companion object {
        private const val CHANNEL_ID = "nira_parallel_downloads"
        private const val NOTIFICATION_ID = 7101

        fun hasSidecar(context: Context, download: DownloadState): Boolean {
            return File(context.cacheDir, "nira-dl/${download.id}").let { it.exists() && it.list()?.isNotEmpty() == true }
        }

        fun parseTotal(contentRange: String?): Long? {
            val total = contentRange?.substringAfterLast('/') ?: return null
            return total.toLongOrNull()?.takeIf { it > 0 }
        }
    }
}
