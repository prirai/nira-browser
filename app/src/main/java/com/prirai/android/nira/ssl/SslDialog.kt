package com.prirai.android.nira.ssl

import android.content.Context

/**
 * Shows an informative bottom sheet with connection/security information
 * Similar to Firefox's QuickSettings panel
 */
fun Context.showSslDialog() {
    val activity = this as? androidx.fragment.app.FragmentActivity ?: return
    
    val bottomSheet = ConnectionInfoBottomSheet.newInstance()
    bottomSheet.show(activity.supportFragmentManager, ConnectionInfoBottomSheet.TAG)
}
