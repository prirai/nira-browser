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

/**
 * Simple favicon persistence cache that saves favicons to internal storage
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
        
        private const val CACHE_SIZE = 20 * 1024 * 1024 // 20MB
        private const val FAVICON_CACHE_DIR = "favicon_cache"
    }
    
    init {
        // Memory cache for quick access
        memoryCache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount
            }
        }
        
        // Disk cache directory
        cacheDir = File(context.cacheDir, FAVICON_CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * Save a favicon for a URL
     */
    suspend fun saveFavicon(url: String, bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            try {
                val key = generateKey(url)
                
                // Save to memory cache
                memoryCache.put(key, bitmap)
                
                // Save to disk cache
                val file = File(cacheDir, "$key.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Load a favicon for a URL
     */
    suspend fun loadFavicon(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val key = generateKey(url)
                
                // Check memory cache first
                memoryCache.get(key)?.let { return@withContext it }
                
                // Check disk cache
                val file = File(cacheDir, "$key.png")
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        // Add back to memory cache
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
     * Get favicon from memory cache only (synchronous)
     */
    fun getFaviconFromMemory(url: String): Bitmap? {
        val key = generateKey(url)
        return memoryCache.get(key)
    }
    
    /**
     * Clear old favicon cache files
     */
    suspend fun cleanupOldFiles(maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000) { // 7 days
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
     * Generate a cache key from URL
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
     */
    private fun extractDomain(url: String): String {
        return try {
            val cleanUrl = url.lowercase()
            when {
                cleanUrl.startsWith("http://") -> cleanUrl.substring(7)
                cleanUrl.startsWith("https://") -> cleanUrl.substring(8)
                else -> cleanUrl
            }.split("/")[0].split("?")[0]
        } catch (e: Exception) {
            url
        }
    }
}