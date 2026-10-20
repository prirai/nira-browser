package com.prirai.android.nira.media

import android.content.Intent
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.media.service.AbstractMediaSessionService
import com.prirai.android.nira.ext.components
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.support.base.android.NotificationsDelegate

/**
 * Nira's `foregroundServiceType="mediaPlayback"` service that keeps
 * audio alive while the app is backgrounded.
 *
 * Upstream `AbstractMediaSessionService.onTaskRemoved` calls
 * `delegate.onTaskRemoved()`, which iterates every tab and invokes
 * `mediaSessionState.controller.stop()` on each, then calls
 * `shutdown()` and `stopSelf()`. That means swiping the task out of
 * the recents panel kills audio playback mid-track - which is the
 * opposite of what a user who explicitly enabled background playback
 * for a site expects.
 *
 * We override `onTaskRemoved` to intentionally skip the stop + shutdown
 * path. Android keeps a foreground service of type `mediaPlayback`
 * alive after the hosting task is removed; the user still has the
 * media notification controls and can stop playback from there (or
 * from the per-site Background playback toggle). The service naturally
 * ends when `handleMediaStopped` fires (end of track / user hits
 * pause in the notification), which still calls `shutdown()` via
 * `MediaSessionServiceDelegate.handleMediaStopped`.
 */
class MediaSessionService : AbstractMediaSessionService() {
    override val crashReporter: CrashReporting? = null
    override val notificationsDelegate: NotificationsDelegate by lazy { components.notificationsDelegate }
    override val store: BrowserStore by lazy { components.store }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Intentionally NOT calling super.onTaskRemoved(). Upstream
        // stops every tab's media controller and shuts the service
        // down; we want playback to survive the task being swiped
        // away so the user can continue listening with the app
        // closed. See class kdoc.
    }
}
