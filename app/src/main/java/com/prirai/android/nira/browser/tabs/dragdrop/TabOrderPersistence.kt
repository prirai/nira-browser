package com.prirai.android.nira.browser.tabs.dragdrop
import android.content.Context
@Deprecated("Legacy - no longer used")
class TabOrderPersistence(context: Context) {
    fun loadOrder(profileKey: String) = emptyList<String>()
    fun saveOrder(profileKey: String, order: List<String>) {}
}
