package com.prirai.android.nira.browser.tabgroups

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [TabGroup::class, TabGroupMember::class],
    version = 2,
    exportSchema = false
)
abstract class TabGroupDatabase : RoomDatabase() {
    abstract fun tabGroupDao(): TabGroupDao
    
    companion object {
        @Volatile
        private var INSTANCE: TabGroupDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add contextId column to tab_groups table
                database.execSQL("ALTER TABLE tab_groups ADD COLUMN contextId TEXT DEFAULT NULL")
            }
        }
        
        fun getInstance(context: Context): TabGroupDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TabGroupDatabase::class.java,
                    "tab_groups_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}