package com.prirai.android.nira.browser.shortcuts

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcutentity")
    fun getAll(): List<ShortcutEntity>

    @Query("SELECT * FROM shortcutentity WHERE uid IN (:shortcutIds)")
    fun loadAllByIds(shortcutIds: IntArray): List<ShortcutEntity>

    @Query("SELECT * FROM shortcutentity WHERE url LIKE :urlFind LIMIT 1")
    fun findByUrl(urlFind: String): ShortcutEntity

    @Update
    fun update(item: ShortcutEntity)

    @Insert
    fun insertAll(vararg item: ShortcutEntity)

    @Delete
    fun delete(item: ShortcutEntity)
}