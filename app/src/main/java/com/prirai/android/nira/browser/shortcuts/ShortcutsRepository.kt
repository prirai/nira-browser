package com.prirai.android.nira.browser.shortcuts

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mozilla.components.feature.top.sites.PinnedSiteStorage
import mozilla.components.feature.top.sites.TopSite

/**
 * Home-screen favorites, backed by the Android Components pinned-sites database.
 *
 * Favorites that were saved in the legacy Room `shortcut-database` are imported
 * once, on first access, and the old database file is then deleted.
 */
class ShortcutsRepository(
    private val context: Context,
    private val storage: PinnedSiteStorage,
) {
    private val migrationLock = Mutex()

    @Volatile
    private var migrated = false

    suspend fun getAll(): List<TopSite> {
        migrateLegacyIfNeeded()
        return storage.getPinnedSites()
    }

    /** Pins [url]; returns false if it was already pinned. */
    suspend fun add(url: String, title: String): Boolean {
        migrateLegacyIfNeeded()
        if (storage.getPinnedSites().any { it.url == url }) return false
        storage.addPinnedSite(title = title, url = url, isDefault = false)
        return true
    }

    suspend fun deleteById(id: Long) {
        migrateLegacyIfNeeded()
        storage.getPinnedSites().firstOrNull { it.id == id }?.let { storage.removePinnedSite(it) }
    }

    private suspend fun migrateLegacyIfNeeded() {
        if (migrated) return
        migrationLock.withLock {
            if (migrated) return
            withContext(Dispatchers.IO) {
                val dbFile = context.getDatabasePath(LEGACY_DB_NAME)
                if (dbFile.exists()) {
                    try {
                        val legacy = readLegacy(dbFile.path)
                        if (legacy.isNotEmpty()) {
                            storage.addAllPinnedSites(legacy.map { (url, title) -> title to url }, isDefault = false)
                        }
                        context.deleteDatabase(LEGACY_DB_NAME)
                    } catch (e: Exception) {
                        // Keep the old file so the import is retried on next launch.
                        Log.w(TAG, "Legacy shortcut import failed", e)
                        return@withContext
                    }
                }
                migrated = true
            }
        }
    }

    private fun readLegacy(path: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT url, title FROM shortcutentity ORDER BY uid", null).use { c ->
                while (c.moveToNext()) {
                    val url = c.getString(0) ?: continue
                    out += url to (c.getString(1) ?: url)
                }
            }
        }
        return out
    }

    private companion object {
        const val TAG = "ShortcutsRepository"
        const val LEGACY_DB_NAME = "shortcut-database"
    }
}
