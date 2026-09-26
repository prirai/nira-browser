package com.prirai.android.nira.webapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.manifest.WebAppManifest
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper for installing PWAs as shortcuts that launch in [WebAppActivity].
 *
 * Nira keeps its own profile-scoped Room list of installed PWAs in
 * [WebAppManager] because upstream `feature-pwa`'s `WebAppUseCases` has no
 * concept of Nira's per-profile isolation and always pins shortcuts pointing
 * at upstream's `WebAppLauncherActivity` (not Nira's fullscreen
 * [WebAppActivity]). We do, however, hand the manifest to
 * `context.components.webAppManifestStorage` so that upstream's
 * [mozilla.components.feature.pwa.intent.WebAppInterceptor] (wired in
 * `Components.kt`) can recognise the PWA scope and route matching URLs into
 * the standalone launcher.
 */
object WebAppInstaller {

    /**
     * Install a PWA that opens in fullscreen [WebAppActivity].
     * @param profileId Profile to associate with this web app
     * @return true if installed successfully, false if duplicate detected
     */
    suspend fun installPwa(
        context: Context,
        session: SessionState,
        manifest: WebAppManifest?,
        icon: Bitmap?,
        profileId: String = "default"
    ): Boolean = withContext(Dispatchers.IO) {
        // Extract base URL instead of using current page URL
        val baseUrl = WebAppManager.getBaseUrl(session.content.url)
        val title = (manifest?.name ?: manifest?.shortName ?: session.content.title).takeIf { it.isNotBlank() }
            ?: baseUrl

        // Use the process-wide WebAppManager via `context.components`. The old
        // `Components(context).webAppManager` idiom would spin up a fresh
        // Components graph on every install call, leaking a duplicate Room
        // instance and bypassing the `by lazy` singleton semantics.
        val webAppManager = context.components.webAppManager

        // Check if already installed with same URL and profile
        if (webAppManager.webAppExists(baseUrl, profileId)) {
            return@withContext false
        }

        // Store PWA in database using WebAppManager
        webAppManager.installWebApp(
            url = baseUrl,
            name = title,
            manifestUrl = manifest?.startUrl?.toString(),
            icon = icon,
            themeColor = manifest?.themeColor?.toString(),
            backgroundColor = manifest?.backgroundColor?.toString(),
            profileId = profileId
        )

        // Hand the manifest to upstream ManifestStorage so `WebAppInterceptor`
        // (installed in Components.kt) can match subsequent navigations that
        // fall inside this PWA's scope and hand them to the standalone
        // launcher. This is the single upstream integration point we retain -
        // everything else (per-profile list, settings UI, uninstall flow)
        // stays in Nira's Room DB.
        if (manifest != null) {
            try {
                context.components.webAppManifestStorage.saveManifest(manifest)
            } catch (t: Throwable) {
                // Non-fatal: the PWA still works via our WebAppActivity even if
                // the upstream manifest cache write fails.
            }
        }

        // Create shortcut intent
        withContext(Dispatchers.Main) {
            createShortcut(context, baseUrl, title, icon)
        }

        true
    }

    /**
     * Create home screen shortcut
     */
    private fun createShortcut(context: Context, url: String, title: String, icon: Bitmap?) {
        val intent = Intent(context, WebAppActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = url.toUri()
            putExtra(WebAppActivity.EXTRA_WEB_APP_URL, url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or 
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }

        val shortcut = ShortcutInfoCompat.Builder(context, url)
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
        val title = session.content.title?.takeIf { it.isNotBlank() } ?: url
        
        val intent = Intent(context, com.prirai.android.nira.BrowserActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = url.toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
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
        
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
