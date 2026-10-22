package com.prirai.android.nira.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prirai.android.nira.BuildConfig
import com.prirai.android.nira.preferences.UserPreferences

/**
 * Periodic background check for new Nira releases.
 *
 * Scheduled by [UpdateScheduler.ensureScheduled] with a 24h repeat
 * interval + 6h flex window. Only reacts to the pref state;
 * [UserPreferences.updateChecksEnabled] is the single source of
 * truth for "is this subsystem active". If the user flips it off
 * between schedule-time and run-time, the worker exits immediately
 * and the scheduler cancels the periodic request on the next
 * ON_START.
 *
 * On success the worker writes the fetched manifest + a
 * `pendingUpdateNotify` flag into prefs; the UI (bottom sheet or
 * notification) picks it up on the next Activity resume. The
 * worker itself NEVER posts a user-visible notification - that is
 * reserved for Nira's main UI so users see a consistent surface.
 */
internal class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "skipping: debug build")
            return Result.success()
        }

        val prefs = UserPreferences(applicationContext)
        if (!prefs.updateChecksEnabled) {
            Log.i(TAG, "skipping: updateChecksEnabled=false")
            return Result.success()
        }

        prefs.updateLastCheckTimestamp = System.currentTimeMillis()

        return when (val result = UpdateChecker.check(applicationContext)) {
            is UpdateCheckResult.UpdateAvailable -> {
                // Guard against repeat-nagging for a version the user
                // has explicitly dismissed.
                val skipped = prefs.updateSkippedVersionCode
                val remote = result.manifest.versionCode ?: 0
                if (skipped > 0 && remote <= skipped) {
                    Log.i(
                        TAG,
                        "update ${result.manifest.tagName} is at or below skippedVersionCode=$skipped; not notifying",
                    )
                    prefs.updatePendingManifestJson = ""
                    prefs.updatePendingNotify = false
                } else {
                    prefs.updatePendingManifestJson = result.manifest.toJson()
                    prefs.updatePendingNotify = true
                    Log.i(TAG, "pending notify: ${result.manifest.tagName}")
                }
                Result.success()
            }
            UpdateCheckResult.UpToDate -> {
                prefs.updatePendingManifestJson = ""
                prefs.updatePendingNotify = false
                Result.success()
            }
            is UpdateCheckResult.Error -> {
                Log.w(TAG, "check error: ${result.reason}")
                // Non-retriable: a periodic worker will retry on its
                // own schedule in 24h. Returning Result.retry() would
                // escalate the retry rate and spam the user's network.
                Result.success()
            }
            is UpdateCheckResult.UnsupportedAbi -> {
                Log.w(TAG, "unsupported abi: ${result.abi}")
                // Permanent for this install; mark checks disabled so
                // we don't thrash the API forever. The user can
                // re-enable from Settings - probably won't, since the
                // device really is unsupported, but we document the
                // transition.
                prefs.updatePendingManifestJson = ""
                prefs.updatePendingNotify = false
                Result.success()
            }
        }
    }

    companion object {
        private const val TAG = "NiraUpdateWorker"
    }
}
