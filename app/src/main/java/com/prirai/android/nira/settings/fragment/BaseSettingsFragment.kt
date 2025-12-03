package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat

abstract class BaseSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the correct SharedPreferences file name to match UserPreferences
        preferenceManager.sharedPreferencesName = "scw_preferences"
    }
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Override in subclasses
    }

    protected fun switchPreference(
        preference: String,
        isChecked: Boolean,
        isEnabled: Boolean = true,
        summary: String? = null,
        onCheckChange: (Boolean) -> Unit
    ) = findPreference<com.prirai.android.nira.settings.MaterialSwitchPreference>(preference)?.apply {
        // Force the preference to match the actual stored value from UserPreferences
        // This handles cases where the preference might not have been saved yet
        // or where there's computed/inverted logic
        val prefs = preferenceManager.sharedPreferences
        if (prefs != null) {
            val currentValue = prefs.getBoolean(preference, !isChecked) // Use opposite as default to detect mismatch
            if (currentValue != isChecked) {
                // Value doesn't match, update SharedPreferences
                prefs.edit().putBoolean(preference, isChecked).apply()
            }
        }
        
        // The preference will now read the correct value from SharedPreferences
        // Force a refresh by re-reading from persistent storage
        this.isChecked = isChecked
        
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        onPreferenceChangeListener = OnPreferenceChangeListener { _, any: Any ->
            onCheckChange(any as Boolean)
            true
        }
    }

    protected fun clickablePreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: () -> Unit
    ) = clickableDynamicPreference(
        preference = preference,
        isEnabled = isEnabled,
        summary = summary,
        onClick = { onClick() }
    )

    protected fun clickableDynamicPreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: (SummaryUpdater) -> Unit
    ) = findPreference<Preference>(preference)?.apply {
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        val summaryUpdate = SummaryUpdater(this)
        onPreferenceClickListener = OnPreferenceClickListener {
            onClick(summaryUpdate)
            true
        }
    }

    protected fun seekbarPreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onStateChanged: (Int) -> Unit
    ) = findPreference<androidx.preference.SeekBarPreference>(preference)?.apply {
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        SummaryUpdater(this)
        onPreferenceChangeListener = OnPreferenceChangeListener { preference: Preference, newValue: Any ->
            onStateChanged(newValue as Int)
            true
        }
    }

}