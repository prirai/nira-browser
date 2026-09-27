package com.prirai.android.nira.webapp

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.net.URL
import java.util.UUID

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
     * Save icon bitmap to file and return path
     */
    private fun saveIconToFile(icon: Bitmap): String {
        try {
            val iconDir = context.getDir("webapp_icons", Context.MODE_PRIVATE)
            val iconFile = java.io.File(iconDir, "icon_${UUID.randomUUID()}.png")
            val outputStream = java.io.FileOutputStream(iconFile)
            icon.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            return iconFile.absolutePath
        } catch (e: Exception) {
            return "icon_${UUID.randomUUID()}.png"
        }
    }

    /**
     * Load icon from file path
     */
    fun loadIconFromFile(iconPath: String?): Bitmap? {
        if (iconPath.isNullOrEmpty()) return null
        
        try {
            val iconFile = java.io.File(iconPath)
            if (iconFile.exists()) {
                return android.graphics.BitmapFactory.decodeFile(iconPath)
            }
        } catch (e: Exception) {
            // Ignore
        }
        
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