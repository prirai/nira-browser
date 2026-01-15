package com.prirai.android.nira.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.net.URL

/**
 * Enhanced favicon persistence cache with superfast domain-based lookup
 * 
 * Features:
 * - Synchronous memory cache for instant access (no suspend needed)
 * - Persistent disk storage survives app restarts
 * - Domain-based key generation (maps.google.com style)
 * - Automatic cleanup of old entries
 * - Thread-safe singleton pattern
 * 
 * Cache Hierarchy:
 * 1. Memory (LRU, 20MB) - Instant synchronous access
 * 2. Disk (persistent) - Fast async access
 * 3. Network (via BrowserIcons) - Slow, only if cache miss
 */
class FaviconCache private constructor(private val context: Context) {
    
    private val memoryCache: LruCache<String, Bitmap>
    private val cacheDir: File
    
    companion object {
        @Volatile
        private var INSTANCE: FaviconCache? = null
        
        fun getInstance(context: Context): FaviconCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FaviconCache(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val CACHE_SIZE = 40 * 1024 * 1024 // 40MB (doubled for more icons)
        private const val FAVICON_CACHE_DIR = "favicon_cache"
    }
    
    init {
        // Memory cache for quick access (larger for more icons)
        memoryCache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount
            }
        }
        
        // Disk cache directory (use filesDir for persistence, not cacheDir)
        cacheDir = File(context.filesDir, FAVICON_CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * Save a favicon for a URL (async for disk I/O)
     */
    suspend fun saveFavicon(url: String, bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            try {
                val key = generateKey(url)
                
                // Save to memory cache immediately (synchronous)
                memoryCache.put(key, bitmap)
                
                // Save to disk cache (async)
                val file = File(cacheDir, "$key.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // 100 quality for crisp icons
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Save a favicon synchronously (blocks thread, use only when already on IO thread)
     */
    fun saveFaviconSync(url: String, bitmap: Bitmap) {
        try {
            val key = generateKey(url)
            
            // Save to memory cache
            memoryCache.put(key, bitmap)
            
            // Save to disk cache
            val file = File(cacheDir, "$key.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load a favicon for a URL (async for disk I/O)
     */
    suspend fun loadFavicon(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val key = generateKey(url)
                
                // Check memory cache first (instant)
                memoryCache.get(key)?.let { return@withContext it }
                
                // Check disk cache (fast)
                val file = File(cacheDir, "$key.png")
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        // Add back to memory cache for next time
                        memoryCache.put(key, bitmap)
                        return@withContext bitmap
                    }
                }
                
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Get favicon from memory cache only (SYNCHRONOUS - instant lookup)
     * Use this for immediate UI display without suspend
     */
    fun getFaviconFromMemory(url: String): Bitmap? {
        val key = generateKey(url)
        return memoryCache.get(key)
    }
    
    /**
     * Check if favicon exists in cache (memory or disk, async)
     */
    suspend fun hasFavicon(url: String): Boolean {
        return loadFavicon(url) != null
    }
    
    /**
     * Preload favicon to memory from disk (async warm-up)
     */
    suspend fun preloadToMemory(url: String) {
        withContext(Dispatchers.IO) {
            try {
                val key = generateKey(url)
                
                // Already in memory?
                if (memoryCache.get(key) != null) return@withContext
                
                // Load from disk to memory
                val file = File(cacheDir, "$key.png")
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        memoryCache.put(key, bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Clear old favicon cache files
     */
    suspend fun cleanupOldFiles(maxAgeMillis: Long = 30 * 24 * 60 * 60 * 1000L) { // 30 days (increased from 7)
        withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                cacheDir.listFiles()?.forEach { file ->
                    if (currentTime - file.lastModified() > maxAgeMillis) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Clear all cached favicons (for testing/debugging)
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                memoryCache.evictAll()
                cacheDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            memorySize = memoryCache.size(),
            memoryMaxSize = memoryCache.maxSize(),
            diskFiles = cacheDir.listFiles()?.size ?: 0
        )
    }
    
    data class CacheStats(
        val memorySize: Int,
        val memoryMaxSize: Int,
        val diskFiles: Int
    )
    
    /**
     * Generate a cache key from URL using domain extraction
     * Supports patterns like:
     * - maps.google.com
     * - www.example.com → example.com
     * - subdomain.example.com
     */
    private fun generateKey(url: String): String {
        return try {
            val domain = extractDomain(url)
            val bytes = MessageDigest.getInstance("MD5").digest(domain.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
    }
    
    /**
     * Extract domain from URL for better cache key generation
     * Normalizes URLs to domain level (removes protocol, path, query)
     * 
     * Examples:
     * - https://maps.google.com/search?q=test → maps.google.com
     * - http://www.example.com/page → example.com (www removed)
     * - subdomain.example.com/path → subdomain.example.com
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
            
            // Remove 'www.' prefix for normalization (www.google.com → google.com)
            if (domain.startsWith("www.") && domain.count { it == '.' } > 1) {
                domain = domain.substring(4)
            }
            
            domain
        } catch (e: Exception) {
            url
        }
    }
}