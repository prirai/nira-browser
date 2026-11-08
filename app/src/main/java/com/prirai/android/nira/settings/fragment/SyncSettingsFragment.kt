package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.MaterialSwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.sync.FxAEntryPoint
import mozilla.components.concept.sync.SyncEngine
import mozilla.components.service.fxa.sync.SyncStatusObserver
import java.text.DateFormat
import java.util.Date

/**
 * Firefox Sync settings, using the same preference-based design language as
 * the other settings pages: a PreferenceScreen (preferences_sync.xml) whose
 * summaries, titles and enabled states are updated live from the
 * [com.prirai.android.nira.browser.sync.FxaSyncManager] StateFlows.
 */
class SyncSettingsFragment : BaseSettingsFragment() {

    private val settingsEntryPoint = object : FxAEntryPoint {
        override val entryName = "app-settings"
    }

    private val syncManager by lazy { requireContext().components.fxaSyncManager }

    // Live state mirrored from the FxaSyncManager StateFlows.
    private var signedIn = false
    private var accountEmail: String? = null
    private var lastSyncTime: Long? = null
    private var isSyncing = false
    private var syncError: String? = null

    // Live state pushed by the SyncStatusObserver - more immediate than the
    // store flows for the in-progress indication on the "Sync now" row.
    private var liveIsSyncing = false
    private var liveLastSyncTime: Long? = null

    // Decorative connected-device count, loaded from the device constellation.
    private var deviceCount: Int? = null

    private var isSigningIn = false
    private var syncObserver: SyncStatusObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeSyncState()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_sync)

        val prefs = UserPreferences(requireContext())

        // Sign in / Sign out - one row whose label and action follow the
        // current account state.
        clickablePreference(preference = getString(R.string.key_sync_sign_in_out)) {
            if (signedIn) {
                lifecycleScope.launch { syncManager.signOut() }
            } else {
                isSigningIn = true
                refreshUi()
                Log.d("FxaAuth", "Sign in pressed, calling beginAuthentication")
                requireContext().components.fxaAuthFeature
                    .beginAuthentication(requireContext(), settingsEntryPoint)
            }
        }

        // Sync now
        clickablePreference(preference = getString(R.string.key_sync_now)) {
            lifecycleScope.launch { syncManager.triggerSync() }
        }

        // Sync engine toggles, backed by the same UserPreferences properties.
        switchPreference(
            preference = getString(R.string.key_sync_history),
            isChecked = prefs.syncHistoryEnabled,
        ) { enabled ->
            prefs.syncHistoryEnabled = enabled
            applyEngineChange(SyncEngine.History, enabled)
        }
        switchPreference(
            preference = getString(R.string.key_sync_bookmarks),
            isChecked = prefs.syncBookmarksEnabled,
        ) { enabled ->
            prefs.syncBookmarksEnabled = enabled
            applyEngineChange(SyncEngine.Bookmarks, enabled)
        }
        switchPreference(
            preference = getString(R.string.key_sync_tabs),
            isChecked = prefs.syncTabsEnabled,
        ) { enabled ->
            prefs.syncTabsEnabled = enabled
            applyEngineChange(SyncEngine.Tabs, enabled)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Seed the mirrored state so the rows render correctly before the
        // collectors deliver their first value.
        signedIn = syncManager.isSignedIn.value
        accountEmail = syncManager.accountEmail.value
        lastSyncTime = syncManager.lastSyncTime.value
        isSyncing = syncManager.isSyncing.value
        syncError = syncManager.syncError.value

        if (signedIn) {
            registerSyncObserver()
            loadDeviceCount()
        }
        refreshUi()
    }

    override fun onDestroyView() {
        unregisterSyncObserver()
        super.onDestroyView()
    }

    /**
     * Collects the FxaSyncManager StateFlows on the main dispatcher and
     * pushes every change straight into the preference rows. The flows are
     * plain Kotlin, not Compose-dependent, so they port cleanly to the
     * preference-based page.
     */
    private fun observeSyncState() {
        lifecycleScope.launch {
            syncManager.isSignedIn.collect { value ->
                signedIn = value
                if (value) {
                    isSigningIn = false
                    registerSyncObserver()
                    loadDeviceCount()
                } else {
                    unregisterSyncObserver()
                    deviceCount = null
                }
                refreshUi()
            }
        }
        lifecycleScope.launch {
            syncManager.accountEmail.collect { value ->
                accountEmail = value
                refreshUi()
            }
        }
        lifecycleScope.launch {
            syncManager.lastSyncTime.collect { value ->
                lastSyncTime = value
                refreshUi()
            }
        }
        lifecycleScope.launch {
            syncManager.isSyncing.collect { value ->
                isSyncing = value
                refreshUi()
            }
        }
        lifecycleScope.launch {
            syncManager.syncError.collect { value ->
                syncError = value
                refreshUi()
            }
        }
        lifecycleScope.launch {
            syncManager.authSuccessEvent.collect { email ->
                val context = context ?: return@collect
                val message = if (email != null) {
                    getString(R.string.signed_in_as, email)
                } else {
                    getString(R.string.signed_in_to_firefox)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Pushes an engine toggle to the account as well, so the server-side
     * choice matches the local UserPreferences value.
     */
    private fun applyEngineChange(engine: SyncEngine, enabled: Boolean) {
        lifecycleScope.launch {
            try {
                syncManager.accountManager.setEngineEnabled(engine, enabled)
                syncManager.triggerSync()
            } catch (e: Exception) {
                // The user just toggled a sync engine and expects action -
                // if the account rejects the update we surface it as a Toast
                // rather than silently leaving the switch in a state that
                // doesn't match the server.
                val context = context ?: return@launch
                val reason = e.message ?: getString(R.string.sync_unknown_error)
                Toast.makeText(
                    context,
                    getString(R.string.sync_engine_change_failed, reason),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /**
     * Connected-device count for the account summary. Non-fatal: the count
     * is decorative, so if FxA is offline we simply leave it out.
     */
    private fun loadDeviceCount() {
        lifecycleScope.launch {
            try {
                val constellation = withContext(Dispatchers.IO) {
                    syncManager.accountManager.authenticatedAccount()?.deviceConstellation()
                }
                withContext(Dispatchers.IO) { constellation?.refreshDevices() }
                deviceCount = constellation?.state()?.otherDevices?.size
            } catch (e: Exception) {
                Log.w("FxaAuth", "Failed to refresh device constellation", e)
            }
            refreshUi()
        }
    }

    private fun registerSyncObserver() {
        if (syncObserver != null) return
        if (view == null) return
        val observer = object : SyncStatusObserver {
            override fun onStarted() {
                liveIsSyncing = true
                refreshUi()
            }

            override fun onIdle() {
                liveIsSyncing = false
                liveLastSyncTime = System.currentTimeMillis()
                refreshUi()
            }

            override fun onError(error: Exception?) {
                liveIsSyncing = false
                refreshUi()
            }
        }
        try {
            syncManager.accountManager.registerForSyncEvents(
                observer = observer,
                owner = viewLifecycleOwner,
                autoPause = true,
            )
            syncObserver = observer
        } catch (e: Exception) {
            // Observer already registered / lifecycle race - log instead of
            // ignoring so the missing sync indicator has an explanation.
            Log.w("FxaAuth", "Failed to register sync observer", e)
        }
    }

    private fun unregisterSyncObserver() {
        val observer = syncObserver ?: return
        try {
            syncManager.accountManager.unregisterForSyncEvents(observer)
        } catch (e: Exception) {
            Log.w("FxaAuth", "Failed to unregister sync observer", e)
        }
        syncObserver = null
    }

    private fun refreshUi() {
        val context = context ?: return
        val effectiveIsSyncing = isSyncing || liveIsSyncing
        val effectiveLastSyncTime = liveLastSyncTime ?: lastSyncTime

        // Account status: email, last sync time, connected devices and any
        // sync error, composed into one summary.
        findPreference<Preference>(getString(R.string.key_sync_account_status))?.summary =
            accountStatusSummary(effectiveLastSyncTime)

        // Sign in / Sign out
        findPreference<Preference>(getString(R.string.key_sync_sign_in_out))?.apply {
            if (signedIn) {
                title = context.getString(R.string.sign_out)
                isEnabled = true
            } else {
                title = if (isSigningIn) {
                    context.getString(R.string.opening_sign_in)
                } else {
                    context.getString(R.string.sign_in_to_firefox)
                }
                isEnabled = !isSigningIn
            }
        }

        // Sync now - only while signed in, disabled while a sync runs
        findPreference<Preference>(getString(R.string.key_sync_now))?.apply {
            isEnabled = signedIn && !effectiveIsSyncing
            summary = if (effectiveIsSyncing) context.getString(R.string.syncing) else null
        }

        // Engine toggles only act on a signed-in account
        val enginesEnabled = signedIn
        findPreference<MaterialSwitchPreference>(getString(R.string.key_sync_history))?.isEnabled = enginesEnabled
        findPreference<MaterialSwitchPreference>(getString(R.string.key_sync_bookmarks))?.isEnabled = enginesEnabled
        findPreference<MaterialSwitchPreference>(getString(R.string.key_sync_tabs))?.isEnabled = enginesEnabled
    }

    private fun accountStatusSummary(lastSync: Long?): String {
        val context = context ?: return ""
        val lines = mutableListOf<String>()
        if (signedIn) {
            accountEmail?.let { lines.add(it) }
            val formatted = lastSync?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(it))
            }
            lines.add(
                if (formatted != null) {
                    context.getString(R.string.last_synced, formatted)
                } else {
                    context.getString(R.string.never_synced)
                }
            )
            deviceCount?.takeIf { it > 0 }?.let { count ->
                lines.add(
                    context.resources.getQuantityString(R.plurals.connected_devices, count, count)
                )
            }
        } else {
            lines.add(context.getString(R.string.sync_not_signed_in))
        }
        syncError?.let { lines.add(it) }
        return lines.joinToString("\n")
    }
}
