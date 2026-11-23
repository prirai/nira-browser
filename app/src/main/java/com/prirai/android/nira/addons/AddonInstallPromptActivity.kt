@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.addons

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.ProfileAddonPolicy
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraActionButton
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.translateName

/**
 * What the browser is waiting on while [AddonInstallPromptActivity] is showing: the extension being
 * installed and the callback that answers the engine's permission prompt.
 */
internal object AddonInstallPromptHolder {
    class Pending(
        val addon: Addon,
        val permissions: List<String>,
        val onResult: (granted: Boolean, profileIds: Set<String>, privateAllowed: Boolean) -> Unit,
    )

    @Volatile
    var pending: Pending? = null
}

/**
 * The install prompt for a new extension: a tall floating window (same look as the add-on details)
 * listing the permissions it asks for and the profiles it should be enabled on.
 */
class AddonInstallPromptActivity : AppCompatActivity() {

    private var pending: AddonInstallPromptHolder.Pending? = null
    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeMode()

        val request = AddonInstallPromptHolder.pending
        if (request == null) {
            finish()
            return
        }
        pending = request

        setContent {
            val context = LocalContext.current
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors,
            ) {
                InstallPromptScreen(
                    addon = request.addon,
                    permissions = request.permissions,
                    onResult = ::answer,
                )
            }
        }
    }

    private fun answer(granted: Boolean, profileIds: Set<String>, privateAllowed: Boolean) {
        if (answered) return
        answered = true
        pending?.onResult?.invoke(granted, profileIds, privateAllowed)
        AddonInstallPromptHolder.pending = null
        finish()
    }

    override fun onDestroy() {
        // Leaving the window any other way (back, tap outside) declines the install.
        if (isFinishing && !answered) {
            answer(false, emptySet(), false)
        }
        super.onDestroy()
    }
}

@Composable
private fun InstallPromptScreen(
    addon: Addon,
    permissions: List<String>,
    onResult: (granted: Boolean, profileIds: Set<String>, privateAllowed: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val profiles = remember { ProfileManager.getInstance(context).getAllProfiles() }
    val activeId = remember { ProfileManager.getInstance(context).getActiveProfile().id }
    val checked = remember { profiles.map { it.id == activeId }.toMutableStateList() }
    val privateAllowed = remember { mutableStateOf(false) }
    val localized = remember(addon, permissions) {
        Addon.localizePermissions(permissions, context).sorted()
    }
    val noProfileSelected = !checked.any { it }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .widthIn(max = 640.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            NiraSheetHeader(
                title = stringResource(R.string.addon_install_title, addon.translateName(context)),
                onClose = { onResult(false, emptySet(), false) },
                showDragHandle = false,
                titleMaxLines = 1,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AddonIcon(addon = addon, size = 54.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = addon.translateName(context),
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        addon.author?.name?.takeIf(String::isNotEmpty)?.let { author ->
                            Text(
                                text = author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                SectionLabel(stringResource(R.string.addon_install_permissions))
                if (localized.isEmpty()) {
                    NiraCardRow(
                        title = stringResource(R.string.addon_no_permissions),
                        isTopRow = true,
                        isBottomRow = true,
                        showDivider = false,
                    )
                } else {
                    localized.forEachIndexed { index, permission ->
                        NiraCardRow(
                            title = permission,
                            titleMaxLines = Int.MAX_VALUE,
                            isTopRow = index == 0,
                            isBottomRow = index == localized.lastIndex,
                            showDivider = index < localized.lastIndex,
                        )
                    }
                }

                SectionLabel(stringResource(R.string.addon_enabled_for_profiles))
                AddonProfileAccessCard(
                    profiles = profiles,
                    isProfileEnabled = { checked[profiles.indexOf(it)] },
                    onProfileChange = { profile, value -> checked[profiles.indexOf(profile)] = value },
                    privateAllowed = privateAllowed.value,
                    onPrivateChange = { privateAllowed.value = it },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onResult(false, emptySet(), false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                NiraActionButton(
                    text = stringResource(R.string.addon_install_add),
                    onClick = {
                        val ids = profiles.filterIndexed { index, _ -> checked[index] }.map { it.id }.toSet()
                        onResult(true, ids, privateAllowed.value)
                    },
                    enabled = !noProfileSelected || privateAllowed.value,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

internal fun applyAddonProfileChoice(
    context: android.content.Context,
    addonId: String,
    profileIds: Set<String>,
) {
    ProfileManager.getInstance(context).getAllProfiles().forEach { profile ->
        ProfileAddonPolicy.setEnabledForProfile(context, profile.id, addonId, profile.id in profileIds)
    }
}
