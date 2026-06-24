package com.prirai.android.nira.browser.sync

import android.content.Context
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mozilla.appservices.fxaclient.FxaConfig
import mozilla.appservices.fxaclient.FxaServer
import mozilla.components.concept.sync.AccountEvent
import mozilla.components.concept.sync.AccountEventsObserver
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.DeviceCapability
import mozilla.components.concept.sync.DeviceCommandIncoming
import mozilla.components.concept.sync.DeviceConfig
import mozilla.components.concept.sync.DeviceType
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.Profile
import mozilla.components.service.fxa.FxaAuthData
import mozilla.components.service.fxa.PeriodicSyncConfig
import mozilla.components.service.fxa.ServerConfig
import mozilla.components.service.fxa.SyncConfig
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.service.fxa.toAuthType

/**
 * Singleton wrapper around [FxaAccountManager].
 * Handles: initialization, sign-in, sign-out, sync trigger, and account state observation.
 * Only syncs data for the default profile (contextId = null / "profile_default").
 */
class FxaSyncManager private constructor(private val context: Context) {

    companion object {
        const val CLIENT_ID = "a2270f727f45f648"
        const val REDIRECT_URL = "urn:ietf:wg:oauth:2.0:oob:oauth-redirect-webchannel"
        const val WEB_CHANNEL_MESSAGING_ID = "mozacWebchannel"

        @Volatile private var instance: FxaSyncManager? = null

        /** The installed FxA WebChannel extension, set after successful installation. */
        @Volatile var webChannelExtension: mozilla.components.concept.engine.webextension.WebExtension? = null

        /** Debug info from the last finishAuthentication attempt. */
        @Volatile var lastAuthDebugInfo: String? = null
        @Volatile var lastAuthTimestamp: Long = 0L

        fun getInstance(context: Context): FxaSyncManager =
            instance ?: synchronized(this) {
                instance ?: FxaSyncManager(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val noOpLifecycleOwner = object : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        init {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    /** Exposed for use by [mozilla.components.feature.accounts.FxaWebChannelFeature]. */
    val serverConfig = FxaConfig(FxaServer.Release, CLIENT_ID, REDIRECT_URL)

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _accountEmail = MutableStateFlow<String?>(null)
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _authSuccessEvent = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val authSuccessEvent: SharedFlow<String?> = _authSuccessEvent.asSharedFlow()

    private val _incomingTabs = MutableSharedFlow<DeviceCommandIncoming.TabReceived>(extraBufferCapacity = 5)
    val incomingTabs: SharedFlow<DeviceCommandIncoming.TabReceived> = _incomingTabs.asSharedFlow()

    val accountManager: FxaAccountManager by lazy {
        FxaAccountManager(
            context = context,
            serverConfig = ServerConfig(FxaServer.Release, CLIENT_ID, REDIRECT_URL),
            deviceConfig = DeviceConfig(
                name = "Nira Browser on ${Build.MODEL}",
                type = DeviceType.MOBILE,
                capabilities = setOf(DeviceCapability.SEND_TAB),
                secureStateAtRest = true,
            ),
            syncConfig = SyncConfig(
                supportedEngines = setOf(SyncEngine.History, SyncEngine.Tabs, SyncEngine.Bookmarks),
                periodicSyncConfig = PeriodicSyncConfig()
            ),
            applicationScopes = setOf("https://identity.mozilla.com/apps/oldsync")
        ).also { manager ->
            manager.register(accountObserver)
            manager.registerForAccountEvents(accountEventsObserver, noOpLifecycleOwner, false)
            // start() is called explicitly from BrowserApp.initializeAfterFirstFrame()
        }
    }

    private val accountObserver = object : AccountObserver {

        override fun onReady(authenticatedAccount: OAuthAccount?) {
            _isSignedIn.value = authenticatedAccount != null
            if (authenticatedAccount != null) {
                scope.launch {
                    _accountEmail.value = accountManager.accountProfile()?.email
                    // Auto-sync on restart so previously synced data is available
                    triggerSync()
                }
            }
        }

        override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
            _isSignedIn.value = true
            _syncError.value = null
            scope.launch {
                val profile = accountManager.accountProfile()
                _accountEmail.value = profile?.email
                _authSuccessEvent.emit(profile?.email)
                // Trigger initial sync after successful authentication so data
                // starts flowing immediately instead of waiting for manual "Sync Now".
                triggerSync()
            }
        }

        override fun onLoggedOut() {
            _isSignedIn.value = false
            _accountEmail.value = null
            _lastSyncTime.value = null
            _syncError.value = null
        }

        override fun onProfileUpdated(profile: Profile) {
            _accountEmail.value = profile.email
        }

        override fun onAuthenticationProblems() {
            _isSignedIn.value = false
            _syncError.value = "Authentication problem — please sign in again."
        }
    }

    private val accountEventsObserver = object : AccountEventsObserver {
        override fun onEvents(events: List<AccountEvent>) {
            events.forEach {
                when (it) {
                    is AccountEvent.DeviceCommandIncoming -> {
                        when (it.command) {
                            is DeviceCommandIncoming.TabReceived -> {
                                val cmd = it.command as DeviceCommandIncoming.TabReceived
                                scope.launch { _incomingTabs.emit(cmd) }
                            }
                            is DeviceCommandIncoming.TabsClosed -> { /* ignore */ }
                        }
                    }
                    else -> { /* ignore other event types */ }
                }
            }
        }
    }

    suspend fun start() {
        try {
            accountManager.start()
            android.util.Log.d("FxaAuth", "start() completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("FxaAuth", "start() failed: ${e.message}")
        }
    }

    suspend fun signOut() {
        try {
            accountManager.logout()
        } catch (e: Exception) {
            android.util.Log.e("FxaAuth", "signOut error: ${e.message}")
        }
    }

    suspend fun finishAuthentication(code: String, state: String, action: String?) {
        val ts = System.currentTimeMillis()
        try {
            val authData = FxaAuthData(
                authType = action.toAuthType(),
                code = code,
                state = state,
            )
            val result = accountManager.finishAuthentication(authData)
            // Check account state RIGHT AFTER finishAuthentication returns
            val hasAccountNow = accountManager.authenticatedAccount() != null
            val emailNow = accountManager.accountProfile()?.email
            lastAuthDebugInfo = "SUCCESS: result=$result immediate after: hasAccount=$hasAccountNow email=$emailNow (action=$action, code=${code.take(6)}..., state=${state.take(6)}...)"
            lastAuthTimestamp = ts
            android.util.Log.d("FxaAuth", "finishAuthentication returned $result, hasAccount=$hasAccountNow email=$emailNow")

            // Re-check after a short delay to catch any tear-down
            if (!hasAccountNow) {
                kotlinx.coroutines.delay(2000)
                val hasAccountDelayed = accountManager.authenticatedAccount() != null
                val emailDelayed = accountManager.accountProfile()?.email
                lastAuthDebugInfo += " | 2s later: hasAccount=$hasAccountDelayed email=$emailDelayed"
                android.util.Log.d("FxaAuth", "2s later: hasAccount=$hasAccountDelayed email=$emailDelayed")
            }
        } catch (e: Exception) {
            lastAuthDebugInfo = "ERROR: finishAuthentication threw ${e::class.simpleName}: ${e.message}"
            lastAuthTimestamp = ts
            android.util.Log.e("FxaAuth", "finishAuthentication failed: ${e.message}")
            android.util.Log.e("FxaAuth", "stacktrace", e)
        }
    }

    suspend fun triggerSync() {
        try {
            _isSyncing.value = true
            _syncError.value = null
            accountManager.syncNow(reason = SyncReason.User)
            accountManager.authenticatedAccount()?.deviceConstellation()?.pollForCommands()
            _lastSyncTime.value = System.currentTimeMillis()
        } catch (e: Exception) {
            _syncError.value = "Sync failed: ${e.message ?: "network error"}"
        } finally {
            _isSyncing.value = false
        }
        // After sync completes, copy synced bookmarks into local bookmark manager
        syncBookmarksToLocal()
    }

    private suspend fun syncBookmarksToLocal() {
        try {
            val app = context as com.prirai.android.nira.BrowserApp
            val placesStorage = app.components.bookmarksStorage
            val localManager = com.prirai.android.nira.browser.bookmark.repository.BookmarkManager.getInstance(context)
            
            // Try root guid, then fall back to empty string
            val tree = placesStorage.getTree("root________", true).getOrNull()
                ?: placesStorage.getTree("", true).getOrNull()
            if (tree == null) {
                android.util.Log.d("FxaAuth", "syncBookmarksToLocal: tree is null, no bookmarks found")
                return
            }
            android.util.Log.d("FxaAuth", "syncBookmarksToLocal: root title='${tree.title}' children=${tree.children?.size}")
            
            val children = tree.children ?: return
            var added = 0
            fun walkNodes(nodes: List<mozilla.components.concept.storage.BookmarkNode>) {
                for (node in nodes) {
                    when (node.type) {
                        BookmarkNodeType.FOLDER -> {
                            node.children?.let { walkNodes(it) }
                        }
                        BookmarkNodeType.ITEM -> {
                            val url = node.url ?: continue
                            val title = node.title ?: url
                            if (!localManager.isBookmarked(url)) {
                                localManager.add(localManager.root,
                                    com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem(
                                        title, url, System.currentTimeMillis()
                                    )
                                )
                                added++
                            }
                        }
                        BookmarkNodeType.SEPARATOR -> { }
                    }
                }
            }
            walkNodes(children)
            
            if (added > 0) {
                localManager.save()
                android.util.Log.d("FxaAuth", "syncBookmarksToLocal: added $added bookmarks")
            } else {
                android.util.Log.d("FxaAuth", "syncBookmarksToLocal: no new bookmarks to add (${countBookmarks(tree)} total in sync)")
            }
        } catch (e: Exception) {
            android.util.Log.e("FxaAuth", "syncBookmarksToLocal error: ${e.message}")
            android.util.Log.e("FxaAuth", "syncBookmarksToLocal stack:", e)
        }
    }

    private fun countBookmarks(node: mozilla.components.concept.storage.BookmarkNode): Int {
        var count = 0
        if (node.type == BookmarkNodeType.ITEM) count++
        node.children?.forEach { count += countBookmarks(it) }
        return count
    }

    fun isSignedIn(): Boolean = accountManager.authenticatedAccount() != null

    fun getAccountEmail(): String? = accountManager.accountProfile()?.email

    fun close() {
        accountManager.close()
    }
}
