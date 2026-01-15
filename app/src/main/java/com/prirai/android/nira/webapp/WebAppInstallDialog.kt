package com.prirai.android.nira.webapp

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.databinding.DialogWebappInstallBinding
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.launch

/**
 * Material 3 dialog for installing web apps with profile selection
 */
class WebAppInstallDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val appName: String,
    private val appUrl: String,
    private val appDescription: String? = null,
    private val defaultProfileId: String? = null,
    private val onInstall: (profileId: String) -> Unit
) {

    private var dialog: AlertDialog? = null
    private var selectedProfile: BrowserProfile? = null

    fun show() {
        val binding = DialogWebappInstallBinding.inflate(LayoutInflater.from(context))

        // Setup app info
        binding.appTitle.text = appName

        if (appDescription != null) {
            binding.appDescription.text = appDescription
        } else {
            binding.appDescription.text = context.getString(
                R.string.install_web_app_profile_message
            )
        }

        // Load and set icon asynchronously using the same method as Discover tab
        lifecycleOwner.lifecycleScope.launch {
            val icon = loadFavicon(appUrl)
            if (icon != null) {
                binding.appIcon.setImageBitmap(icon)
                binding.appIcon.imageTintList = null
            } else {
                binding.appIcon.setImageResource(R.drawable.ic_language)
            }
        }

        // Setup profile dropdown
        val profileManager = ProfileManager.getInstance(context)
        val profiles = profileManager.getAllProfiles()
        val profileNames = profiles.map { it.name }

        // Find default profile
        val defaultProfile = if (defaultProfileId != null) {
            profiles.find { it.id == defaultProfileId } ?: profiles.first()
        } else {
            profileManager.getActiveProfile()
        }
        selectedProfile = defaultProfile

        // Setup dropdown adapter
        val adapter = ArrayAdapter(
            context,
            R.layout.dropdown_menu_popup_item,
            profileNames
        )

        val dropdown = binding.profileDropdown
        dropdown.setAdapter(adapter)
        dropdown.setText(defaultProfile.name, false)

        // Handle profile selection
        dropdown.setOnItemClickListener { _, _, position, _ ->
            selectedProfile = profiles[position]
        }

        // Setup buttons
        binding.cancelButton.setOnClickListener {
            dialog?.dismiss()
        }

        binding.installButton.setOnClickListener {
            selectedProfile?.let { profile ->
                onInstall(profile.id)
                dialog?.dismiss()
            }
        }

        // Create and show dialog
        dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        // Apply theme to dialog
        dialog?.window?.let { window ->
            val userPreferences = UserPreferences(context)
            val isDarkTheme = ThemeManager.isDarkMode(context)

            // Apply AMOLED or themed background
            val bgColor = if (userPreferences.amoledMode && isDarkTheme) {
                android.graphics.Color.BLACK
            } else {
                context.getColorFromAttr(android.R.attr.colorBackground)
            }

            window.decorView.setBackgroundColor(bgColor)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }

        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    /**
     * Load favicon using Mozilla Components BrowserIcons
     */
    private suspend fun loadFavicon(url: String): Bitmap? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Try cache first
                com.prirai.android.nira.utils.FaviconCache.getInstance(context).loadFavicon(url)
                    ?.let { return@withContext it }

                // Use Mozilla Components BrowserIcons
                val iconRequest = mozilla.components.browser.icons.IconRequest(
                    url = url,
                    size = mozilla.components.browser.icons.IconRequest.Size.DEFAULT,
                    resources = listOf(
                        mozilla.components.browser.icons.IconRequest.Resource(
                            url = url,
                            type = mozilla.components.browser.icons.IconRequest.Resource.Type.FAVICON
                        )
                    )
                )
                val icon = com.prirai.android.nira.components.Components(context).icons.loadIcon(iconRequest).await()
                if (icon.bitmap != null) {
                    // Save to cache for future use
                    com.prirai.android.nira.utils.FaviconCache.getInstance(context).saveFavicon(url, icon.bitmap)
                    icon.bitmap
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Helper extension to get color from theme attribute
 */
private fun Context.getColorFromAttr(attr: Int): Int {
    val typedValue = android.util.TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}
