@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.webapp

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.utils.FaviconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dialog for cloning a web app into another profile: the app's icon, a
 * name field pre-filled with "(Clone)" and the profile picker, on
 * [NiraAlertDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppCloneDialog(
    webApp: WebAppEntity,
    onDismiss: () -> Unit,
    onClone: (name: String, profileId: String) -> Unit,
) {
    val context = LocalContext.current
    val profiles = remember { ProfileManager.getInstance(context).getAllProfiles() }
    var selectedProfile by remember {
        mutableStateOf(ProfileManager.getInstance(context).getActiveProfile())
    }
    var cloneName by rememberSaveable(webApp.id) {
        mutableStateOf(context.getString(R.string.webapp_clone_name_suffix, webApp.name))
    }
    var nameEmpty by remember { mutableStateOf(false) }

    NiraAlertDialog(
        onDismissRequest = onDismiss,
        icon = { WebAppIcon(webApp = webApp, size = 48.dp) },
        title = {
            Text(
                text = stringResource(R.string.clone_webapp_dialog_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.clone_webapp_dialog_message, webApp.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = cloneName,
                    onValueChange = {
                        cloneName = it
                        nameEmpty = false
                    },
                    label = { Text(stringResource(R.string.web_app_name)) },
                    singleLine = true,
                    isError = nameEmpty,
                    supportingText = if (nameEmpty) {
                        { Text(stringResource(R.string.name_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ProfileDropdown(
                    profiles = profiles,
                    selectedProfile = selectedProfile,
                    onSelected = { selectedProfile = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = cloneName.trim()
                    if (trimmed.isEmpty()) {
                        nameEmpty = true
                    } else {
                        onClone(trimmed, selectedProfile.id)
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.clone))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Dialog for binding a web app to a profile: the app's icon, the
 * binding explanation and the profile picker, pre-selected to the
 * app's current profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppProfileDialog(
    webApp: WebAppEntity,
    onDismiss: () -> Unit,
    onAssociate: (profileId: String) -> Unit,
) {
    val context = LocalContext.current
    val profileManager = remember { ProfileManager.getInstance(context) }
    val profiles = remember { profileManager.getAllProfiles() }
    var selectedProfile by remember {
        mutableStateOf(
            profiles.find { it.id == webApp.profileId }
                ?: profileManager.getActiveProfile(),
        )
    }

    NiraAlertDialog(
        onDismissRequest = onDismiss,
        icon = { WebAppIcon(webApp = webApp, size = 48.dp) },
        title = {
            Text(
                text = stringResource(R.string.associate_profile),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.associate_profile_message, webApp.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProfileDropdown(
                    profiles = profiles,
                    selectedProfile = selectedProfile,
                    onSelected = { selectedProfile = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAssociate(selectedProfile.id) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.associate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Confirmation dialog for uninstalling a web app.
 */
@Composable
fun WebAppUninstallDialog(
    webApp: WebAppEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.uninstall_web_app),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = { Text(stringResource(R.string.uninstall_web_app_message, webApp.name)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.uninstall))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Confirmation dialog for clearing a web app's data.
 */
@Composable
fun WebAppClearDataDialog(
    webApp: WebAppEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.clear_data),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = { Text(stringResource(R.string.clear_data_message, webApp.name)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.clear_data))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * The read-only profile picker shared by the web app dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdown(
    profiles: List<BrowserProfile>,
    selectedProfile: BrowserProfile,
    onSelected: (BrowserProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedProfile.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.select_profile)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = {
                        onSelected(profile)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A web app's icon on the stored-icon loader - the icon file saved at
 * install time, then the favicon cache - with the globe glyph as its
 * placeholder while the load is in flight.
 */
@Composable
private fun WebAppIcon(webApp: WebAppEntity, size: Dp) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(initialValue = null, webApp.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.components.webAppManager.loadIconFromFile(webApp.iconUrl)
                    ?: FaviconCache.getInstance(context).loadFavicon(webApp.url)
            }.getOrNull()
        }
    }

    if (icon != null) {
        Image(
            bitmap = icon!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size),
        )
    }
}
