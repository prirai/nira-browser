package com.prirai.android.nira.privacy.cookies

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prirai.android.nira.ext.components
import mozilla.components.concept.engine.Engine
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

/**
 * Periodic sweeper that walks every row in `site_cookie_policies` and,
 * for any origin whose `savedAt + hoursUntilClear` is in the past, calls
 * `engine.clearData(host)` to wipe cookies + DOM storage for that host.
 *
 * Scheduling is owned by [CookiePolicyScheduler.ensureScheduled]. We
 * intentionally run on the WorkManager default period
 * (`CookiePolicyScheduler.SWEEP_INTERVAL`) and *not* exact alarms,
 * because Doze mode and background-work restrictions make exact
 * scheduling unreliable above API 23 and the user's expressed
 * expectation ("every N hours") already assumes best-effort rather than
 * second-precision timing.
 *
 * Error handling: cookie-clear failures per-origin are logged and
 * skipped - the row is left untouched so the next sweep retries. If the
 * worker itself crashes, WorkManager will reschedule it per its
 * backoff policy; we therefore do not catch at the top level and
 * instead return [Result.failure] so the retry policy applies.
 */
class CookiePolicyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = CookiePolicyRepository.getInstance(applicationContext)
        val engine: Engine = applicationContext.components.engine

        val policies = repo.getAll()
        if (policies.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        var swept = 0
        policies.forEach { policy ->
            val dueAt = policy.savedAt + policy.hoursUntilClear.toLong() * HOUR_MILLIS
            if (now < dueAt) return@forEach

            val host = policy.origin.tryGetHostFromUrl().orEmpty()
            if (host.isEmpty()) {
                // Malformed origin - skip rather than wipe the whole engine.
                Log.w(TAG, "Skipping policy with un-parseable origin: ${policy.origin}")
                return@forEach
            }

            try {
                engine.clearData(
                    data = Engine.BrowsingData.select(
                        Engine.BrowsingData.COOKIES,
                        Engine.BrowsingData.DOM_STORAGES,
                    ),
                    host = host,
                )
                repo.markSweptNow(policy.origin)
                swept++
            } catch (t: Throwable) {
                Log.w(TAG, "clearData failed for ${policy.origin}", t)
            }
        }

        if (swept > 0) Log.i(TAG, "Cookie-policy sweep cleared $swept origin(s)")
        return Result.success()
    }

    companion object {
        private const val TAG = "CookiePolicyWorker"
        private const val HOUR_MILLIS = 60L * 60L * 1000L
    }
}
