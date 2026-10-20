package com.prirai.android.nira.permissions

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.support.ktx.kotlin.getOrigin

/**
 * Pauses tabs that are playing media when the app goes into the
 * background unless their origin is on
 * [BackgroundPlaybackAllowlist].
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
 * # Semantics
 * On ON_STOP, walk `store.state.tabs` and `state.customTabs`. For every
 * tab whose `mediaSessionState.playbackState == PLAYING`:
 *   - If the tab's origin is on [BackgroundPlaybackAllowlist], leave it
 *     alone (user explicitly opted in).
 *   - Otherwise call `mediaSessionState.controller.pause()`.
 *
 * No action on ON_START - a user tapping back into the app does not
 * magically resume the paused tabs; they tap play again, same as
 * every other browser.
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

        var paused = 0
        playing.forEach { tab ->
            val origin = tab.content.url.getOrigin()
            if (origin != null && origin in allowed) {
                // User opted this origin in; leave it playing.
                return@forEach
            }
            try {
                tab.mediaSessionState?.controller?.pause()
                paused++
            } catch (t: Throwable) {
                Log.w(TAG, "pause() failed for tab ${tab.id}", t)
            }
        }
        if (paused > 0) {
            Log.i(
                TAG,
                "App backgrounded; paused $paused tab(s) not on bg-playback allow-list",
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
