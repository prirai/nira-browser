package com.prirai.android.nira

import android.content.Intent
import androidx.navigation.NavController
import com.prirai.android.nira.ext.components
import mozilla.components.feature.media.service.AbstractMediaSessionService


class OpenSpecificTabIntentProcessor(
    private val activity: BrowserActivity
) : HomeIntentProcessor {

    override fun process(intent: Intent, navController: NavController, out: Intent): Boolean {
        if (intent.action == AbstractMediaSessionService.ACTION_SWITCH_TAB) {
            activity.components.store
            val tabId = intent.extras?.getString(AbstractMediaSessionService.EXTRA_TAB_ID)

            if (tabId != null) {
                activity.components.tabsUseCases.selectTab(tabId)
                activity.openToBrowser(BrowserDirection.FromGlobal)
                return true
            }
        }

        return false
    }
}