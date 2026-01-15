package com.prirai.android.nira.settings.activity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.applyPersistentInsets
import com.prirai.android.nira.ext.isAppInDarkTheme
import com.prirai.android.nira.settings.fragment.SettingsFragment
import com.prirai.android.nira.theme.applyCompleteTheme

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        applyCompleteTheme(this)
        
        // Enable edge-to-edge with standardized approach
        enableEdgeToEdgeMode()
        
        // Apply background color for AMOLED mode
        val bgColor = com.prirai.android.nira.theme.ThemeManager.getBackgroundColor(this)
        window.decorView.setBackgroundColor(bgColor)
        findViewById<android.view.View>(R.id.container)?.setBackgroundColor(bgColor)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, SettingsFragment())
                .commit()
        }

        // add back arrow to toolbar
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowHomeEnabled(true)
        }

        // Apply insets to container
        findViewById<View>(R.id.container)?.applyPersistentInsets()
        
        supportActionBar?.elevation = 0f

        title = getString(R.string.settings)
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                title = getString(R.string.settings)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment ?: return false
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }

        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
        
        // Update the title to the preference title
        title = pref.title
        
        return true
    }
}