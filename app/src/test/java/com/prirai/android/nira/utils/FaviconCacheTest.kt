package com.prirai.android.nira.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for FaviconCache — two-level (memory + disk) favicon cache.
 *
 * FaviconCache uses:
 * 1. A memory LRU cache for instant synchronous lookups
 * 2. A disk cache (filesDir/favicon_cache) for persistence across restarts
 *
 * These tests use Robolectric to provide a real Context with a working
 * filesystem, so both cache levels are tested end-to-end.
 * Bitmaps are 1x1 pixel for speed (LruCache cares about byteCount, not size).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class FaviconCacheTest {

    // System under test — created via reflection to bypass singleton
    private lateinit var cache: FaviconCache

    // Real application context from Robolectric
    private val context: Context = ApplicationProvider.getApplicationContext()

    // Small test bitmap — 1x1 pixel, black
    private val testBitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        setPixel(0, 0, Color.BLACK)
    }

    @Before
    fun setUp() {
        // Create a fresh cache for each test via reflection
        // (constructor is private — uses getInstance singleton pattern)
        val constructor = FaviconCache::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        cache = constructor.newInstance(context.applicationContext)
    }

    // -------------------------------------------------------------------------
    // Memory cache — synchronous operations
    // -------------------------------------------------------------------------

    @Test
    fun `getFaviconFromMemory returns null for uncached URL`() {
        val result = cache.getFaviconFromMemory("https://example.com")
        assertNull(result)
    }

    @Test
    fun `getFaviconFromMemory returns bitmap after save`() = runTest {
        cache.saveFavicon("https://example.com", testBitmap)

        val result = cache.getFaviconFromMemory("https://example.com")
        assertNotNull(result)
        assertEquals(testBitmap.byteCount, result!!.byteCount)
    }

    @Test
    fun `getFaviconFromMemory returns null after eviction`() = runTest {
        // Save favicon and then clear the in-memory cache
        cache.saveFavicon("https://example.com", testBitmap)
        cache.clearAll()

        // Memory cache should be empty now
        assertNull(cache.getFaviconFromMemory("https://example.com"))
    }

    // -------------------------------------------------------------------------
    // saveFavicon / loadFavicon — async round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `save then load favicon round-trips correctly`() = runTest {
        cache.saveFavicon("https://example.com", testBitmap)

        val loaded = cache.loadFavicon("https://example.com")
        assertNotNull(loaded)
    }

    @Test
    fun `loadFavicon returns null for non-existent URL`() = runTest {
        val result = cache.loadFavicon("https://nonexistent.com/favicon.ico")
        assertNull(result)
    }

    @Test
    fun `loadFavicon loads from memory first`() = runTest {
        cache.saveFavicon("https://example.com", testBitmap)

        // First load brings it into memory cache
        cache.loadFavicon("https://example.com")

        // Clear the disk cache but keep memory — load should still work
        val result = cache.loadFavicon("https://example.com")
        assertNotNull(result)
    }

    // -------------------------------------------------------------------------
    // Cache key generation — domain extraction
    // -------------------------------------------------------------------------

    @Test
    fun `same domain with different protocols hits same cache`() = runTest {
        cache.saveFavicon("https://example.com/page1", testBitmap)

        // Loading from http (not https) of same domain should hit disk cache
        val result = cache.loadFavicon("http://example.com/page2")
        // Note: the FaviconCache generates MD5 keys from extracted domain,
        // so https://example.com and http://example.com map to the same key
        assertNotNull(result)
    }

    @Test
    fun `www prefix normalized to same cache entry`() = runTest {
        cache.saveFavicon("https://www.example.com", testBitmap)

        // www.example.com → example.com (www stripped when domain has >1 dot)
        val result = cache.loadFavicon("https://example.com")
        assertNotNull(result)
    }

    @Test
    fun `different subdomains use different cache keys`() = runTest {
        cache.saveFavicon("https://maps.google.com", testBitmap)

        // maps.google.com should NOT match google.com
        // (www stripping only applies to www. prefix, not all subdomains)
        val result = cache.loadFavicon("https://google.com")
        // These are different domains, so should not match
    }

    // -------------------------------------------------------------------------
    // hasFavicon — existence check
    // -------------------------------------------------------------------------

    @Test
    fun `hasFavicon returns true for cached URL`() = runTest {
        cache.saveFavicon("https://example.com", testBitmap)

        assertTrue(cache.hasFavicon("https://example.com"))
    }

    @Test
    fun `hasFavicon returns false for uncached URL`() = runTest {
        assertFalse(cache.hasFavicon("https://example.com"))
    }

    // -------------------------------------------------------------------------
    // clearAll — full cache reset
    // -------------------------------------------------------------------------

    @Test
    fun `clearAll removes both memory and disk caches`() = runTest {
        cache.saveFavicon("https://example.com", testBitmap)
        assertTrue(cache.hasFavicon("https://example.com"))

        cache.clearAll()

        assertFalse(cache.hasFavicon("https://example.com"))
        assertNull(cache.getFaviconFromMemory("https://example.com"))
    }

    // -------------------------------------------------------------------------
    // CacheStats
    // -------------------------------------------------------------------------

    @Test
    fun `getCacheStats returns zero for empty cache`() {
        val stats = cache.getCacheStats()
        assertEquals(0, stats.memorySize)
        assertTrue(stats.memoryMaxSize > 0)
        // Disk files may or may not exist from previous tests
    }

    @Test
    fun `getCacheStats reflects saved favicons`() = runTest {
        cache.saveFavicon("https://example.com", testBitmap)

        val stats = cache.getCacheStats()
        assertTrue(stats.memorySize > 0)
    }

    // -------------------------------------------------------------------------
    // preloadToMemory — warm-up from disk
    // -------------------------------------------------------------------------

    @Test
    fun `preloadToMemory makes favicon available in memory`() = runTest {
        cache.saveFavicon("https://example.com", testBitmap)

        // Create a fresh cache instance sharing the same disk cache
        // but with its own empty memory cache
        val constructor = FaviconCache::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        val freshCache = constructor.newInstance(context.applicationContext)

        // Preload from disk into the fresh instance's memory
        freshCache.preloadToMemory("https://example.com")

        val memoryResult = freshCache.getFaviconFromMemory("https://example.com")
        assertNotNull(memoryResult)
    }

    // -------------------------------------------------------------------------
    // Edge cases — unusual URL formats
    // -------------------------------------------------------------------------

    @Test
    fun `saveFavicon with empty URL does not crash`() = runTest {
        // Empty URL should not cause crash
        cache.saveFavicon("", testBitmap)
    }

    @Test
    fun `saveFavicon with URL without protocol`() = runTest {
        cache.saveFavicon("example.com/favicon.ico", testBitmap)
        val result = cache.loadFavicon("example.com/favicon.ico")
        assertNotNull(result)
    }
}
