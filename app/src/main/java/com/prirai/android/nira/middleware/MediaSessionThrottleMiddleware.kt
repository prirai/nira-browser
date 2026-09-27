package com.prirai.android.nira.middleware

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.MediaSessionAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Coalesces high-frequency [MediaSessionAction] dispatches so we don't churn
 * the media session pipeline (which in turn causes AC's `AudioFocus.request()`
 * to fire multiple times in quick succession — which on Android 12 causes
 * `AUDIOFOCUS_REQUEST_DELAYED` and an unwanted `controller.pause()`).
 *
 * Rules:
 *  - [MediaSessionAction.UpdateMediaPlaybackStateAction]: drop when the new
 *    playback state is identical to the current one for that tab.
 *  - [MediaSessionAction.UpdateMediaPositionStateAction]: drop when it fires
 *    within [POSITION_THROTTLE_MS] of the last accepted position update for
 *    that tab. The Android media notification progress bar loses a tiny bit
 *    of accuracy, which is a worthwhile trade for stable playback.
 *  - [MediaSessionAction.UpdateMediaMetadataAction]: drop when the new
 *    metadata's title+artist+album equals the last accepted metadata for
 *    that tab.
 *
 * See detailed device-log analysis in the `bg-pip` branch commit history.
 * Root cause: `MediaSessionServiceDelegate.handleMediaPlaying()` calls
 * `audioFocus.request()`; the flow observer in `MediaSessionFeature` fires
 * on every `mediaSessionState` change (data-class equality). YouTube's page
 * mutates metadata / positionState many times per second while playing, and
 * each mutation ripples all the way through to a fresh audio-focus request.
 */
internal class MediaSessionThrottleMiddleware :
    Middleware<BrowserState, BrowserAction> {

    private companion object {
        const val POSITION_THROTTLE_MS = 1000L
    }

    private val lastPositionAt = mutableMapOf<String, Long>()
    private val lastMetadataKey = mutableMapOf<String, String>()

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        when (action) {
            is MediaSessionAction.ActivatedMediaSessionAction -> {
                // Only allow the very first activation per tab. YouTube can
                // re-activate the media session on Play resume, which resets
                // mediaSessionState and defeats the playback-state dedupe.
                val state = store.state
                val tab = state.tabs.firstOrNull { it.id == action.tabId }
                    ?: state.customTabs.firstOrNull { it.id == action.tabId }
                if (tab?.mediaSessionState?.controller != null) {
                    return
                }
            }

            is MediaSessionAction.UpdateMediaPlaybackStateAction -> {
                val state = store.state
                val tab = state.tabs.firstOrNull { it.id == action.tabId }
                    ?: state.customTabs.firstOrNull { it.id == action.tabId }
                val current: MediaSession.PlaybackState? = tab?.mediaSessionState?.playbackState
                if (current == action.playbackState) {
                    // No-op: dropping duplicate playback state update.
                    return
                }
            }

            is MediaSessionAction.UpdateMediaPositionStateAction -> {
                val now = android.os.SystemClock.elapsedRealtime()
                val last = lastPositionAt[action.tabId]
                if (last != null && now - last < POSITION_THROTTLE_MS) {
                    return
                }
                lastPositionAt[action.tabId] = now
            }

            is MediaSessionAction.UpdateMediaMetadataAction -> {
                val md = action.metadata
                val key = (md.title.orEmpty()) + "|" +
                    (md.artist.orEmpty()) + "|" +
                    (md.album.orEmpty())
                if (lastMetadataKey[action.tabId] == key) {
                    return
                }
                lastMetadataKey[action.tabId] = key
            }

            is MediaSessionAction.UpdateMediaFeatureAction -> {
                val state = store.state
                val tab = state.tabs.firstOrNull { it.id == action.tabId }
                    ?: state.customTabs.firstOrNull { it.id == action.tabId }
                if (tab?.mediaSessionState?.features == action.features) return
            }

            is MediaSessionAction.UpdateMediaMutedAction -> {
                val state = store.state
                val tab = state.tabs.firstOrNull { it.id == action.tabId }
                    ?: state.customTabs.firstOrNull { it.id == action.tabId }
                if (tab?.mediaSessionState?.muted == action.muted) return
            }

            is MediaSessionAction.UpdateMediaFullscreenAction -> {
                val state = store.state
                val tab = state.tabs.firstOrNull { it.id == action.tabId }
                    ?: state.customTabs.firstOrNull { it.id == action.tabId }
                val ms = tab?.mediaSessionState
                if (ms != null &&
                    ms.fullscreen == action.fullScreen &&
                    ms.elementMetadata == action.elementMetadata
                ) return
            }

            is MediaSessionAction.DeactivatedMediaSessionAction -> {
                lastPositionAt.remove(action.tabId)
                lastMetadataKey.remove(action.tabId)
            }

            else -> {
                // Not a media-session action we care about.
            }
        }

        next(action)
    }
}
