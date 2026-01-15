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
                
                // Navigate to the external app browser fragment
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.container) as? NavHostFragment
                navHostFragment?.let { host ->
                    val bundle = Bundle().apply {
                        putString("activeSessionId", sessionId)
                        putString(EXTRA_SESSION_ID, sessionId)
                        // Pass intent extras to fragment
                        intent.extras?.let { extras ->
                            putAll(extras)
                        }
                    }
                    host.navController.navigate(R.id.externalAppBrowserFragment, bundle)
                }
            }
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
