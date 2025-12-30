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
 * Crops the thumbnail to show only the top 60-70% of the image.
 */
@Composable
fun ThumbnailImageView(
    tab: TabSessionState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailLoader = remember { ThumbnailLoader(context.components.thumbnailStorage) }
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    
    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        var showPlaceholder by remember { mutableStateOf(true) }
        
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { imageView ->
                imageView.contentDescription = tab.content.title.ifEmpty { tab.content.url }
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
                            
                            imageView.drawable?.let { drawable ->
                                val drawableWidth = drawable.intrinsicWidth
                                val drawableHeight = drawable.intrinsicHeight
                                
                                if (drawableWidth > 0 && drawableHeight > 0) {
                                    val matrix = android.graphics.Matrix()
                                    
                                    val scale = maxOf(
                                        width.toFloat() / drawableWidth,
                                        height.toFloat() / drawableHeight
                                    )
                                    
                                    val scaledHeight = drawableHeight * scale
                                    val offsetY = -(scaledHeight * 0.0f)
                                    
                                    matrix.setScale(scale, scale)
                                    matrix.postTranslate(
                                        (width - drawableWidth * scale) / 2f,
                                        offsetY
                                    )
                                    
                                    imageView.scaleType = ImageView.ScaleType.MATRIX
                                    imageView.imageMatrix = matrix
                                }
                            }
                            showPlaceholder = false
                        } catch (e: Exception) {
                            showPlaceholder = true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
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
