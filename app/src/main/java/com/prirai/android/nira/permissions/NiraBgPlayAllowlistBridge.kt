package com.prirai.android.nira.permissions

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge between [BackgroundPlaybackAllowlist] (Kotlin, user-facing)
 * and the `nira-bg-play` built-in WebExtension (JS, runs inside Gecko).
 *
 * Flow:
 *   1. The extension's `background.js` calls
 *      `browser.runtime.connectNative("nira.bgplay.allowlist")` on
 *      startup to open a persistent port to us.
 *   2. We hold on to the `Port` and `postMessage` the current
 *      allow-list each time it changes (plus one eager push the
 *      moment the port connects).
 *   3. The background script keeps the allow-list in memory and
 *      mirrors it to `browser.storage.local` so content scripts that
 *      load before the first push still have a last-known value.
 *   4. Content scripts call `runtime.sendMessage(...)` to the
 *      background to check if their origin is allow-listed; only
 *      allow-listed origins get the Page-Visibility / pause-override
 *      patches applied.
 *
 * The port is unique per process and per installed extension. If the
 * extension is reinstalled on a later launch, [attach] is called
 * again and we re-register the handler and reset the port.
 */
internal object NiraBgPlayAllowlistBridge {

    private const val TAG = "NiraBgPlayBridge"
    private const val PORT_NAME = "nira.bgplay.allowlist"

    @Volatile
    private var activePort: Port? = null

    @Volatile
    private var collectorJob: Job? = null

    /**
     * Attach the bridge to [extension]. Idempotent: subsequent calls
     * cancel any prior collector and reset the port reference, so a
     * reinstall of the extension (or a cold-start re-attach) does not
     * leak collectors.
     *
     * @param extension the just-installed [WebExtension].
     * @param allowlist source of truth for which origins are allowed.
     * @param scope long-lived scope that outlives any single port
     *   connection; used to collect [BackgroundPlaybackAllowlist.origins].
     */
    fun attach(
        extension: WebExtension,
        allowlist: BackgroundPlaybackAllowlist,
        scope: CoroutineScope,
    ) {
        collectorJob?.cancel()
        activePort = null

        val handler = object : MessageHandler {
            override fun onPortConnected(port: Port) {
                activePort = port
                // Eagerly push the current allow-list so the extension
                // has a correct view even before the first mutation.
                try {
                    port.postMessage(buildMessage(allowlist.origins.value))
                } catch (t: Throwable) {
                    Log.w(TAG, "initial postMessage failed", t)
                }
            }

            override fun onPortDisconnected(port: Port) {
                if (activePort === port) activePort = null
            }
        }

        extension.registerBackgroundMessageHandler(PORT_NAME, handler)

        // Push on every allow-list change. If the port is not yet
        // connected we skip - the eager push in onPortConnected will
        // deliver the current value when the extension's background
        // script connects.
        collectorJob = scope.launch(Dispatchers.Main) {
            allowlist.origins.collect { origins ->
                val port = activePort ?: return@collect
                try {
                    port.postMessage(buildMessage(origins))
                } catch (t: Throwable) {
                    Log.w(TAG, "postMessage on change failed", t)
                }
            }
        }
    }

    private fun buildMessage(origins: Set<String>): JSONObject {
        val array = JSONArray()
        origins.forEach { array.put(it) }
        return JSONObject().apply {
            put("type", "set")
            put("origins", array)
        }
    }
}
