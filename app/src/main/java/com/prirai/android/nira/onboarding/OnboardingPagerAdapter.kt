package com.prirai.android.nira.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    
    override fun getItemCount(): Int = 5
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingWelcomeFragment()
            1 -> OnboardingThemeFragment()
            2 -> OnboardingSearchFragment()
            3 -> OnboardingPrivacyFragment()
            4 -> OnboardingCustomizationFragment()
            else -> OnboardingWelcomeFragment()
        }
    }
}
