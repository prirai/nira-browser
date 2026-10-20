package com.prirai.android.nira.ssl

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.prirai.android.nira.permissions.BackgroundPlaybackAllowlist
import com.prirai.android.nira.privacy.cookies.CookiePolicyRepository
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissions.AutoplayStatus
import mozilla.components.concept.engine.permission.SitePermissions.Status
import mozilla.components.support.ktx.kotlin.getOrigin
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl
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
        // Prefer an explicit origin passed via arguments (used by the
        // Site Exceptions screen in settings) so this sheet can be
        // reused for any origin, not just the currently selected tab.
        // Fall back to deriving from the selected tab for the normal
        // "lock icon -> site permissions pill" entry point.
        val argOrigin = arguments?.getString(ARG_ORIGIN)
        val argPrivate = arguments?.getBoolean(ARG_PRIVATE, false) ?: false
        val selectedTab = context.components.store.state.selectedTab
        val tabUrl = selectedTab?.content?.url.orEmpty()
        val url = argOrigin ?: tabUrl
        isPrivate = argOrigin?.let { argPrivate } ?: (selectedTab?.content?.private == true)
        origin = argOrigin ?: url.getOrigin() ?: run {
            // No valid origin (chrome:// / about:); nothing to show.
            dismiss()
            return
        }

        view.findViewById<TextView>(R.id.origin_label).text = origin
        view.findViewById<MaterialButton>(R.id.close_button).setOnClickListener { dismiss() }

        // Destructive site-scoped action: wipe cookies + DOM storage for
        // this origin and reload. Lives inside the permissions sheet so
        // the user sees permissions and data controls together.
        view.findViewById<MaterialButton>(R.id.clear_cookies_button).setOnClickListener {
            showClearCookiesConfirmation(url)
        }

        // Automatic cookie retention: let the user pick a window; the
        // CookiePolicyWorker then sweeps every ~hour and clears cookies
        // for any origin whose window has elapsed.
        val retentionButton = view.findViewById<MaterialButton>(R.id.cookie_retention_button)
        val retentionSubtitle = view.findViewById<TextView>(R.id.cookie_retention_subtitle)
        retentionButton.setOnClickListener {
            showCookieRetentionChooser(retentionSubtitle)
        }
        refreshCookieRetentionSubtitle(retentionSubtitle)

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
     * Build the row list from the stored permissions. Only AC permissions
     * the site has actually interacted with (non-default values) are
     * shown so the sheet does not become a wall of twelve "Ask every
     * time" entries for a page that never asked for anything.
     *
     * Background playback is a Nira-local permission (not in AC's
     * `SitePermissions` enum). Its row is always rendered since the
     * user may want to opt-in before the site is "touched" in any AC
     * sense.
     *
     * The autoplay permissions are also always shown when `stored` is
     * non-null, since they are two-state and AC seeds them to concrete
     * values.
     */
    private fun renderRows(
        container: LinearLayout,
        emptyHint: TextView,
        stored: SitePermissions?,
    ) {
        container.removeAllViews()

        // Background playback - always rendered because it is Nira-local
        // storage (BackgroundPlaybackAllowlist) and the user may want to
        // opt in before any AC permission has been touched.
        addBackgroundPlaybackRow(container)

        if (stored == null) {
            // Hint stays visible because no AC permission has been
            // interacted with yet, but the bg-playback row above is
            // still there to toggle.
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

    /**
     * Nira-local "Background playback" row. Backed by
     * [BackgroundPlaybackAllowlist] (SharedPref `Set<String>` of origins)
     * instead of AC's `SitePermissions`, because AC's data class is a
     * closed set and does not include a background-playback field.
     *
     * The row is always rendered (unlike AC rows that are gated on a
     * stored decision) so the user can opt in before interacting with
     * the site in any other way. Subtitle hints that the extension
     * currently only has an effect on YouTube hostnames; the toggle
     * still writes to the allow-list uniformly so a future broadening
     * of the extension's content-script match works without a schema
     * migration.
     */
    private fun addBackgroundPlaybackRow(container: LinearLayout) {
        val context = requireContext()
        val allowlist = BackgroundPlaybackAllowlist.getInstance(context)
        val row = inflateRow(
            container,
            R.string.permission_background_playback,
            iconsR.drawable.mozac_ic_autoplay_24,
        )
        val switch = row.findViewById<MaterialSwitch>(R.id.permission_switch)
        val subtitle = row.findViewById<TextView>(R.id.permission_subtitle)
        subtitle.visibility = View.VISIBLE
        subtitle.setText(R.string.permission_background_playback_subtitle)
        switch.isChecked = allowlist.isAllowed(origin)
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) allowlist.allow(origin) else allowlist.block(origin)
        }
        container.addView(row)
    }

    /**
     * Confirm-before-clear flow for cookies + DOM storage scoped to the
     * current site. The confirm dialog explicitly calls out that the
     * user may have to sign in again, matching the previous behaviour
     * in `ConnectionInfoBottomSheet` before this action was relocated
     * here.
     */
    private fun showClearCookiesConfirmation(url: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_cookies_dialog_title)
            .setMessage(R.string.clear_cookies_dialog_message)
            .setPositiveButton(R.string.clear_cookies_dialog_positive) { _, _ ->
                clearCookiesForCurrentSite(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearCookiesForCurrentSite(url: String) {
        if (url.isEmpty()) {
            dismiss()
            return
        }
        val context = requireContext()
        context.components.engine.clearData(
            data = mozilla.components.concept.engine.Engine.BrowsingData.select(
                mozilla.components.concept.engine.Engine.BrowsingData.COOKIES,
                mozilla.components.concept.engine.Engine.BrowsingData.DOM_STORAGES,
            ),
            host = url.tryGetHostFromUrl(),
        )
        Toast.makeText(
            context,
            R.string.clear_cookies_toast,
            Toast.LENGTH_SHORT,
        ).show()
        context.components.sessionUseCases.reload()
        dismiss()
    }

    /**
     * Pop up a single-choice dialog letting the user pick a retention
     * window for the current origin. Writing a value calls
     * `CookiePolicyRepository.setPolicy`; picking "Never" removes the
     * row. After writing we refresh the subtitle on the retention
     * button so the user sees the new state without re-opening the
     * sheet.
     */
    private fun showCookieRetentionChooser(subtitleView: TextView) {
        val labels = arrayOf(
            getString(R.string.cookie_retention_option_never),
            getString(R.string.cookie_retention_option_6h),
            getString(R.string.cookie_retention_option_24h),
            getString(R.string.cookie_retention_option_7d),
            getString(R.string.cookie_retention_option_30d),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.cookie_retention_dialog_title)
            .setItems(labels) { _, index ->
                val repo = CookiePolicyRepository.getInstance(requireContext())
                val origin = origin
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        when (index) {
                            0 -> repo.remove(origin)
                            1 -> repo.setPolicy(origin, hoursUntilClear = 6)
                            2 -> repo.setPolicy(origin, hoursUntilClear = 24)
                            3 -> repo.setPolicy(origin, hoursUntilClear = 24 * 7)
                            4 -> repo.setPolicy(origin, hoursUntilClear = 24 * 30)
                        }
                    }
                    refreshCookieRetentionSubtitle(subtitleView)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshCookieRetentionSubtitle(subtitleView: TextView) {
        val repo = CookiePolicyRepository.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val current = withContext(Dispatchers.IO) { repo.get(origin) }
            subtitleView.text = if (current == null) {
                getString(R.string.cookie_retention_none)
            } else {
                getString(
                    R.string.cookie_retention_configured,
                    formatRetentionHours(current.hoursUntilClear),
                )
            }
        }
    }

    private fun formatRetentionHours(hours: Int): String {
        return if (hours >= 24 && hours % 24 == 0) {
            getString(R.string.cookie_retention_days, hours / 24)
        } else {
            getString(R.string.cookie_retention_hours, hours)
        }
    }

    companion object {
        const val TAG = "SitePermissionsBottomSheet"

        private const val ARG_ORIGIN = "arg.origin"
        private const val ARG_PRIVATE = "arg.private"

        fun newInstance(): SitePermissionsBottomSheet = SitePermissionsBottomSheet()

        /**
         * Open the sheet pre-wired to an explicit [origin] (instead of
         * deriving from `BrowserStore.selectedTab`). Used by the Site
         * Exceptions settings screen so the sheet works outside the
         * browser surface.
         */
        fun newInstanceForOrigin(
            origin: String,
            isPrivate: Boolean = false,
        ): SitePermissionsBottomSheet = SitePermissionsBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_ORIGIN, origin)
                putBoolean(ARG_PRIVATE, isPrivate)
            }
        }

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
