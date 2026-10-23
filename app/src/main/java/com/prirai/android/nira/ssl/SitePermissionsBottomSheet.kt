@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.ssl

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.permissions.BackgroundPlaybackAllowlist
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.privacy.cookies.CookiePolicyRepository
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissions.AutoplayStatus
import mozilla.components.concept.engine.permission.SitePermissions.Status
import mozilla.components.support.ktx.kotlin.getOrigin
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl
import mozilla.components.ui.icons.R as iconsR

/**
 * Which AC permission a row flips. BACKGROUND_PLAYBACK is Nira-local (backed
 * by [BackgroundPlaybackAllowlist], not AC's `SitePermissions`); every other
 * kind maps 1:1 to an AC field.
 */
private enum class PermissionKind {
    BACKGROUND_PLAYBACK,
    LOCATION,
    NOTIFICATION,
    MICROPHONE,
    CAMERA,
    LOCAL_STORAGE,
    MEDIA_KEY_SYSTEM_ACCESS,
    CROSS_ORIGIN_STORAGE,
    LOCAL_DEVICE,
    LOCAL_NETWORK,
    AUTOPLAY_AUDIBLE,
    AUTOPLAY_INAUDIBLE,
}

private data class SitePermissionRowUi(
    val kind: PermissionKind,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val checked: Boolean,
)

/**
 * Material 3 bottom sheet that lists every site-permission for the current
 * origin and lets the user flip each one.
 *
 * Opened from `ConnectionInfoBottomSheet`'s "Site permissions" row. The
 * backing store is Nira's `components.permissionStorage`, which is the
 * same `GeckoSitePermissionsStorage` AC uses for its own prompt
 * decisions - so toggling here is immediately picked up by
 * `SitePermissionsFeature.shouldShowPrompt` and sites that previously
 * triggered a prompt will stop prompting (and vice versa).
 *
 * Rows render with the shared grouped-card idiom: mozac icon + title +
 * Material 3 Switch, rebuilt from the stored row after every flip so the
 * switches always reflect storage.
 *
 * UX conventions:
 * - A `Status` field has three values (ALLOWED / NO_DECISION / BLOCKED).
 *   The M3 switch is boolean, so we treat NO_DECISION as off (user hasn't
 *   decided -> site will prompt) and ALLOWED as on. Flipping on writes
 *   ALLOWED; flipping off writes BLOCKED (so sites don't keep prompting).
 * - AutoplayStatus is already two-state (BLOCKED/ALLOWED); switch maps 1:1.
 */
class SitePermissionsBottomSheet : BottomSheetDialogFragment() {

    private var current: SitePermissions? = null
    private var isPrivate: Boolean = false
    private var siteUrl: String = ""

    private val originState = mutableStateOf<String?>(null)
    private val rowsState = mutableStateOf<List<SitePermissionRowUi>>(emptyList())
    private val showEmptyHintState = mutableStateOf(false)
    private val retentionLabelState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val context = LocalContext.current
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors,
            ) {
                SitePermissionsSheetContent(
                    origin = originState.value,
                    rows = rowsState.value,
                    showEmptyHint = showEmptyHintState.value,
                    retentionLabel = retentionLabelState.value,
                    onToggle = ::onPermissionToggled,
                    onClearCookies = ::clearCookiesForCurrentSite,
                    onRetentionPicked = ::onRetentionPicked,
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        // Prefer an explicit origin passed via arguments (used by the
        // Site Exceptions screen in settings) so this sheet can be
        // reused for any origin, not just the currently selected tab.
        // Fall back to deriving from the selected tab for the normal
        // "lock icon -> site permissions row" entry point.
        val argOrigin = arguments?.getString(ARG_ORIGIN)
        val argPrivate = arguments?.getBoolean(ARG_PRIVATE, false) ?: false
        val selectedTab = context.components.store.state.selectedTab
        val tabUrl = selectedTab?.content?.url.orEmpty()
        siteUrl = argOrigin ?: tabUrl
        isPrivate = argOrigin?.let { argPrivate } ?: (selectedTab?.content?.private == true)
        val origin = argOrigin ?: siteUrl.getOrigin() ?: run {
            // No valid origin (chrome:// / about:); nothing to show.
            dismiss()
            return
        }
        originState.value = origin

        // Query storage on IO; render on Main.
        viewLifecycleOwner.lifecycleScope.launch {
            val stored = withContext(Dispatchers.IO) {
                context.components.permissionStorage.findSitePermissionsBy(
                    origin,
                    private = isPrivate,
                )
            }
            current = stored
            rebuildRows()
        }
        refreshRetentionLabel()
    }

    /**
     * Rebuilds the row list from the stored permissions. Only AC permissions
     * the site has actually interacted with (non-default values) are shown
     * so the sheet does not become a wall of twelve "Ask every time"
     * entries for a page that never asked for anything.
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
    private fun rebuildRows() {
        val origin = originState.value ?: return
        val rows = mutableListOf<SitePermissionRowUi>()

        // Background playback - always rendered because it is Nira-local
        // storage (BackgroundPlaybackAllowlist) and the user may want to
        // opt in before any AC permission has been touched.
        rows += SitePermissionRowUi(
            kind = PermissionKind.BACKGROUND_PLAYBACK,
            titleRes = R.string.permission_background_playback,
            iconRes = iconsR.drawable.mozac_ic_autoplay_24,
            checked = BackgroundPlaybackAllowlist.getInstance(requireContext()).isAllowed(origin),
        )

        val stored = current
        if (stored != null) {
            fun statusRow(
                kind: PermissionKind,
                value: Status,
                @StringRes titleRes: Int,
                @DrawableRes iconRes: Int,
            ) {
                // Hide permissions the site has not touched yet - keeps the
                // sheet focused on actual decisions.
                if (value == Status.NO_DECISION) return
                rows += SitePermissionRowUi(kind, titleRes, iconRes, value.isAllowed())
            }
            statusRow(PermissionKind.LOCATION, stored.location, R.string.permission_location, iconsR.drawable.mozac_ic_location_24)
            statusRow(PermissionKind.NOTIFICATION, stored.notification, R.string.permission_notification, iconsR.drawable.mozac_ic_notification_24)
            statusRow(PermissionKind.MICROPHONE, stored.microphone, R.string.permission_microphone, iconsR.drawable.mozac_ic_microphone_24)
            statusRow(PermissionKind.CAMERA, stored.camera, R.string.permission_camera, iconsR.drawable.mozac_ic_camera_24)
            statusRow(PermissionKind.LOCAL_STORAGE, stored.localStorage, R.string.permission_persistent_storage, iconsR.drawable.mozac_ic_storage_24)
            statusRow(PermissionKind.MEDIA_KEY_SYSTEM_ACCESS, stored.mediaKeySystemAccess, R.string.permission_drm, iconsR.drawable.mozac_ic_cookies_24)
            statusRow(PermissionKind.CROSS_ORIGIN_STORAGE, stored.crossOriginStorageAccess, R.string.permission_cross_origin_storage, iconsR.drawable.mozac_ic_cookies_24)
            statusRow(PermissionKind.LOCAL_DEVICE, stored.localDeviceAccess, R.string.permission_local_device_access, iconsR.drawable.mozac_ic_device_mobile_24)
            statusRow(PermissionKind.LOCAL_NETWORK, stored.localNetworkAccess, R.string.permission_local_network_access, iconsR.drawable.mozac_ic_local_network_24)

            // Autoplay rows - these are two-state so always meaningful once
            // the origin has any stored permissions row.
            fun autoplayRow(
                kind: PermissionKind,
                value: AutoplayStatus,
                @StringRes titleRes: Int,
                @DrawableRes iconRes: Int,
            ) {
                rows += SitePermissionRowUi(kind, titleRes, iconRes, value == AutoplayStatus.ALLOWED)
            }
            autoplayRow(PermissionKind.AUTOPLAY_AUDIBLE, stored.autoplayAudible, R.string.permission_autoplay_audible, iconsR.drawable.mozac_ic_autoplay_24)
            autoplayRow(PermissionKind.AUTOPLAY_INAUDIBLE, stored.autoplayInaudible, R.string.permission_autoplay_inaudible, iconsR.drawable.mozac_ic_autoplay_24)
        }

        rowsState.value = rows
        showEmptyHintState.value = stored == null
    }

    private fun onPermissionToggled(kind: PermissionKind, checked: Boolean) {
        val origin = originState.value ?: return
        if (kind == PermissionKind.BACKGROUND_PLAYBACK) {
            val allowlist = BackgroundPlaybackAllowlist.getInstance(requireContext())
            if (checked) allowlist.allow(origin) else allowlist.block(origin)
        } else {
            persist { applyPermission(kind, checked) }
        }
        rebuildRows()
    }

    private fun SitePermissions.applyPermission(
        kind: PermissionKind,
        checked: Boolean,
    ): SitePermissions {
        val status = if (checked) Status.ALLOWED else Status.BLOCKED
        val autoplayStatus = if (checked) AutoplayStatus.ALLOWED else AutoplayStatus.BLOCKED
        return when (kind) {
            PermissionKind.LOCATION -> copy(location = status)
            PermissionKind.NOTIFICATION -> copy(notification = status)
            PermissionKind.MICROPHONE -> copy(microphone = status)
            PermissionKind.CAMERA -> copy(camera = status)
            PermissionKind.LOCAL_STORAGE -> copy(localStorage = status)
            PermissionKind.MEDIA_KEY_SYSTEM_ACCESS -> copy(mediaKeySystemAccess = status)
            PermissionKind.CROSS_ORIGIN_STORAGE -> copy(crossOriginStorageAccess = status)
            PermissionKind.LOCAL_DEVICE -> copy(localDeviceAccess = status)
            PermissionKind.LOCAL_NETWORK -> copy(localNetworkAccess = status)
            PermissionKind.AUTOPLAY_AUDIBLE -> copy(autoplayAudible = autoplayStatus)
            PermissionKind.AUTOPLAY_INAUDIBLE -> copy(autoplayInaudible = autoplayStatus)
            PermissionKind.BACKGROUND_PLAYBACK -> this
        }
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
        origin = originState.value ?: "",
        savedAt = System.currentTimeMillis(),
    )

    /**
     * Confirm-before-clear flow for cookies + DOM storage scoped to the
     * current site, triggered by the "Clear cookies and site data" button.
     * The confirm dialog explicitly calls out that the user may have to
     * sign in again.
     */
    private fun clearCookiesForCurrentSite() {
        if (siteUrl.isEmpty()) {
            dismiss()
            return
        }
        val context = requireContext()
        context.components.engine.clearData(
            data = Engine.BrowsingData.select(
                Engine.BrowsingData.COOKIES,
                Engine.BrowsingData.DOM_STORAGES,
            ),
            host = siteUrl.tryGetHostFromUrl(),
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
     * Writes the retention window picked in the chooser dialog. Picking
     * "Never" removes the row; any other value calls
     * `CookiePolicyRepository.setPolicy`. After writing we refresh the
     * chip label so the user sees the new state without re-opening the
     * sheet.
     */
    private fun onRetentionPicked(index: Int) {
        val origin = originState.value ?: return
        val repo = CookiePolicyRepository.getInstance(requireContext())
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
            refreshRetentionLabel()
        }
    }

    /**
     * Reads the stored [CookiePolicy] for the current origin into the chip
     * label state ("Never" when no row exists, otherwise e.g. "24 h" /
     * "7 d"). Called from [onViewCreated] on initial render and from
     * [onRetentionPicked] after the user picks a new value.
     */
    private fun refreshRetentionLabel() {
        val origin = originState.value ?: return
        val repo = CookiePolicyRepository.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val current = withContext(Dispatchers.IO) { repo.get(origin) }
            retentionLabelState.value = if (current == null) {
                getString(R.string.cookie_retention_none)
            } else {
                formatRetentionHours(current.hoursUntilClear)
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
         * "Site permissions" row.
         *
         * Returns up to 5 icons so the row doesn't overflow; the full
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

/**
 * The Compose body of the site-permissions sheet: the permission rows of the
 * origin, the cookie-policy chip row and the clear-cookies action, rendered
 * with the shared grouped-card idiom. The two destructive/choice flows render
 * as [NiraAlertDialog]s.
 */
@Composable
private fun SitePermissionsSheetContent(
    origin: String?,
    rows: List<SitePermissionRowUi>,
    showEmptyHint: Boolean,
    retentionLabel: String?,
    onToggle: (PermissionKind, Boolean) -> Unit,
    onClearCookies: () -> Unit,
    onRetentionPicked: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (origin == null) return

    var showClearCookiesDialog by remember { mutableStateOf(false) }
    var showRetentionChooser by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp),
    ) {
        NiraSheetHeader(
            title = stringResource(R.string.site_permissions_title),
            subtitle = origin,
            onClose = onDismiss,
            dividerAlpha = null,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (showEmptyHint) {
                // No AC permission has been interacted with yet, but the
                // background-playback row above is still there to toggle.
                Text(
                    text = stringResource(R.string.site_permissions_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (rows.isNotEmpty()) {
                Column {
                    rows.forEachIndexed { index, row ->
                        SitePermissionCardRow(
                            row = row,
                            isTopRow = index == 0,
                            isBottomRow = index == rows.lastIndex,
                            showDivider = index > 0,
                            onToggle = onToggle,
                        )
                    }
                }
                // Hint for the Nira-local background-playback row (the
                // first row of the card): the toggle writes to the
                // allow-list uniformly so a future broadening of the
                // extension's content-script match needs no migration.
                Text(
                    text = stringResource(R.string.permission_background_playback_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NiraCardRow(
                title = stringResource(R.string.cookie_policy_label),
                isTopRow = true,
                isBottomRow = true,
                showDivider = false,
                trailing = {
                    AssistChip(
                        onClick = { showRetentionChooser = true },
                        label = { Text(retentionLabel.orEmpty()) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_history),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                },
            )

            OutlinedButton(
                onClick = { showClearCookiesDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.clear_cookies_and_site_data))
            }
        }
    }

    if (showClearCookiesDialog) {
        NiraAlertDialog(
            onDismissRequest = { showClearCookiesDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCookiesDialog = false
                        onClearCookies()
                    },
                ) { Text(stringResource(R.string.clear_cookies_dialog_positive)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCookiesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.clear_cookies_dialog_title)) },
            text = { Text(stringResource(R.string.clear_cookies_dialog_message)) },
        )
    }

    if (showRetentionChooser) {
        RetentionChooserDialog(
            onDismiss = { showRetentionChooser = false },
            onPicked = { index ->
                showRetentionChooser = false
                onRetentionPicked(index)
            },
        )
    }
}

@Composable
private fun RetentionChooserDialog(
    onDismiss: () -> Unit,
    onPicked: (Int) -> Unit,
) {
    val options = listOf(
        R.string.cookie_retention_option_never,
        R.string.cookie_retention_option_6h,
        R.string.cookie_retention_option_24h,
        R.string.cookie_retention_option_7d,
        R.string.cookie_retention_option_30d,
    )
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.cookie_retention_dialog_title)) },
        text = {
            Column {
                options.forEachIndexed { index, optionRes ->
                    Text(
                        text = stringResource(optionRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPicked(index) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun SitePermissionCardRow(
    row: SitePermissionRowUi,
    isTopRow: Boolean,
    isBottomRow: Boolean,
    showDivider: Boolean,
    onToggle: (PermissionKind, Boolean) -> Unit,
) {
    val title = stringResource(row.titleRes)
    NiraCardRow(
        title = title,
        isTopRow = isTopRow,
        isBottomRow = isBottomRow,
        showDivider = showDivider,
        leading = {
            Icon(
                painter = painterResource(row.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        trailing = {
            Switch(
                checked = row.checked,
                onCheckedChange = { onToggle(row.kind, it) },
                modifier = Modifier.semantics { contentDescription = title },
            )
        },
    )
}
