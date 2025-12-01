package com.prirai.android.nira.browser.tabs

import mozilla.components.browser.state.state.TabSessionState

/**
 * Represents either a tab group or an ungrouped tab
 */
sealed class TabItem {
    data class Group(
        val groupId: String,
        val name: String,
        val color: Int,
        val tabs: List<TabSessionState>,
        val isExpanded: Boolean = false
    ) : TabItem()
    
    data class SingleTab(
        val tab: TabSessionState
    ) : TabItem()
}
