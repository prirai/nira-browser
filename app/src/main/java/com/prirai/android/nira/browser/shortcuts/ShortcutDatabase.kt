package com.prirai.android.nira.browser.shortcuts

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ShortcutEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ShortcutDatabase : RoomDatabase() {
    abstract fun shortcutDao(): ShortcutDao
}