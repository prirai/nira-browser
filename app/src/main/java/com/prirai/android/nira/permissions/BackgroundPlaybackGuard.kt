package com.prirai.android.nira.permissions

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.support.ktx.kotlin.getOrigin

/**
 * Stops media playback on tabs whose origin is NOT on
 * [BackgroundPlaybackAllowlist] when the app goes into the background.
 *
 * # Why not rely on `MediaSessionService.onTaskRemoved`?
 * `onTaskRemoved` only fires when the user **removes the task** from
 * recents (swipes it away). The common ways users leave the app are
 * HOME button, back-to-launcher, screen-off, switching to another app -
 * none of those remove the task, so `onTaskRemoved` does not fire and
 * the foreground `mediaPlayback` service keeps the audio playing.
 *
 * [ProcessLifecycleOwner.ON_STOP] fires in every one of those cases
 * (when the last visible Activity of the process stops), which is the
 * right trigger for a per-site "may keep playing in background" gate.
 *
 * # Why stop() and not pause()?
 * AC's [mozilla.components.feature.media.service.MediaSessionServiceDelegate]
 * treats `pause()` and `stop()` very differently:
 *
 *   - `pause()` -> `handleMediaPaused` runs: foreground service goes
 *     away (`stopForegroundCompat(false)`) BUT the MediaSession stays
 *     active and `updateNotification` re-emits the PAUSED-state
 *     notification. The visible media notification remains, which
 *     looks like a stuck playback control.
 *   - `stop()` -> `handleMediaStopped` runs: foreground service goes
 *     away, audio focus is abandoned, AND `updateNotification` is
 *     called with a STOPPED state which hits the `else -> cancel()`
 *     branch in [MediaSessionServiceDelegate.updateNotification],
 *     clearing the notification. The MediaSession itself goes
 *     inactive on the next `handleNoMedia` -> `shutdown()` tick.
 *
 * We want the second behaviour. Calling `pause()` here produced the
 * "paused media session stays visible in the notification shade after
 * I background the app" symptom the user reported on 2026-10-20.
 *
 * # Semantics
 * On ON_STOP, walk `store.state.tabs` and `state.customTabs`. For every
 * tab whose `mediaSessionState.playbackState == PLAYING`:
 *   - If the tab's origin is on [BackgroundPlaybackAllowlist], leave it
 *     alone (user explicitly opted in).
 *   - Otherwise call `mediaSessionState.controller.stop()`.
 *
 * No action on ON_START - a user tapping back into the app does not
 * auto-resume the stopped tabs; they tap play again.
 *
 * # Lifecycle
 * Attached once in [BrowserApp.onCreate] via
 * `ProcessLifecycleOwner.get().lifecycle.addObserver(...)`. Lives for
 * the whole process, no explicit teardown needed.
 */
internal class BackgroundPlaybackGuard(
    private val store: BrowserStore,
    private val allowlist: BackgroundPlaybackAllowlist,
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        val allowed = allowlist.origins.value
        val state = store.state
        val playing = (state.tabs + state.customTabs).filter { tab ->
            tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING
        }
        if (playing.isEmpty()) return

        var stopped = 0
        playing.forEach { tab ->
            val origin = tab.content.url.getOrigin()
            if (origin != null && origin in allowed) {
                // User opted this origin in; leave it playing.
                return@forEach
            }
            try {
                tab.mediaSessionState?.controller?.stop()
                stopped++
            } catch (t: Throwable) {
                Log.w(TAG, "stop() failed for tab ${tab.id}", t)
            }
        }
        if (stopped > 0) {
            Log.i(
                TAG,
                "App backgrounded; stopped $stopped tab(s) not on bg-playback allow-list",
            )
        }
    }

    companion object {
        private const val TAG = "NiraBgPlayGuard"

        /** Register exactly once for the lifetime of the process. */
        fun install(store: BrowserStore, allowlist: BackgroundPlaybackAllowlist) {
            val guard = BackgroundPlaybackGuard(store, allowlist)
            ProcessLifecycleOwner.get().lifecycle.addObserver(guard)
        }
    }
}
