package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceCategory
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.update.UpdateCheckResult
import com.prirai.android.nira.update.UpdateChecker
import com.prirai.android.nira.update.UpdateScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import org.mozilla.geckoview.BuildConfig as GeckoBuildConfig
import com.prirai.android.nira.BuildConfig as NiraBuildConfig


class AboutSettingsFragment : BaseSettingsFragment() {


    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_about)

        val packageInfo =
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)

        clickablePreference(
            preference = "pref_version",
            summary = "${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})",
            onClick = { }
        )

        clickablePreference(
            preference = "pref_version_geckoview",
            summary = GeckoBuildConfig.MOZ_APP_VERSION + "-" + GeckoBuildConfig.MOZ_APP_BUILDID,
            onClick = { }
        )

        clickablePreference(
            preference = "pref_version_mozac",
            summary = mozilla.components.Build.VERSION + ", " + mozilla.components.Build.GIT_HASH,
            onClick = { }
        )

        setupUpdatesCategory()
    }

    /**
     * Hide the Updates category entirely on debug builds (the whole
     * subsystem no-ops there), and otherwise wire the switch,
     * "Check now" action, and "Last checked" summary.
     */
    private fun setupUpdatesCategory() {
        val category = findPreference<PreferenceCategory>("pref_cat_updates") ?: return
        if (NiraBuildConfig.DEBUG) {
            category.isVisible = false
            return
        }

        val prefs = UserPreferences(requireContext())
        val checkNowKey = "pref_update_check_now"

        switchPreference(
            preference = "pref_update_checks_enabled",
            isChecked = prefs.updateChecksEnabled,
        ) { enabled ->
            prefs.updateChecksEnabled = enabled
            if (enabled) {
                UpdateScheduler.ensureScheduled(requireContext())
            } else {
                UpdateScheduler.cancel(requireContext())
            }
            // Re-enable / disable the manual-check row to match.
            findPreference<androidx.preference.Preference>(checkNowKey)?.isEnabled = enabled
        }

        findPreference<androidx.preference.Preference>(checkNowKey)?.apply {
            isEnabled = prefs.updateChecksEnabled
            setOnPreferenceClickListener {
                runManualCheck()
                true
            }
        }

        refreshLastCheckSummary()
    }

    private fun runManualCheck() {
        val context = requireContext()
        val prefs = UserPreferences(context)
        Toast.makeText(context, R.string.settings_update_check_in_progress, Toast.LENGTH_SHORT)
            .show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.check(context) }
            prefs.updateLastCheckTimestamp = System.currentTimeMillis()

            val toastRes = when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    // Clear any prior skip so the user's manual
                    // "Check now" always surfaces the current update
                    // in the usual bottom sheet flow.
                    prefs.updateSkippedVersionCode = 0
                    prefs.updatePendingManifestJson = result.manifest.toJson()
                    prefs.updatePendingNotify = true
                    R.string.settings_update_check_available
                }
                UpdateCheckResult.UpToDate -> {
                    prefs.updatePendingManifestJson = ""
                    prefs.updatePendingNotify = false
                    R.string.settings_update_check_up_to_date
                }
                is UpdateCheckResult.Error ->
                    R.string.settings_update_check_error
                is UpdateCheckResult.UnsupportedAbi ->
                    R.string.settings_update_check_unsupported
            }
            if (isAdded) {
                Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
                refreshLastCheckSummary()
            }
        }
    }

    private fun refreshLastCheckSummary() {
        val row = findPreference<androidx.preference.Preference>("pref_update_last_check") ?: return
        val ts = UserPreferences(requireContext()).updateLastCheckTimestamp
        row.summary = if (ts <= 0L) {
            getString(R.string.settings_update_last_check_never)
        } else {
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            fmt.format(Date(ts))
        }
    }
}
