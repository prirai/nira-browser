package com.prirai.android.nira.ssl

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissions.AutoplayStatus
import mozilla.components.concept.engine.permission.SitePermissions.Status
import mozilla.components.support.ktx.kotlin.getOrigin
import mozilla.components.ui.icons.R as iconsR

/**
 * Material 3 bottom sheet that lists every site-permission for the current
 * origin and lets the user flip each one.
 *
 * Opened from `ConnectionInfoBottomSheet`'s "Site permissions" pill. The
 * backing store is Nira's `components.permissionStorage`, which is the
 * same `GeckoSitePermissionsStorage` AC uses for its own prompt
 * decisions - so toggling here is immediately picked up by
 * `SitePermissionsFeature.shouldShowPrompt` and sites that previously
 * triggered a prompt will stop prompting (and vice versa).
 *
 * Row layout: icon + title + optional subtitle + MaterialSwitch. Rows are
 * built dynamically in `addPermissionRow(...)` so the layout file does not
 * have to repeat 12 near-identical blocks.
 *
 * UX conventions:
 * - A `Status` field has three values (ALLOWED / NO_DECISION / BLOCKED).
 *   The M3 switch is boolean, so we treat NO_DECISION as off (user hasn't
 *   decided -> site will prompt) and ALLOWED as on. Flipping on writes
 *   ALLOWED; flipping off writes BLOCKED (so sites don't keep prompting).
 *   Subtitle text makes the tri-state visible.
 * - AutoplayStatus is already two-state (BLOCKED/ALLOWED); switch maps 1:1.
 */
class SitePermissionsBottomSheet : BottomSheetDialogFragment() {

    private var current: SitePermissions? = null
    private var origin: String = ""
    private var isPrivate: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_site_permissions_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val selectedTab = context.components.store.state.selectedTab
        val url = selectedTab?.content?.url.orEmpty()
        isPrivate = selectedTab?.content?.private == true
        origin = url.getOrigin() ?: run {
            // No valid origin (chrome:// / about:); nothing to show.
            dismiss()
            return
        }

        view.findViewById<TextView>(R.id.origin_label).text = origin
        view.findViewById<MaterialButton>(R.id.close_button).setOnClickListener { dismiss() }

        val listContainer = view.findViewById<LinearLayout>(R.id.permissions_list)
        val emptyHint = view.findViewById<TextView>(R.id.empty_hint)

        // Query storage on IO; render on Main.
        viewLifecycleOwner.lifecycleScope.launch {
            val stored = withContext(Dispatchers.IO) {
                context.components.permissionStorage.findSitePermissionsBy(
                    origin,
                    private = isPrivate,
                )
            }
            current = stored
            renderRows(listContainer, emptyHint, stored)
        }
    }

    /**
     * Build the row list from the stored permissions. Only permissions the
     * site has actually interacted with (non-default values) are shown so
     * the sheet does not become a wall of twelve "Ask every time" entries
     * for a page that never asked for anything.
     *
     * The autoplay permissions are an exception: they always have a
     * concrete value (BLOCKED or ALLOWED), so showing them unconditionally
     * would still be a wall. We only show them when the user previously
     * had a prompt or when `stored != null` (which implies at least one
     * permission has been touched for this origin).
     */
    private fun renderRows(
        container: LinearLayout,
        emptyHint: TextView,
        stored: SitePermissions?,
    ) {
        container.removeAllViews()

        if (stored == null) {
            emptyHint.visibility = View.VISIBLE
            return
        }
        emptyHint.visibility = View.GONE

        // Standard (Status) permissions - skip NO_DECISION so the sheet
        // stays focused on the site's actual decisions.
        addStatusRow(
            container, stored.location,
            R.string.permission_location,
            iconsR.drawable.mozac_ic_location_24,
        ) { copy(location = it) }

        addStatusRow(
            container, stored.notification,
            R.string.permission_notification,
            iconsR.drawable.mozac_ic_notification_24,
        ) { copy(notification = it) }

        addStatusRow(
            container, stored.microphone,
            R.string.permission_microphone,
            iconsR.drawable.mozac_ic_microphone_24,
        ) { copy(microphone = it) }

        addStatusRow(
            container, stored.camera,
            R.string.permission_camera,
            iconsR.drawable.mozac_ic_camera_24,
        ) { copy(camera = it) }

        addStatusRow(
            container, stored.localStorage,
            R.string.permission_persistent_storage,
            iconsR.drawable.mozac_ic_storage_24,
        ) { copy(localStorage = it) }

        addStatusRow(
            container, stored.mediaKeySystemAccess,
            R.string.permission_drm,
            iconsR.drawable.mozac_ic_cookies_24,
        ) { copy(mediaKeySystemAccess = it) }

        addStatusRow(
            container, stored.crossOriginStorageAccess,
            R.string.permission_cross_origin_storage,
            iconsR.drawable.mozac_ic_cookies_24,
        ) { copy(crossOriginStorageAccess = it) }

        addStatusRow(
            container, stored.localDeviceAccess,
            R.string.permission_local_device_access,
            iconsR.drawable.mozac_ic_device_mobile_24,
        ) { copy(localDeviceAccess = it) }

        addStatusRow(
            container, stored.localNetworkAccess,
            R.string.permission_local_network_access,
            iconsR.drawable.mozac_ic_local_network_24,
        ) { copy(localNetworkAccess = it) }

        // Autoplay rows - these are two-state so always meaningful once
        // the origin has any stored permissions row.
        addAutoplayRow(
            container, stored.autoplayAudible,
            R.string.permission_autoplay_audible,
            iconsR.drawable.mozac_ic_autoplay_24,
        ) { copy(autoplayAudible = it) }

        addAutoplayRow(
            container, stored.autoplayInaudible,
            R.string.permission_autoplay_inaudible,
            iconsR.drawable.mozac_ic_autoplay_24,
        ) { copy(autoplayInaudible = it) }

        if (container.childCount == 0) {
            // Every Status permission was NO_DECISION and autoplay rows
            // somehow failed to render - fall back to the empty hint.
            emptyHint.visibility = View.VISIBLE
        }
    }

    private fun addStatusRow(
        container: LinearLayout,
        value: Status,
        @StringRes titleRes: Int,
        @DrawableRes iconRes: Int,
        apply: SitePermissions.(Status) -> SitePermissions,
    ) {
        // Hide permissions the site has not touched yet - keeps the sheet
        // focused on actual decisions.
        if (value == Status.NO_DECISION) return
        val row = inflateRow(container, titleRes, iconRes)
        val switch = row.findViewById<MaterialSwitch>(R.id.permission_switch)
        val subtitle = row.findViewById<TextView>(R.id.permission_subtitle)

        switch.isChecked = value.isAllowed()
        bindSubtitle(subtitle, value)
        switch.setOnCheckedChangeListener { _, isChecked ->
            val newStatus = if (isChecked) Status.ALLOWED else Status.BLOCKED
            bindSubtitle(subtitle, newStatus)
            persist { apply(newStatus) }
        }
        container.addView(row)
    }

    private fun addAutoplayRow(
        container: LinearLayout,
        value: AutoplayStatus,
        @StringRes titleRes: Int,
        @DrawableRes iconRes: Int,
        apply: SitePermissions.(AutoplayStatus) -> SitePermissions,
    ) {
        val row = inflateRow(container, titleRes, iconRes)
        val switch = row.findViewById<MaterialSwitch>(R.id.permission_switch)
        val subtitle = row.findViewById<TextView>(R.id.permission_subtitle)

        switch.isChecked = value == AutoplayStatus.ALLOWED
        subtitle.visibility = View.VISIBLE
        subtitle.setText(
            if (switch.isChecked) R.string.permission_status_allowed
            else R.string.permission_status_blocked,
        )
        switch.setOnCheckedChangeListener { _, isChecked ->
            val newStatus = if (isChecked) AutoplayStatus.ALLOWED else AutoplayStatus.BLOCKED
            subtitle.setText(
                if (isChecked) R.string.permission_status_allowed
                else R.string.permission_status_blocked,
            )
            persist { apply(newStatus) }
        }
        container.addView(row)
    }

    private fun inflateRow(
        container: LinearLayout,
        @StringRes titleRes: Int,
        @DrawableRes iconRes: Int,
    ): View {
        val row = LayoutInflater.from(container.context)
            .inflate(R.layout.item_site_permission_row, container, false)
        row.findViewById<ImageView>(R.id.permission_icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.permission_title).setText(titleRes)
        return row
    }

    private fun bindSubtitle(subtitle: TextView, value: Status) {
        subtitle.visibility = View.VISIBLE
        subtitle.setText(
            when (value) {
                Status.ALLOWED -> R.string.permission_status_allowed
                Status.BLOCKED -> R.string.permission_status_blocked
                Status.NO_DECISION -> R.string.permission_status_ask
            },
        )
    }

    /**
     * Apply a mutation to the current SitePermissions row and persist to
     * storage. If no row existed before (`current == null`), save a fresh
     * row; otherwise update in place.
     */
    private fun persist(mutate: SitePermissions.() -> SitePermissions) {
        val existing = current
        val updated = (existing ?: freshRow()).mutate()
            .copy(savedAt = System.currentTimeMillis())
        current = updated
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val storage = requireContext().components.permissionStorage
            if (existing == null) {
                storage.save(updated, request = null, private = isPrivate)
            } else {
                storage.update(updated, private = isPrivate)
            }
        }
    }

    private fun freshRow(): SitePermissions = SitePermissions(
        origin = origin,
        savedAt = System.currentTimeMillis(),
    )

    companion object {
        const val TAG = "SitePermissionsBottomSheet"

        fun newInstance(): SitePermissionsBottomSheet = SitePermissionsBottomSheet()

        /**
         * Icon references for the permissions currently in a non-default
         * state on the given stored row. Used by `ConnectionInfoBottomSheet`
         * to render the "icons of enabled permissions" visual next to the
         * "Site permissions" pill.
         *
         * Returns up to 5 icons so the pill doesn't overflow; the full
         * list is always available by tapping into the bottom sheet.
         */
        fun summaryIcons(stored: SitePermissions?): List<Int> {
            if (stored == null) return emptyList()
            val out = mutableListOf<Int>()
            if (stored.location != Status.NO_DECISION) {
                out += iconsR.drawable.mozac_ic_location_24
            }
            if (stored.notification != Status.NO_DECISION) {
                out += iconsR.drawable.mozac_ic_notification_24
            }
            if (stored.microphone != Status.NO_DECISION) {
                out += iconsR.drawable.mozac_ic_microphone_24
            }
            if (stored.camera != Status.NO_DECISION) {
                out += iconsR.drawable.mozac_ic_camera_24
            }
            if (stored.localStorage != Status.NO_DECISION) {
                out += iconsR.drawable.mozac_ic_storage_24
            }
            return out.take(5)
        }
    }
}
