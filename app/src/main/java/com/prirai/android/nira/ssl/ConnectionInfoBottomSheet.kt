@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.ssl

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
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
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.support.ktx.kotlin.getOrigin
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

private enum class SecurityStatus { SECURE, INSECURE, INTERNAL }

private data class ConnectionInfoUi(
    val status: SecurityStatus,
    @DrawableRes val iconRes: Int,
    val title: String,
    val url: String,
    val issuer: String?,
    val holder: String?,
    val showPrivacySection: Boolean,
    val origin: String?,
)

/**
 * Bottom sheet dialog fragment for displaying connection/security information
 * Similar to Firefox's QuickSettings panel
 */
class ConnectionInfoBottomSheet : BottomSheetDialogFragment() {

    private val uiState = mutableStateOf<ConnectionInfoUi?>(null)
    private val etpEnabled = mutableStateOf(false)
    private val summaryIconsState = mutableStateOf<List<Int>>(emptyList())

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
                ConnectionInfoSheetContent(
                    state = uiState.value,
                    etpEnabled = etpEnabled.value,
                    summaryIcons = summaryIconsState.value,
                    onTrackingProtectionChanged = ::onTrackingProtectionToggled,
                    onSitePermissionsClick = ::openSitePermissions,
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val selectedTab = context.components.store.state.selectedTab
        val securityInfo = selectedTab?.content?.securityInfo
        val url = selectedTab?.content?.url ?: ""
        val host = url.tryGetHostFromUrl()
        val isInternalPage = isInternalUrl(url)
        val origin = if (isInternalPage) null else url.getOrigin()

        val status = when {
            isInternalPage -> SecurityStatus.INTERNAL
            securityInfo?.isSecure == true -> SecurityStatus.SECURE
            else -> SecurityStatus.INSECURE
        }

        uiState.value = ConnectionInfoUi(
            status = status,
            iconRes = when (status) {
                SecurityStatus.INTERNAL -> R.drawable.shield_lock_24
                SecurityStatus.SECURE -> R.drawable.security_24
                SecurityStatus.INSECURE -> R.drawable.encrypted_off_24
            },
            title = selectedTab?.content?.title ?: host,
            url = url,
            issuer = if (isInternalPage) {
                null
            } else {
                securityInfo?.issuer ?: getString(R.string.certificate_issuer_unknown)
            },
            holder = if (isInternalPage) null else securityInfo?.host ?: host,
            showPrivacySection = !isInternalPage,
            origin = origin,
        )

        etpEnabled.value = UserPreferences(context).trackingProtection

        if (origin != null) {
            // Glanceable summary of the stored AC permissions in a
            // non-default state, rendered next to the site-permissions row.
            viewLifecycleOwner.lifecycleScope.launch {
                val stored = withContext(Dispatchers.IO) {
                    context.components.permissionStorage.findSitePermissionsBy(
                        origin,
                        private = selectedTab?.content?.private == true,
                    )
                }
                summaryIconsState.value = SitePermissionsBottomSheet.summaryIcons(stored)
            }
        }
    }

    private fun onTrackingProtectionToggled(enabled: Boolean) {
        val context = requireContext()
        etpEnabled.value = enabled
        UserPreferences(context).trackingProtection = enabled

        // Reload the page to apply changes
        context.components.sessionUseCases.reload()

        Toast.makeText(
            context,
            getString(
                if (enabled) {
                    R.string.tracking_protection_enabled_toast
                } else {
                    R.string.tracking_protection_disabled_toast
                },
            ),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /**
     * Always-visible "Site permissions" entry row. Opens
     * [SitePermissionsBottomSheet] regardless of whether the current origin
     * has any stored permission decisions, so the user can grant / revoke /
     * clear-cookies without having to make a decision first (the
     * "always visible" behaviour requested post-v0.5.14).
     *
     * Only hidden when the URL has no origin (chrome:// / about:); the
     * [ConnectionInfoUi.origin] guard doubles the caller's internal-page
     * short-circuit as a defence-in-depth check.
     */
    private fun openSitePermissions() {
        SitePermissionsBottomSheet.newInstance()
            .show(parentFragmentManager, SitePermissionsBottomSheet.TAG)
        dismiss()
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

/**
 * The Compose body of the connection-info sheet: the security status, the
 * page title/URL, the certificate of a secure page and the privacy section
 * (Enhanced Tracking Protection toggle + the site-permissions entry row),
 * rendered with the shared grouped-card idiom.
 */
@Composable
private fun ConnectionInfoSheetContent(
    state: ConnectionInfoUi?,
    etpEnabled: Boolean,
    summaryIcons: List<Int>,
    onTrackingProtectionChanged: (Boolean) -> Unit,
    onSitePermissionsClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val info = state ?: return
    val statusRes = when (info.status) {
        SecurityStatus.SECURE -> R.string.connection_is_secure
        SecurityStatus.INSECURE -> R.string.connection_is_not_secure
        SecurityStatus.INTERNAL -> R.string.connection_internal_page
    }
    // Tertiary reads as "secure" (the old sheet used a green indicator),
    // error as "not secure"; internal pages carry the neutral-secure look.
    val statusColor = when (info.status) {
        SecurityStatus.INSECURE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp),
    ) {
        NiraSheetHeader(
            title = info.title,
            subtitle = info.url,
            onClose = onDismiss,
            dividerAlpha = null,
            titleMaxLines = 2,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(info.iconRes),
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(statusRes),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = statusColor,
                )
            }

            if (info.issuer != null && info.holder != null) {
                CertificateSection(issuer = info.issuer, holder = info.holder)
            }

            if (info.showPrivacySection) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.privacy_and_security),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        val etpTitle = stringResource(R.string.enhanced_tracking_protection)
                        NiraCardRow(
                            title = etpTitle,
                            isTopRow = true,
                            isBottomRow = info.origin == null,
                            showDivider = info.origin != null,
                            trailing = {
                                Switch(
                                    checked = etpEnabled,
                                    onCheckedChange = onTrackingProtectionChanged,
                                    modifier = Modifier.semantics { contentDescription = etpTitle },
                                )
                            },
                        )
                        if (info.origin != null) {
                            NiraCardRow(
                                title = stringResource(R.string.site_permissions),
                                isTopRow = false,
                                isBottomRow = true,
                                showDivider = false,
                                onClick = onSitePermissionsClick,
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        summaryIcons.forEach { iconRes ->
                                            Icon(
                                                painter = painterResource(iconRes),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CertificateSection(issuer: String, holder: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.certificate_information),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                CertificateField(
                    label = stringResource(R.string.issued_by),
                    value = issuer,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                )
                CertificateField(
                    label = stringResource(R.string.issued_to),
                    value = holder,
                )
            }
        }
    }
}

@Composable
private fun CertificateField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
