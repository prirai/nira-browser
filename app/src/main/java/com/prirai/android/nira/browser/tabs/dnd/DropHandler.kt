package com.prirai.android.nira.browser.tabs.dnd

import com.prirai.android.nira.browser.tabs.compose.TabViewModel

class DropHandler(private val viewModel: TabViewModel) {
    
    fun handleDrop(payload: DragPayload, targetId: TabId?, targetType: TargetType) {
        when {
            // Tab dropped on Tab -> Create new group
            payload.type == DragType.TAB && targetType == TargetType.TAB && targetId != null -> {
                viewModel.createGroupFromTabs(payload.tabId, targetId)
            }
            
            // Tab dropped on Group -> Add to group
            payload.type == DragType.TAB && targetType == TargetType.GROUP && targetId != null -> {
                viewModel.addTabToGroup(payload.tabId, targetId, payload.fromContainerId)
            }
            
            // Tab dropped on root -> Ungroup
            payload.type == DragType.TAB && targetType == TargetType.ROOT -> {
                viewModel.ungroupTab(payload.tabId, payload.fromContainerId)
            }
            
            // Group dropped on root -> Reorder
            payload.type == DragType.GROUP && targetType == TargetType.ROOT && targetId != null -> {
                viewModel.reorderGroup(payload.tabId, targetId)
            }
        }
    }
}

enum class TargetType {
    TAB,
    GROUP,
    ROOT
}
