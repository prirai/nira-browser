package com.prirai.android.nira.webapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.prirai.android.nira.R
import mozilla.components.feature.pwa.ext.getWebAppManifest

/**
 * Activity for Progressive Web Apps (PWAs) - Fullscreen, no browser chrome
 * Provides a native app experience for installed web apps
 */
class WebAppActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WEB_APP_URL = "extra_web_app_url"
        const val EXTRA_WEB_APP_MANIFEST = "extra_web_app_manifest"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_webapp)
        
        // Hide system UI for fullscreen experience
        hideSystemUI()
        
        // Extract URL from intent
        val url = extractUrlFromIntent(intent)
        
        if (url.isNullOrEmpty()) {
            // No valid URL, close activity
            finish()
            return
        }
        
        // Load the web app fragment if not already added
        if (savedInstanceState == null) {
            val fragment = WebAppFragment.newInstance(url)
            supportFragmentManager.beginTransaction()
                .replace(R.id.webapp_container, fragment)
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle new URL when activity is reused
        val url = extractUrlFromIntent(intent)
        if (!url.isNullOrEmpty()) {
            val fragment = supportFragmentManager.findFragmentById(R.id.webapp_container) as? WebAppFragment
            if (fragment != null) {
                // Update existing fragment with new URL
                val newFragment = WebAppFragment.newInstance(url)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.webapp_container, newFragment)
                    .commit()
            }
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        
        // Try to get URL from various sources
        
        // 1. Check for explicit URL extra (our custom launches)
        intent.getStringExtra(EXTRA_WEB_APP_URL)?.let { return it }
        
        // 2. Check for Mozilla PWA manifest
        intent.getWebAppManifest()?.startUrl?.let { return it }
        
        // 3. Check for data URI (shortcut clicks)
        intent.data?.toString()?.let { return it }
        
        // 4. Check for ACTION_VIEW with data
        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { return it }
        }
        
        return null
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        // Configure behavior
        windowInsetsController.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // Hide both status and navigation bars
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        // For older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-hide system UI when returning to the app
        hideSystemUI()
    }

    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.webapp_container) as? WebAppFragment
        if (fragment?.handleBackPressed() != true) {
            super.onBackPressed()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-hide system UI when window gains focus
            hideSystemUI()
        }
    }
}
