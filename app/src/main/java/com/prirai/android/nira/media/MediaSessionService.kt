package com.prirai.android.nira.media

import android.content.Intent
import android.util.Log
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.permissions.BackgroundPlaybackAllowlist
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.feature.media.service.AbstractMediaSessionService
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.ktx.kotlin.getOrigin

/**
 * Nira's `foregroundServiceType="mediaPlayback"` service.
 *
 * Upstream `AbstractMediaSessionService.onTaskRemoved` always calls
 * `delegate.onTaskRemoved()`, which iterates every tab and invokes
 * `mediaSessionState.controller.stop()` on each, then calls
 * `shutdown()` and `stopSelf()`. That default kills audio mid-track
 * when the user swipes the task from recents.
 *
 * We only want to keep playback alive after task-removal when the
 * currently playing tab's origin is on
 * [BackgroundPlaybackAllowlist]. Otherwise we follow the upstream
 * "stop + shutdown" path so swiping the app really does stop the
 * audio - which is the user's expectation when they have not opted
 * that site in to background playback.
 *
 * Note on scope: the allow-list check reads a snapshot of the
 * `BrowserStore` and of `BackgroundPlaybackAllowlist.origins.value`
 * at the moment of task-removal. If the user changes the allow-list
 * later (while audio is already in the background) nothing changes
 * until the next pause/stop event; the already-granted "allowed to
 * run in background" status is preserved for the current session.
 */
class MediaSessionService : AbstractMediaSessionService() {
    override val crashReporter: CrashReporting? = null
    override val notificationsDelegate: NotificationsDelegate by lazy { components.notificationsDelegate }
    override val store: BrowserStore by lazy { components.store }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (shouldKeepPlayingAfterTaskRemoved()) {
            Log.i(TAG, "Task removed; keeping playback alive (origin on bg-playback allow-list)")
            // Intentionally NOT calling super.onTaskRemoved() so the
            // foreground service (and the audio) survives.
            return
        }
        Log.i(TAG, "Task removed; stopping playback (no allow-listed origin playing)")
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Walk every tab (normal + custom) and ask: is there any one with
     * `MediaSession.PlaybackState.PLAYING` whose origin is in the
     * [BackgroundPlaybackAllowlist]? If yes we are allowed to keep
     * running. We check normal tabs AND custom tabs because external
     * app VIEW intents produce custom tabs that can also hold media.
     */
    private fun shouldKeepPlayingAfterTaskRemoved(): Boolean {
        val allowlist = try {
            BackgroundPlaybackAllowlist.getInstance(applicationContext).origins.value
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read bg-playback allow-list", t)
            return false
        }
        if (allowlist.isEmpty()) return false

        val state = try {
            store.state
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read BrowserStore state", t)
            return false
        }

        val playingTabs: List<SessionState> =
            (state.tabs + state.customTabs).filter { tab ->
                tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING
            }
        if (playingTabs.isEmpty()) return false

        return playingTabs.any { tab ->
            val origin = tab.content.url.getOrigin() ?: return@any false
            origin in allowlist
        }
    }

    companion object {
        private const val TAG = "NiraMediaSvc"
    }
}
