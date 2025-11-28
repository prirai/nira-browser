package com.prirai.android.nira.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var skipButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var finishButton: MaterialButton
    
    private lateinit var adapter: OnboardingPagerAdapter
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        
        userPreferences = UserPreferences(this)
        
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        skipButton = findViewById(R.id.skipButton)
        nextButton = findViewById(R.id.nextButton)
        finishButton = findViewById(R.id.finishButton)
        
        adapter = OnboardingPagerAdapter(this)
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtons(position)
            }
        })
        
        skipButton.setOnClickListener {
            completeOnboarding()
        }
        
        nextButton.setOnClickListener {
            if (viewPager.currentItem < adapter.itemCount - 1) {
                viewPager.currentItem += 1
            }
        }
        
        finishButton.setOnClickListener {
            completeOnboarding()
        }
        
        updateButtons(0)
    }
    
    private fun updateButtons(position: Int) {
        val isLastPage = position == adapter.itemCount - 1
        
        skipButton.visibility = if (isLastPage) MaterialButton.GONE else MaterialButton.VISIBLE
        nextButton.visibility = if (isLastPage) MaterialButton.GONE else MaterialButton.VISIBLE
        finishButton.visibility = if (isLastPage) MaterialButton.VISIBLE else MaterialButton.GONE
    }
    
    private fun completeOnboarding() {
        userPreferences.firstLaunch = false
        val intent = Intent(this, BrowserActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
