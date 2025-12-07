package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import com.prirai.android.nira.R

class SettingsFragment : BaseSettingsFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_headers)
    }
}