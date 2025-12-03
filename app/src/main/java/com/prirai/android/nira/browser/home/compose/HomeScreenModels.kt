package com.prirai.android.nira.browser.home.compose

data class ShortcutItem(
    val id: Int = 0,
    val title: String,
    val url: String,
    val icon: String? = null
)

data class BookmarkItem(
    val id: String,
    val title: String,
    val url: String,
    val icon: String? = null,
    val isFolder: Boolean = false
)
