package com.prirai.android.nira

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.prirai.android.nira.customtabs.ExternalAppBrowserActivity
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.components.feature.intent.processing.TabIntentProcessor
import mozilla.components.support.utils.SafeIntent

class IntentReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MainScope().launch {
            val intent = intent?.let { Intent(it) } ?: Intent()
            val safeIntent = SafeIntent(intent)

            intent.flags = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
            intent.flags = intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK.inv()
            intent.flags = intent.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS.inv()

            // Check if this is a custom tabs intent
            val isCustomTab = safeIntent.hasExtra("android.support.customtabs.extra.SESSION") ||
                             safeIntent.hasExtra("androidx.browser.customtabs.extra.SESSION")

            // Process the intent to create a tab session
            val processor = TabIntentProcessor(components.tabsUseCases, components.searchUseCases.newTabSearch, isPrivate = false)
            processor.process(intent)

            val activityClass = if (isCustomTab) {
                // For custom tabs, use the ExternalAppBrowserActivity
                ExternalAppBrowserActivity::class
            } else {
                // For regular intents, use the normal BrowserActivity
                BrowserActivity::class
            }

            intent.setClassName(applicationContext, activityClass.java.name)

            startActivity(intent)
            finish()
        }
    }
}