package com.prirai.android.nira.history

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for HistoryTimeFormatter — relative time formatting for browsing history.
 *
 * Uses Robolectric to provide a real Context. The System.currentTimeMillis()
 * cannot be reliably frozen in these tests without complex mocking, so the
 * relative assertions focus on verifying the function runs correctly and
 * returns reasonable values for historical timestamps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class HistoryTimeFormatterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `current time returns Just now`() {
        // Using exactly current time — should always be "Just now" (diff < 1 min)
        val result = HistoryTimeFormatter.getRelativeTimeString(
            context,
            System.currentTimeMillis()
        )
        assertEquals("Just now", result)
    }

    @Test
    fun `a few seconds ago returns Just now`() {
        val result = HistoryTimeFormatter.getRelativeTimeString(
            context,
            System.currentTimeMillis() - 15_000
        )
        assertEquals("Just now", result)
    }

    @Test
    fun `about a minute ago returns 1m ago`() {
        val result = HistoryTimeFormatter.getRelativeTimeString(
            context,
            System.currentTimeMillis() - 60_000
        )
        assertEquals("1m ago", result)
    }

    @Test
    fun `about an hour ago returns 1h ago`() {
        val result = HistoryTimeFormatter.getRelativeTimeString(
            context,
            System.currentTimeMillis() - 60 * 60_000
        )
        assertEquals("1h ago", result)
    }

    @Test
    fun `about a day ago returns 1d ago`() {
        val result = HistoryTimeFormatter.getRelativeTimeString(
            context,
            System.currentTimeMillis() - 24 * 60 * 60_000
        )
        assertEquals("1d ago", result)
    }

    @Test
    fun `future timestamp returns Just now`() {
        val result = HistoryTimeFormatter.getRelativeTimeString(
            context,
            System.currentTimeMillis() + 10_000
        )
        assertEquals("Just now", result)
    }

    @Test
    fun `function runs without exception`() {
        // Verify the function handles various inputs without crashing
        val testCases = listOf(
            System.currentTimeMillis() - 5 * 60_000,     // 5 min
            System.currentTimeMillis() - 12 * 60_000,    // 12 min
            System.currentTimeMillis() - 3 * 60 * 60_000, // 3 hours
            System.currentTimeMillis() - 5 * 24 * 60 * 60_000, // 5 days
            System.currentTimeMillis() - 14 * 24 * 60 * 60_000, // 2 weeks
            System.currentTimeMillis() - 60 * 24 * 60 * 60_000, // 2 months
            0L, // epoch
            Long.MAX_VALUE, // far future
        )

        testCases.forEach { timestamp ->
            val result = HistoryTimeFormatter.getRelativeTimeString(context, timestamp)
            // Should return a non-null, non-empty string
            assert(result.isNotEmpty()) { "Empty result for timestamp: $timestamp" }
        }
    }
}
