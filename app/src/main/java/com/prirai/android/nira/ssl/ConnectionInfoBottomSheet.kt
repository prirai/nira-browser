package com.prirai.android.nira.ssl

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

/**
 * Bottom sheet dialog fragment for displaying connection/security information
 * Similar to Firefox's QuickSettings panel
 */
class ConnectionInfoBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_connection_info_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val securityInfo = context.components.store.state.selectedTab?.content?.securityInfo
        val selectedTab = context.components.store.state.selectedTab
        val url = selectedTab?.content?.url ?: ""
        val host = url.tryGetHostFromUrl()
        val title = selectedTab?.content?.title ?: host

        val securityIcon = view.findViewById<ImageView>(R.id.security_icon)
        val securityStatus = view.findViewById<TextView>(R.id.security_status)
        val websiteTitle = view.findViewById<TextView>(R.id.website_title)
        val websiteUrl = view.findViewById<TextView>(R.id.website_url)
        val certificateIssuer = view.findViewById<TextView>(R.id.certificate_issuer)
        val certificateHolder = view.findViewById<TextView>(R.id.certificate_holder)
        val trackingProtectionSwitch = view.findViewById<SwitchMaterial>(R.id.tracking_protection_switch)
        val trackingProtectionSubtitle = view.findViewById<TextView>(R.id.tracking_protection_subtitle)
        val clearCookiesButton = view.findViewById<MaterialButton>(R.id.clear_cookies_button)
        val closeButton = view.findViewById<MaterialButton>(R.id.close_button)

        // Set security status
        if (securityInfo?.secure == true) {
            securityIcon.setImageResource(R.drawable.ic_baseline_lock)
            securityStatus.text = "Connection is secure"
            securityStatus.setTextColor(0xFF5cb85c.toInt())
        } else {
            securityIcon.setImageResource(R.drawable.ic_baseline_lock_open)
            securityStatus.text = "Connection is not secure"
            securityStatus.setTextColor(0xFFd9534f.toInt())
        }

        // Set website info
        websiteTitle.text = title
        websiteUrl.text = url

        // Set certificate info
        certificateIssuer.text = securityInfo?.issuer ?: "Unknown"
        certificateHolder.text = securityInfo?.host ?: host

        // Setup tracking protection toggle
        val userPrefs = UserPreferences(context)
        trackingProtectionSwitch.isChecked = userPrefs.trackingProtection
        
        trackingProtectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            userPrefs.trackingProtection = isChecked
            trackingProtectionSubtitle.text = if (isChecked) {
                "Blocking trackers"
            } else {
                "Not blocking trackers"
            }
            
            // Reload the page to apply changes
            context.components.sessionUseCases.reload()
            
            Toast.makeText(
                context,
                if (isChecked) "Tracking protection enabled" else "Tracking protection disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        trackingProtectionSubtitle.text = if (trackingProtectionSwitch.isChecked) {
            "Blocking trackers"
        } else {
            "Not blocking trackers"
        }

        // Clear cookies button
        clearCookiesButton.setOnClickListener {
            showClearCookiesConfirmation()
        }

        // Close button
        closeButton.setOnClickListener {
            dismiss()
        }
    }

    private fun showClearCookiesConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear cookies and site data?")
            .setMessage("This will clear all cookies and site data for this website. You may need to sign in again.")
            .setPositiveButton("Clear") { _, _ ->
                clearCookiesForCurrentSite()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCookiesForCurrentSite() {
        val context = requireContext()
        val selectedTab = context.components.store.state.selectedTab
        val url = selectedTab?.content?.url ?: ""
        
        if (url.isNotEmpty()) {
            // Clear cookies for the current site
            context.components.engine.clearData(
                mozilla.components.concept.engine.Engine.BrowsingData.select(
                    mozilla.components.concept.engine.Engine.BrowsingData.COOKIES,
                    mozilla.components.concept.engine.Engine.BrowsingData.DOM_STORAGES
                ),
                host = url.tryGetHostFromUrl()
            )
            
            Toast.makeText(
                context,
                "Cookies and site data cleared",
                Toast.LENGTH_SHORT
            ).show()
            
            // Reload the page
            context.components.sessionUseCases.reload()
            
            dismiss()
        }
    }

    companion object {
        const val TAG = "ConnectionInfoBottomSheet"

        fun newInstance(): ConnectionInfoBottomSheet {
            return ConnectionInfoBottomSheet()
        }
    }
}
