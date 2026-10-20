package com.prirai.android.nira.privacy.cookies

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Standalone Room database for Nira-local cookie-retention policies.
 *
 * Kept separate from `TabGroupDatabase` for two reasons:
 *   1. The two schemas evolve on completely different cadences; coupling
 *      them would force a migration every time either side touches a
 *      column.
 *   2. The sweeper's `CoroutineWorker` must be able to open the DB in a
 *      WorkManager process without pulling the entire tab-group graph
 *      into memory.
 *
 * `fallbackToDestructiveMigration(true)` is intentional for v1: the data
 * is user-provided retention windows and savedAt stamps - losing them on
 * an unforeseen schema change is acceptable and strictly better than
 * crashing on launch after an upgrade.
 */
@Database(
    entities = [CookiePolicy::class],
    version = 1,
    exportSchema = false,
)
abstract class CookiePolicyDatabase : RoomDatabase() {
    abstract fun cookiePolicyDao(): CookiePolicyDao

    companion object {
        @Volatile
        private var instance: CookiePolicyDatabase? = null

        fun getInstance(context: Context): CookiePolicyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CookiePolicyDatabase::class.java,
                    "nira_cookie_policies.db",
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
