package com.prirai.android.nira.downloads

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DownloadFormattingTest {
    init {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun formatBytesPicksUnit() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("2.0 GB", formatBytes(2_147_483_648L))
    }

    @Test
    fun formatDurationPicksUnit() {
        assertEquals("45s left", formatDuration(45))
        assertEquals("2m 5s left", formatDuration(125))
        assertEquals("1h 1m left", formatDuration(3660))
    }
}
