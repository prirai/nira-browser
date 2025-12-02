package com.prirai.android.nira.webapp

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.prirai.android.nira.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.manifest.WebAppManifest

/**
 * Manager for enhanced PWA installation flow
 * Handles PWA detection, installation prompts, and user onboarding
 */
class WebAppInstallationManager(private val context: Context) {
    private val _installationState = MutableLiveData<InstallationState>()
    val installationState: LiveData<InstallationState> = _installationState

    private val _detectedPwas = MutableLiveData<List<DetectedPwa>>()
    val detectedPwas: LiveData<List<DetectedPwa>> = _detectedPwas

    private val webAppManager: WebAppManager by lazy {
        com.prirai.android.nira.components.Components(context).webAppManager
    }

    sealed class InstallationState {
        object Idle : InstallationState()
        object Detecting : InstallationState()
        data class DetectionComplete(val pwas: List<DetectedPwa>) : InstallationState()
        data class Installing(val pwa: DetectedPwa) : InstallationState()
        data class InstallationComplete(val pwa: WebAppEntity) : InstallationState()
        data class Error(val message: String) : InstallationState()
    }

    data class DetectedPwa(
        val url: String,
        val name: String,
        val manifest: WebAppManifest?,
        val icon: Bitmap?,
        val canInstall: Boolean,
        val installQuality: InstallQuality
    )

    enum class InstallQuality {
        EXCELLENT, GOOD, BASIC, POOR
    }

    /**
     * Start detecting PWAs on the current page
     */
    fun startDetection(session: SessionState) {
        _installationState.value = InstallationState.Detecting

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Simulate PWA detection
                val url = session.content.url
                val title = session.content.title

                // Check if this is a PWA-capable site
                val isPwaCapable = checkIfPwaCapable(url)
                val manifest = if (isPwaCapable) getMockManifest(url) else null
                val icon = getWebsiteIcon(url)

                val detectedPwa = DetectedPwa(
                    url = url,
                    name = title,
                    manifest = manifest,
                    icon = icon,
                    canInstall = isPwaCapable,
                    installQuality = determineInstallQuality(manifest)
                )

                withContext(Dispatchers.Main) {
                    _detectedPwas.value = listOf(detectedPwa)
                    _installationState.value = InstallationState.DetectionComplete(listOf(detectedPwa))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _installationState.value = InstallationState.Error("Failed to detect PWA: ${e.message}")
                }
            }
        }
    }

    /**
     * Install a detected PWA with enhanced flow
     */
    fun installPwa(detectedPwa: DetectedPwa, callback: (Result<WebAppEntity>) -> Unit) {
        _installationState.value = InstallationState.Installing(detectedPwa)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val webAppEntity = webAppManager.installWebApp(
                    url = detectedPwa.url,
                    name = detectedPwa.name,
                    manifestUrl = detectedPwa.manifest?.startUrl,
                    icon = detectedPwa.icon,
                    themeColor = detectedPwa.manifest?.themeColor?.toString(),
                    backgroundColor = detectedPwa.manifest?.backgroundColor?.toString()
                )

                // Get the installed entity
                val installedApp = webAppManager.getWebAppByUrl(detectedPwa.url)

                withContext(Dispatchers.Main) {
                    installedApp?.let {
                        _installationState.value = InstallationState.InstallationComplete(it)
                        callback(Result.success(it))
                    } ?: run {
                        _installationState.value = InstallationState.Error("Installation failed")
                        callback(Result.failure(Exception("Installation failed")))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _installationState.value = InstallationState.Error("Installation failed: ${e.message}")
                    callback(Result.failure(e))
                }
            }
        }
    }

    /**
     * Check if a website is PWA-capable
     */
    private fun checkIfPwaCapable(url: String): Boolean {
        // This would check for service worker, manifest, etc.
        // For now, we'll use a simple heuristic
        return url.contains("twitter.com") ||
               url.contains("facebook.com") ||
               url.contains("youtube.com") ||
               url.contains("example.com")
    }

    /**
     * Get mock manifest for demonstration
     */
    private fun getMockManifest(url: String): WebAppManifest? {
        // In a real implementation, this would parse the actual manifest
        return WebAppManifest(
            name = "Example PWA",
            shortName = "Example",
            startUrl = url,
            display = mozilla.components.concept.engine.manifest.WebAppManifest.DisplayMode.STANDALONE,
            themeColor = 0x4285F4,
            backgroundColor = 0xFFFFFF
        )
    }

    /**
     * Get website icon
     */
    private suspend fun getWebsiteIcon(url: String): Bitmap? {
        // This would use the browser's icon service
        return null // Placeholder
    }

    /**
     * Determine install quality based on manifest
     */
    private fun determineInstallQuality(manifest: WebAppManifest?): InstallQuality {
        return if (manifest != null) {
            if (manifest.name?.isNotEmpty() == true &&
                manifest.startUrl?.isNotEmpty() == true) {
                InstallQuality.EXCELLENT
            } else {
                InstallQuality.GOOD
            }
        } else {
            InstallQuality.BASIC
        }
    }

    /**
     * Get installation prompt message
     */
    fun getInstallationPrompt(pwa: DetectedPwa): String {
        return when (pwa.installQuality) {
            InstallQuality.EXCELLENT -> context.getString(R.string.pwa_install_excellent, pwa.name)
            InstallQuality.GOOD -> context.getString(R.string.pwa_install_good, pwa.name)
            InstallQuality.BASIC -> context.getString(R.string.pwa_install_basic, pwa.name)
            InstallQuality.POOR -> context.getString(R.string.pwa_install_poor, pwa.name)
        }
    }

    /**
     * Get PWA benefits description
     */
    fun getPwaBenefits(pwa: DetectedPwa): List<String> {
        val benefits = mutableListOf<String>()

        if (pwa.installQuality == InstallQuality.EXCELLENT || pwa.installQuality == InstallQuality.GOOD) {
            benefits.add(context.getString(R.string.pwa_benefit_offline))
            benefits.add(context.getString(R.string.pwa_benefit_notifications))
            benefits.add(context.getString(R.string.pwa_benefit_homescreen))
        }

        if (pwa.manifest?.themeColor != null) {
            benefits.add(context.getString(R.string.pwa_benefit_themed))
        }

        return benefits
    }

    /**
     * Reset installation state
     */
    fun reset() {
        _installationState.value = InstallationState.Idle
        _detectedPwas.value = emptyList()
    }
}