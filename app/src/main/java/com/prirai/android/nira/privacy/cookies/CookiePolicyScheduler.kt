package com.prirai.android.nira.privacy.cookies

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Installs the periodic [CookiePolicyWorker] into WorkManager and
 * provides a one-shot [sweepNow] helper for the "clear now" affordance
 * in the settings screen.
 *
 * The scheduler is idempotent - `ensureScheduled` uses
 * [ExistingPeriodicWorkPolicy.KEEP] so repeated calls on each cold-start
 * are safe and do not reset the next-run clock.
 */
object CookiePolicyScheduler {

    const val WORK_NAME = "nira.privacy.cookies.sweep"
    const val WORK_NAME_ONESHOT = "nira.privacy.cookies.sweep.oneshot"

    /** WorkManager minimum period for periodic work is 15 minutes. */
    val SWEEP_INTERVAL_MINUTES: Long = 60

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<CookiePolicyWorker>(
            SWEEP_INTERVAL_MINUTES, TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
