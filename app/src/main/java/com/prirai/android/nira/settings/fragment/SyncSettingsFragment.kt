package com.prirai.android.nira.settings.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.sync.FxAEntryPoint
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.manager.SyncEnginesStorage
import mozilla.components.service.fxa.sync.SyncStatusObserver
import java.text.DateFormat
import java.util.Date

class SyncSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val prefs = UserPreferences(context)
                NiraTheme(
                    darkTheme = ThemeManager.isDarkMode(context),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors
                ) {
                    val syncManager = requireContext().components.fxaSyncManager
                    val coroutineScope = rememberCoroutineScope()

                    val settingsEntryPoint = object : FxAEntryPoint {
                        override val entryName = "app-settings"
                    }

                    val isSignedIn by syncManager.isSignedIn.collectAsState()
                    val accountEmail by syncManager.accountEmail.collectAsState()
                    val lastSyncTime by syncManager.lastSyncTime.collectAsState()
                    val isSyncing by syncManager.isSyncing.collectAsState()
                    val syncError by syncManager.syncError.collectAsState()

                    // Sync engine toggle state backed by UserPreferences
                    var syncHistoryEnabled by remember { mutableStateOf(prefs.syncHistoryEnabled) }
                    var syncTabsEnabled by remember { mutableStateOf(prefs.syncTabsEnabled) }
                    var syncBookmarksEnabled by remember { mutableStateOf(prefs.syncBookmarksEnabled) }

                    // Live state updated by SyncStatusObserver
                    var liveIsSyncing by remember { mutableStateOf(false) }
                    var liveLastSyncTime by remember { mutableStateOf<Long?>(null) }

                    // Connected device count
                    var deviceCount by remember { mutableStateOf<Int?>(null) }

                    val lifecycleOwner = LocalLifecycleOwner.current

                    // Refresh auth state on resume
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                coroutineScope.launch { syncManager.refreshAuthState() }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    // Show toast on successful sign-in
                    LaunchedEffect(Unit) {
                        syncManager.authSuccessEvent.collect { email ->
                            val msg = if (email != null) "Signed in as $email" else "Signed in to Firefox"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }

                    // Load connected device count when signed in
                    LaunchedEffect(isSignedIn) {
                        if (isSignedIn) {
                            try {
                                val constellation = withContext(Dispatchers.IO) {
                                    syncManager.accountManager.authenticatedAccount()?.deviceConstellation()
                                }
                                withContext(Dispatchers.IO) { constellation?.refreshDevices() }
                                deviceCount = constellation?.state()?.otherDevices?.size
                            } catch (_: Exception) { }
                        } else {
                            deviceCount = null
                        }
                    }

                    // Register SyncStatusObserver for live sync state updates
                    DisposableEffect(isSignedIn) {
                        if (isSignedIn) {
                            val syncObserver = object : SyncStatusObserver {
                                override fun onStarted() {
                                    coroutineScope.launch { liveIsSyncing = true }
                                }

                                override fun onIdle() {
                                    coroutineScope.launch {
                                        liveIsSyncing = false
                                        liveLastSyncTime = System.currentTimeMillis()
                                    }
                                }

                                override fun onError(error: Exception?) {
                                    coroutineScope.launch { liveIsSyncing = false }
                                }
                            }
                            try {
                                syncManager.accountManager.registerForSyncEvents(
                                    observer = syncObserver,
                                    owner = lifecycleOwner,
                                    autoPause = true
                                )
                            } catch (_: Exception) { }
                            onDispose {
                                try {
                                    syncManager.accountManager.unregisterForSyncEvents(syncObserver)
                                } catch (_: Exception) { }
                            }
                        } else {
                            onDispose { }
                        }
                    }

                    var isSigningIn by remember { mutableStateOf(false) }
                    LaunchedEffect(isSignedIn) {
                        if (isSignedIn) isSigningIn = false
                    }

                    // Merge StateFlow values with live observer values
                    val effectiveIsSyncing = isSyncing || liveIsSyncing
                    val effectiveLastSyncTime = liveLastSyncTime ?: lastSyncTime

                    SyncSettingsScreen(
                        isSignedIn = isSignedIn,
                        accountEmail = accountEmail,
                        lastSyncTime = effectiveLastSyncTime,
                        isSyncing = effectiveIsSyncing,
                        syncError = syncError,
                        isSigningIn = isSigningIn,
                        deviceCount = deviceCount,
                        syncHistoryEnabled = syncHistoryEnabled,
                        syncTabsEnabled = syncTabsEnabled,
                        syncBookmarksEnabled = syncBookmarksEnabled,
                        onSignIn = {
                            isSigningIn = true
                            requireContext().components.fxaAuthFeature
                                .beginAuthentication(requireContext(), settingsEntryPoint)
                        },
                        onSignOut = { coroutineScope.launch { syncManager.signOut() } },
                        onSyncNow = { coroutineScope.launch { syncManager.triggerSync() } },
                        onManageAccount = {
                            coroutineScope.launch {
                                try {
                                    val account = syncManager.accountManager.authenticatedAccount()
                                        ?: return@launch
                                    val url = account.getManageAccountURL(settingsEntryPoint)
                                    if (url != null) {
                                        context.components.tabsUseCases.addTab(url, selectTab = true)
                                        context.startActivity(
                                            Intent(context, BrowserActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                            }
                                        )
                                    }
                                } catch (_: Exception) { }
                            }
                        },
                        onEngineToggle = { engine, enabled ->
                            when (engine) {
                                SyncEngine.History -> {
                                    prefs.syncHistoryEnabled = enabled
                                    syncHistoryEnabled = enabled
                                }
                                SyncEngine.Tabs -> {
                                    prefs.syncTabsEnabled = enabled
                                    syncTabsEnabled = enabled
                                }
                                SyncEngine.Bookmarks -> {
                                    prefs.syncBookmarksEnabled = enabled
                                    syncBookmarksEnabled = enabled
                                }
                                else -> {}
                            }
                            coroutineScope.launch {
                                try {
                                    SyncEnginesStorage(context).setStatus(engine, enabled)
                                    syncManager.triggerSync()
                                } catch (_: Exception) { }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncSettingsScreen(
    isSignedIn: Boolean,
    accountEmail: String?,
    lastSyncTime: Long?,
    isSyncing: Boolean,
    syncError: String?,
    isSigningIn: Boolean,
    deviceCount: Int?,
    syncHistoryEnabled: Boolean,
    syncTabsEnabled: Boolean,
    syncBookmarksEnabled: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onManageAccount: () -> Unit,
    onEngineToggle: (SyncEngine, Boolean) -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Firefox Sync",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (isSignedIn) {
                SignedInContent(
                    accountEmail = accountEmail,
                    lastSyncTime = lastSyncTime,
                    isSyncing = isSyncing,
                    syncError = syncError,
                    deviceCount = deviceCount,
                    onSyncNow = onSyncNow,
                    onSignOut = onSignOut,
                    onManageAccount = onManageAccount
                )
            } else {
                SignedOutContent(
                    syncError = syncError,
                    isSigningIn = isSigningIn,
                    onSignIn = onSignIn
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SyncEnginesCard(
                isSignedIn = isSignedIn,
                syncHistoryEnabled = syncHistoryEnabled,
                syncTabsEnabled = syncTabsEnabled,
                syncBookmarksEnabled = syncBookmarksEnabled,
                onEngineToggle = onEngineToggle
            )
        }
    }
}

@Composable
private fun SignedInContent(
    accountEmail: String?,
    lastSyncTime: Long?,
    isSyncing: Boolean,
    syncError: String?,
    deviceCount: Int?,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onManageAccount: () -> Unit
) {
    // Account info card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Circle avatar with first letter of email
                val initial = accountEmail?.firstOrNull()?.uppercaseChar()?.toString() ?: "F"
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Firefox Account",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = accountEmail ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val formatted = remember(lastSyncTime) {
                        if (lastSyncTime != null) {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(lastSyncTime))
                        } else null
                    }
                    Text(
                        text = if (formatted != null) "Last synced: $formatted" else "Never synced",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                VerticalDivider(
                    modifier = Modifier.height(52.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text("Active", style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    border = null
                )
            }

            if (deviceCount != null && deviceCount > 0) {
                Text(
                    text = "Connected to $deviceCount other device${if (deviceCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }

    // Sync error banner
    if (syncError != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = syncError,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    // Sync Now + Sign Out
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onSyncNow,
            enabled = !isSyncing,
            modifier = Modifier.weight(1f)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isSyncing) "Syncing…" else "Sync Now")
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Sign Out")
        }
    }

    // Manage Account
    OutlinedButton(
        onClick = onManageAccount,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Manage Account")
    }
}

@Composable
private fun SignedOutContent(
    syncError: String?,
    isSigningIn: Boolean,
    onSignIn: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Sync your browsing data across devices using a Firefox Account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (syncError != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = syncError,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    Button(
        onClick = onSignIn,
        enabled = !isSigningIn,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isSigningIn) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isSigningIn) "Opening sign-in…" else "Sign in to Firefox")
    }
}

@Composable
private fun SyncEnginesCard(
    isSignedIn: Boolean,
    syncHistoryEnabled: Boolean,
    syncTabsEnabled: Boolean,
    syncBookmarksEnabled: Boolean,
    onEngineToggle: (SyncEngine, Boolean) -> Unit
) {
    Text(
        text = "What syncs",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            SyncEngineRow(
                icon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(20.dp)) },
                label = "History",
                enabled = syncHistoryEnabled,
                showToggle = isSignedIn,
                onToggleChange = if (isSignedIn) ({ onEngineToggle(SyncEngine.History, it) }) else null
            )
            SyncEngineRow(
                icon = { Icon(Icons.Rounded.Tab, contentDescription = null, modifier = Modifier.size(20.dp)) },
                label = "Open tabs",
                enabled = syncTabsEnabled,
                showToggle = isSignedIn,
                onToggleChange = if (isSignedIn) ({ onEngineToggle(SyncEngine.Tabs, it) }) else null
            )
            SyncEngineRow(
                icon = { Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(20.dp)) },
                label = "Bookmarks",
                enabled = syncBookmarksEnabled,
                showToggle = isSignedIn,
                onToggleChange = if (isSignedIn) ({ onEngineToggle(SyncEngine.Bookmarks, it) }) else null
            )
        }
    }
}

@Composable
private fun SyncEngineRow(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    note: String? = null,
    showToggle: Boolean = false,
    onToggleChange: ((Boolean) -> Unit)? = null
) {
    if (showToggle && onToggleChange != null) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            supportingContent = note?.let { { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) } },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            },
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleChange
                )
            }
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline
                )
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (enabled) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Enabled",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
