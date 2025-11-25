package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.prirai.android.nira.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_headers)
    }
}