package com.prirai.android.nira.browser.tabs.dnd

typealias TabId = String

data class DragPayload(
    val tabId: TabId,
    val fromContainerId: TabId?, // null if root
    val type: DragType
)

enum class DragType {
    TAB,
    GROUP
}
