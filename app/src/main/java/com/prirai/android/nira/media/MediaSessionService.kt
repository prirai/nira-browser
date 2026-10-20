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
 * On swipe-from-recents (`onTaskRemoved`) we always tear down,
 * regardless of whether the current site is on the
 * [com.prirai.android.nira.permissions.BackgroundPlaybackAllowlist].
 * The allow-list only matters during **backgrounding** (HOME, lock,
 * app-switch) which [com.prirai.android.nira.permissions.BackgroundPlaybackGuard]
 * handles on `ProcessLifecycleOwner.ON_STOP`.
 *
 * # Why override `onTaskRemoved` instead of letting
 * `AbstractMediaSessionService` do it
 * The upstream override routes to
 * `MediaSessionServiceDelegate.onTaskRemoved` which only iterates
 * `store.state.tabs` and skips `state.customTabs` (see the
 * "no need to do this for custom tabs" comment in AC 156.0.1). If
 * audio is playing in a custom tab (external VIEW intent) the
 * controller is never told to stop, the engine session is destroyed
 * mid-stream without a graceful stop, and on some OEM builds the
 * foreground `mediaPlayback` service lingers because audio focus is
 * still held.
 *
 * We proactively walk BOTH `tabs` and `customTabs`, call `stop()` on
 * every playing controller, THEN call `super.onTaskRemoved` so AC's
 * `shutdown()` runs (releases MediaSession, cancels notification,
 * stops the service). Finally we `stopForegroundCompat` + `stopSelf`
 * as a defensive final step in case AC's shutdown lost the race with
 * the engine shutdown.
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

    companion object {
        private const val TAG = "NiraMediaSvc"
    }
}
