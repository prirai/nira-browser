package com.prirai.android.nira.browser.sync

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mozilla.appservices.fxaclient.FxaServer
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.DeviceCapability
import mozilla.components.concept.sync.DeviceConfig
import mozilla.components.concept.sync.DeviceType
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.Profile
import mozilla.components.service.fxa.PeriodicSyncConfig
import mozilla.components.service.fxa.ServerConfig
import mozilla.components.service.fxa.SyncConfig
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.sync.SyncReason

/**
 * Singleton wrapper around [FxaAccountManager].
 * Handles: initialization, sign-in, sign-out, sync trigger, and account state observation.
 * Only syncs data for the default profile (contextId = null / "profile_default").
 */
class FxaSyncManager private constructor(private val context: Context) {

    companion object {
        const val CLIENT_ID = "3c49430b43dfba77"
        const val REDIRECT_URL = "https://accounts.firefox.com/oauth/success/$CLIENT_ID"

        @Volatile private var instance: FxaSyncManager? = null

        fun getInstance(context: Context): FxaSyncManager =
            instance ?: synchronized(this) {
                instance ?: FxaSyncManager(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    val accountManager: FxaAccountManager by lazy {
        FxaAccountManager(
            context = context,
            serverConfig = ServerConfig(FxaServer.Release, CLIENT_ID, REDIRECT_URL),
            deviceConfig = DeviceConfig(
                name = "Nira Browser on ${Build.MODEL}",
                type = DeviceType.MOBILE,
                capabilities = setOf(DeviceCapability.SEND_TAB)
            ),
            syncConfig = SyncConfig(
                supportedEngines = setOf(SyncEngine.History, SyncEngine.Tabs),
                periodicSyncConfig = PeriodicSyncConfig()
            ),
            applicationScopes = setOf("https://identity.mozilla.com/apps/oldsync")
        ).also { manager ->
            manager.register(accountObserver)
        }
    }

    private val accountObserver = object : AccountObserver {
        override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
            _isSignedIn.value = true
            _syncError.value = null
            scope.launch {
                _accountEmail.value = accountManager.accountProfile()?.email
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

    /**
     * Starts the account manager and restores any existing session.
     * Must be called once during app startup (from a coroutine).
     * Gracefully degrades if initialization fails.
     */
    suspend fun initialize() {
        try {
            accountManager.start()
            _isSignedIn.value = accountManager.authenticatedAccount() != null
            if (_isSignedIn.value) {
                _accountEmail.value = accountManager.accountProfile()?.email
            }
        } catch (e: Exception) {
            // Sync is unavailable but the app continues normally
        }
    }

    suspend fun signOut() {
        try {
            accountManager.logout()
        } catch (e: Exception) {
            // Ignore sign-out errors
        }
    }

    /** Triggers an immediate sync. Catches network errors and exposes them via [syncError]. */
    suspend fun triggerSync() {
        try {
            _isSyncing.value = true
            _syncError.value = null
            accountManager.syncNow(reason = SyncReason.User)
            _lastSyncTime.value = System.currentTimeMillis()
        } catch (e: Exception) {
            _syncError.value = "Sync failed: ${e.message ?: "network error"}"
        } finally {
            _isSyncing.value = false
        }
    }

    fun isSignedIn(): Boolean = accountManager.authenticatedAccount() != null

    fun getAccountEmail(): String? = accountManager.accountProfile()?.email
}
