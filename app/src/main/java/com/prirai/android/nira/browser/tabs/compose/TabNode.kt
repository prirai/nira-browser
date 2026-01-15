package com.prirai.android.nira.browser.tabs.compose

import android.graphics.Bitmap
import mozilla.components.browser.state.state.TabSessionState

/**
 * Unified data model for tab system.
 * Single source of truth for all tab views (pill bar, list, grid).
 */
sealed interface TabNode {
    val id: String
}

/**
 * Individual tab
 */
data class Tab(
    override val id: String,
    val session: TabSessionState,
    val title: String,
    val url: String,
    val favicon: Bitmap?,
    val thumbnail: Bitmap?,
    val isIncognito: Boolean,
    val isSelected: Boolean
) : TabNode {
    companion object {
        fun fromSession(session: TabSessionState, isSelected: Boolean): Tab {
            return Tab(
                id = session.id,
                session = session,
                title = session.content.title.ifEmpty { "New Tab" },
                url = session.content.url,
                favicon = session.content.icon,
                thumbnail = null, // Thumbnails are managed separately
                isIncognito = session.content.private,
                isSelected = isSelected
            )
        }
    }
}

/**
 * Group of tabs
 */
data class TabGroup(
    override val id: String,
    val name: String,
    val color: Int,
    val tabs: List<Tab>,
    val collapsed: Boolean,
    val contextId: String?
) : TabNode {
    val tabCount: Int get() = tabs.size
}
