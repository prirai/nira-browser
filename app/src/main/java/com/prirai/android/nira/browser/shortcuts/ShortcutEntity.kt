package com.prirai.android.nira.browser.shortcuts

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class
ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo(name = "url") var url: String?,
    @ColumnInfo(name = "title") var title: String?
)