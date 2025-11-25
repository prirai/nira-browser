package com.prirai.android.nira.browser.tabgroups

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [TabGroup::class, TabGroupMember::class],
    version = 1,
    exportSchema = false
)
abstract class TabGroupDatabase : RoomDatabase() {
    abstract fun tabGroupDao(): TabGroupDao
    
    companion object {
        @Volatile
        private var INSTANCE: TabGroupDatabase? = null
        
        fun getInstance(context: Context): TabGroupDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TabGroupDatabase::class.java,
                    "tab_groups_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}