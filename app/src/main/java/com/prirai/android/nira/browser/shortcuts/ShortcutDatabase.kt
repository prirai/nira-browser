package com.prirai.android.nira.browser.shortcuts

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database backing the home-screen "shortcuts" grid.
 *
 * The migration list used to be triplicated across `BrowserFragment`,
 * `ComposeHomeFragment` and `AddShortcutDialogFragment`, and two other callsites
 * (`HomepageJavaScriptInterface`, `AppRequestInterceptor`) were opening the same
 * SQLite file with **no migrations at all** - a latent crash for anyone
 * upgrading from schema v1 or v2. Everything now goes through
 * [ShortcutDatabase.getInstance], which:
 *  - lazily builds a single [RoomDatabase] per process (matches the pattern
 *    used by `TabGroupDatabase.getInstance`),
 *  - always installs [ALL_MIGRATIONS],
 *  - is safe to call from any thread.
 */
@Database(
    entities = [ShortcutEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class ShortcutDatabase : RoomDatabase() {

    abstract fun shortcutDao(): ShortcutDao

    companion object {
        private const val DB_NAME = "shortcut-database"

        /** v1 -> v2: add nullable `title` column so shortcut rows can carry a label. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shortcutentity ADD COLUMN title TEXT")
            }
        }

        /**
         * v2 -> v3: rebuild the table so `uid` is `INTEGER NOT NULL` (Room ordering
         * change - the schema used to allow implicit nullability). We copy through a
         * `_new` table to keep existing rows.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE shortcutentity_new (uid INTEGER NOT NULL, url TEXT, title TEXT, PRIMARY KEY(uid))",
                )
                db.execSQL(
                    "INSERT INTO shortcutentity_new (uid, url, title) SELECT uid, url, title FROM shortcutentity",
                )
                db.execSQL("DROP TABLE shortcutentity")
                db.execSQL("ALTER TABLE shortcutentity_new RENAME TO shortcutentity")
            }
        }

        /**
         * Full migration list, in order. Adding a new schema version? Append the
         * new [Migration] object here and nothing else needs to change.
         */
        private val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        @Volatile
        private var INSTANCE: ShortcutDatabase? = null

        /**
         * Returns the process-wide [ShortcutDatabase] singleton. Threadsafe.
         */
        fun getInstance(context: Context): ShortcutDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShortcutDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
