package com.prirai.android.nira.webapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            finish()
            return
        }

        // Load profile ID and setup the fragment
        lifecycleScope.launch {
            val profileId = getProfileIdForUrl(url)

            // Load web app details to set task description with icon
            val webApp = com.prirai.android.nira.components.Components(this@WebAppActivity)
                .webAppManager.getWebAppByUrl(url)

            // Set task description with app name and icon for recents
            webApp?.let { app ->
                withContext(Dispatchers.IO) {
                    // Comprehensive icon loading with multiple fallbacks
                    val icon =
                        com.prirai.android.nira.components.Components(this@WebAppActivity)
                            .webAppManager.loadIconFromFile(app.iconUrl)
                            ?: com.prirai.android.nira.utils.FaviconLoader.loadFavicon(this@WebAppActivity, url)
                            ?: com.prirai.android.nira.utils.FaviconLoader.loadFaviconForPwa(
                                this@WebAppActivity,
                                url,
                                size = 128
                            )

                    if (icon != null) {
                        withContext(Dispatchers.Main) {
                            val taskDescription = android.app.ActivityManager.TaskDescription(app.name, icon)
                            setTaskDescription(taskDescription)
                        }
                    }
                }
            }

            // Load the web app fragment if not already added
            if (savedInstanceState == null) {
                val fragment = WebAppFragment.newInstance(url, profileId)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.webapp_container, fragment)
                    .commit()
            }
        }

        // Handle back button
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager.findFragmentById(R.id.webapp_container) as? WebAppFragment
                if (fragment?.handleBackPressed() != true) {
                    finishAndRemoveTask()
                }
            }
        })
    }

    private suspend fun getProfileIdForUrl(url: String): String = withContext(Dispatchers.IO) {
        // Lookup webapp by URL to get its profile
        val webApp = com.prirai.android.nira.components.Components(this@WebAppActivity)
            .webAppManager.getWebAppByUrl(url)
        webApp?.profileId ?: "default"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Handle new URL when activity is reused
        val url = extractUrlFromIntent(intent)
        if (!url.isNullOrEmpty()) {
            // Get the correct profile for this URL asynchronously
            lifecycleScope.launch {
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
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null


        intent.getStringExtra(EXTRA_WEB_APP_URL)?.let { return it }

        intent.getWebAppManifest()?.startUrl?.let { return it }

        intent.data?.toString()?.let { return it }

        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { return it }
        }

        return null
    }

    private fun setupSystemBars() {
        // Show system bars normally
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // Set status bar color based on theme
        val bgColor = com.prirai.android.nira.theme.ThemeManager.getBackgroundColor(this)
        window.statusBarColor = bgColor

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
