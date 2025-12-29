package com.prirai.android.nira.browser.tabs.compose

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.concept.base.images.ImageLoadRequest

/**
 * Composable that displays a tab thumbnail using Mozilla's ThumbnailLoader.
 * Falls back to a placeholder icon if thumbnail is not available.
 */
@Composable
fun ThumbnailImageView(
    tab: TabSessionState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailLoader = remember { ThumbnailLoader(context.components.thumbnailStorage) }
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val vignetteColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
    
    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Show placeholder icon by default
        var showPlaceholder by remember { mutableStateOf(true) }
        
        // Thumbnail ImageView using AndroidView
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    // Set initial placeholder
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { imageView ->
                // Load thumbnail after view is measured
                imageView.post {
                    val width = imageView.width
                    val height = imageView.height
                    if (width > 0 && height > 0) {
                        val size = maxOf(width, height)
                        try {
                            thumbnailLoader.loadIntoView(
                                imageView,
                                ImageLoadRequest(tab.id, size, isPrivate = tab.content.private)
                            )
                            showPlaceholder = false
                        } catch (e: Exception) {
                            showPlaceholder = true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Add vignette overlay at bottom to improve text visibility
        if (!showPlaceholder && !tab.content.url.startsWith("about:")) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val gradient = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                vignetteColor
                            ),
                            startY = size.height * 0.7f,
                            endY = size.height
                        )
                        drawRect(brush = gradient)
                    }
            )
        }
        
        // Overlay placeholder icon for about: pages or if thumbnail fails
        if (showPlaceholder || tab.content.url.startsWith("about:")) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
