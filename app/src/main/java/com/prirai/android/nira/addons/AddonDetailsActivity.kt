@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.prirai.android.nira.addons

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarHalf
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.getParcelableExtraCompat
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraActionButton
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.theme.NiraTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.update.DefaultAddonUpdater
import mozilla.components.feature.addons.ui.showInformationDialog
import mozilla.components.feature.addons.ui.translateDescription
import mozilla.components.feature.addons.ui.translateName
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper

/**
 * The store-listing page of an add-on, on the shared Material 3 Expressive Compose stack:
 * a header with the icon, name, author and rating stars, the translated description, a
 * grouped [NiraCardRow] info card and the install call to action with its in-progress state.
 */
class AddonDetailsActivity : AppCompatActivity() {

    private val updateAttemptStorage: DefaultAddonUpdater.UpdateAttemptStorage by lazy {
        DefaultAddonUpdater.UpdateAttemptStorage(this)
    }

    private val webExtensionPromptFeature = ViewBoundFeatureWrapper<WebExtensionPromptFeature>()

    private val installing = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeMode()

        val addon = requireNotNull(intent.getParcelableExtraCompat<Addon>("add_on"))

        val rootView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val prefs = UserPreferences(context)
                NiraTheme(
                    darkTheme = ThemeManager.isDarkMode(context),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors,
                ) {
                    AddonDetailsScreen(
                        addon = addon,
                        installing = installing.value,
                        onInstall = { installAddon(addon) },
                        onShowUpdaterDialog = { showUpdaterDialog(addon) },
                        onBack = { onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
        setContentView(rootView)

        webExtensionPromptFeature.set(
            feature = WebExtensionPromptFeature(
                store = components.store,
                provideAddons = { components.addonManager.getAddons() },
                context = this,
                view = rootView,
                fragmentManager = supportFragmentManager,
                onLinkClicked = { url, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(intent)
                },
            ),
            owner = this,
            view = rootView,
        )
    }

    private fun installAddon(addon: Addon) {
        if (installing.value) {
            return
        }
        installing.value = true
        applicationContext.components.addonManager.installAddon(
            url = addon.downloadUrl,
            installationMethod = InstallationMethod.MANAGER,
            onSuccess = { installedAddon ->
                runOnUiThread {
                    installing.value = false
                    ExtensionsBottomSheetFragment.clearCache()
                    Toast.makeText(
                        this,
                        getString(
                            R.string.mozac_feature_addons_successfully_installed,
                            installedAddon.translateName(this),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                }
            },
            onError = { error ->
                runOnUiThread {
                    installing.value = false
                    if (error !is CancellationException) {
                        Toast.makeText(
                            this,
                            getString(
                                R.string.mozac_feature_addons_failed_to_install,
                                addon.translateName(this),
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }

    private fun showUpdaterDialog(addon: Addon) {
        val context = this@AddonDetailsActivity
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val updateAttempt = updateAttemptStorage.findUpdateAttemptBy(addon.id)
            updateAttempt?.let {
                withContext(Dispatchers.Main) {
                    it.showInformationDialog(context)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddonDetailsScreen(
    addon: Addon,
    installing: Boolean,
    onInstall: () -> Unit,
    onShowUpdaterDialog: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mozac_feature_addons_details)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

            addon.rating?.let { rating ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingStars(average = rating.average)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${getFormattedAmount(rating.reviews)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!addon.isInstalled()) {
                Spacer(modifier = Modifier.height(16.dp))
                NiraActionButton(
                    text = if (installing) {
                        stringResource(R.string.addon_installing)
                    } else {
                        stringResource(R.string.mozac_feature_addons_install_addon)
                    },
                    onClick = onInstall,
                    enabled = !installing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            DescriptionText(addon = addon, modifier = Modifier.padding(top = 16.dp))

            Spacer(modifier = Modifier.height(16.dp))
            InfoCard(
                addon = addon,
                onShowUpdaterDialog = onShowUpdaterDialog,
            )
        }
    }
}

@Composable
private fun RatingStars(
    average: Float,
    modifier: Modifier = Modifier,
) {
    val ratingDescription =
        stringResource(R.string.mozac_feature_addons_rating_content_description_2, average)
    Row(modifier = modifier.semantics { contentDescription = ratingDescription }) {
        repeat(5) { index ->
            val icon = when {
                index + 1 <= average -> Icons.Rounded.Star
                index < average -> Icons.Rounded.StarHalf
                else -> Icons.Rounded.StarOutline
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (index < average) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DescriptionText(
    addon: Addon,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val description = remember(addon) {
        val detailsText = addon.translateDescription(context)
        HtmlCompat.fromHtml(detailsText.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_COMPACT)
    }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { viewContext ->
            TextView(viewContext).apply {
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.text = description
            textView.setTextColor(textColor)
        },
    )
}

@Composable
private fun InfoCard(
    addon: Addon,
    onShowUpdaterDialog: () -> Unit,
) {
    val context = LocalContext.current
    val version = addon.installedState?.version?.ifEmpty { addon.version } ?: addon.version

    Column {
        NiraCardRow(
            title = stringResource(R.string.mozac_feature_addons_version),
            isTopRow = true,
            isBottomRow = false,
            showDivider = true,
            trailing = {
                Text(
                    text = version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (addon.isInstalled()) {
                        // NiraCardRow has no long-press slot, so the update-attempt dialog
                        // stays on the value text, exactly where the old screen had it.
                        Modifier.combinedClickable(onClick = {}, onLongClick = onShowUpdaterDialog)
                    } else {
                        Modifier
                    },
                )
            },
        )
        NiraCardRow(
            title = stringResource(R.string.mozac_feature_addons_last_updated),
            isTopRow = false,
            isBottomRow = false,
            showDivider = true,
            trailing = {
                Text(
                    text = formatDate(addon.updatedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        NiraCardRow(
            title = stringResource(R.string.mozac_feature_addons_home_page),
            isTopRow = false,
            isBottomRow = true,
            showDivider = false,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW).setData(addon.homepageUrl.toUri())
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

/**
 * The add-on icon on its installed state's drawable, its listing icon URL or the extension
 * glyph as a fallback — the same resolution order the add-ons list uses.
 */
@Composable
internal fun AddonIcon(
    addon: Addon,
    size: Dp,
) {
    val context = LocalContext.current
    val icon = addon.installedState?.icon ?: addon.icon
    val iconUrl = if (addon.installedState != null) "" else addon.iconUrl

    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        when {
            icon != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(icon)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
            iconUrl.isNotEmpty() -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(iconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatDate(text: String): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    return DateFormat.getDateInstance().format(formatter.parse(text)!!)
}
