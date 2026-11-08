@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.ui.components.NiraAlertDialog
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.Engine
import java.util.concurrent.TimeUnit


class PrivacyAndSecuritySettingsFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_privacy_and_security)

        clickablePreference(
            preference = resources.getString(R.string.key_clear_tabs),
            onClick = { clearTabs() }
        )
        clickablePreference(
            preference = resources.getString(R.string.key_clear_history),
            onClick = { clearHistory() }
        )
        clickablePreference(
            preference = resources.getString(R.string.key_clear_cookies),
            onClick = { clearCookies() }
        )
        clickablePreference(
            preference = resources.getString(R.string.key_clear_cache),
            onClick = { clearCache() }
        )
        clickablePreference(
            preference = resources.getString(R.string.key_clear_permissions),
            onClick = { clearPermissions() }
        )

        setupETP()
        setupConnectionPrivacy()
        setupSleepingTabs()
        setupProtectionToggles()
    }

    /**
     * The protection master switches that used to live on the General and
     * Advanced pages - tracking protection, safe browsing and fingerprint
     * resistance all belong here. Each needs an app restart to take
     * effect, same as their previous homes.
     */
    private fun setupProtectionToggles() {
        val prefs = UserPreferences(requireContext())

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_tracking_protection),
            isChecked = prefs.trackingProtection,
        ) {
            prefs.trackingProtection = it
            Toast.makeText(context, requireContext().resources.getText(R.string.app_restart), Toast.LENGTH_LONG).show()
        }

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_safe_browsing),
            isChecked = prefs.safeBrowsing,
        ) {
            prefs.safeBrowsing = it
            Toast.makeText(context, requireContext().resources.getText(R.string.app_restart), Toast.LENGTH_LONG).show()
        }

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_resist_fingerprinting),
            isChecked = prefs.resistFingerprinting,
        ) {
            prefs.resistFingerprinting = it
        }
    }

    private fun setupSleepingTabs() {
        val prefs = UserPreferences(requireContext())
        clickablePreference(
            preference = resources.getString(R.string.key_sleeping_tabs),
            summary = sleepingTabsSummary(prefs.sleepingTabsMode),
            onClick = { showSleepingTabsPicker(prefs) }
        )
    }

    private fun showSleepingTabsPicker(prefs: UserPreferences) {
        showNiraDialog { onDismiss ->
            ChoiceListDialog(
                title = getString(R.string.sleeping_tabs_title),
                items = resources.getStringArray(R.array.sleeping_tabs_mode_names),
                initialSelection = prefs.sleepingTabsMode,
                onChoice = { which ->
                    prefs.sleepingTabsMode = which
                    requireContext().components.sleepingTabsManager
                        .onPreferencesChanged(requireContext().components.store)
                    findPreference<Preference>(resources.getString(R.string.key_sleeping_tabs))
                        ?.summary = sleepingTabsSummary(which)
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun sleepingTabsSummary(mode: Int): String = when (mode) {
        UserPreferences.SLEEPING_TABS_OFF -> getString(R.string.sleeping_tabs_off_summary)
        UserPreferences.SLEEPING_TABS_AGGRESSIVE -> getString(R.string.sleeping_tabs_aggressive_summary)
        else -> getString(R.string.sleeping_tabs_balanced_summary)
    }

    private fun setupConnectionPrivacy() {
        val prefs = UserPreferences(requireContext())

        clickablePreference(
            preference = resources.getString(R.string.key_https_only),
            summary = httpsOnlySummary(prefs.httpsOnlyMode),
            onClick = { showHttpsOnlyPicker(prefs) }
        )

        clickablePreference(
            preference = resources.getString(R.string.key_doh),
            summary = dohModeSummary(prefs.dohMode),
            onClick = { showDohModePicker(prefs) }
        )

        clickablePreference(
            preference = resources.getString(R.string.key_doh_provider),
            isEnabled = prefs.isDohProviderSelectable(),
            summary = dohProviderSummary(prefs.dohProviderUrl),
            onClick = { showDohProviderPicker(prefs) }
        )

        switchPreference(
            preference = resources.getString(R.string.key_global_privacy_control),
            isChecked = prefs.globalPrivacyControl,
        ) {
            prefs.globalPrivacyControl = it
            requireContext().components.applyPrivacyEngineSettings()
        }
    }

    private fun showHttpsOnlyPicker(prefs: UserPreferences) {
        showNiraDialog { onDismiss ->
            ChoiceListDialog(
                title = getString(R.string.https_only_title),
                items = resources.getStringArray(R.array.https_only_mode_names),
                initialSelection = prefs.httpsOnlyMode,
                onChoice = { which ->
                    prefs.httpsOnlyMode = which
                    requireContext().components.applyPrivacyEngineSettings()
                    findPreference<Preference>(resources.getString(R.string.key_https_only))
                        ?.summary = httpsOnlySummary(which)
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun showDohModePicker(prefs: UserPreferences) {
        showNiraDialog { onDismiss ->
            ChoiceListDialog(
                title = getString(R.string.doh_title),
                items = resources.getStringArray(R.array.doh_mode_names),
                initialSelection = prefs.dohMode,
                onChoice = { which ->
                    prefs.dohMode = which
                    requireContext().components.applyPrivacyEngineSettings()
                    findPreference<Preference>(resources.getString(R.string.key_doh))
                        ?.summary = dohModeSummary(which)
                    findPreference<Preference>(resources.getString(R.string.key_doh_provider))
                        ?.isEnabled = prefs.isDohProviderSelectable()
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun showDohProviderPicker(prefs: UserPreferences) {
        val labels = arrayOf(
            getString(R.string.doh_provider_cloudflare),
            getString(R.string.doh_provider_nextdns),
            getString(R.string.doh_provider_custom),
        )
        val urls = arrayOf(
            UserPreferences.CLOUDFLARE_DOH_URI,
            UserPreferences.NEXTDNS_DOH_URI,
        )
        val checked = when (prefs.dohProviderUrl) {
            UserPreferences.CLOUDFLARE_DOH_URI -> 0
            UserPreferences.NEXTDNS_DOH_URI -> 1
            else -> 2
        }

        showNiraDialog { onDismiss ->
            ChoiceListDialog(
                title = getString(R.string.doh_provider_title),
                items = labels,
                initialSelection = checked,
                onChoice = { which ->
                    if (which == 2) {
                        showCustomDohProviderDialog(prefs)
                    } else {
                        prefs.dohProviderUrl = urls[which]
                        requireContext().components.applyPrivacyEngineSettings()
                        findPreference<Preference>(resources.getString(R.string.key_doh_provider))
                            ?.summary = dohProviderSummary(prefs.dohProviderUrl)
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun showCustomDohProviderDialog(prefs: UserPreferences) {
        showNiraDialog { onDismiss ->
            CustomDohProviderDialog(
                initialUrl = prefs.dohProviderUrl.takeUnless {
                    it == UserPreferences.CLOUDFLARE_DOH_URI || it == UserPreferences.NEXTDNS_DOH_URI
                }.orEmpty(),
                onConfirm = { url ->
                    if (url.startsWith("https://")) {
                        prefs.dohProviderUrl = url
                        requireContext().components.applyPrivacyEngineSettings()
                        findPreference<Preference>(resources.getString(R.string.key_doh_provider))
                            ?.summary = dohProviderSummary(url)
                    } else {
                        Toast.makeText(context, R.string.doh_provider_custom_error, Toast.LENGTH_LONG).show()
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun httpsOnlySummary(mode: Int): String = when (mode) {
        UserPreferences.HTTPS_ONLY_PRIVATE -> getString(R.string.https_only_private)
        UserPreferences.HTTPS_ONLY_ALL -> getString(R.string.https_only_all)
        else -> getString(R.string.https_only_off)
    }

    private fun dohModeSummary(mode: Int): String = when (mode) {
        UserPreferences.DOH_INCREASED -> getString(R.string.doh_increased_summary)
        UserPreferences.DOH_MAX -> getString(R.string.doh_max_summary)
        UserPreferences.DOH_OFF -> getString(R.string.doh_off_summary)
        else -> getString(R.string.doh_default_summary)
    }

    private fun dohProviderSummary(url: String): String = when (url) {
        UserPreferences.CLOUDFLARE_DOH_URI -> getString(R.string.doh_provider_cloudflare)
        UserPreferences.NEXTDNS_DOH_URI -> getString(R.string.doh_provider_nextdns)
        else -> url.ifBlank { getString(R.string.doh_provider_custom) }
    }

    private fun setupETP() {
        val prefs = UserPreferences(requireContext())

        val etpLevelPref = findPreference<Preference>("etp_level")
        val customCategories = findPreference<PreferenceCategory>("etp_custom_categories")

        val initialLevel = prefs.etpLevel
        customCategories?.isVisible = initialLevel == 3

        etpLevelPref?.setOnPreferenceClickListener {
            showEtpLevelPicker(prefs, customCategories)
            true
        }

        // Wire up custom category switches to UserPreferences
        val switchDefs = listOf(
            "etp_tracking_ads" to { v: Boolean -> prefs.etpTrackingAds = v },
            "etp_tracking_content" to { v: Boolean -> prefs.etpTrackingContent = v },
            "etp_cryptominers" to { v: Boolean -> prefs.etpCryptominers = v },
            "etp_fingerprinters" to { v: Boolean -> prefs.etpFingerprinters = v },
            "etp_social_tracking" to { v: Boolean -> prefs.etpSocialTracking = v },
            "etp_email_tracking" to { v: Boolean -> prefs.etpEmailTracking = v },
        )

        for ((key, setter) in switchDefs) {
            findPreference<com.prirai.android.nira.settings.MaterialSwitchPreference>(key)?.let { pref ->
                val sp = preferenceManager.sharedPreferences
                if (sp != null && !sp.contains(key)) {
                    sp.edit()?.putBoolean(key, pref.isChecked)?.apply()
                }
                pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    setter(newValue as Boolean)
                    true
                }
            }
        }
    }

    private fun showEtpLevelPicker(prefs: UserPreferences, customCategories: PreferenceCategory?) {
        // The same radio-list picker every other mode option uses
        // (HTTPS-Only, DoH, Sleeping tabs) - no bespoke card layout.
        val labels = arrayOf(
            getString(R.string.etp_none_title),
            getString(R.string.etp_standard_title),
            getString(R.string.etp_strict_title),
            getString(R.string.etp_custom_title),
        )
        showNiraDialog { onDismiss ->
            ChoiceListDialog(
                title = getString(R.string.etp_level_title),
                items = labels,
                initialSelection = prefs.etpLevel,
                onChoice = { level ->
                    prefs.etpLevel = level
                    customCategories?.isVisible = level == 3

                    if (level == 2) {
                        showEtpStrictWarning()
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun showEtpStrictWarning() {
        showNiraDialog { onDismiss ->
            NiraAlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = stringResource(R.string.etp_strict_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                text = { Text(stringResource(R.string.etp_strict_warning)) },
                confirmButton = {
                    TextButton(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            )
        }
    }

    private fun clearTabs() {
        showNiraDialog { onDismiss ->
            NiraAlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = stringResource(R.string.clear_tabs),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDismiss()
                            lifecycleScope.launch {
                                requireContext().components.tabsUseCases.removeAllTabs.invoke()
                            }
                            Toast.makeText(context, R.string.tabs_cleared, Toast.LENGTH_LONG).show()
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.mozac_feature_prompts_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    private fun clearHistory() {
        val timeArray: Array<Long> = arrayOf(
            System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365),
            0
        )

        showNiraDialog { onDismiss ->
            ClearHistoryDialog(
                labels = resources.getStringArray(R.array.history_delete_times),
                times = timeArray,
                onClear = { since ->
                    lifecycleScope.launch {
                        requireContext().components.historyStorage.deleteVisitsSince(since)
                    }
                    Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_LONG).show()
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun clearCookies(){
         requireContext().components.engine.clearData(
                Engine.BrowsingData.select(
                    Engine.BrowsingData.COOKIES,
                    Engine.BrowsingData.AUTH_SESSIONS
                )
            )
        Toast.makeText(context, R.string.cookies_cleared, Toast.LENGTH_LONG).show()
    }

    private fun clearCache(){
        requireContext().components.engine.clearData(
            Engine.BrowsingData.select(Engine.BrowsingData.ALL_CACHES)
        )
        Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_LONG).show()
    }

    private fun clearPermissions(){
        requireContext().components.engine.clearData(
            Engine.BrowsingData.select(Engine.BrowsingData.PERMISSIONS)
        )
        lifecycleScope.launch { components.permissionStorage.removeAll() }
        Toast.makeText(context, R.string.permissions_cleared, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * The picker behind Sleeping tabs, HTTPS-Only and DoH mode: a radio list where
 * tapping a row applies the choice and closes the dialog, with a Cancel
 * button to leave without changing anything.
 */
@Composable
private fun ChoiceListDialog(
    title: String,
    items: Array<String>,
    initialSelection: Int,
    onChoice: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(initialSelection) }
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, label ->
                    ChoiceRow(
                        label = label,
                        selected = index == selected,
                        onClick = {
                            selected = index
                            onDismiss()
                            onChoice(index)
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun CustomDohProviderDialog(
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.doh_provider_custom),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.doh_provider_custom_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm(url.trim())
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.mozac_feature_prompts_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ClearHistoryDialog(
    labels: Array<String>,
    times: Array<Long>,
    onClear: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.clear_history),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.select_time_range),
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                labels.forEachIndexed { index, label ->
                    ChoiceRow(
                        label = label,
                        selected = index == selected,
                        onClick = { selected = index },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onClear(times[selected])
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_history),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
