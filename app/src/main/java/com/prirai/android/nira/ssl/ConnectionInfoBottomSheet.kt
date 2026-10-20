package com.prirai.android.nira.ssl

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.support.ktx.kotlin.getOrigin
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
        val certificateSection = view.findViewById<View>(R.id.certificate_section)
        val certificateIssuer = view.findViewById<TextView>(R.id.certificate_issuer)
        val certificateHolder = view.findViewById<TextView>(R.id.certificate_holder)
        val privacySection = view.findViewById<View>(R.id.privacy_section)
        val trackingProtectionSwitch = view.findViewById<MaterialSwitch>(R.id.tracking_protection_switch)
        val trackingProtectionSubtitle = view.findViewById<TextView>(R.id.tracking_protection_subtitle)
        val closeButton = view.findViewById<MaterialButton>(R.id.close_button)

        // Check if this is an internal page
        val isInternalPage = isInternalUrl(url)

        // Set security status
        if (isInternalPage) {
            securityIcon.setImageResource(R.drawable.shield_lock_24)
            securityStatus.text = "Internal page"
            securityStatus.setTextColor(context.getColor(R.color.security_indicator_secure))
        } else if (securityInfo?.isSecure == true) {
            securityIcon.setImageResource(R.drawable.security_24)
            securityStatus.text = "Connection is secure"
            securityStatus.setTextColor(com.prirai.android.nira.theme.ColorConstants.getSecureColor(context))
        } else {
            securityIcon.setImageResource(R.drawable.encrypted_off_24)
            securityStatus.text = "Connection is not secure"
            securityStatus.setTextColor(com.prirai.android.nira.theme.ColorConstants.getInsecureColor(context))
        }

        // Set website info
        websiteTitle.text = title
        websiteUrl.text = url

        // Show/hide certificate and privacy sections for internal pages
        if (isInternalPage) {
            certificateSection.visibility = View.GONE
            privacySection.visibility = View.GONE
        } else {
            certificateSection.visibility = View.VISIBLE
            privacySection.visibility = View.VISIBLE
            
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

            // Site permissions pill: always visible on non-internal pages so
            // the user can enter the detailed sheet regardless of whether the
            // site has made any permission decisions yet. The icon strip is
            // still populated from the stored SitePermissions row when
            // available. Clear-cookies lives inside the detailed sheet.
            setupSitePermissionsPill(view, url, selectedTab?.content?.private == true)
        }

        // Close button
        closeButton.setOnClickListener {
            dismiss()
        }
    }

    /**
     * Always-visible "Site permissions" entry pill. The pill opens
     * [SitePermissionsBottomSheet] regardless of whether the current
     * origin has any stored permission decisions, so the user can grant
     * / revoke / clear-cookies without having to make a decision first
     * (the "always visible" behaviour requested post-v0.5.14).
     *
     * When the on-disk `SitePermissions` row has non-default values the
     * pill still renders up to five summary icons as a glanceable hint
     * of what's been decided for this site.
     *
     * Only hidden when the URL has no origin (chrome:// / about:). The
     * caller already short-circuits internal pages by not calling us,
     * but we double-check here as a defence-in-depth guard.
     */
    private fun setupSitePermissionsPill(view: View, url: String, isPrivate: Boolean) {
        val pill = view.findViewById<MaterialCardView>(R.id.site_permissions_pill)
        val iconsRow = view.findViewById<LinearLayout>(R.id.site_permissions_icons)
        val origin = url.getOrigin()
        if (origin == null) {
            pill.visibility = View.GONE
            return
        }

        val context = requireContext()
        val iconTint = ColorStateList.valueOf(
            MaterialColors.getColor(
                pill,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
            ),
        )

        pill.visibility = View.VISIBLE
        iconsRow.removeAllViews()
        pill.setOnClickListener {
            SitePermissionsBottomSheet.newInstance()
                .show(parentFragmentManager, SitePermissionsBottomSheet.TAG)
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val stored = withContext(Dispatchers.IO) {
                context.components.permissionStorage.findSitePermissionsBy(
                    origin,
                    private = isPrivate,
                )
            }
            val icons = SitePermissionsBottomSheet.summaryIcons(stored)
            if (icons.isEmpty()) return@launch

            val density = resources.displayMetrics.density
            val sizePx = (18f * density).toInt()
            val marginPx = (6f * density).toInt()
            icons.forEach { iconRes ->
                val iv = AppCompatImageView(context)
                val lp = LinearLayout.LayoutParams(sizePx, sizePx)
                lp.marginStart = marginPx
                iv.layoutParams = lp
                iv.setImageDrawable(AppCompatResources.getDrawable(context, iconRes))
                iv.imageTintList = iconTint
                iconsRow.addView(iv)
            }
        }
    }

    private fun isInternalUrl(url: String): Boolean {
        return url.startsWith("about:") ||
               url.startsWith("moz-extension://") ||
               url.startsWith("resource://") ||
               url.startsWith("chrome://")
    }

    companion object {
        const val TAG = "ConnectionInfoBottomSheet"

        fun newInstance(): ConnectionInfoBottomSheet {
            return ConnectionInfoBottomSheet()
        }
    }
}
