package com.prirai.android.nira.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.IconRequest
import java.net.URL

/**
 * Centralized favicon loader with intelligent caching
 *
 * This is the SINGLE source of truth for all favicon loading in the app.
 * Use this everywhere for consistent, fast favicon access.
 *
 * Features:
 * - Superfast memory lookup (synchronous)
 * - Persistent disk cache (survives restarts)
 * - Google favicon service for PWAs (fast CDN)
 * - Automatic BrowserIcons integration
 * - Domain-based key generation
 * - Thread-safe singleton pattern
 *
 * Cache Hierarchy (fastest to slowest):
 * 1. Memory cache - Instant synchronous access (0ms)
 * 2. Disk cache - Fast async access (~10ms)
 * 3. Google Favicon Service - Fast CDN (~50-200ms) [PWAs only]
 * 4. BrowserIcons - Network if needed (~100-1000ms)
 *
 * Usage:
 * ```kotlin
 * // Synchronous memory check (instant, returns null if not cached)
 * val cached = FaviconLoader.getFromMemorySync(context, url)
 *
 * // Full async load (checks all levels, fetches if needed)
 * val favicon = FaviconLoader.loadFavicon(context, url)
 *
 * // PWA-specific load (uses Google service for speed)
 * val favicon = FaviconLoader.loadFaviconForPwa(context, url)
 * ```
 */
object FaviconLoader {

    private const val GOOGLE_FAVICON_SERVICE = "https://www.google.com/s2/favicons"

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

                if (bitmap != null) {
                    // Save to cache for future fast access
                    cache.saveFaviconSync(url, bitmap)
                    bitmap
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Load favicon optimized for PWAs (uses Google's fast CDN service)
     *
     * Order:
     * 1. Memory cache (instant)
     * 2. Disk cache (fast)
     * 3. Google Favicon Service (fast CDN, ~50-200ms)
     * 4. BrowserIcons (fallback if Google service fails)
     *
     * Use this for PWA suggestions, webapp lists, and other PWA-related UI
     * for faster initial loading compared to BrowserIcons.
     */
    suspend fun loadFaviconForPwa(
        context: Context,
        url: String,
        size: Int = 64
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val cache = FaviconCache.getInstance(context)

                cache.getFaviconFromMemory(url)?.let {
                    return@withContext it
                }

                cache.loadFavicon(url)?.let {
                    return@withContext it
                }

                val domain = extractDomain(url)
                val googleFaviconUrl = "$GOOGLE_FAVICON_SERVICE?domain=$domain&sz=$size"

                try {
                    val connection = URL(googleFaviconUrl).openConnection()
                    connection.connectTimeout = 5000 // 5 second timeout
                    connection.readTimeout = 5000
                    connection.connect()

                    val inputStream = connection.getInputStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    if (bitmap != null && bitmap.width > 1 && bitmap.height > 1) {
                        // Valid favicon (Google returns 1x1 pixel for errors)
                        cache.saveFaviconSync(url, bitmap)
                        return@withContext bitmap
                    }
                } catch (e: Exception) {
                    // Google service failed, fall through to BrowserIcons
                    e.printStackTrace()
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

                if (bitmap != null) {
                    // Save to cache for future fast access
                    cache.saveFaviconSync(url, bitmap)
                    bitmap
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Extract domain from URL for Google favicon service
     */
    private fun extractDomain(url: String): String {
        return try {
            var domain = url.lowercase().trim()

            // Remove protocol
            domain = when {
                domain.startsWith("http://") -> domain.substring(7)
                domain.startsWith("https://") -> domain.substring(8)
                else -> domain
            }

            // Extract just the domain (before /, ?, #)
            domain = domain.split("/", "?", "#")[0]

            // Remove port if present
            domain = domain.split(":")[0]

            domain
        } catch (e: Exception) {
            url
        }
    }

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
     * Load PWA favicon with retry logic (uses Google service for speed)
     *
     * Optimized for PWA suggestions and webapp lists
     */
    suspend fun loadFaviconForPwaWithRetry(
        context: Context,
        url: String,
        size: Int = 64,
        maxRetries: Int = 2,
        retryDelayMs: Long = 300
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            var attempts = 0
            var result: Bitmap? = null

            while (attempts <= maxRetries && result == null) {
                result = loadFaviconForPwa(context, url, size)

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
