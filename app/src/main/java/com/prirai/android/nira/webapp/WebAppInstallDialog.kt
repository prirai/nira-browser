@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.webapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.ViewGroup
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.theme.NiraTheme
import com.prirai.android.nira.utils.FaviconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.IconRequest

/**
 * Install-a-web-app dialog on the shared Material 3 Expressive stack:
 * a [NiraAlertDialog] with the app's favicon, a profile
 * [ExposedDropdownMenuBox] and an Install action.
 *
 * Kept as a class because View-world callers (BrowserMenuController)
 * construct it and call [show]; [show] attaches a [ComposeView] to the
 * activity's content view — the same hosting pattern TabsBottomSheetFragment
 * uses for ProfileCreateDialog — so the composable content can render a
 * real [NiraAlertDialog] window. Compose callers (UnifiedWebAppFragment)
 * compose [WebAppInstallDialogContent] directly instead.
 *
 * @param lifecycleOwner retained only for call-site compatibility; the
 *   dialog's async icon load now follows the composition's lifecycle.
 */
class WebAppInstallDialog(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") lifecycleOwner: LifecycleOwner,
    private val appName: String,
    private val appUrl: String,
    private val appDescription: String? = null,
    private val defaultProfileId: String? = null,
    private val onInstall: (profileId: String) -> Unit,
) {
    private var composeHost: ComposeView? = null

    fun show() {
        val content = context.findActivity()
            ?.findViewById<ViewGroup>(android.R.id.content)
            ?: return
        val host = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val activityContext = LocalContext.current
                val prefs = UserPreferences(activityContext)
                NiraTheme(
                    darkTheme = ThemeManager.isDarkMode(activityContext),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors,
                ) {
                    WebAppInstallDialogContent(
                        appName = appName,
                        appUrl = appUrl,
                        appDescription = appDescription,
                        defaultProfileId = defaultProfileId,
                        onDismiss = ::dismiss,
                        onInstall = { profileId ->
                            onInstall(profileId)
                            dismiss()
                        },
                    )
                }
            }
        }
        content.addView(host)
        composeHost = host
    }

    fun dismiss() {
        composeHost?.let { host -> (host.parent as? ViewGroup)?.removeView(host) }
        composeHost = null
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The install dialog's content: app favicon and name, the description (or
 * the default profile-explanation text), the profile picker and the
 * Install / Cancel actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppInstallDialogContent(
    appName: String,
    appUrl: String,
    onDismiss: () -> Unit,
    onInstall: (profileId: String) -> Unit,
    appDescription: String? = null,
    defaultProfileId: String? = null,
) {
    val context = LocalContext.current
    val profiles = remember { ProfileManager.getInstance(context).getAllProfiles() }
    var selectedProfile by remember {
        mutableStateOf(
            if (defaultProfileId != null) {
                profiles.find { it.id == defaultProfileId } ?: profiles.first()
            } else {
                ProfileManager.getInstance(context).getActiveProfile()
            },
        )
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    NiraAlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppFavicon(url = appUrl, size = 48.dp) },
        title = {
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = appDescription ?: stringResource(R.string.install_web_app_profile_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedProfile.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.select_profile)) },
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = {
                                    selectedProfile = profile
                                    dropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onInstall(selectedProfile.id) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.install))
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
 * The dialog's app favicon on the install loader - memory cache, then
 * `BrowserIcons`, saving the result back to the cache - with the globe
 * glyph as its placeholder while the load is in flight.
 */
@Composable
private fun AppFavicon(url: String, size: Dp) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                FaviconCache.getInstance(context).loadFavicon(url)
                    ?: context.components.icons.loadIcon(
                        IconRequest(
                            url = url,
                            size = IconRequest.Size.DEFAULT,
                            resources = listOf(
                                IconRequest.Resource(
                                    url = url,
                                    type = IconRequest.Resource.Type.FAVICON,
                                ),
                            ),
                        ),
                    ).await().bitmap.also {
                        FaviconCache.getInstance(context).saveFavicon(url, it)
                    }
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
