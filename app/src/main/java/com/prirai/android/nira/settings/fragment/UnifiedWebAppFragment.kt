@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.settings.fragment

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.components.animateNiraGridItem
import com.prirai.android.nira.ui.components.animateNiraListItem
import com.prirai.android.nira.ui.components.pressScale
import com.prirai.android.nira.ui.theme.NiraTheme
import com.prirai.android.nira.utils.FaviconLoader
import com.prirai.android.nira.webapp.PwaSuggestionManager
import com.prirai.android.nira.webapp.WebAppActivity
import com.prirai.android.nira.webapp.WebAppClearDataDialog
import com.prirai.android.nira.webapp.WebAppCloneDialog
import com.prirai.android.nira.webapp.WebAppEntity
import com.prirai.android.nira.webapp.WebAppInstallDialogContent
import com.prirai.android.nira.webapp.WebAppProfileDialog
import com.prirai.android.nira.webapp.WebAppUninstallDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

/**
 * Unified fragment for managing installed PWAs and discovering new ones,
 * on the shared Material 3 Expressive stack: an M3 [TabRow] switching
 * between the installed-apps list (grouped [NiraCardRow] rows with an
 * enable switch and a per-row action menu) and the PWA suggestion grid.
 */
class UnifiedWebAppFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val prefs = UserPreferences(context)
                NiraTheme(
                    darkTheme = ThemeManager.isDarkMode(context),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors,
                ) {
                    WebAppsScreen()
                }
            }
        }
    }

    companion object {
        fun newInstance(): UnifiedWebAppFragment {
            return UnifiedWebAppFragment()
        }
    }
}

/** An acknowledged message dialog: title, body and an optional labelled confirm action. */
private data class MessageDialog(
    val title: String,
    val message: String,
    val confirmText: String? = null,
    val onConfirm: (() -> Unit)? = null,
)

@Composable
private fun WebAppsScreen() {
    val context = LocalContext.current
    val components = context.components
    val scope = rememberCoroutineScope()
    val suggestionManager = remember { components.pwaSuggestionManager }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val installedApps by components.webAppManager.getAllWebApps().collectAsState(initial = emptyList())
    var suggestions by remember {
        mutableStateOf<List<PwaSuggestionManager.PwaSuggestion>>(emptyList())
    }

    DisposableEffect(suggestionManager) {
        val observer = Observer<List<PwaSuggestionManager.PwaSuggestion>> { suggestions = it }
        suggestionManager.suggestedPwas.observeForever(observer)
        onDispose { suggestionManager.suggestedPwas.removeObserver(observer) }
    }
    LaunchedEffect(suggestionManager) { suggestionManager.getAllSuggestedPwas() }

    var installTarget by remember { mutableStateOf<PwaSuggestionManager.PwaSuggestion?>(null) }
    var cloneTarget by remember { mutableStateOf<WebAppEntity?>(null) }
    var profileTarget by remember { mutableStateOf<WebAppEntity?>(null) }
    var uninstallTarget by remember { mutableStateOf<WebAppEntity?>(null) }
    var clearDataTarget by remember { mutableStateOf<WebAppEntity?>(null) }
    var message by remember { mutableStateOf<MessageDialog?>(null) }

    fun showMessage(
        title: String,
        messageText: String,
        confirmText: String? = null,
        onConfirm: (() -> Unit)? = null,
    ) {
        message = MessageDialog(title, messageText, confirmText, onConfirm)
    }

    fun launchWebApp(webApp: WebAppEntity) {
        scope.launch {
            components.webAppManager.updateWebAppUsage(webApp.id)
            val intent = Intent(context, WebAppActivity::class.java).apply {
                putExtra(WebAppActivity.EXTRA_WEB_APP_URL, webApp.url)
            }
            context.startActivity(intent)
        }
    }

    fun launchSuggestedPwa(pwa: PwaSuggestionManager.PwaSuggestion) {
        context.startActivity(
            Intent(context, WebAppActivity::class.java).apply {
                putExtra(WebAppActivity.EXTRA_WEB_APP_URL, pwa.url)
            },
        )
    }

    fun addShortcut(webApp: WebAppEntity) {
        scope.launch {
            try {
                val icon = withContext(Dispatchers.IO) {
                    components.webAppManager.loadIconFromFile(webApp.iconUrl)
                        ?: FaviconLoader.loadFavicon(context, webApp.url)
                }
                val shortcut = ShortcutInfoCompat.Builder(context, "webapp_${webApp.id}")
                    .setShortLabel(webApp.name)
                    .setLongLabel(webApp.name)
                    .setIntent(
                        Intent(context, WebAppActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            data = webApp.url.toUri()
                            putExtra(WebAppActivity.EXTRA_WEB_APP_URL, webApp.url)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        },
                    )
                    .apply {
                        setIcon(
                            if (icon != null) {
                                IconCompat.createWithBitmap(icon)
                            } else {
                                IconCompat.createWithResource(context, R.drawable.ic_language)
                            },
                        )
                    }
                    .build()

                // The system pins the shortcut on the user's confirmation.
                ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            } catch (e: Exception) {
                showMessage(
                    context.getString(R.string.shortcut_failed),
                    e.message ?: context.getString(R.string.unknown_error),
                )
            }
        }
    }

    fun startInstallation(pwa: PwaSuggestionManager.PwaSuggestion, profileId: String) {
        installTarget = null
        scope.launch {
            try {
                // Check if already installed with same profile
                if (components.webAppManager.webAppExists(pwa.url, profileId)) {
                    showMessage(
                        context.getString(R.string.already_installed),
                        context.getString(R.string.web_app_already_installed_profile),
                    )
                    return@launch
                }

                val icon: Bitmap? = withContext(Dispatchers.IO) {
                    runCatching {
                        // PWA-optimized loader (Google service + fallbacks),
                        // higher res for installation.
                        FaviconLoader.loadFaviconForPwa(context, pwa.url, size = 128)
                    }.getOrNull()
                }

                components.webAppManager.installWebApp(
                    url = pwa.url,
                    name = pwa.name,
                    manifestUrl = null,
                    icon = icon,
                    themeColor = null,
                    backgroundColor = null,
                    profileId = profileId,
                )

                val intent = Intent(context, WebAppActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = pwa.url.toUri()
                    putExtra(WebAppActivity.EXTRA_WEB_APP_URL, pwa.url)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                }

                val shortcut = ShortcutInfoCompat.Builder(context, pwa.url)
                    .setShortLabel(pwa.name)
                    .setLongLabel(pwa.name)
                    .setIntent(intent)
                    .apply {
                        setIcon(
                            if (icon != null) {
                                IconCompat.createWithBitmap(icon)
                            } else {
                                IconCompat.createWithResource(context, R.drawable.ic_language)
                            },
                        )
                    }
                    .build()

                ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)

                showMessage(
                    context.getString(R.string.app_installed),
                    context.getString(R.string.pwa_installation_complete, pwa.name),
                    confirmText = context.getString(R.string.launch),
                    onConfirm = { launchSuggestedPwa(pwa) },
                )

                // Switch to the installed-apps tab, as before.
                selectedTab = 0
            } catch (e: Exception) {
                showMessage(
                    context.getString(R.string.installation_failed),
                    e.message ?: context.getString(R.string.unknown_error),
                )
            }
        }
    }

    fun cloneWebApp(webApp: WebAppEntity, newName: String, profileId: String) {
        cloneTarget = null
        scope.launch {
            try {
                val webAppManager = components.webAppManager
                webAppManager.installWebApp(
                    url = webApp.url,
                    name = newName,
                    manifestUrl = webApp.manifestUrl,
                    icon = webAppManager.loadIconFromFile(webApp.iconUrl),
                    themeColor = webApp.themeColor,
                    backgroundColor = webApp.backgroundColor,
                    profileId = profileId,
                )
                showMessage(
                    context.getString(R.string.success),
                    context.getString(R.string.web_app_cloned_success, newName),
                )
            } catch (e: Exception) {
                showMessage(
                    context.getString(R.string.error),
                    e.message ?: context.getString(R.string.unknown_error),
                )
            }
        }
    }

    fun associateProfile(webApp: WebAppEntity, profileId: String) {
        profileTarget = null
        scope.launch {
            components.webAppManager.updateWebApp(webApp.copy(profileId = profileId))
            showMessage(
                context.getString(R.string.success),
                context.getString(R.string.profile_associated),
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.installed_apps)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.discover_apps)) },
            )
        }

        val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = effectsSpec) togetherWith fadeOut(animationSpec = effectsSpec)
            },
            label = "webapps-tab",
            modifier = Modifier.weight(1f),
        ) { tab ->
            when (tab) {
                0 -> InstalledAppsPane(
                    installedApps = installedApps,
                    onLaunch = { launchWebApp(it) },
                    onEnableToggle = { webApp, enabled ->
                        scope.launch {
                            components.webAppManager.setWebAppEnabled(webApp.id, enabled)
                        }
                    },
                    onAddShortcut = { addShortcut(it) },
                    onAssociateProfile = { profileTarget = it },
                    onClone = { cloneTarget = it },
                    onClearData = { clearDataTarget = it },
                    onUninstall = { uninstallTarget = it },
                )
                else -> DiscoverPane(
                    suggestions = suggestions,
                    onInstall = { installTarget = it },
                )
            }
        }
    }

    installTarget?.let { pwa ->
        WebAppInstallDialogContent(
            appName = pwa.name,
            appUrl = pwa.url,
            appDescription = pwa.description,
            defaultProfileId = ProfileManager.getInstance(context).getActiveProfile().id,
            onDismiss = { installTarget = null },
            onInstall = { startInstallation(pwa, it) },
        )
    }

    cloneTarget?.let { webApp ->
        WebAppCloneDialog(
            webApp = webApp,
            onDismiss = { cloneTarget = null },
            onClone = { name, profileId -> cloneWebApp(webApp, name, profileId) },
        )
    }

    profileTarget?.let { webApp ->
        WebAppProfileDialog(
            webApp = webApp,
            onDismiss = { profileTarget = null },
            onAssociate = { profileId -> associateProfile(webApp, profileId) },
        )
    }

    uninstallTarget?.let { webApp ->
        WebAppUninstallDialog(
            webApp = webApp,
            onDismiss = { uninstallTarget = null },
            onConfirm = {
                uninstallTarget = null
                scope.launch { components.webAppManager.uninstallWebApp(webApp.id) }
            },
        )
    }

    clearDataTarget?.let { webApp ->
        WebAppClearDataDialog(
            webApp = webApp,
            onDismiss = { clearDataTarget = null },
            onConfirm = {
                clearDataTarget = null
                // Honest UX: the engine-level wipe isn't implemented yet
                // (see WebAppManager) - keep saying so instead of lying
                // about success.
                showMessage(
                    webApp.name,
                    context.getString(R.string.web_app_action_not_implemented),
                )
            },
        )
    }

    message?.let { msg ->
        NiraAlertDialog(
            onDismissRequest = { message = null },
            title = {
                Text(
                    text = msg.title,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            },
            text = { Text(msg.message) },
            confirmButton = {
                Button(
                    onClick = {
                        message = null
                        msg.onConfirm?.invoke()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(msg.confirmText ?: stringResource(android.R.string.ok))
                }
            },
        )
    }
}

/** The Installed tab: a grouped [NiraCardRow] list of the installed web apps. */
@Composable
private fun InstalledAppsPane(
    installedApps: List<WebAppEntity>,
    onLaunch: (WebAppEntity) -> Unit,
    onEnableToggle: (WebAppEntity, Boolean) -> Unit,
    onAddShortcut: (WebAppEntity) -> Unit,
    onAssociateProfile: (WebAppEntity) -> Unit,
    onClone: (WebAppEntity) -> Unit,
    onClearData: (WebAppEntity) -> Unit,
    onUninstall: (WebAppEntity) -> Unit,
) {
    if (installedApps.isEmpty()) {
        NiraEmptyState(
            title = stringResource(R.string.no_installed_webapps),
            icon = Icons.Rounded.Apps,
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(
            items = installedApps,
            key = { _, webApp -> "webapp-${webApp.id}" },
        ) { index, webApp ->
            InstalledWebAppRow(
                webApp = webApp,
                isTopRow = index == 0,
                isBottomRow = index == installedApps.lastIndex,
                onLaunch = { onLaunch(webApp) },
                onEnableToggle = { onEnableToggle(webApp, it) },
                onAddShortcut = { onAddShortcut(webApp) },
                onAssociateProfile = { onAssociateProfile(webApp) },
                onClone = { onClone(webApp) },
                onClearData = { onClearData(webApp) },
                onUninstall = { onUninstall(webApp) },
                modifier = Modifier.then(animateNiraListItem()),
            )
        }
    }
}

/** The Discover tab: the PWA suggestion grid. */
@Composable
private fun DiscoverPane(
    suggestions: List<PwaSuggestionManager.PwaSuggestion>,
    onInstall: (PwaSuggestionManager.PwaSuggestion) -> Unit,
) {
    if (suggestions.isEmpty()) {
        NiraEmptyState(
            title = stringResource(R.string.no_suggested_webapps),
            icon = Icons.Rounded.TravelExplore,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = suggestions,
            key = { "pwa-${it.url}" },
        ) { pwa ->
            PwaSuggestionCard(
                pwa = pwa,
                onClick = { onInstall(pwa) },
                modifier = Modifier.then(animateNiraGridItem()),
            )
        }
    }
}

/**
 * One installed web app on the shared grouped-card idiom: the favicon
 * and name, the origin / profile / usage metadata, the enable switch
 * and the action menu the old PopupMenu carried.
 */
@Composable
private fun InstalledWebAppRow(
    webApp: WebAppEntity,
    isTopRow: Boolean,
    isBottomRow: Boolean,
    onLaunch: () -> Unit,
    onEnableToggle: (Boolean) -> Unit,
    onAddShortcut: () -> Unit,
    onAssociateProfile: () -> Unit,
    onClone: () -> Unit,
    onClearData: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val profileName = remember(webApp.profileId) {
        ProfileManager.getInstance(context).getAllProfiles()
            .find { it.id == webApp.profileId }?.name
    } ?: stringResource(R.string.webapp_default_profile_name)

    val lastUsed = if (webApp.lastUsedDate > 0) {
        val daysAgo = remember(webApp.lastUsedDate) {
            ((System.currentTimeMillis() - webApp.lastUsedDate) / DAY_MILLIS).toInt()
        }
        when {
            daysAgo == 0 -> stringResource(R.string.webapp_last_used_today)
            daysAgo == 1 -> stringResource(R.string.webapp_last_used_one_day)
            else -> stringResource(R.string.webapp_last_used_days, daysAgo)
        }
    } else {
        stringResource(R.string.webapp_never_used)
    }

    val switchLabel = stringResource(R.string.enabled)

    NiraCardRow(
        title = webApp.name,
        isTopRow = isTopRow,
        isBottomRow = isBottomRow,
        showDivider = !isBottomRow,
        onClick = onLaunch,
        modifier = modifier,
        leading = { InstalledWebAppFavicon(webApp = webApp) },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 150.dp),
            ) {
                Text(
                    text = webApp.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.current_profile, profileName),
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.webapp_usage_info, webApp.launchCount, lastUsed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = webApp.isEnabled,
                onCheckedChange = onEnableToggle,
                modifier = Modifier.semantics { contentDescription = switchLabel },
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_shortcut)) },
                        onClick = {
                            menuOpen = false
                            onAddShortcut()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.associate_profile)) },
                        onClick = {
                            menuOpen = false
                            onAssociateProfile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.clone_webapp)) },
                        onClick = {
                            menuOpen = false
                            onClone()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.clear_data)) },
                        onClick = {
                            menuOpen = false
                            onClearData()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.uninstall)) },
                        onClick = {
                            menuOpen = false
                            onUninstall()
                        },
                    )
                }
            }
        },
    )
}

/**
 * An installed web app's favicon: the icon file saved at install time,
 * then the centralized [FaviconLoader], with the globe glyph as its
 * placeholder while the load is in flight.
 */
@Composable
private fun InstalledWebAppFavicon(webApp: WebAppEntity) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(initialValue = null, webApp.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.components.webAppManager.loadIconFromFile(webApp.iconUrl)
                    ?: FaviconLoader.loadFavicon(context, webApp.url)
            }.getOrNull()
        }
    }

    if (icon != null) {
        Image(
            bitmap = icon!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
    } else {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** One PWA suggestion card: favicon and name; tapping it starts the install flow. */
@Composable
private fun PwaSuggestionCard(
    pwa: PwaSuggestionManager.PwaSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SuggestionFavicon(url = pwa.url)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = pwa.name,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A suggestion's favicon on the PWA-optimized loader - the instant memory
 * cache first, then the Google service with fallbacks - with the globe
 * glyph as its placeholder while the load is in flight.
 */
@Composable
private fun SuggestionFavicon(url: String) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(initialValue = null, url) {
        value = FaviconLoader.getFromMemorySync(context, url)
            ?: FaviconLoader.loadFaviconForPwaWithRetry(context, url, size = 64)
    }

    if (icon != null) {
        Image(
            bitmap = icon!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}
