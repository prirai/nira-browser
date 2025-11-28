package com.prirai.android.nira.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences

class OnboardingPrivacyFragment : Fragment() {
    
    private lateinit var userPreferences: UserPreferences
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_privacy, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userPreferences = UserPreferences(requireContext())
        
        val trackingProtectionSwitch: SwitchMaterial = view.findViewById(R.id.trackingProtectionSwitch)
        val safeBrowsingSwitch: SwitchMaterial = view.findViewById(R.id.safeBrowsingSwitch)
        val searchSuggestionsSwitch: SwitchMaterial = view.findViewById(R.id.searchSuggestionsSwitch)
        
        trackingProtectionSwitch.isChecked = userPreferences.trackingProtection
        safeBrowsingSwitch.isChecked = userPreferences.safeBrowsing
        searchSuggestionsSwitch.isChecked = userPreferences.searchSuggestionsEnabled
        
        trackingProtectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            userPreferences.trackingProtection = isChecked
        }
        
        safeBrowsingSwitch.setOnCheckedChangeListener { _, isChecked ->
            userPreferences.safeBrowsing = isChecked
        }
        
        searchSuggestionsSwitch.setOnCheckedChangeListener { _, isChecked ->
            userPreferences.searchSuggestionsEnabled = isChecked
        }
    }
}
