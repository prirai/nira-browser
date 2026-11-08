@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.addons

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.getParcelableExtraCompat
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.theme.NiraTheme
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.translateName

private const val LEARN_MORE_URL =
    "https://smartcookieweb.com/help-biscuit/extensions/#permission-requests"

/**
 * The permission list of an add-on, on the shared Material 3 Expressive Compose stack:
 * one grouped [NiraCardRow] card of the translated permissions, and the learn-more row
 * that opens the support page.
 */
class PermissionsDetailsActivity : AppCompatActivity() {

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
                PermissionsScreen(
                    addon = addon,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsScreen(
    addon: Addon,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val permissions = remember(addon) { addon.translatePermissions(context).sorted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(addon.translateName(context)) },
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
        if (permissions.isEmpty()) {
            NiraEmptyState(
                title = stringResource(R.string.addon_no_permissions),
                icon = Icons.Rounded.Security,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                itemsIndexed(permissions, key = { index, permission -> "permission-$index-$permission" }) { index, permission ->
                    NiraCardRow(
                        title = permission,
                        isTopRow = index == 0,
                        isBottomRow = index == permissions.lastIndex,
                        showDivider = index < permissions.lastIndex,
                    )
                }
                item(key = "learn-more") {
                    Spacer(modifier = Modifier.height(16.dp))
                    NiraCardRow(
                        title = stringResource(R.string.mozac_feature_addons_learn_more),
                        isTopRow = true,
                        isBottomRow = true,
                        showDivider = false,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).setData(LEARN_MORE_URL.toUri())
                            context.startActivity(intent)
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}
