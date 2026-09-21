package com.prirai.android.nira.browser.tabs.sleep

import android.content.ComponentCallbacks2
import android.content.Context
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.lib.state.ext.flowScoped

/**
 * Aggressively suspends the [EngineSession] for tabs the user is not actively
 * using so only a small pool of live sessions remains in memory.
 *
 * Uses [EngineAction.SuspendEngineSessionAction], which:
 *  1. Saves the tab's [EngineSessionState] into the store.
 *  2. Dispatches [EngineAction.UnlinkEngineSessionAction] (clears the reference).
 *  3. Closes the underlying `GeckoSession`.
 *
 * When the tab is next selected, [EngineViewPresenter] sees `engineSession == null`
 * and dispatches [EngineAction.CreateEngineSessionAction], which restores the tab
 * from the saved state. The user sees the tab reload from its serialized state
 * (same as Firefox Desktop sleeping tabs).
 *
 * Triggers:
 *  - Idle timeout — checked periodically while the app is in the foreground.
 *  - Live-tab cap — enforced every time a tab is selected.
 *  - App backgrounded — suspend everything except the selected tab (aggressive).
 *  - Low memory — suspend all non-selected tabs.
 *
 * A tab is never suspended when:
 *  - It is the currently selected tab.
 *  - It is playing media (audio/video).
 *  - It has an active download.
 *  - It has a pending prompt.
 *  - It is a custom tab.
 *  - Its engine session isn't linked yet (`engineSession == null`).
 */
class SleepingTabsManager private constructor(
    private val appContext: Context,
) {

    private val prefs = UserPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var idleJob: Job? = null
    private var storeScope: kotlinx.coroutines.CoroutineScope? = null

    /** Called from Components after the BrowserStore is created. */
    fun start(store: BrowserStore) {
        // Enforce the live-tab cap every time the selection changes.
        storeScope = store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow
                .map { state: BrowserState -> state.selectedTabId }
                .distinctUntilChanged()
                .collect {
                    enforceLiveTabCap(store)
                }
        }

        startIdleSweeper(store)
    }

    /** Restart the idle sweeper when preferences change. */
    fun onPreferencesChanged(store: BrowserStore) {
        startIdleSweeper(store)
    }

    private fun startIdleSweeper(store: BrowserStore) {
        idleJob?.cancel()
        val mode = prefs.getSleepingTabsMode()
        if (mode.idleTimeoutMs <= 0L) return
        idleJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(mode.sweepIntervalMs)
                sweepIdleTabs(store)
            }
        }
    }

    /**
     * Called from [android.app.Application.onTrimMemory] / equivalent.
     * Aggressive at [ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW] and above.
     */
    fun onTrimMemory(store: BrowserStore, level: Int) {
        if (prefs.sleepingTabsMode == UserPreferences.SLEEPING_TABS_OFF) {
            // Even if the feature is off we still avoid the raw close() loop that
            // caused the ghosting bug. Suspend properly.
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                suspendAllExceptSelected(store)
            }
            return
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            suspendAllExceptSelected(store)
        }
    }

    /** Called when the app is paused (goes to background). */
    fun onAppBackgrounded(store: BrowserStore) {
        val mode = prefs.getSleepingTabsMode()
        if (mode.suspendOnBackground) {
            suspendAllExceptSelected(store)
        }
    }

    /** Suspend a specific tab immediately (e.g. from a UI action). */
    fun sleepTab(store: BrowserStore, tabId: String) {
        val tab = store.state.tabs.firstOrNull { it.id == tabId } ?: return
        if (canSuspend(store, tab)) {
            store.dispatch(EngineAction.SuspendEngineSessionAction(tab.id))
        }
    }

    // -- internals -----------------------------------------------------------

    private fun sweepIdleTabs(store: BrowserStore) {
        val mode = prefs.getSleepingTabsMode()
        if (mode.idleTimeoutMs <= 0L) return
        val now = System.currentTimeMillis()
        val selectedId = store.state.selectedTabId

        for (tab in store.state.tabs) {
            if (tab.id == selectedId) continue
            if (!canSuspend(store, tab)) continue
            val idle = now - lastActivity(tab)
            if (idle >= mode.idleTimeoutMs) {
                store.dispatch(EngineAction.SuspendEngineSessionAction(tab.id))
            }
        }
    }

    private fun enforceLiveTabCap(store: BrowserStore) {
        val mode = prefs.getSleepingTabsMode()
        val cap = mode.maxLiveTabs
        if (cap <= 0) return

        val selectedId = store.state.selectedTabId
        val live = store.state.tabs.filter { it.engineState.engineSession != null }
        if (live.size <= cap) return

        // Keep the selected tab plus the (cap - 1) most-recently accessed live tabs.
        val excess = live
            .filter { it.id != selectedId && canSuspend(store, it) }
            .sortedBy { lastActivity(it) } // oldest first
            .take(live.size - cap)

        for (tab in excess) {
            store.dispatch(EngineAction.SuspendEngineSessionAction(tab.id))
        }
    }

    private fun suspendAllExceptSelected(store: BrowserStore) {
        val selectedId = store.state.selectedTabId
        for (tab in store.state.tabs) {
            if (tab.id == selectedId) continue
            if (!canSuspend(store, tab)) continue
            store.dispatch(EngineAction.SuspendEngineSessionAction(tab.id))
        }
    }

    private fun canSuspend(store: BrowserStore, tab: TabSessionState): Boolean {
        // No live session -> nothing to suspend.
        if (tab.engineState.engineSession == null) return false
        // Playing media, e.g. music streaming.
        val playing = tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING
        if (playing) return false
        // Active download attached to this tab.
        if (tab.content.download != null) return false
        // Pending user prompts.
        if (tab.content.promptRequests.isNotEmpty()) return false
        // Pending permission requests.
        if (tab.content.permissionRequestsList.isNotEmpty()) return false
        return true
    }

    private fun lastActivity(tab: TabSessionState): Long =
        maxOf(tab.lastAccess, tab.lastMediaAccessState.lastMediaAccess)

    fun stop() {
        storeScope?.cancel()
        storeScope = null
        scope.cancel()
    }

    companion object {
        @Volatile
        private var instance: SleepingTabsManager? = null

        fun getInstance(context: Context): SleepingTabsManager =
            instance ?: synchronized(this) {
                instance ?: SleepingTabsManager(context.applicationContext).also { instance = it }
            }
    }
}

/** Sleeping-tabs policy for a single mode. */
data class SleepingTabsMode(
    val idleTimeoutMs: Long,
    val maxLiveTabs: Int,
    val suspendOnBackground: Boolean,
    val sweepIntervalMs: Long = 60_000L,
) {
    companion object {
        val Off = SleepingTabsMode(
            idleTimeoutMs = 0L,
            maxLiveTabs = 0,
            suspendOnBackground = false,
        )
        val Balanced = SleepingTabsMode(
            idleTimeoutMs = 30 * 60_000L, // 30 min
            maxLiveTabs = 8,
            suspendOnBackground = true,
            sweepIntervalMs = 60_000L,
        )
        val Aggressive = SleepingTabsMode(
            idleTimeoutMs = 5 * 60_000L, // 5 min
            maxLiveTabs = 3,
            suspendOnBackground = true,
            sweepIntervalMs = 30_000L,
        )
    }
}
