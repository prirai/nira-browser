package com.prirai.android.nira.browser.profile

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.tabs.compose.TabOrderManager
import com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles export and import of browser profiles as ZIP files.
 *
 * ZIP contents:
 *   manifest.json  — version/timestamp metadata
 *   profile.json   — BrowserProfile fields
 *   tabgroups.json — tab groups belonging to this profile (contextId-filtered)
 *   tabs.json      — tab ordering data from TabOrderManager
 *
 * Note: GeckoView session data (cookies, history, passwords) is NOT included
 * because it is stored internally by the engine and not accessible.
 * Tab IDs in tabs.json refer to sessions that will not exist on the target
 * device — the ordering structure is preserved for group name/color metadata.
 */
class ProfileExportManager private constructor(private val context: Context) {

    companion object {
        private const val EXPORT_VERSION = 1
        private const val APP_VERSION = "nira-1.0"
        private const val PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

        @Volatile
        private var instance: ProfileExportManager? = null

        fun getInstance(context: Context): ProfileExportManager =
            instance ?: synchronized(this) {
                instance ?: ProfileExportManager(context.applicationContext).also { instance = it }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Export
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exports the given profile to a ZIP file stored in the app's cache dir.
     * Returns a content:// URI via FileProvider suitable for sharing.
     */
    suspend fun exportProfile(profile: BrowserProfile): Uri = withContext(Dispatchers.IO) {
        val contextId = profileToContextId(profile)
        val timestamp = System.currentTimeMillis()
        val safeName = profile.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val fileName = "nira_profile_${safeName}_${timestamp}.zip"

        val exportFile = File(context.cacheDir, fileName)

        ZipOutputStream(exportFile.outputStream().buffered()).use { zos ->
            writeEntry(zos, "manifest.json", buildManifestJson(timestamp))
            writeEntry(zos, "profile.json", buildProfileJson(profile))
            writeEntry(zos, "tabgroups.json", buildTabGroupsJson(contextId))
            writeEntry(zos, "tabs.json", buildTabsJson(profile))
        }

        val authority = context.packageName + PROVIDER_AUTHORITY_SUFFIX
        FileProvider.getUriForFile(context, authority, exportFile)
    }

    private fun buildManifestJson(timestamp: Long): String =
        JSONObject().apply {
            put("version", EXPORT_VERSION)
            put("exportedAt", timestamp)
            put("appVersion", APP_VERSION)
        }.toString()

    private fun buildProfileJson(profile: BrowserProfile): String =
        JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("color", profile.color)
            put("emoji", profile.emoji)
            put("isDefault", profile.isDefault)
            put("createdAt", profile.createdAt)
        }.toString()

    private fun buildTabGroupsJson(contextId: String?): String {
        val groupManager = UnifiedTabGroupManager.getInstance(context)
        val groups = groupManager.getAllGroups().filter { matchesContext(it, contextId) }

        val array = JSONArray()
        groups.forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("colorInt", group.color)
                put("colorName", colorToName(group.color))
                put("createdAt", group.createdAt)
                put("tabIds", JSONArray(group.tabIds))
            })
        }
        return array.toString()
    }

    private suspend fun buildTabsJson(profile: BrowserProfile): String {
        val groupManager = UnifiedTabGroupManager.getInstance(context)
        val tabOrderManager = TabOrderManager.getInstance(context, groupManager)
        val profileId = if (profile.isDefault || profile.id == "default") "default" else profile.id
        val order = tabOrderManager.loadOrder(profileId)

        val items = JSONArray()
        order.primaryOrder.forEach { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.SingleTab -> {
                    items.put(JSONObject().apply {
                        put("type", "tab")
                        put("tabId", item.tabId)
                    })
                }
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    items.put(JSONObject().apply {
                        put("type", "group")
                        put("groupId", item.groupId)
                        put("groupName", item.groupName)
                        put("colorInt", item.color)
                        put("isExpanded", item.isExpanded)
                        put("tabIds", JSONArray(item.tabIds))
                    })
                }
            }
        }

        return JSONObject().apply {
            put("profileId", order.profileId)
            put("lastModified", order.lastModified)
            put("order", items)
        }.toString()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Import
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Imports a profile from a ZIP file URI.
     * Returns the newly created [BrowserProfile].
     *
     * @throws IllegalArgumentException if the ZIP is not a valid Nira export.
     */
    suspend fun importProfile(zipUri: Uri): BrowserProfile = withContext(Dispatchers.IO) {
        val entries = readZipEntries(zipUri)

        val manifestJson = entries["manifest.json"]
            ?: throw IllegalArgumentException("Not a valid Nira profile export")
        val profileJson = entries["profile.json"]
            ?: throw IllegalArgumentException("Not a valid Nira profile export")

        val manifest = JSONObject(manifestJson)
        val version = manifest.optInt("version", 0)
        if (version < 1) throw IllegalArgumentException("Not a valid Nira profile export")

        val profileObj = JSONObject(profileJson)
        val profileName = profileObj.optString("name", "Imported Profile")
        val color = profileObj.optInt("color", BrowserProfile.PROFILE_COLORS.first())
        val emoji = profileObj.optString("emoji", "👤")

        // Avoid duplicate names
        val profileManager = ProfileManager.getInstance(context)
        val existingNames = profileManager.getAllProfiles().map { it.name }
        val finalName = if (profileName in existingNames) "$profileName (imported)" else profileName

        val importedProfile = profileManager.createProfile(finalName, color, emoji)

        // Import tab groups (metadata only; tab session IDs are not restorable)
        val tabGroupsJson = entries["tabgroups.json"]
        if (!tabGroupsJson.isNullOrBlank()) {
            importTabGroups(importedProfile, tabGroupsJson)
        }

        importedProfile
    }

    private suspend fun importTabGroups(profile: BrowserProfile, tabGroupsJson: String) {
        // Tab groups are exported as metadata for reference.
        // Because GeckoView session data cannot be transferred between devices,
        // groups cannot be meaningfully re-created on import (they would be empty).
        // The exported tabgroups.json preserves group names/colors for future use
        // if tab migration support is ever added.
        //
        // Currently: no-op — profile metadata is preserved, groups are not re-created.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun readZipEntries(zipUri: Uri): Map<String, String> {
        val inputStream = context.contentResolver.openInputStream(zipUri)
            ?: throw IllegalArgumentException("Cannot open file")

        val entries = mutableMapOf<String, String>()
        ZipInputStream(inputStream.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    private fun writeEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    /** Returns the contextId string used to filter DB/DataStore entries for a profile. */
    private fun profileToContextId(profile: BrowserProfile): String? =
        when {
            profile.isDefault || profile.id == "default" -> null // matches null and "profile_default"
            else -> "profile_${profile.id}"
        }

    /** True if the group belongs to the given contextId (handles null == "profile_default"). */
    private fun matchesContext(group: TabGroupData, contextId: String?): Boolean =
        if (contextId == null) {
            group.contextId == null || group.contextId == "profile_default"
        } else {
            group.contextId == contextId
        }

    private fun colorToName(color: Int): String =
        com.prirai.android.nira.theme.ColorConstants.TabGroups.getColorName(color)
}
