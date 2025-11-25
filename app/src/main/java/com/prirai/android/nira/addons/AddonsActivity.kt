package com.prirai.android.nira.addons

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.prirai.android.nira.R
import com.prirai.android.nira.theme.applyAppTheme


// An activity to manage add-ons.

class AddonsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)

        val addonId = intent.getStringExtra("ADDON_ID")
        val addonUrl = intent.getStringExtra("ADDON_URL")

        applyAppTheme(this)

        supportActionBar?.elevation = 0f

        if (savedInstanceState == null) {
            val fm: FragmentManager = supportFragmentManager
            val arguments = Bundle()
            if(addonId != null) arguments.putString("ADDON_ID", addonId)
            if(addonUrl != null) arguments.putString("ADDON_URL", addonUrl)

            val addonFragment = AddonsFragment()
            addonFragment.arguments = arguments

            fm.beginTransaction().replace(R.id.container, addonFragment).commit()
        }
    }
}
