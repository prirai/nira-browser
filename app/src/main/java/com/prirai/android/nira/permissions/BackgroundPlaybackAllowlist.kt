package com.prirai.android.nira.permissions

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-origin allow-list for the "Background playback" permission.
 *
 * Semantically similar to AC's [mozilla.components.concept.engine.permission.SitePermissions]
 * entries but intentionally stored outside AC's `SitePermissions` data
 * class, because AC's row is a fixed closed set of permission fields
 * and adding a new field would require forking mozilla-components.
 *
 * Flow:
 * - UI (`SitePermissionsBottomSheet`) toggles an origin on/off via
 *   [allow] / [block].
 * - `BrowserApp.initializeAfterFirstFrame` installs the
 *   `nira-bg-play` built-in extension once at launch and attaches
 *   [NiraBgPlayAllowlistBridge], which pushes the current [origins]
 *   set to the extension's background script over a persistent
 *   native-messaging port. The extension gates per-origin inside
 *   its own content.js, so a cleared or empty allow-list is a
 *   no-op without having to uninstall the extension.
 *
 * Origin format: the exact `scheme://host:port` form returned by
 * `support-ktx`'s `String.getOrigin()` extension (e.g.
 * `"https://www.youtube.com:443"`). This is what AC writes for its
 * own `SitePermissions.origin` field, so the bottom-sheet UI can
 * reuse the same origin derivation.
 *
 * The set is persisted as a `stringSet` in a dedicated SharedPreferences
 * file so it does not pollute the generic `scw_preferences` bag.
 */
class BackgroundPlaybackAllowlist private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _origins = MutableStateFlow(loadOrigins())

    /**
     * Reactive view of the current allow-list. `BrowserApp` subscribes
     * to this to drive extension install / uninstall.
     */
    val origins: StateFlow<Set<String>> = _origins.asStateFlow()

    /** `true` iff [origin] is on the allow-list. */
    fun isAllowed(origin: String): Boolean = origin in _origins.value

    /** Add [origin] to the allow-list. No-op if already present. */
    fun allow(origin: String) {
        val current = _origins.value
        if (origin in current) return
        val updated = current + origin
        persist(updated)
    }

    /** Remove [origin] from the allow-list. No-op if absent. */
    fun block(origin: String) {
        val current = _origins.value
        if (origin !in current) return
        val updated = current - origin
        persist(updated)
    }

    /**
     * One-time migration helper: seed the allow-list with YouTube
     * origins when the user previously had the deprecated global
     * `backgroundPlaybackYoutube` boolean enabled. Idempotent; the
     * migration flag is itself a pref so repeat process starts don't
     * re-seed.
     */
    fun migrateFromLegacyGlobalPref(enabledBefore: Boolean) {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        prefs.edit { putBoolean(KEY_MIGRATED, true) }
        if (!enabledBefore) return
        val seeded = _origins.value + LEGACY_YOUTUBE_ORIGINS
        persist(seeded)
    }

    private fun persist(updated: Set<String>) {
        prefs.edit { putStringSet(KEY_ORIGINS, updated) }
        _origins.value = updated
    }

    private fun loadOrigins(): Set<String> =
        prefs.getStringSet(KEY_ORIGINS, emptySet())?.toSet() ?: emptySet()

    companion object {
        private const val PREFS_NAME = "nira_bg_playback_allowlist"
        private const val KEY_ORIGINS = "allowed_origins"
        private const val KEY_MIGRATED = "legacy_global_pref_migrated"

        /**
         * Origins seeded on migration from the legacy global
         * `backgroundPlaybackYoutube` boolean. Matches the hostnames
         * the extension's `content_scripts.matches` glob targets, so
         * users who had the old toggle enabled keep working after
         * upgrade.
         */
        private val LEGACY_YOUTUBE_ORIGINS = setOf(
            "https://www.youtube.com:443",
            "https://m.youtube.com:443",
            "https://music.youtube.com:443",
            "https://youtube.com:443",
        )

        @Volatile private var instance: BackgroundPlaybackAllowlist? = null

        fun getInstance(context: Context): BackgroundPlaybackAllowlist =
            instance ?: synchronized(this) {
                instance ?: BackgroundPlaybackAllowlist(context).also { instance = it }
            }
    }
}
