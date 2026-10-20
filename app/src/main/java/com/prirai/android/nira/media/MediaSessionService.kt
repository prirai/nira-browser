package com.prirai.android.nira.media

import android.app.Service
import android.content.Intent
import android.os.Build
import android.util.Log
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.feature.media.service.AbstractMediaSessionService
import mozilla.components.support.base.android.NotificationsDelegate

/**
 * Nira's `foregroundServiceType="mediaPlayback"` service.
 *
 * # Task-removal semantics
 * On swipe-from-recents we always tear down, regardless of the
 * [com.prirai.android.nira.permissions.BackgroundPlaybackAllowlist].
 * The allow-list only matters during **backgrounding** (HOME, lock,
 * app-switch) which [com.prirai.android.nira.permissions.BackgroundPlaybackGuard]
 * handles on `ProcessLifecycleOwner.ON_STOP`.
 *
 * # How the teardown reaches us
 * Two paths, both lead to full teardown:
 *
 * 1. **`android:stopWithTask="true"` in the manifest.** The system
 *    destroys this service the moment the task is removed, even if
 *    the service is bound (which it always is while media is
 *    playing - `MediaSessionFeature` uses `BIND_AUTO_CREATE` and
 *    only unbinds via `handleNoMedia`, which never fires on
 *    task-removal because the Activity never gets a chance to run
 *    its teardown code). Without this flag the bound-service
 *    semantics would keep the process pinned alive.
 *    `onDestroy` then runs the AC shutdown chain (release
 *    `MediaSession`, cancel notification, abandon audio focus).
 *
 * 2. **`onTaskRemoved` override.** Still implemented defensively so
 *    we also handle the rare path where the system DOES call
 *    `onTaskRemoved` before destroying us. We proactively stop
 *    media on both `tabs` and `customTabs` (upstream
 *    `MediaSessionServiceDelegate.onTaskRemoved` skips custom
 *    tabs - see the "no need to do this for custom tabs" comment
 *    in AC 156.0.1), then fall through to `super`.
 *
 * `onDestroy` emits a diagnostic log line so field logs can
 * distinguish which path ran.
 */
class MediaSessionService : AbstractMediaSessionService() {
    override val crashReporter: CrashReporting? = null
    override val notificationsDelegate: NotificationsDelegate by lazy { components.notificationsDelegate }
    override val store: BrowserStore by lazy { components.store }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val state = try { store.state } catch (_: Throwable) { null }
        if (state != null) {
            val playing = (state.tabs + state.customTabs).filter { tab ->
                tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING ||
                    tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PAUSED
            }
            Log.i(
                TAG,
                "onTaskRemoved: stopping ${playing.size} tab(s) with active media",
            )
            playing.forEach { tab ->
                try {
                    tab.mediaSessionState?.controller?.stop()
                } catch (t: Throwable) {
                    Log.w(TAG, "controller.stop() failed for tab ${tab.id}", t)
                }
            }
        }

        super.onTaskRemoved(rootIntent)

        // Defensive final teardown: if AC's shutdown already ran this
        // is a no-op; if it did not (OEM task-removed quirks, race
        // with the engine shutdown) this ensures the foreground
        // service is torn down so the OS can reclaim the process.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            Log.i(TAG, "onTaskRemoved: defensive stopSelf completed")
        } catch (t: Throwable) {
            Log.w(TAG, "defensive stopSelf failed", t)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NiraMediaSvc"
    }
}
