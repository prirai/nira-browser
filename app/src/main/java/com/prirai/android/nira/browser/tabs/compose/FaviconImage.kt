package com.prirai.android.nira.browser.tabs.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.icons.IconRequest
import com.prirai.android.nira.ext.components
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * FaviconImage Composable
 *
 * Displays a favicon for a tab with automatic loading and caching.
 * Handles three sources of favicons in priority order:
 * 1. Tab's content.icon (from GeckoView)
 * 2. BrowserIcons component (Mozilla's icon loader)
 * 3. FaviconCache (local cache)
 * 4. Fallback icon (default globe icon)
 *
 * Features:
 * - Automatically loads favicon on composition
 * - Observes tab state changes for icon updates
 * - Uses coroutines for non-blocking loading
 * - Fallback to default icon if loading fails
 * - Optimized for Compose with remember and LaunchedEffect
 *
 * Usage:
 * ```kotlin
 * FaviconImage(
 *     tab = tabSessionState,
 *     size = 24.dp,
 *     modifier = Modifier
 * )
 * ```
 */

/**
 * Displays a favicon for a tab with automatic loading
 *
 * @param tab The tab session state
 * @param size Size of the favicon (default: 24.dp)
 * @param modifier Optional modifier
 */
@Composable
fun FaviconImage(
    tab: TabSessionState,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Use produceState instead of LaunchedEffect + remember to handle lifecycle properly
    val faviconBitmap by produceState<Bitmap?>(initialValue = tab.content.icon, tab.id, tab.content.icon, tab.content.url) {
        value = loadFavicon(tab, context)
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (faviconBitmap != null) {
            Image(
                bitmap = faviconBitmap!!.asImageBitmap(),
                contentDescription = "Favicon for ${tab.content.title}",
                modifier = Modifier.size(size)
            )
        } else {
            // Fallback icon
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Default favicon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size)
            )
        }
    }
}

/**
 * Load favicon from multiple sources
 */
private suspend fun loadFavicon(tab: TabSessionState, context: android.content.Context): Bitmap? {
    return try {
        withContext(Dispatchers.IO) {
            // Priority 1: Tab's existing icon (from GeckoView)
            tab.content.icon?.let { return@withContext it }

            // Priority 2: BrowserIcons component (Mozilla's loader)
            // This is the standard way to load icons in Android Components
            val icons = context.components.icons

            val iconRequest = IconRequest(
                url = tab.content.url,
                size = IconRequest.Size.DEFAULT,
                resources = listOf(
                    IconRequest.Resource(
                        url = tab.content.url,
                        type = IconRequest.Resource.Type.FAVICON
                    )
                )
            )

            val icon = icons.loadIcon(iconRequest).await()
            icon.bitmap?.let { return@withContext it }

            // Priority 3: FaviconCache (local cache)
            val faviconCache = context.components.faviconCache
            faviconCache.loadFavicon(tab.content.url)?.let { return@withContext it }

            // Priority 4: Return null to show fallback
            null
        }
    } catch (e: Exception) {
        // Only log actual errors, not cancellations
        if (e !is kotlinx.coroutines.CancellationException) {
            android.util.Log.e("FaviconImage", "Error loading favicon for ${tab.content.url}", e)
        }
        null
    }
}

/**
 * Variant that takes a URL instead of a TabSessionState
 * Useful for bookmarks, history, etc.
 */
@Composable
fun FaviconImageFromUrl(
    url: String,
    title: String = "",
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Use produceState for proper lifecycle handling
    val faviconBitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = loadFaviconFromUrl(url, context)
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (faviconBitmap != null) {
            Image(
                bitmap = faviconBitmap!!.asImageBitmap(),
                contentDescription = "Favicon for $title",
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Default favicon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size)
            )
        }
    }
}

/**
 * Load favicon from URL only (no TabSessionState)
 */
private suspend fun loadFaviconFromUrl(url: String, context: android.content.Context): Bitmap? {
    return try {
        withContext(Dispatchers.IO) {
            val icons = context.components.icons

            val iconRequest = IconRequest(
                url = url,
                size = IconRequest.Size.DEFAULT,
                resources = listOf(
                    IconRequest.Resource(
                        url = url,
                        type = IconRequest.Resource.Type.FAVICON
                    )
                )
            )

            val icon = icons.loadIcon(iconRequest).await()
            icon.bitmap?.let { return@withContext it }

            // Fallback to cache
            val faviconCache = context.components.faviconCache
            faviconCache.loadFavicon(url)
        }
    } catch (e: Exception) {
        // Only log actual errors, not cancellations
        if (e !is kotlinx.coroutines.CancellationException) {
            android.util.Log.e("FaviconImage", "Error loading favicon from URL: $url", e)
        }
        null
    }
}

/**
 * Preload favicons for multiple tabs
 * Call this when loading a list of tabs to improve perceived performance
 */
fun preloadFavicons(context: android.content.Context, tabs: List<TabSessionState>) {
    GlobalScope.launch(Dispatchers.IO) {
        val icons = context.components.icons

        tabs.forEach { tab ->
            try {
                if (tab.content.icon == null) {
                    val iconRequest = IconRequest(
                        url = tab.content.url,
                        size = IconRequest.Size.DEFAULT
                    )
                    icons.loadIcon(iconRequest)
                }
            } catch (e: Exception) {
                // Silently fail for preloading
            }
        }
    }
}
