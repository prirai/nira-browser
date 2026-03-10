package com.prirai.android.nira.browser.tabs.compose

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton to track when the tab sheet is dismissed
 * Used to trigger auto-scroll in the tab bar only after the sheet closes
 */
object TabSheetStateManager {
    
    private val _dismissedTimestamp = MutableStateFlow(0L)
    val dismissedTimestamp: StateFlow<Long> = _dismissedTimestamp
    
    /**
     * Call this when the tab sheet is dismissed
     */
    fun notifyTabSheetDismissed() {
        _dismissedTimestamp.value = System.currentTimeMillis()
    }
}
