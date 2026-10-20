package com.prirai.android.nira.permissions

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.webextension.WebExtension
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Reactive install/uninstall controller for the `nira-bg-play`
 * built-in WebExtension.
 *
 * The previous revision installed the extension unconditionally at
 * app launch. That meant:
 *   - A user who never visits an allow-listed site still had the
 *     extension's content.js injected into every page load (bailing
 *     out early via the allow-list query, but still paying the inject
 *     cost).
 *   - Toggling every origin off in the Site Permissions sheet
 *     correctly cleared the allow-list but left the extension
 *     installed and the background script running forever.
 *
 * Rewired to the fail-safe-closed invariant the user asked for:
 *   - On every change to [BackgroundPlaybackAllowlist.origins]:
 *       - If the set is non-empty and the extension is not currently
 *         installed, install it.
 *       - If the set is empty and the extension IS currently
 *         installed, uninstall it.
 *   - Successful installs attach [NiraBgPlayAllowlistBridge] so the
 *     per-origin check inside the extension sees the current
 *     allow-list on the first query.
 *
 * All work happens on `Dispatchers.Main` because [Engine] install /
 * uninstall callbacks must be invoked on the Main thread (GeckoView
 * Handler constraints), and the allow-list StateFlow is cheap to
 * observe from Main.
 */
internal class NiraBgPlayController(
    private val applicationContext: Context,
    private val engine: Engine,
    private val allowlist: BackgroundPlaybackAllowlist,
    private val scope: CoroutineScope,
) {
    private val tag = "NiraBgPlay"

    @Volatile
    private var current: WebExtension? = null

    @Volatile
    private var inFlight: Boolean = false

    private var observerJob: Job? = null

    /**
     * Start observing the allow-list and install/uninstall the
     * extension accordingly. Idempotent - repeat calls cancel any
     * prior collector.
     *
     * Seeds [current] from `engine.listInstalledWebExtensions()` on
     * launch so an extension that was installed by an earlier Nira
     * build (prior to this reactive controller) is discovered and
     * eligible for uninstall when the allow-list is empty. Without
     * this step a stale install would persist in the Gecko profile
     * across upgrades and keep running on every page load even after
     * the user has toggled every origin off.
     */
    fun start() {
        observerJob?.cancel()
        observerJob = scope.launch(Dispatchers.Main) {
            // Discover any pre-existing install before we start
            // reacting to allow-list changes.
            try {
                current = findExistingInstall()
                if (current != null) {
                    Log.i(tag, "nira-bg-play already installed from a previous session")
                }
            } catch (t: Throwable) {
                Log.w(tag, "listInstalledWebExtensions failed", t)
            }
            allowlist.origins
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { shouldBeInstalled ->
                    reconcile(shouldBeInstalled)
                }
        }
    }

    private suspend fun findExistingInstall(): WebExtension? = withContext(Dispatchers.Main) {
        suspendCoroutine<WebExtension?> { cont ->
            engine.listInstalledWebExtensions(
                onSuccess = { installed ->
                    cont.resume(installed.firstOrNull { it.id == EXTENSION_ID })
                },
                onError = { _ -> cont.resume(null) },
            )
        }
    }

    /**
     * Walk from the observed desired state to the real engine state.
     * We short-circuit when an install or uninstall is already in
     * flight so we never issue two overlapping install calls (GeckoView
     * rejects the second).
     */
    private suspend fun reconcile(shouldBeInstalled: Boolean) {
        if (inFlight) return
        if (shouldBeInstalled && current == null) {
            install()
        } else if (!shouldBeInstalled && current != null) {
            uninstall()
        }
    }

    private suspend fun install() {
        inFlight = true
        val installed = withContext(Dispatchers.Main) {
            suspendCoroutine<WebExtension?> { cont ->
                engine.installBuiltInWebExtension(
                    id = EXTENSION_ID,
                    url = EXTENSION_URL,
                    onSuccess = { ext ->
                        Log.i(tag, "nira-bg-play installed")
                        cont.resume(ext)
                    },
                    onError = { err ->
                        Log.w(tag, "install FAILED", err)
                        cont.resume(null)
                    },
                )
            }
        }
        inFlight = false
        if (installed != null) {
            current = installed
            // Push the current allow-list to the just-installed
            // extension; subsequent changes are picked up by the
            // bridge's own collector.
            try {
                NiraBgPlayAllowlistBridge.attach(
                    extension = installed,
                    allowlist = allowlist,
                    scope = scope,
                )
            } catch (t: Throwable) {
                Log.w(tag, "bridge attach failed", t)
            }
        }
    }

    private suspend fun uninstall() {
        val ext = current ?: return
        inFlight = true
        withContext(Dispatchers.Main) {
            suspendCoroutine<Unit> { cont ->
                engine.uninstallWebExtension(
                    ext = ext,
                    onSuccess = {
                        Log.i(tag, "nira-bg-play uninstalled")
                        cont.resume(Unit)
                    },
                    onError = { id, err ->
                        // Non-fatal: Gecko leaves the extension around
                        // until the next process start if an uninstall
                        // partially fails. Log and continue.
                        Log.w(tag, "uninstall FAILED for $id", err)
                        cont.resume(Unit)
                    },
                )
            }
        }
        current = null
        inFlight = false
    }

    companion object {
        const val EXTENSION_ID = "nira-bg-play@prirai.android.nira"
        const val EXTENSION_URL = "resource://android/assets/extensions/nira-bg-play/"
    }
}
