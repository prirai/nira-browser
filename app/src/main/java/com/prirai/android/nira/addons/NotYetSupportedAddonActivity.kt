@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.addons

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.getParcelableArrayListExtraCompat
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.animateNiraListItem
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.translateName

/**
 * The list of add-ons that are installed but not supported by the browser, on the shared
 * Material 3 Expressive Compose stack: one grouped [NiraCardRow] card whose rows carry the
 * add-on's icon, name and a remove action. Removing the last one finishes the screen, as
 * the mozac adapter this replaces did.
 */
class NotYetSupportedAddonActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeMode()

        val addons = requireNotNull(intent.getParcelableArrayListExtraCompat<Addon>("add_ons"))

        setContent {
            val context = LocalContext.current
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors,
            ) {
                UnsupportedAddonsScreen(
                    addons = addons,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnsupportedAddonsScreen(
    addons: List<Addon>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var remaining by remember { mutableStateOf(addons.toList()) }
    var pendingUninstall by remember { mutableStateOf(false) }

    fun remove(addon: Addon) {
        if (pendingUninstall) {
            return
        }
        pendingUninstall = true
        context.components.addonManager.uninstallAddon(
            addon,
            onSuccess = {
                remaining = remaining.filterNot { it.id == addon.id }
                pendingUninstall = false
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.mozac_feature_addons_successfully_removed,
                        addon.translateName(context),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                if (remaining.isEmpty()) {
                    onBack()
                }
            },
            onError = { _, _ ->
                pendingUninstall = false
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.mozac_feature_addons_failed_to_remove,
                        addon.translateName(context),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mozac_feature_addons_addons)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(remaining, key = { "addon-${it.id}" }) { addon ->
                val index = remaining.indexOf(addon)
                NiraCardRow(
                    title = addon.translateName(context),
                    isTopRow = index == 0,
                    isBottomRow = index == remaining.lastIndex,
                    showDivider = index < remaining.lastIndex,
                    modifier = Modifier.then(animateNiraListItem()),
                    leading = {
                        AddonIcon(addon = addon, size = 36.dp)
                    },
                    trailing = {
                        IconButton(
                            onClick = { remove(addon) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.mozac_feature_addons_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}
