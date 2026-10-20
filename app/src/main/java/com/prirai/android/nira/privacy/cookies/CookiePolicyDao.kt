package com.prirai.android.nira.privacy.cookies

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CookiePolicyDao {

    /** Observe every policy in the table; used by the exceptions screen. */
    @Query("SELECT * FROM site_cookie_policies ORDER BY origin ASC")
    fun observeAll(): Flow<List<CookiePolicy>>

    /** Snapshot read used by the WorkManager sweeper on IO. */
    @Query("SELECT * FROM site_cookie_policies")
    suspend fun getAll(): List<CookiePolicy>

    @Query("SELECT * FROM site_cookie_policies WHERE origin = :origin LIMIT 1")
    suspend fun get(origin: String): CookiePolicy?

    /**
     * Insert-or-replace: the primary key is `origin`, so writing twice
     * with the same origin updates the retention window and bumps
     * `savedAt`. Callers should stamp `savedAt` to
     * `System.currentTimeMillis()` before invoking.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(policy: CookiePolicy)

    /** Advance `savedAt` to [now] without touching the retention window. */
    @Query("UPDATE site_cookie_policies SET savedAt = :now WHERE origin = :origin")
    suspend fun touch(origin: String, now: Long)

    @Query("DELETE FROM site_cookie_policies WHERE origin = :origin")
    suspend fun delete(origin: String)

    @Query("DELETE FROM site_cookie_policies")
    suspend fun deleteAll()
}
