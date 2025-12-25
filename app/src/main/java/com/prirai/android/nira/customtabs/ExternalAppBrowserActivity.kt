package com.prirai.android.nira.customtabs

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.browser.customtabs.CustomTabsIntent
import androidx.navigation.fragment.NavHostFragment
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.feature.customtabs.CustomTabIntentProcessor
import mozilla.components.feature.intent.ext.EXTRA_SESSION_ID
import mozilla.components.support.utils.SafeIntent

/**
 * Activity that holds the [ExternalAppBrowserFragment] that is launched within an external app,
 * such as custom tabs. Uses Mozilla's CustomTabIntentProcessor for standardized intent handling.
 */
open class ExternalAppBrowserActivity : BrowserActivity() {

    override fun navigateToBrowserOnColdStart() {
        // Do nothing - custom tabs handle their own navigation
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasCalledOnCreate = true
        
        if (savedInstanceState == null) {
            // Use Mozilla's CustomTabIntentProcessor to handle the intent
            val processor = CustomTabIntentProcessor(
                addCustomTabUseCase = components.customTabsUseCases.add,
                resources = resources,
                isPrivate = false
            )
            
            // Process the intent to create the custom tab session
            if (processor.process(intent)) {
                val sessionId = SafeIntent(intent).getStringExtra(EXTRA_SESSION_ID)
                
                // Apply custom tab theming based on intent colors
                applyCustomTabTheming()
                
                // Navigate to the external app browser fragment
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.container) as? NavHostFragment
                navHostFragment?.let { host ->
                    val bundle = Bundle().apply {
                        putString("activeSessionId", sessionId)
                        putString(EXTRA_SESSION_ID, sessionId)
                        // Pass intent extras (including toolbar colors) to fragment
                        intent.extras?.let { extras ->
                            putAll(extras)
                        }
                    }
                    host.navController.navigate(R.id.externalAppBrowserFragment, bundle)
                }
            }
        }
    }
    
    /**
     * Apply custom tab theming to status bar and navigation bar based on intent extras.
     */
    private fun applyCustomTabTheming() {
        val safeIntent = SafeIntent(intent)
        
        // Get toolbar color from intent (standard Custom Tabs extra)
        val toolbarColor = safeIntent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, -1)
        
        // Get navigation bar color from intent
        val navBarColor = safeIntent.getIntExtra(CustomTabsIntent.EXTRA_NAVIGATION_BAR_COLOR, -1)
        
        // Apply status bar color (same as toolbar for consistency)
        if (toolbarColor != -1) {
            window.statusBarColor = toolbarColor
            
            // Determine if we need light or dark icons based on toolbar color
            val isLightToolbar = isColorLight(toolbarColor)
            updateSystemBarsAppearance(lightStatusBar = isLightToolbar, lightNavBar = false)
        }
        
        // Apply navigation bar color
        if (navBarColor != -1) {
            window.navigationBarColor = navBarColor
            
            // Update navigation bar appearance
            val isLightNavBar = isColorLight(navBarColor)
            updateSystemBarsAppearance(lightStatusBar = toolbarColor != -1 && isColorLight(toolbarColor), lightNavBar = isLightNavBar)
        }
    }
    
    /**
     * Determines if a color is light (requires dark text/icons) or dark (requires light text/icons).
     * Uses the WCAG formula for relative luminance.
     */
    private fun isColorLight(color: Int): Boolean {
        val red = android.graphics.Color.red(color) / 255.0
        val green = android.graphics.Color.green(color) / 255.0
        val blue = android.graphics.Color.blue(color) / 255.0
        
        // Calculate relative luminance
        val r = if (red <= 0.03928) red / 12.92 else Math.pow((red + 0.055) / 1.055, 2.4)
        val g = if (green <= 0.03928) green / 12.92 else Math.pow((green + 0.055) / 1.055, 2.4)
        val b = if (blue <= 0.03928) blue / 12.92 else Math.pow((blue + 0.055) / 1.055, 2.4)
        
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        
        // If luminance > 0.5, it's a light color
        return luminance > 0.5
    }
    
    /**
     * Updates the appearance of system bars (light or dark icons).
     */
    private fun updateSystemBarsAppearance(lightStatusBar: Boolean, lightNavBar: Boolean) {
        val insetsController = window.insetsController ?: return
        
        if (lightStatusBar) {
            insetsController.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            insetsController.setSystemBarsAppearance(
                0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        
        if (lightNavBar) {
            insetsController.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            insetsController.setSystemBarsAppearance(
                0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }
    }
    


    override fun onResume() {
        super.onResume()
        
        // For custom tabs, the session is created by the fragment when it processes the intent
        // So we don't check for hasExternalTab() here - let the fragment handle it
    }
    
    private var hasCalledOnCreate = false

    override fun onDestroy() {
        // Clean up custom tab session before calling super
        if (isFinishing) {
            // When this activity finishes, the process is staying around and the session still
            // exists then remove it now to free all its resources. Once this activity is finished
            // then there's no way to get back to it other than relaunching it.
            val tabId = getExternalTabId()
            val customTab = tabId?.let { components.store.state.findCustomTab(it) }
            if (tabId != null && customTab != null) {
                components.tabsUseCases.removeTab(tabId)
            }
            
            // Remove from recents when custom tab is closed
            finishAndRemoveTask()
        }
        
        super.onDestroy()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getExternalTabId(): String? {
        return getIntentSessionId(SafeIntent(intent))
    }
}
