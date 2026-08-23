package com.prirai.android.nira.downloads

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID
import com.prirai.android.nira.ext.components

object DownloadController {
    const val PARALLEL_MIN_BYTES = 8L * 1024 * 1024
    const val PARALLEL_THREADS = 4

    const val ACTION_PAUSE = "com.prirai.android.nira.downloads.PAUSE"
    const val ACTION_RESUME = "com.prirai.android.nira.downloads.RESUME"
    const val ACTION_CANCEL = "com.prirai.android.nira.downloads.CANCEL"

    fun shouldUseParallel(context: Context, download: DownloadState): Boolean {
        val length = download.contentLength ?: 0L
        val http = download.url.startsWith("http://") || download.url.startsWith("https://")
        return http && (
            length >= PARALLEL_MIN_BYTES ||
                ParallelDownloadService.hasSidecar(context, download)
            )
    }

    fun start(context: Context, download: DownloadState) {
        val app = context.applicationContext
        if (shouldUseParallel(app, download)) {
            startService(app, ParallelDownloadService::class.java, download.id)
        } else {
            startService(app, DownloadService::class.java, download.id)
        }
    }

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
