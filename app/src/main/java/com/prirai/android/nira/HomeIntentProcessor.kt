package com.prirai.android.nira

import android.content.Intent
import androidx.navigation.NavController

/**
 * Processor for Android intents received in [com.prirai.android.nira.BrowserActivity].
 */
interface HomeIntentProcessor {
    fun process(intent: Intent, navController: NavController, out: Intent): Boolean
}
