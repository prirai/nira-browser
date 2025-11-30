package com.prirai.android.nira.webapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.manifest.WebAppManifest

/**
 * Helper for installing PWAs as shortcuts that launch in WebAppActivity
 */
object WebAppInstaller {

    /**
     * Install a PWA that opens in fullscreen WebAppActivity
     */
    fun installPwa(
        context: Context,
        session: SessionState,
        manifest: WebAppManifest?,
        icon: Bitmap?
    ) {
        val url = session.content.url
        val title = manifest?.name ?: manifest?.shortName ?: session.content.title ?: url
        
        // Create intent that launches WebAppActivity
        val intent = Intent(context, WebAppActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
            putExtra(WebAppActivity.EXTRA_WEB_APP_URL, url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        // Create shortcut
        val shortcut = ShortcutInfoCompat.Builder(context, url)
            .setShortLabel(title)
            .setLongLabel(title)
            .setIntent(intent)
            .apply {
                if (icon != null) {
                    setIcon(IconCompat.createWithBitmap(icon))
                } else {
                    // Use app icon as fallback
                    setIcon(IconCompat.createWithResource(context, com.prirai.android.nira.R.mipmap.ic_launcher))
                }
            }
            .build()
        
        // Add shortcut to launcher
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
    
    /**
     * Add a regular shortcut that opens in main browser
     */
    fun addToHomescreen(
        context: Context,
        session: SessionState,
        icon: Bitmap?
    ) {
        val url = session.content.url
        val title = session.content.title ?: url
        
        // Create intent that launches main BrowserActivity
        val intent = Intent(context, com.prirai.android.nira.BrowserActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        // Create shortcut
        val shortcut = ShortcutInfoCompat.Builder(context, "shortcut_$url")
            .setShortLabel(title)
            .setLongLabel(title)
            .setIntent(intent)
            .apply {
                if (icon != null) {
                    setIcon(IconCompat.createWithBitmap(icon))
                } else {
                    setIcon(IconCompat.createWithResource(context, com.prirai.android.nira.R.mipmap.ic_launcher))
                }
            }
            .build()
        
        // Add shortcut to launcher
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
