package com.prirai.android.nira.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.ThemeChoice

class OnboardingWebThemeFragment : Fragment() {
    
    private lateinit var userPreferences: UserPreferences
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_web_theme, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userPreferences = UserPreferences(requireContext())
        
        val lightCard: MaterialCardView = view.findViewById(R.id.webLightThemeCard)
        val darkCard: MaterialCardView = view.findViewById(R.id.webDarkThemeCard)
        val systemCard: MaterialCardView = view.findViewById(R.id.webSystemThemeCard)
        
        updateSelection()
        
        lightCard.setOnClickListener {
            userPreferences.webThemeChoice = ThemeChoice.LIGHT.ordinal
            updateSelection()
        }
        
        darkCard.setOnClickListener {
            userPreferences.webThemeChoice = ThemeChoice.DARK.ordinal
            updateSelection()
        }
        
        systemCard.setOnClickListener {
            userPreferences.webThemeChoice = ThemeChoice.SYSTEM.ordinal
            updateSelection()
        }
    }
    
    private fun updateSelection() {
        val lightCard: MaterialCardView = requireView().findViewById(R.id.webLightThemeCard)
        val darkCard: MaterialCardView = requireView().findViewById(R.id.webDarkThemeCard)
        val systemCard: MaterialCardView = requireView().findViewById(R.id.webSystemThemeCard)
        
        lightCard.isChecked = userPreferences.webThemeChoice == ThemeChoice.LIGHT.ordinal
        darkCard.isChecked = userPreferences.webThemeChoice == ThemeChoice.DARK.ordinal
        systemCard.isChecked = userPreferences.webThemeChoice == ThemeChoice.SYSTEM.ordinal
    }
}
