package com.prirai.android.nira.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.prirai.android.nira.components.Components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.IconRequest
import java.net.URL

/**
 * Centralized favicon loader with 3-tier loading strategy:
 * 1. Check FaviconCache (instant if cached)
 * 2. Try Google Favicon Service (most reliable)
 * 3. Try Mozilla Components Icons (fallback)
 */
object FaviconLoader {
    
    private const val GOOGLE_FAVICON_SERVICE = "https://www.google.com/s2/favicons"
    private const val DEFAULT_ICON_SIZE = 128
    private const val CONNECTION_TIMEOUT = 5000
    private const val READ_TIMEOUT = 5000
    
    /**
     * Load favicon for a given URL
     * 
     * @param context Android context
     * @param url Website URL to load favicon for
     * @param size Icon size (default 128)
     * @return Bitmap if found, null otherwise
     */
    suspend fun loadFavicon(
        context: Context,
        url: String,
        size: Int = DEFAULT_ICON_SIZE
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val domain = extractDomain(url)
                
                // 1. Try favicon cache first (instant if cached)
                FaviconCache.getInstance(context).loadFavicon(url)?.let { 
                    return@withContext it 
                }
                
                // 2. Try Google Favicon Service (most reliable)
                loadFromGoogleService(domain, size)?.let { bitmap ->
                    // Save to cache for future use
                    FaviconCache.getInstance(context).saveFavicon(url, bitmap)
                    return@withContext bitmap
                }
                
                // 3. Try Mozilla Components Icons as final fallback
                loadFromMozillaIcons(context, url)?.let { bitmap ->
                    // Save to cache for future use
                    FaviconCache.getInstance(context).saveFavicon(url, bitmap)
                    return@withContext bitmap
                }
                
                null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Load favicon from Google's favicon service
     */
    private fun loadFromGoogleService(domain: String, size: Int): Bitmap? {
        return try {
            val faviconUrl = "$GOOGLE_FAVICON_SERVICE?domain=$domain&sz=$size"
            val connection = URL(faviconUrl).openConnection()
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            val inputStream = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Load favicon using Mozilla Components Icons
     */
    private suspend fun loadFromMozillaIcons(context: Context, url: String): Bitmap? {
        return try {
            val iconRequest = IconRequest(url = url)
            Components(context).icons.loadIcon(iconRequest).await()?.bitmap
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Extract domain from URL
     */
    private fun extractDomain(url: String): String {
        return try {
            URL(url).host
        } catch (e: Exception) {
            url
        }
    }
    
    /**
     * Build Google Favicon Service URL
     */
    fun getGoogleFaviconUrl(domain: String, size: Int = DEFAULT_ICON_SIZE): String {
        return "$GOOGLE_FAVICON_SERVICE?domain=$domain&sz=$size"
    }
}
