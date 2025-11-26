package com.prirai.android.nira.ssl

import android.content.Context
import android.net.http.SslCertificate
import android.view.LayoutInflater
import android.widget.TextView
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

/**
 * Shows an informative bottom sheet with connection/security information
 * Similar to Firefox's QuickSettings panel
 */
fun Context.showSslDialog() {
    val activity = this as? androidx.fragment.app.FragmentActivity ?: return
    
    val bottomSheet = ConnectionInfoBottomSheet.newInstance()
    bottomSheet.show(activity.supportFragmentManager, ConnectionInfoBottomSheet.TAG)
}
