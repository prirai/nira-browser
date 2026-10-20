package com.prirai.android.nira.media

import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.feature.media.service.AbstractMediaSessionService
import mozilla.components.support.base.android.NotificationsDelegate
import com.prirai.android.nira.ext.components

/**
 * Nira's `foregroundServiceType="mediaPlayback"` service.
 *
 * No overrides beyond the three abstract members required by
 * [AbstractMediaSessionService]. In particular we **do not** override
 * `onTaskRemoved` any more.
 *
 * # Task-removal semantics (post round-7)
 * Earlier revisions overrode `onTaskRemoved` to keep audio playing
 * when a currently-playing tab's origin was on
 * [com.prirai.android.nira.permissions.BackgroundPlaybackAllowlist].
 * That was wrong: swiping the app out of recents is the strongest
 * "I'm done" signal the OS exposes, and the user still expects the
 * process + media notification to go away. Keeping the foreground
 * service alive past task-removal pinned the browser process in
 * memory indefinitely with no visible UI to control it.
 *
 * The allow-list only governs **backgrounding** (HOME button, lock,
 * app-switch, etc.). That path is handled by
 * [com.prirai.android.nira.permissions.BackgroundPlaybackGuard] on
 * `ProcessLifecycleOwner.ON_STOP`: non-allow-listed tabs are stopped
 * (notification cleared, service drops foreground), allow-listed tabs
 * continue playing.
 *
 * Task-removal falls through to upstream `onTaskRemoved`, which stops
 * every tab's media controller, calls `shutdown()`, cancels the
 * notification, and `stopSelf()`s the service. The browser process is
 * then free to be reclaimed by Android.
 */
class MediaSessionService : AbstractMediaSessionService() {
    override val crashReporter: CrashReporting? = null
    override val notificationsDelegate: NotificationsDelegate by lazy { components.notificationsDelegate }
    override val store: BrowserStore by lazy { components.store }
}
