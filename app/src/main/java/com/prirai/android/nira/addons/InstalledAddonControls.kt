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
import com.prirai.android.nira.browser.profile.BrowserProfile
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
 * Management controls of an installed add-on (per-profile and private-browsing access,
 * settings, permissions, remove), embedded in the add-on details window.
 */
@Composable
internal fun InstalledAddonControls(
    addon: Addon,
    onRemove: (Addon) -> Unit,
) {
    val context = LocalContext.current
    var currentAddon by remember { mutableStateOf<Addon?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var allowedInPrivateBrowsing by remember { mutableStateOf(false) }
    val profiles = remember { ProfileManager.getInstance(context).getAllProfiles() }
    // Bumped after a policy write so the switches re-read it.
    var policyVersion by remember { mutableStateOf(0) }

    fun setProfileEnabled(target: Addon, profile: BrowserProfile, isEnabled: Boolean) {
        ProfileAddonPolicy.setEnabledForProfile(context, profile.id, target.id, isEnabled)
        policyVersion++
        // Extensions are engine-wide, so only the active profile's choice takes effect now;
        // the others are applied when that profile becomes active.
        if (profile.id == ProfileManager.getInstance(context).getActiveProfile().id) {
            if (isEnabled) {
                context.components.addonManager.enableAddon(target)
            } else {
                context.components.addonManager.disableAddon(target)
            }
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
                allowedInPrivateBrowsing = found.isAllowedInPrivateBrowsing()
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

    run {
        val bound = currentAddon
        when {
            bound == null && !loadFailed -> NiraLoadingIndicator()
            bound != null -> Column {
                val supported = bound.isSupported()
                if (supported) {
                    Column {
                        Text(
                            text = stringResource(R.string.addon_enabled_for_profiles),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                        AddonProfileAccessCard(
                            profiles = profiles,
                            isProfileEnabled = {
                                policyVersion
                                ProfileAddonPolicy.isEnabledForProfile(context, it.id, bound.id)
                            },
                            onProfileChange = { profile, value -> setProfileEnabled(bound, profile, value) },
                            privateAllowed = allowedInPrivateBrowsing,
                            onPrivateChange = { togglePrivateBrowsing(bound) },
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
            }
        }
    }
}
