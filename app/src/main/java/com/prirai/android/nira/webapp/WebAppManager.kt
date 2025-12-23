package com.prirai.android.nira.webapp

import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.*
import java.net.URL

/**
 * Manager for Progressive Web Apps (PWAs)
 * Handles installation, storage, and management of PWAs
 */
class WebAppManager(private val context: Context) {
    private val webAppDatabase: WebAppDatabase by lazy {
        WebAppDatabase.getDatabase(context)
    }

    /**
     * Install a new PWA
     */
    suspend fun installWebApp(
        url: String,
        name: String,
        manifestUrl: String?,
        icon: Bitmap?,
        themeColor: String?,
        backgroundColor: String?,
        profileId: String = "default"
    ): Long {
        val webApp = WebAppEntity(
            id = UUID.randomUUID().toString(),
            url = url,
            name = name,
            manifestUrl = manifestUrl,
            iconUrl = icon?.let { saveIconToFile(it) }, // Convert bitmap to file path
            themeColor = themeColor,
            backgroundColor = backgroundColor,
            installDate = System.currentTimeMillis(),
            lastUsedDate = System.currentTimeMillis(),
            launchCount = 0,
            isEnabled = true,
            profileId = profileId
        )

        return webAppDatabase.webAppDao().insert(webApp)
    }

    /**
     * Get all installed PWAs
     */
    fun getAllWebApps(): Flow<List<WebAppEntity>> {
        return webAppDatabase.webAppDao().getAll()
    }

    /**
     * Get a specific PWA by ID
     */
    suspend fun getWebAppById(id: String): WebAppEntity? {
        return webAppDatabase.webAppDao().getById(id)
    }

    /**
     * Get PWA by URL
     */
    suspend fun getWebAppByUrl(url: String): WebAppEntity? {
        return webAppDatabase.webAppDao().getByUrl(url)
    }

    /**
     * Get PWA by URL and profile
     */
    suspend fun getWebAppByUrlAndProfile(url: String, profileId: String): WebAppEntity? {
        return webAppDatabase.webAppDao().getByUrlAndProfile(url, profileId)
    }

    /**
     * Check if web app exists with URL and profile
     */
    suspend fun webAppExists(url: String, profileId: String): Boolean {
        return webAppDatabase.webAppDao().getByUrlAndProfile(url, profileId) != null
    }

    /**
     * Update PWA usage statistics
     */
    suspend fun updateWebAppUsage(id: String) {
        webAppDatabase.webAppDao().updateLastUsed(id, System.currentTimeMillis())
        webAppDatabase.webAppDao().incrementLaunchCount(id)
    }

    /**
     * Update a web app entity
     */
    suspend fun updateWebApp(webApp: WebAppEntity) {
        webAppDatabase.webAppDao().update(webApp)
    }

    /**
     * Uninstall a PWA
     */
    suspend fun uninstallWebApp(id: String) {
        webAppDatabase.webAppDao().deleteById(id)
    }

    /**
     * Toggle PWA enabled state
     */
    suspend fun setWebAppEnabled(id: String, enabled: Boolean) {
        webAppDatabase.webAppDao().setEnabled(id, enabled)
    }

    /**
     * Update PWA icon
     */
    suspend fun updateWebAppIcon(id: String, icon: Bitmap) {
        val iconPath = saveIconToFile(icon)
        webAppDatabase.webAppDao().updateIcon(id, iconPath)
    }

    /**
     * Clear all PWA data (storage, cache, etc.)
     */
    suspend fun clearWebAppData(id: String) {
        // TODO: Implement actual storage clearing
        // This would involve clearing service worker caches, localStorage, etc.
    }

    /**
     * Check if PWA supports offline mode
     */
    suspend fun checkOfflineSupport(url: String): Boolean {
        // Check if the PWA has a service worker and cache manifest
        // This would involve checking the web app manifest and service worker registration
        return false // Placeholder - would need GeckoView integration
    }

    /**
     * Get offline status for all PWAs
     */
    suspend fun getOfflineStatusForAllApps(): List<WebAppWithOfflineSupport> {
        val allApps = webAppDatabase.webAppDao().getAllSynchronously()
        return allApps.map { webApp ->
            val isOfflineCapable = checkOfflineSupport(webApp.url)
            WebAppWithOfflineSupport(
                webApp = webApp,
                isOfflineCapable = isOfflineCapable,
                offlineStorageSize = 0, // Would calculate actual cache size
                lastCacheUpdate = System.currentTimeMillis(), // Would get from service worker
                canUpdateWhileOffline = isOfflineCapable // Simple logic for now
            )
        }
    }

    /**
     * Force update PWA cache (for offline use)
     */
    suspend fun updatePwaCache(id: String) {
        // This would trigger service worker cache update
        // Would need GeckoView session integration
    }

    /**
     * Get total offline storage used by all PWAs
     */
    suspend fun getTotalOfflineStorage(): Long {
        // Would sum up all PWA cache storage
        return 0 // Placeholder
    }

    /**
     * Get notification settings for a PWA
     */
    suspend fun getNotificationSettings(webAppId: String): PwaNotificationSettings {
        // Would retrieve from database or preferences
        return PwaNotificationSettings(
            webAppId = webAppId,
            notificationsEnabled = true,
            showBadges = true,
            playSounds = true,
            vibrate = true,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    /**
     * Update notification settings for a PWA
     */
    suspend fun updateNotificationSettings(settings: PwaNotificationSettings) {
        // Would save to database or preferences
    }

    /**
     * Check if PWA can send notifications
     */
    fun canSendNotifications(webAppId: String): Boolean {
        // Would check GeckoView permissions and our own settings
        return true // Placeholder
    }

    /**
     * Save icon bitmap to file and return path
     */
    private fun saveIconToFile(icon: Bitmap): String {
        // This would save the bitmap to internal storage and return the path
        // For now, we'll return a placeholder
        return "icon_${UUID.randomUUID()}.png"
    }

    /**
     * Load icon from file path
     */
    fun loadIconFromFile(iconPath: String?): Bitmap? {
        // This would load the bitmap from the file path
        // For now, return null
        return null
    }

    companion object {
        /**
         * Extract base URL from a full URL (scheme + host)
         */
        fun getBaseUrl(url: String): String {
            return try {
                val parsed = URL(url)
                "${parsed.protocol}://${parsed.host}"
            } catch (e: Exception) {
                url
            }
        }
    }
}

private suspend fun WebAppDao.getAllSynchronously(): List<WebAppEntity> {
    // Helper to get all apps synchronously for offline status checks
    return getAll().first() // Convert Flow to List
}

/**
 * Entity representing an installed PWA
 */
@Entity(tableName = "web_apps")
data class WebAppEntity(
    @PrimaryKey val id: String,
    val url: String,
    val name: String,
    val manifestUrl: String?,
    val iconUrl: String?, // Store icon as URL/path instead of Bitmap
    val themeColor: String?,
    val backgroundColor: String?,
    val installDate: Long,
    val lastUsedDate: Long,
    val launchCount: Int,
    val isEnabled: Boolean,
    val profileId: String = "default" // Associated profile, defaults to "default"
)

/**
 * Extended WebAppEntity with offline support
 */
data class WebAppWithOfflineSupport(
    val webApp: WebAppEntity,
    val isOfflineCapable: Boolean,
    val offlineStorageSize: Long,
    val lastCacheUpdate: Long,
    val canUpdateWhileOffline: Boolean
)

/**
 * Database Access Object for WebApps
 */
@Dao
interface WebAppDao {
    @Insert
    suspend fun insert(webApp: WebAppEntity): Long

    @Update
    suspend fun update(webApp: WebAppEntity)

    @Query("SELECT * FROM web_apps ORDER BY lastUsedDate DESC")
    fun getAll(): Flow<List<WebAppEntity>>

    @Query("SELECT * FROM web_apps WHERE id = :id")
    suspend fun getById(id: String): WebAppEntity?

    @Query("SELECT * FROM web_apps WHERE url = :url")
    suspend fun getByUrl(url: String): WebAppEntity?

    @Query("SELECT * FROM web_apps WHERE url = :url AND profileId = :profileId")
    suspend fun getByUrlAndProfile(url: String, profileId: String): WebAppEntity?

    @Query("DELETE FROM web_apps WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE web_apps SET lastUsedDate = :lastUsedDate WHERE id = :id")
    suspend fun updateLastUsed(id: String, lastUsedDate: Long)

    @Query("UPDATE web_apps SET launchCount = launchCount + 1 WHERE id = :id")
    suspend fun incrementLaunchCount(id: String)

    @Query("UPDATE web_apps SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE web_apps SET iconUrl = :iconUrl WHERE id = :id")
    suspend fun updateIcon(id: String, iconUrl: String)
}

/**
 * Room Database for WebApps
 */
@Database(entities = [WebAppEntity::class], version = 3)
abstract class WebAppDatabase : RoomDatabase() {
    abstract fun webAppDao(): WebAppDao

    companion object {
        @Volatile
        private var INSTANCE: WebAppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add profileId column with default value "Default"
                database.execSQL("ALTER TABLE web_apps ADD COLUMN profileId TEXT NOT NULL DEFAULT 'Default'")
            }
        }
        
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Fix profileId capitalization to match browser profiles
                // "Default" -> "default", "Work" -> "work", "School" -> "school", etc.
                database.execSQL("UPDATE web_apps SET profileId = LOWER(profileId)")
            }
        }

        fun getDatabase(context: Context): WebAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WebAppDatabase::class.java,
                    "web_apps_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}