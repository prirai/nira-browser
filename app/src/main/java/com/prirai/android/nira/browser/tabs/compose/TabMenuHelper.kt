package com.prirai.android.nira.browser.tabs.compose

import android.content.Context
import android.view.View
import kotlinx.coroutines.CoroutineScope
import mozilla.components.browser.state.state.TabSessionState

/**
 * Legacy wrapper for backward compatibility.
 * All menu implementations now use UnifiedTabMenus for consistency.
 */
object TabMenuHelper {
    
    fun showTabMenu(
        context: Context,
        anchorView: View,
        tab: TabSessionState,
        isInGroup: Boolean,
        viewModel: TabViewModel,
        scope: CoroutineScope,
        onMoveToProfile: (String) -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        if (isInGroup) {
            // Find the group ID for this tab
            val groupId = "unknown" // This should be passed in, but for now...
            UnifiedTabMenus.showGroupedTabMenu(
                context = context,
                anchorView = anchorView,
                tab = tab,
                groupId = groupId,
                viewModel = viewModel,
                scope = scope,
                onMoveToProfile = onMoveToProfile,
                onDismiss = onDismiss
            )
        } else {
            UnifiedTabMenus.showUngroupedTabMenu(
                context = context,
                anchorView = anchorView,
                tab = tab,
                viewModel = viewModel,
                scope = scope,
                onMoveToProfile = onMoveToProfile,
                onDismiss = onDismiss
            )
        }
    }
    
    fun showGroupMenu(
        context: Context,
        anchorView: View,
        groupId: String,
        viewModel: TabViewModel,
        scope: CoroutineScope,
        onRename: (String) -> Unit,
        onChangeColor: (String) -> Unit,
        onMoveToProfile: (String) -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        UnifiedTabMenus.showGroupMenu(
            context = context,
            anchorView = anchorView,
            groupId = groupId,
            groupName = "", // Could be improved
            viewModel = viewModel,
            scope = scope,
            onRename = onRename,
            onChangeColor = onChangeColor,
            onMoveToProfile = onMoveToProfile,
            onDismiss = onDismiss
        )
    }
}
