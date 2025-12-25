package com.prirai.android.nira.integration

import android.content.Context
import androidx.fragment.app.Fragment
import com.prirai.android.nira.R
import mozilla.components.feature.qr.QrFeature
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.log.logger.Logger

/**
 * Integration for QR code scanning using Mozilla's QrFeature.
 * Allows users to scan QR codes containing URLs or text.
 */
class QrScannerIntegration(
    private val context: Context,
    private val fragment: Fragment,
    private val onScanResult: (String) -> Unit,
    private val onNeedToRequestPermissions: (Array<String>) -> Unit
) : LifecycleAwareFeature {
    
    private val logger = Logger("QrScannerIntegration")
    
    private val qrFeature = QrFeature(
        context = context,
        fragmentManager = fragment.parentFragmentManager,
        onScanResult = { result ->
            try {
                onScanResult(result)
            } catch (e: Exception) {
                logger.error("Error processing QR scan result", e)
            }
        },
        onNeedToRequestPermissions = onNeedToRequestPermissions
    )
    
    /**
     * Initiate QR code scanning.
     */
    fun scan() {
        try {
            qrFeature.scan(R.id.container)
        } catch (e: Exception) {
            logger.error("Error launching QR scanner", e)
            // Silently fail - camera might not be available
        }
    }
    
    override fun start() {
        try {
            qrFeature.start()
        } catch (e: Exception) {
            logger.error("Error starting QR feature", e)
        }
    }
    
    override fun stop() {
        try {
            qrFeature.stop()
        } catch (e: Exception) {
            logger.error("Error stopping QR feature", e)
        }
    }
    
    /**
     * Handle permission results from the fragment.
     */
    fun onPermissionsResult(permissions: Array<String>, grantResults: IntArray) {
        try {
            qrFeature.onPermissionsResult(permissions, grantResults)
        } catch (e: Exception) {
            logger.error("Error handling permission result", e)
        }
    }
}
