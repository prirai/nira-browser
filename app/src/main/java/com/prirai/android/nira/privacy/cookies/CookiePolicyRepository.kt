package com.prirai.android.nira.privacy.cookies

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper around [CookiePolicyDao] that stamps `savedAt` and
 * normalises retention windows on write. Hosts (sheets, settings
 * screens, the sweeper) should go through this and never touch the DAO
 * directly.
 */
class CookiePolicyRepository private constructor(
    private val dao: CookiePolicyDao,
) {

    fun observeAll(): Flow<List<CookiePolicy>> = dao.observeAll()

    suspend fun getAll(): List<CookiePolicy> = dao.getAll()

    suspend fun get(origin: String): CookiePolicy? = dao.get(origin)

    /**
     * Set the retention window for [origin]. [hoursUntilClear] is clamped
     * to `[0, 24 * 365]` (zero = clear on every sweep; one year is the
     * practical ceiling, enforced so a slip-of-the-finger does not
     * schedule a sweep decades out). Stamps `savedAt` to `now`.
     */
    suspend fun setPolicy(origin: String, hoursUntilClear: Int) {
        val clamped = hoursUntilClear.coerceIn(MIN_HOURS, MAX_HOURS)
        dao.upsert(
            CookiePolicy(
                origin = origin,
                hoursUntilClear = clamped,
                savedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Called by the sweeper after it has wiped cookies for [origin]. */
    suspend fun markSweptNow(origin: String) {
        dao.touch(origin, System.currentTimeMillis())
    }

    suspend fun remove(origin: String) {
        dao.delete(origin)
    }

    suspend fun removeAll() {
        dao.deleteAll()
    }

    companion object {
        /** Minimum retention window - zero is reserved for 'clear on every sweep'. */
        const val MIN_HOURS = 0

        /** One year ceiling - see [setPolicy]. */
        const val MAX_HOURS = 24 * 365

        @Volatile private var instance: CookiePolicyRepository? = null

        fun getInstance(context: Context): CookiePolicyRepository {
            return instance ?: synchronized(this) {
                instance ?: CookiePolicyRepository(
                    CookiePolicyDatabase.getInstance(context).cookiePolicyDao(),
                ).also { instance = it }
            }
        }
    }
}
