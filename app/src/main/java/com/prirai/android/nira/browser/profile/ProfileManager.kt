package com.prirai.android.nira.browser.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.prirai.android.nira.browser.SearchEnginePreferences
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException

/**
 * Manages browser profiles - creation, deletion, and persistence
 * Profiles are stored in SharedPreferences as JSON (`profiles` key, a
 * JSONArray of profile objects).
 *
 * # JSON format
 * The on-disk shape is the field-for-field dump of [BrowserProfile]:
 * `[{"id":"...","name":"...","color":12345,"emoji":"...","isDefault":false,"createdAt":169...}]`.
 * This matches the Moshi output that previous versions wrote, so existing
 * installs read correctly without any migration shim.
 *
 * # Why not Moshi
 * The previous implementation used Moshi + `KotlinJsonAdapterFactory`, which
 * pulls in kotlin-reflect. kotlin-reflect is a ~2.5 MB jar that classloads
 * on first use; `ProfileManager.getActiveProfile()` is called from
 * `BrowserActivity.onCreate` on the main thread, so the first launch paid
 * 20-60 ms of reflection classload on the critical path. Six-field manual
 * JSON mapping via Android's bundled `org.json` is zero-dependency and runs
 * in microseconds.
 */
class ProfileManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Scope used for the fire-and-forget work in setActiveProfile. Reusing a
    // single scope avoids allocating a transient CoroutineScope on every call
    // (the previous `kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch`
    // pattern created a new scope per call that was never cancelled).
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val PREFS_NAME = "profile_manager"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_LAST_PRIVATE_PROFILE = "last_private_profile"

        @Volatile
        private var instance: ProfileManager? = null

        fun getInstance(context: Context): ProfileManager {
            return instance ?: synchronized(this) {
                instance ?: ProfileManager(context.applicationContext).also { instance = it }
            }
        }

        // Field names - kept in sync with [BrowserProfile] property names so
        // the on-disk format is drop-in compatible with the previous Moshi
        // serialisation. Changing any of these is a format break and requires
        // a migration.
        private const val F_ID = "id"
        private const val F_NAME = "name"
        private const val F_COLOR = "color"
        private const val F_EMOJI = "emoji"
        private const val F_IS_DEFAULT = "isDefault"
        private const val F_CREATED_AT = "createdAt"

        private fun BrowserProfile.toJson(): JSONObject = JSONObject().apply {
            put(F_ID, id)
            put(F_NAME, name)
            put(F_COLOR, color)
            put(F_EMOJI, emoji)
            put(F_IS_DEFAULT, isDefault)
            put(F_CREATED_AT, createdAt)
        }

        private fun JSONObject.toProfile(): BrowserProfile = BrowserProfile(
            id = optString(F_ID, java.util.UUID.randomUUID().toString()),
            name = optString(F_NAME, ""),
            color = optInt(F_COLOR, 0),
            emoji = optString(F_EMOJI, "\uD83D\uDC64"), // 👤
            isDefault = optBoolean(F_IS_DEFAULT, false),
            createdAt = optLong(F_CREATED_AT, System.currentTimeMillis()),
        )
    }

    /**
     * Get all profiles (excluding private mode)
     * Always includes the default profile
     */
    fun getAllProfiles(): List<BrowserProfile> {
        val json = prefs.getString(KEY_PROFILES, null)
        val profiles = if (json != null) {
            parseProfilesJson(json)
        } else {
            emptyList()
        }

        // Always ensure default profile exists
        val defaultProfile = BrowserProfile.getDefaultProfile()
        return if (profiles.none { it.id == defaultProfile.id }) {
            listOf(defaultProfile) + profiles
        } else {
            profiles
        }
    }

    private fun parseProfilesJson(json: String): List<BrowserProfile> {
        return try {
            val array = JSONArray(json)
            val out = ArrayList<BrowserProfile>(array.length())
            for (i in 0 until array.length()) {
                out.add(array.getJSONObject(i).toProfile())
            }
            out
        } catch (e: JSONException) {
            android.util.Log.w("ProfileManager", "Malformed profiles JSON, dropping value", e)
            emptyList()
        }
    }

    private fun List<BrowserProfile>.toJsonString(): String {
        val array = JSONArray()
        forEach { array.put(it.toJson()) }
        return array.toString()
    }
    
    /**
     * Get the currently active profile
     */
    fun getActiveProfile(): BrowserProfile {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, "default")
        return getAllProfiles().find { it.id == activeId } ?: BrowserProfile.getDefaultProfile()
    }
    
    /**
     * Set the active profile
     */
    fun setActiveProfile(profile: BrowserProfile) {
        prefs.edit { putString(KEY_ACTIVE_PROFILE_ID, profile.id) }
        // Reuse the long-lived ioScope instead of allocating a one-shot
        // CoroutineScope per call. The previous pattern leaked a tiny amount
        // of coroutine machinery on every profile switch and skipped
        // SupervisorJob semantics.
        ioScope.launch {
            ProfileAddonPolicy.applyForProfile(context, profile.id)
            SearchEnginePreferences.apply(context, private = false)
        }
    }
    
    /**
     * Create a new profile
     */
    fun createProfile(name: String, color: Int, emoji: String): BrowserProfile {
        val newProfile = BrowserProfile(
            name = name,
            color = color,
            emoji = emoji,
            isDefault = false
        )
        
        val profiles = getAllProfiles().toMutableList()
        profiles.add(newProfile)
        saveProfiles(profiles)
        
        return newProfile
    }
    
    /**
     * Update an existing profile
     */
    fun updateProfile(profile: BrowserProfile) {
        if (profile.isDefault) {
            // Can't modify default profile name/icon, only in-memory representation
            return
        }
        
        val profiles = getAllProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            profiles[index] = profile
            saveProfiles(profiles)
        }
    }
    
    /**
     * Delete a profile (cannot delete default)
     */
    fun deleteProfile(profileId: String) {
        if (profileId == "default") {
            throw IllegalArgumentException("Cannot delete default profile")
        }
        
        val profiles = getAllProfiles().toMutableList()
        profiles.removeAll { it.id == profileId }
        saveProfiles(profiles)
        
        // If deleted profile was active, switch to default
        if (getActiveProfile().id == profileId) {
            setActiveProfile(BrowserProfile.getDefaultProfile())
        }
        
        // Clean up profile-specific storage
        cleanupProfileStorage(profileId)
    }
    
    /**
     * Check if we're in private browsing mode
     */
    fun isPrivateMode(): Boolean {
        return prefs.getBoolean(KEY_LAST_PRIVATE_PROFILE, false)
    }
    
    /**
     * Set private browsing mode
     */
    fun setPrivateMode(isPrivate: Boolean) {
        prefs.edit { putBoolean(KEY_LAST_PRIVATE_PROFILE, isPrivate) }
    }
    
    private fun saveProfiles(profiles: List<BrowserProfile>) {
        // Don't save default profile to prefs, it's generated
        val toSave = profiles.filter { !it.isDefault }
        val json = toSave.toJsonString()
        prefs.edit { putString(KEY_PROFILES, json) }
    }
    
    private fun cleanupProfileStorage(profileId: String) {
        // Delete profile-specific session storage
        val profileDir = context.getDir("profile_$profileId", Context.MODE_PRIVATE)
        profileDir.deleteRecursively()
    }

    /**
     * Migrate a tab to another profile by updating its `contextId` while
     * preserving as much tab state as possible.
     *
     * @param tabId the ID of the tab to migrate
     * @param targetProfileId the ID of the target profile (`"private"` for private mode)
     * @return true if migration was applied, false if the tab was missing or already
     *         under the target profile.
     *
     * # Implementation notes
     * mozilla-components has no upstream action that updates
     * `TabSessionState.contextId` in place, so we still remove and re-add the tab.
     * The refactor from the previous version:
     *  - Reuses the **same tab id** on re-add, so `TabGroupMember` rows and any
     *    other Nira id-keyed metadata stay valid without an extra Room update.
     *  - Copies the full `TabSessionState` (title, url, `parentId`, `readerState`,
     *    `lastAccess`, `historyMetadata`, `source`, `createdAt`, thumbnails,
     *    `hasFormData`, `desktopMode`, `contextId'`) instead of re-issuing a fresh
     *    `addTab(url, title, ...)` that discards every non-URL field.
     *  - Unlinks the live `EngineSession` before the `RemoveTabAction` so
     *    `TabsRemovedMiddleware` skips `engineSession.close()` (it only closes
     *    sessions still linked at the time of removal). We re-link the same
     *    `EngineSession` to the new tab entry immediately after adding it, so
     *    Gecko never tears down the page - form data, scroll position and
     *    JS state all survive the migration.
     *
     * If the tab has no `EngineSession` yet (e.g. lazy-restored tab that hasn't
     * been selected), the unlink/link steps are trivially no-ops and the flow
     * degrades cleanly to a pure state-copy.
     */
    fun migrateTabToProfile(tabId: String, targetProfileId: String): Boolean {
        val store = context.components.store
        val tab = store.state.tabs.find { it.id == tabId } ?: return false

        val targetContextId = if (targetProfileId == "private") "private" else "profile_$targetProfileId"
        if (tab.contextId == targetContextId) return false

        val isTargetPrivate = targetProfileId == "private"
        val wasSelected = store.state.selectedTabId == tabId
        val existingEngineSession = tab.engineState.engineSession

        // Build a copy that keeps everything - id, engineState (minus the live
        // EngineSession which we re-link manually), content, thumbnails, index,
        // reader state, history metadata - but with the new contextId and
        // matching private flag.
        val rewritten = tab.copy(
            contextId = targetContextId,
            content = tab.content.copy(private = isTargetPrivate),
            // Detach the live session reference from the state copy so
            // AddTabAction doesn't try to reconcile it via EngineDelegateMiddleware.
            // We attach it via LinkEngineSessionAction below.
            engineState = tab.engineState.copy(engineSession = null),
        )

        // Step 1: unlink so TabsRemovedMiddleware doesn't close the session.
        if (existingEngineSession != null) {
            store.dispatch(
                mozilla.components.browser.state.action.EngineAction.UnlinkEngineSessionAction(tabId),
            )
        }

        // Step 2: remove the old entry. `selectParentIfExists = false` matches
        // the previous behavior - the caller decides selection via wasSelected.
        store.dispatch(
            mozilla.components.browser.state.action.TabListAction.RemoveTabAction(
                tabId = tabId,
                selectParentIfExists = false,
            ),
        )

        // Step 3: re-add the rewritten copy with the SAME id. `ProfileMiddleware`
        // sees a non-null contextId on the tab and passes it through unchanged
        // (see ProfileMiddleware.kt L39-L42).
        store.dispatch(
            mozilla.components.browser.state.action.TabListAction.AddTabAction(
                tab = rewritten,
                select = wasSelected,
            ),
        )

        // Step 4: re-link the surviving EngineSession, if any. This restores
        // Gecko's live page - no reload, no lost form data.
        if (existingEngineSession != null) {
            store.dispatch(
                mozilla.components.browser.state.action.EngineAction.LinkEngineSessionAction(
                    tabId = tabId,
                    engineSession = existingEngineSession,
                ),
            )
        }

        return true
    }
    
    /**
     * Migrate multiple tabs to another profile
     * @param tabIds List of tab IDs to migrate
     * @param targetProfileId The ID of the target profile ("private" for private mode)
     * @return Number of tabs successfully migrated
     */
    fun migrateTabsToProfile(tabIds: List<String>, targetProfileId: String): Int {
        var successCount = 0
        tabIds.forEach { tabId ->
            if (migrateTabToProfile(tabId, targetProfileId)) {
                successCount++
            }
        }
        return successCount
    }
}
