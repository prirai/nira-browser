package com.prirai.android.nira.settings.fragment

import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.prirai.android.nira.R
import com.prirai.android.nira.addons.AddonsActivity

class SettingsFragment : BaseSettingsFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_headers)

        tintCategoryIcons()

        // Wire Add-on settings to open AddonsActivity
        findPreference<androidx.preference.Preference>("addons_settings")?.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), AddonsActivity::class.java)
            startActivity(intent)
            true
        }
    }

    /**
     * Each settings category icon carries a muted, theme-adaptive tint
     * (light/night color resource pairs) instead of the neutral control
     * color - the same muted-accent language the profile icons use. Hue is
     * the category's identity; the resource pair keeps it legible in both
     * themes.
     */
    private fun tintCategoryIcons() {
        val tints = mapOf(
            "com.prirai.android.nira.settings.fragment.GeneralSettingsFragment" to R.color.settings_icon_general,
            "com.prirai.android.nira.settings.fragment.SyncSettingsFragment" to R.color.settings_icon_sync,
            "com.prirai.android.nira.settings.fragment.PrivacyAndSecuritySettingsFragment" to R.color.settings_icon_privacy,
            "com.prirai.android.nira.settings.fragment.CustomizationSettingsFragment" to R.color.settings_icon_customization,
            "com.prirai.android.nira.settings.fragment.UnifiedWebAppFragment" to R.color.settings_icon_webapps,
            "addons_settings" to R.color.settings_icon_addons,
            "com.prirai.android.nira.settings.fragment.ImportExportSettingsFragment" to R.color.settings_icon_import_export,
            "com.prirai.android.nira.settings.fragment.AdvancedSettingsFragment" to R.color.settings_icon_advanced,
            "com.prirai.android.nira.settings.fragment.AboutSettingsFragment" to R.color.settings_icon_about,
        )
        // The headers sit inside a PreferenceCategory, so walk groups.
        fun tintGroup(group: androidx.preference.PreferenceGroup) {
            for (index in 0 until group.preferenceCount) {
                val preference = group.getPreference(index)
                when (preference) {
                    is androidx.preference.PreferenceGroup -> tintGroup(preference)
                    is androidx.preference.Preference -> {
                        val tintRes = tints[preference.fragment ?: preference.key] ?: continue
                        preference.icon = preference.icon?.mutate()?.apply {
                            setTint(ContextCompat.getColor(requireContext(), tintRes))
                        }
                    }
                }
            }
        }
        tintGroup(preferenceScreen)
    }
}