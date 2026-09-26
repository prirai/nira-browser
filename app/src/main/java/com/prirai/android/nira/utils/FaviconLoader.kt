package com.prirai.android.nira.utils

import android.content.Context
import android.graphics.Bitmap
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.IconRequest
import mozilla.components.support.base.log.logger.Logger

/**
 * Centralized favicon loader with intelligent caching.
 *
 * This is the SINGLE source of truth for all favicon loading in the app.
 * Use this everywhere for consistent, fast favicon access.
 *
 * Cache Hierarchy (fastest to slowest):
 * 1. Memory cache - Instant synchronous access (0ms)
 * 2. Disk cache - Fast async access (~10ms)
 * 3. Upstream `BrowserIcons` - own cache + network fetch (~100-1000ms)
 *
 * A previous revision consulted `https://www.google.com/s2/favicons` as a
 * "fast CDN" fallback for PWAs. That path was removed: it leaked every PWA
 * URL to Google on first-fetch, which contradicts the privacy-focused
 * mission of the app. Upstream `BrowserIcons` has its own on-disk cache and
 * a generator fallback, so it's fast enough on its own; latency-sensitive
 * callers should use [getFromMemorySync] first and load asynchronously.
 *
 * Usage:
 * ```kotlin
 * // Synchronous memory check (instant, returns null if not cached)
 * val cached = FaviconLoader.getFromMemorySync(context, url)
 *
 * // Full async load (checks all levels, fetches if needed)
 * val favicon = FaviconLoader.loadFavicon(context, url)
 *
 * // PWA-specific load (same as loadFavicon; retained for source compat)
 * val favicon = FaviconLoader.loadFaviconForPwa(context, url)
 * ```
 */
object FaviconLoader {

    private val logger = Logger("FaviconLoader")

    /**
     * Load favicon with full cache hierarchy
     *
     * Order:
     * 1. Memory cache (instant)
     * 2. Disk cache (fast)
     * 3. BrowserIcons (slow, may fetch from network)
     *
     * Automatically saves to cache for future fast access
     */
    suspend fun loadFavicon(context: Context, url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val cache = FaviconCache.getInstance(context)

                cache.getFaviconFromMemory(url)?.let {
                    return@withContext it
                }

                cache.loadFavicon(url)?.let {
                    return@withContext it
                }

                val iconRequest = IconRequest(
                    url = url,
                    size = IconRequest.Size.DEFAULT,
                    resources = listOf(
                        IconRequest.Resource(
                            url = url,
                            type = IconRequest.Resource.Type.FAVICON
                        ),
                        IconRequest.Resource(
                            url = url,
                            type = IconRequest.Resource.Type.APPLE_TOUCH_ICON
                        ),
                        IconRequest.Resource(
                            url = url,
                            type = IconRequest.Resource.Type.IMAGE_SRC
                        )
                    )
                )

                val icon = context.components.icons.loadIcon(iconRequest).await()
                val bitmap = icon.bitmap
                // BrowserIcons always returns a bitmap (may be a generated
                // placeholder). Cache it so subsequent lookups hit the fast
                // path in FaviconCache.
                cache.saveFaviconSync(url, bitmap)
                bitmap
            } catch (e: Exception) {
                logger.warn("Favicon load failed for $url", e)
                null
            }
        }
    }

    /**
     * Load favicon for a PWA. Alias of [loadFavicon] - kept for source-compat
     * with the 4 existing callsites (PwaSuggestionsAdapter, PwaSuggestionManager,
     * WebAppActivity, UnifiedWebAppFragment) so they don't need to change.
     *
     * Historically this was a separate implementation that hit
     * `https://www.google.com/s2/favicons` as a fast CDN. That path was
     * removed for privacy - see the class kdoc.
     *
     * @param size retained for API compat; ignored (BrowserIcons picks its own size).
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun loadFaviconForPwa(
        context: Context,
        url: String,
        size: Int = 64,
    ): Bitmap? = loadFavicon(context, url)

    /**
     * Get favicon from memory cache only (SYNCHRONOUS - instant)
     *
     * Returns null if not in memory.
     * Use this for immediate UI display without blocking.
     *
     * Example:
     * ```kotlin
     * val cached = FaviconLoader.getFromMemorySync(context, url)
     * if (cached != null) {
     *     imageView.setImageBitmap(cached)
     * } else {
     *     imageView.setImageResource(R.drawable.ic_default)
     *     // Launch async load in background
     *     lifecycleScope.launch {
     *         val favicon = FaviconLoader.loadFavicon(context, url)
     *         if (favicon != null) {
     *             imageView.setImageBitmap(favicon)
     *         }
     *     }
     * }
     * ```
     */
    fun getFromMemorySync(context: Context, url: String): Bitmap? {
        return FaviconCache.getInstance(context).getFaviconFromMemory(url)
    }

    /**
     * Load favicon with retry logic for race conditions
     *
     * Useful when multiple components might be loading the same favicon simultaneously
     * (e.g., during preload + user navigation)
     */
    suspend fun loadFaviconWithRetry(
        context: Context,
        url: String,
        maxRetries: Int = 2,
        retryDelayMs: Long = 300
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            var attempts = 0
            var result: Bitmap? = null

            while (attempts <= maxRetries && result == null) {
                result = loadFavicon(context, url)

                if (result == null && attempts < maxRetries) {
                    // Wait before retry (another load might complete)
                    kotlinx.coroutines.delay(retryDelayMs)
                    attempts++
                } else {
                    break
                }
            }

            result
        }
    }

    /**
     * Load PWA favicon with retry logic. Alias of [loadFaviconWithRetry] -
     * kept for source-compat.
     *
     * @param size retained for API compat; ignored.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun loadFaviconForPwaWithRetry(
        context: Context,
        url: String,
        size: Int = 64,
        maxRetries: Int = 2,
        retryDelayMs: Long = 300,
    ): Bitmap? = loadFaviconWithRetry(context, url, maxRetries, retryDelayMs)

    /**
     * Preload favicon to memory from disk (warm-up cache)
     *
     * Call this for URLs that will be displayed soon
     * to ensure instant access when needed
     */
    suspend fun preloadToMemory(context: Context, url: String) {
        FaviconCache.getInstance(context).preloadToMemory(url)
    }

    /**
     * Save favicon to cache (for manual insertion)
     */
    suspend fun saveFavicon(context: Context, url: String, bitmap: Bitmap) {
        FaviconCache.getInstance(context).saveFavicon(url, bitmap)
    }

    /**
     * Check if favicon is cached (memory or disk)
     */
    suspend fun isCached(context: Context, url: String): Boolean {
        return FaviconCache.getInstance(context).hasFavicon(url)
    }

    /**
     * Clear old cache files (maintenance)
     */
    suspend fun cleanupOldCache(context: Context) {
        FaviconCache.getInstance(context).cleanupOldFiles()
    }
}
