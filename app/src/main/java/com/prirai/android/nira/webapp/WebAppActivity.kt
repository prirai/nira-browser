package com.prirai.android.nira.webapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.prirai.android.nira.R
import mozilla.components.feature.pwa.ext.getWebAppManifest
import kotlinx.coroutines.runBlocking

/**
 * Activity for Progressive Web Apps (PWAs) - Fullscreen, no browser chrome
 * Provides a native app experience for installed web apps
 */
class WebAppActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WEB_APP_URL = "extra_web_app_url"
        const val EXTRA_WEB_APP_MANIFEST = "extra_web_app_manifest"
    }

    private var notificationPermissionCallback: ((Boolean) -> Unit)? = null
    
    private val requestNotificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            notificationPermissionCallback?.invoke(isGranted)
            notificationPermissionCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_webapp)
        
        // Configure system bars
        setupSystemBars()
        
        // Extract URL from intent
        val url = extractUrlFromIntent(intent)
        
        if (url.isNullOrEmpty()) {
            // No valid URL, close activity
            finish()
            return
        }
        
        // Get profile ID for this webapp (from database)
        val profileId = getProfileIdForUrl(url)
        
        // Load the web app fragment if not already added
        if (savedInstanceState == null) {
            val fragment = WebAppFragment.newInstance(url, profileId)
            supportFragmentManager.beginTransaction()
                .replace(R.id.webapp_container, fragment)
                .commit()
        }
        
        // Handle back button
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager.findFragmentById(R.id.webapp_container) as? WebAppFragment
                if (fragment?.handleBackPressed() != true) {
                    // If can't go back in history, finish and remove from recents
                    finishAndRemoveTask()
                }
            }
        })
    }
    
    private fun getProfileIdForUrl(url: String): String {
        // Lookup webapp by URL to get its profile
        var profileId = "default"
        runBlocking {
            val webApp = com.prirai.android.nira.components.Components(this@WebAppActivity).webAppManager.getWebAppByUrl(url)
            profileId = webApp?.profileId ?: "default"
        }
        return profileId
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle new URL when activity is reused
        val url = extractUrlFromIntent(intent)
        if (!url.isNullOrEmpty()) {
            // Get the correct profile for this URL
            val profileId = getProfileIdForUrl(url)
            
            val fragment = supportFragmentManager.findFragmentById(R.id.webapp_container) as? WebAppFragment
            if (fragment != null) {
                // Update existing fragment with new URL and profile
                val newFragment = WebAppFragment.newInstance(url, profileId)
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
    
    private fun setupSystemBars() {
        // Show system bars normally
        WindowCompat.setDecorFitsSystemWindows(window, true)
        
        // Set status bar color based on theme
        window.statusBarColor = if (resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            android.graphics.Color.parseColor("#1C1C1C") // Dark theme
        } else {
            android.graphics.Color.WHITE // Light theme
        }
        
        // Ensure system bars are visible and configure appearance
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.apply {
            show(WindowInsetsCompat.Type.systemBars())
            // Set status bar icons to dark in light theme, light in dark theme
            isAppearanceLightStatusBars = resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK != 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            // Set navigation bar icons
            isAppearanceLightNavigationBars = resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK != 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        
        // Make navigation bar match status bar
        window.navigationBarColor = window.statusBarColor
    }

    /**
     * Request notification permission for PWA notifications (Android 13+)
     * 
     * @param callback Called with true if permission granted, false if denied
     */
    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionCallback = callback
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-Android 13, notification permission is granted by default
            callback(true)
        }
    }

}
