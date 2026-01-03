package com.prirai.android.nira.browser.tabs.model

import android.graphics.Bitmap
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

typealias TabId = String

sealed interface TabNode {
    val id: TabId
}

data class Tab(
    override val id: TabId,
    val title: String,
    val url: String,
    val favicon: Bitmap?,
    val thumbnail: Bitmap?,
    val isIncognito: Boolean,
    val isSelected: Boolean = false
) : TabNode

data class TabGroup(
    override val id: TabId,
    val name: String,
    val color: Int,
    val tabs: SnapshotStateList<Tab>,
    val collapsed: Boolean = false
) : TabNode {
    constructor(
        id: TabId,
        name: String,
        color: Int,
        tabs: List<Tab>,
        collapsed: Boolean = false
    ) : this(id, name, color, mutableStateListOf<Tab>().apply { addAll(tabs) }, collapsed)
}
