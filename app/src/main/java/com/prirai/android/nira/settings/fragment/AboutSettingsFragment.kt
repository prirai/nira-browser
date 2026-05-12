package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import com.prirai.android.nira.BuildConfig
import com.prirai.android.nira.R
import org.mozilla.geckoview.BuildConfig as GeckoBuildConfig


class AboutSettingsFragment : BaseSettingsFragment() {


    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_about)

        val packageInfo =
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)

        val versionSummary = if (BuildConfig.DEBUG) {
            "${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)}) — ${BuildConfig.DEBUG_VERSION}"
        } else {
            "${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})"
        }

        clickablePreference(
            preference = "pref_version",
            summary = versionSummary,
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

    }

}