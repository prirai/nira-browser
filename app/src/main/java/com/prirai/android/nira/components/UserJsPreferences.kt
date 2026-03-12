package com.prirai.android.nira.components

import android.content.Context
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

/**
 * Applies privacy-hardening GeckoView preferences inspired by arkenfox/betterfox.
 *
 * GeckoView 148 exposes only a limited set of typed setter methods on
 * [GeckoRuntimeSettings] / [GeckoRuntimeSettings.Builder]. For preferences that
 * don't have a typed API, we write a Gecko config JSON file
 * (`{"prefs": { "pref.name": value }}`) and pass its path to
 * [GeckoRuntimeSettings.Builder.configFilePath]. These become Gecko default prefs
 * that are still overridable by the user via about:config.
 *
 * Safe browsing is intentionally excluded — it is controlled by UserPreferences.safeBrowsing.
 *
 * Usage:
 * ```
 * // 1. Before builder.build()
 * UserJsPreferences.applyTo(applicationContext, builder)
 * val runtimeSettings = builder.<other methods>.build()
 *
 * // 2. After builder.build(), apply typed setters
 * UserJsPreferences.applyTypedSettings(runtimeSettings)
 * ```
 */
object UserJsPreferences {

    private const val CONFIG_FILE_NAME = "gecko_privacy_config.json"

    // ── Prefs applied via GeckoView config JSON file ──────────────────────────
    // (No typed builder/setter API exists for these in GeckoView 148.)

    private val privacyPrefs = mapOf<String, Any>(
        // Disables navigator.sendBeacon — prevents silent background data leakage
        "beacon.enabled" to false,
        // Classic fingerprint resistance (letterboxing, uniform UA, etc.)
        "privacy.resistFingerprinting" to true,
        // Send Do-Not-Track request header
        "privacy.donottrackheader.enabled" to true,
        // Clear cookies and HTTP sessions on browser shutdown
        "privacy.clearOnShutdown.cookies" to true,
        "privacy.clearOnShutdown.sessions" to true,
        // Auto-reject cookie consent banners (normal + private browsing)
        "cookiebanners.service.mode" to 1,
        "cookiebanners.service.mode.privateBrowsing" to 1,
    )

    private val telemetryPrefs = mapOf<String, Any>(
        "datareporting.healthreport.uploadEnabled" to false,
        "datareporting.policy.dataSubmissionEnabled" to false,
        "app.normandy.enabled" to false,
        "app.shield.optoutstudies.enabled" to false,
        "browser.ping-centre.telemetry" to false,
    )

    private val networkPrefs = mapOf<String, Any>(
        // Send Referer only for same-origin requests
        "network.http.referer.XOriginPolicy" to 2,
        // Trim cross-origin Referer to origin only
        "network.http.referer.XOriginTrimmingPolicy" to 2,
        "network.http.sendRefererHeader" to 1,
        // Disable all forms of speculative/predictive network activity
        "network.predictor.enabled" to false,
        "network.prefetch-next" to false,
        "network.dns.disablePrefetch" to true,
        "browser.urlbar.speculativeConnect.enabled" to false,
    )

    private val securityPrefs = mapOf<String, Any>(
        // HTTPS-first: try HTTPS before falling back to HTTP
        "dom.security.https_first" to true,
        // HTTPS-only: block all plain HTTP requests
        "dom.security.https_only_mode" to true,
        // Do not probe HTTP while in HTTPS-only mode
        "dom.security.https_only_mode_send_http_background_request" to false,
        // Block pop-up windows opened while a page is loading
        "dom.disable_open_during_load" to true,
        // Restrict which events may trigger a pop-up
        "dom.popup_allowed_events" to "click dblclick mousedown pointerdown",
        // Enable next-gen storage (prevents cross-site storage tracking)
        "dom.storage.next_gen" to true,
        // Prevent sites from detecting whether assistive technology is active
        "accessibility.force_disabled" to 1,
    )

    private val mediaPrefs = mapOf<String, Any>(
        // Block all media autoplay by default
        "media.autoplay.blocking_policy" to 2,
        "media.autoplay.default" to 5,
    )

    private val performancePrefs = mapOf<String, Any>(
        // Reduce layout notification bursts for smoother rendering
        "content.notify.interval" to 100000,
        // Disable session-history content-viewer cache to reduce memory usage
        "browser.sessionhistory.max_total_viewers" to 0,
    )

    // ── Config file writing ──────────────────────────────────────────────────

    private fun buildConfigJson(): String {
        val allPrefs = privacyPrefs + telemetryPrefs + networkPrefs +
                securityPrefs + mediaPrefs + performancePrefs

        return buildString {
            append("{\"prefs\":{")
            allPrefs.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(",")
                append("\"$key\":")
                when (value) {
                    is Boolean -> append(value)
                    is Int -> append(value)
                    is String -> {
                        append("\"")
                        append(value.replace("\\", "\\\\").replace("\"", "\\\""))
                        append("\"")
                    }
                    else -> append(value)
                }
            }
            append("}}")
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Writes the privacy config JSON file and registers it with [builder] via
     * [GeckoRuntimeSettings.Builder.configFilePath]. Must be called **before**
     * [GeckoRuntimeSettings.Builder.build].
     */
    fun applyTo(context: Context, builder: GeckoRuntimeSettings.Builder) {
        val configFile = File(context.filesDir, CONFIG_FILE_NAME)
        configFile.writeText(buildConfigJson())
        builder.configFilePath(configFile.absolutePath)
    }

    /**
     * Applies privacy settings that have a typed setter API on [GeckoRuntimeSettings].
     * Must be called **after** [GeckoRuntimeSettings.Builder.build].
     *
     * These typed setters are belt-and-suspenders on top of the config file approach:
     * they set the live runtime preference value directly, not just the Gecko default.
     */
    fun applyTypedSettings(settings: GeckoRuntimeSettings) {
        // Global Privacy Control (privacy.globalprivacycontrol.enabled)
        settings.setGlobalPrivacyControl(true)
        // Enhanced fingerprinting protection for both normal and private browsing
        settings.setFingerprintingProtection(true)
        settings.setFingerprintingProtectionPrivateBrowsing(true)
    }
}
