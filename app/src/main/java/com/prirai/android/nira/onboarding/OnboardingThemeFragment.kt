package com.prirai.android.nira.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.ThemeChoice

class OnboardingThemeFragment : Fragment() {
    
    private lateinit var userPreferences: UserPreferences
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_theme, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userPreferences = UserPreferences(requireContext())
        
        // App theme cards
        val lightCard: MaterialCardView = view.findViewById(R.id.lightThemeCard)
        val darkCard: MaterialCardView = view.findViewById(R.id.darkThemeCard)
        val systemCard: MaterialCardView = view.findViewById(R.id.systemThemeCard)
        
        // Web theme section and cards
        view.findViewById(R.id.webThemeSection)
        val webLightCard: MaterialCardView = view.findViewById(R.id.webLightCard)
        val webDarkCard: MaterialCardView = view.findViewById(R.id.webDarkCard)
        val webSystemCard: MaterialCardView = view.findViewById(R.id.webSystemCard)
        
        updateSelection()
        
        lightCard.setOnClickListener {
            userPreferences.appThemeChoice = ThemeChoice.LIGHT.ordinal
            userPreferences.webThemeChoice = ThemeChoice.LIGHT.ordinal // Force web to light
            updateSelection()
            applyThemeInstantly()
        }
        
        darkCard.setOnClickListener {
            userPreferences.appThemeChoice = ThemeChoice.DARK.ordinal
            // Don't force web theme, allow user to choose
            updateSelection()
            applyThemeInstantly()
        }
        
        systemCard.setOnClickListener {
            userPreferences.appThemeChoice = ThemeChoice.SYSTEM.ordinal
            userPreferences.webThemeChoice = ThemeChoice.SYSTEM.ordinal // Force web to system
            updateSelection()
            applyThemeInstantly()
        }
        
        // Web theme click listeners
        webLightCard.setOnClickListener {
            if (userPreferences.appThemeChoice == ThemeChoice.DARK.ordinal) {
                userPreferences.webThemeChoice = ThemeChoice.LIGHT.ordinal
                updateSelection()
            }
        }
        
        webDarkCard.setOnClickListener {
            if (userPreferences.appThemeChoice == ThemeChoice.DARK.ordinal) {
                userPreferences.webThemeChoice = ThemeChoice.DARK.ordinal
                updateSelection()
            }
        }
        
        webSystemCard.setOnClickListener {
            if (userPreferences.appThemeChoice == ThemeChoice.DARK.ordinal) {
                userPreferences.webThemeChoice = ThemeChoice.SYSTEM.ordinal
                updateSelection()
            }
        }
    }
    
    private fun applyThemeInstantly() {
        activity?.recreate()
    }
    
    private fun updateSelection() {
        val lightCard: MaterialCardView = requireView().findViewById(R.id.lightThemeCard)
        val darkCard: MaterialCardView = requireView().findViewById(R.id.darkThemeCard)
        val systemCard: MaterialCardView = requireView().findViewById(R.id.systemThemeCard)
        
        val webThemeSection: LinearLayout = requireView().findViewById(R.id.webThemeSection)
        val webLightCard: MaterialCardView = requireView().findViewById(R.id.webLightCard)
        val webDarkCard: MaterialCardView = requireView().findViewById(R.id.webDarkCard)
        val webSystemCard: MaterialCardView = requireView().findViewById(R.id.webSystemCard)
        
        lightCard.isChecked = userPreferences.appThemeChoice == ThemeChoice.LIGHT.ordinal
        darkCard.isChecked = userPreferences.appThemeChoice == ThemeChoice.DARK.ordinal
        systemCard.isChecked = userPreferences.appThemeChoice == ThemeChoice.SYSTEM.ordinal
        
        // Show web theme options only when app theme is dark
        if (userPreferences.appThemeChoice == ThemeChoice.DARK.ordinal) {
            webThemeSection.visibility = View.VISIBLE
            webLightCard.isEnabled = true
            webDarkCard.isEnabled = true
            webSystemCard.isEnabled = true
            
            webLightCard.isChecked = userPreferences.webThemeChoice == ThemeChoice.LIGHT.ordinal
            webDarkCard.isChecked = userPreferences.webThemeChoice == ThemeChoice.DARK.ordinal
            webSystemCard.isChecked = userPreferences.webThemeChoice == ThemeChoice.SYSTEM.ordinal
        } else {
            webThemeSection.visibility = View.GONE
        }
    }
}
