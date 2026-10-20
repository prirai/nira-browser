package com.prirai.android.nira.privacy.cookies

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-origin "clear my cookies and site data every N hours" policy.
 *
 * This lives next to AC's `SitePermissions` row (not inside it) because the
 * concept of "automatic retention" is Nira-specific - MozAC's permission
 * storage is a frozen three-state (ALLOWED / NO_DECISION / BLOCKED) that
 * doesn't model time-bound decisions. Combining the two lets a user say
 * "allow cookies, but wipe them every 24 hours" without us having to
 * patch the upstream AC data class.
 *
 * The sweeper ([CookiePolicyWorker]) runs on a WorkManager periodic
 * schedule and clears cookies for every origin whose
 * `savedAt + hoursUntilClear` is in the past, then advances `savedAt` to
 * `System.currentTimeMillis()` so the next sweep is scheduled relative
 * to the clear point.
 *
 * @property origin the full origin string (`https://example.com`), matching
 *   the `SitePermissions.origin` key format. Used verbatim as the primary
 *   key - we expect ~tens of rows per install, not thousands, so no
 *   normalisation / dedup is attempted beyond what the caller provides.
 * @property hoursUntilClear the retention window in hours. `0` is reserved
 *   to mean "clear on every sweep" (effectively "no retention"); negative
 *   values are rejected at the DAO layer.
 * @property savedAt epoch millis of the last time the row was written or
 *   a sweep moved the goalpost forward. Not nullable - a fresh row is
 *   stamped with `System.currentTimeMillis()` by the repository.
 */
@Entity(tableName = "site_cookie_policies")
data class CookiePolicy(
    @PrimaryKey val origin: String,
    val hoursUntilClear: Int,
    val savedAt: Long,
)
