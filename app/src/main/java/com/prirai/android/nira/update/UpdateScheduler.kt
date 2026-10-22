package com.prirai.android.nira.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prirai.android.nira.BuildConfig
import com.prirai.android.nira.preferences.UserPreferences
import java.util.concurrent.TimeUnit

/**
 * Owns the WorkManager plumbing for [UpdateCheckWorker].
 *
 * Mirror of [com.prirai.android.nira.privacy.cookies.CookiePolicyScheduler]
 * in shape: `ensureScheduled(context)` is idempotent (uses
 * [ExistingPeriodicWorkPolicy.KEEP]) and `cancel(context)` tears
 * the schedule down.
 *
 * # Period
 * 24 hours repeat with a 6 hour flex window. The flex lets Android
 * batch our wake with other periodic work and run us at the end of
 * the 24h -> 24h+6h window. Battery cost is a single HTTPS fetch
 * (~2 KB response from the GitHub API) under
 * [NetworkType.UNMETERED], so we only run on Wi-Fi.
 *
 * Manual checks (initiated from the Settings "Check now" button)
 * bypass this scheduler and call [UpdateChecker.check] directly on
 * the fragment's lifecycleScope.
 */
internal object UpdateScheduler {

    const val WORK_NAME = "nira.update.check"

    private val REPEAT_INTERVAL_HOURS: Long = 24
    private val FLEX_INTERVAL_HOURS: Long = 6

    /**
     * Schedule the periodic worker iff the subsystem is active for
     * this install (release build + user opted-in). Idempotent;
     * safe to call on every cold start.
     *
     * If the subsystem is inactive we also [cancel] any previously-
     * enqueued work, so toggling `updateChecksEnabled` off from
     * Settings takes effect on the next launch without having to
     * reach into WorkManager directly.
     */
    fun ensureScheduled(context: Context) {
        if (BuildConfig.DEBUG) {
            cancel(context)
            return
        }
        val prefs = UserPreferences(context)
        if (!prefs.updateChecksEnabled) {
            cancel(context)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            REPEAT_INTERVAL_HOURS, TimeUnit.HOURS,
            FLEX_INTERVAL_HOURS, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(WORK_NAME)
    }
}
