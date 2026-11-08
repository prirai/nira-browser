@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.addons

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PermCameraMic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.ProfileAddonPolicy
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.getParcelableExtraCompat
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraLoadingIndicator
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManagerException
import mozilla.components.feature.addons.ui.translateName

/**
 * The detail page of an installed add-on, on the shared Material 3 Expressive Compose stack:
 * the enable / private-browsing toggles, the per-profile allowlist (behind a [NiraAlertDialog]),
 * the settings, details and permissions rows, and the remove action — with the same
 * addon-manager state plumbing the View screen carried.
 */
class InstalledAddonDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeMode()

        val addon = requireNotNull(intent.getParcelableExtraCompat<Addon>("add_on"))

        setContent {
            val context = LocalContext.current
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors,
            ) {
                InstalledAddonDetailsScreen(
                    addon = addon,
                    onRemove = { uninstallAddon(it) },
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }
    }

    private fun uninstallAddon(addon: Addon) {
        components.addonManager.uninstallAddon(
            addon,
            onSuccess = {
                // Clear the cache so the bottom sheet shows updated data
                ExtensionsBottomSheetFragment.clearCache()
                Toast.makeText(
                    this,
                    getString(
                        R.string.mozac_feature_addons_successfully_uninstalled,
                        addon.translateName(this),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            },
            onError = { _, _ ->
                Toast.makeText(
                    this,
                    getString(
                        R.string.mozac_feature_addons_failed_to_uninstall,
                        addon.translateName(this),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstalledAddonDetailsScreen(
    addon: Addon,
    onRemove: (Addon) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var currentAddon by remember { mutableStateOf<Addon?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var allowedInPrivateBrowsing by remember { mutableStateOf(false) }
    var allowlistSummary by remember { mutableStateOf("") }
    var showAllowlistDialog by remember { mutableStateOf(false) }

    fun updateAllowlistSummary(addonId: String) {
        val profiles = ProfileManager.getInstance(context).getAllProfiles()
        val enabledNames = profiles
            .filter { ProfileAddonPolicy.isEnabledForProfile(context, it.id, addonId) }
            .map { it.name }
        allowlistSummary = if (enabledNames.size == profiles.size) {
            context.getString(R.string.addon_enabled_for_all_profiles)
        } else {
            enabledNames.joinToString().ifEmpty { context.getString(R.string.addon_enabled_for_profiles) }
        }
    }

    fun toggleEnabled(target: Addon) {
        val profileId = ProfileManager.getInstance(context).getActiveProfile().id
        val newState = !enabled
        enabled = newState
        if (newState) {
            context.components.addonManager.enableAddon(
                target,
                onSuccess = {
                    ProfileAddonPolicy.setEnabledForProfile(context, profileId, target.id, true)
                    enabled = true
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.mozac_feature_addons_successfully_enabled,
                            target.translateName(context),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onError = {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.mozac_feature_addons_failed_to_enable,
                            target.translateName(context),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        } else {
            context.components.addonManager.disableAddon(
                target,
                onSuccess = {
                    ProfileAddonPolicy.setEnabledForProfile(context, profileId, target.id, false)
                    enabled = false
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.mozac_feature_addons_successfully_disabled,
                            target.translateName(context),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onError = {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.mozac_feature_addons_failed_to_disable,
                            target.translateName(context),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    fun togglePrivateBrowsing(target: Addon) {
        val newState = !allowedInPrivateBrowsing
        allowedInPrivateBrowsing = newState
        context.components.addonManager.setAddonAllowedInPrivateBrowsing(
            target,
            newState,
            onSuccess = {
                allowedInPrivateBrowsing = newState
            },
        )
    }

    fun openSettings(target: Addon, optionsPageUrl: String) {
        if (target.installedState?.openOptionsPageInTab == true) {
            context.components.tabsUseCases.addTab(optionsPageUrl)
            val intent = Intent(context, BrowserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        } else {
            val intent = Intent(context, AddonSettingsActivity::class.java)
            intent.putExtra("add_on", target)
            context.startActivity(intent)
        }
    }

    LaunchedEffect(addon) {
        try {
            val found = withContext(Dispatchers.IO) {
                context.components.addonManager.getAddons().find { addon.id == it.id }
            }
            if (found == null) {
                loadFailed = true
                Toast.makeText(
                    context,
                    R.string.mozac_feature_addons_failed_to_query_extensions,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                currentAddon = found
                enabled = found.isEnabled()
                allowedInPrivateBrowsing = found.isAllowedInPrivateBrowsing()
                updateAllowlistSummary(found.id)
            }
        } catch (e: AddonManagerException) {
            loadFailed = true
            Toast.makeText(
                context,
                R.string.mozac_feature_addons_failed_to_query_extensions,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text((currentAddon ?: addon).translateName(context))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val bound = currentAddon
        when {
            bound == null && !loadFailed -> NiraLoadingIndicator(modifier = Modifier.padding(padding))
            bound != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            ) {
                val supported = bound.isSupported()
                if (supported) {
                    Column {
                        NiraCardRow(
                            title = stringResource(R.string.mozac_feature_addons_enabled),
                            isTopRow = true,
                            isBottomRow = false,
                            showDivider = true,
                            onClick = { toggleEnabled(bound) },
                            trailing = {
                                Switch(checked = enabled, onCheckedChange = null)
                            },
                        )
                        NiraCardRow(
                            title = stringResource(R.string.mozac_feature_addons_settings_allow_in_private_browsing),
                            isTopRow = false,
                            isBottomRow = false,
                            showDivider = true,
                            onClick = { togglePrivateBrowsing(bound) },
                            trailing = {
                                Switch(checked = allowedInPrivateBrowsing, onCheckedChange = null)
                            },
                        )
                        NiraCardRow(
                            title = stringResource(R.string.addon_enabled_for_profiles),
                            isTopRow = false,
                            isBottomRow = true,
                            showDivider = false,
                            onClick = { showAllowlistDialog = true },
                            trailing = {
                                Text(
                                    text = allowlistSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End,
                                )
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val optionsPageUrl = bound.installedState?.optionsPageUrl
                Column {
                    NiraCardRow(
                        title = stringResource(R.string.mozac_feature_addons_settings),
                        isTopRow = true,
                        isBottomRow = !supported,
                        showDivider = supported,
                        onClick = if (optionsPageUrl != null) {
                            { openSettings(bound, optionsPageUrl) }
                        } else {
                            null
                        },
                        leading = {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    if (supported) {
                        NiraCardRow(
                            title = stringResource(R.string.mozac_feature_addons_details),
                            isTopRow = false,
                            isBottomRow = false,
                            showDivider = true,
                            onClick = {
                                val intent = Intent(context, AddonDetailsActivity::class.java)
                                intent.putExtra("add_on", bound)
                                context.startActivity(intent)
                            },
                            leading = {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        NiraCardRow(
                            title = stringResource(R.string.mozac_feature_addons_permissions),
                            isTopRow = false,
                            isBottomRow = true,
                            showDivider = false,
                            onClick = {
                                val intent = Intent(context, PermissionsDetailsActivity::class.java)
                                intent.putExtra("add_on", bound)
                                context.startActivity(intent)
                            },
                            leading = {
                                Icon(
                                    imageVector = Icons.Rounded.PermCameraMic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { onRemove(bound) },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.mozac_feature_addons_remove))
                }

                if (showAllowlistDialog) {
                    val profiles = remember(bound) {
                        ProfileManager.getInstance(context).getAllProfiles()
                    }
                    val checked = remember(bound) {
                        profiles.map {
                            ProfileAddonPolicy.isEnabledForProfile(context, it.id, bound.id)
                        }.toMutableStateList()
                    }
                    NiraAlertDialog(
                        onDismissRequest = { showAllowlistDialog = false },
                        title = { Text(stringResource(R.string.addon_enabled_for_profiles)) },
                        text = {
                            Column {
                                profiles.forEachIndexed { index, profile ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .toggleable(
                                                value = checked[index],
                                                role = Role.Checkbox,
                                                onValueChange = { checked[index] = it },
                                            ),
                                    ) {
                                        Checkbox(checked = checked[index], onCheckedChange = null)
                                        Text(
                                            text = profile.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAllowlistDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val activeProfileId = ProfileManager
                                        .getInstance(context)
                                        .getActiveProfile()
                                        .id
                                    profiles.forEachIndexed { index, profile ->
                                        val isEnabled = checked[index]
                                        ProfileAddonPolicy.setEnabledForProfile(
                                            context,
                                            profile.id,
                                            bound.id,
                                            isEnabled,
                                        )
                                        if (profile.id == activeProfileId) {
                                            if (isEnabled) {
                                                context.components.addonManager.enableAddon(bound)
                                            } else {
                                                context.components.addonManager.disableAddon(bound)
                                            }
                                            enabled = isEnabled
                                        }
                                    }
                                    updateAllowlistSummary(bound.id)
                                    showAllowlistDialog = false
                                },
                            ) {
                                Text(stringResource(R.string.mozac_feature_prompts_ok))
                            }
                        },
                    )
                }
            }
        }
    }
}
