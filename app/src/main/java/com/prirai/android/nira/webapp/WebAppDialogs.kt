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
import com.prirai.android.nira.databinding.DialogWebappCloneBinding
import com.prirai.android.nira.databinding.DialogWebappProfileBinding
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.launch

/**
 * Material 3 dialog for cloning a web app with profile selection
 */
class WebAppCloneDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val webApp: WebAppEntity,
    private val onClone: (name: String, profileId: String) -> Unit
) {

    private var dialog: AlertDialog? = null
    private var selectedProfile: BrowserProfile? = null

    fun show() {
        val binding = DialogWebappCloneBinding.inflate(LayoutInflater.from(context))

        // Setup app info
        binding.appTitle.text = context.getString(R.string.clone_webapp_dialog_title)
        binding.appDescription.text = context.getString(R.string.clone_webapp_dialog_message, webApp.name)

        // Pre-fill with clone name
        binding.appNameInput.setText("${webApp.name} (Clone)")

        // Load and set icon asynchronously
        lifecycleOwner.lifecycleScope.launch {
            val icon = loadWebAppIcon(webApp)
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
        val defaultProfile = profileManager.getActiveProfile()
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

        binding.cloneButton.setOnClickListener {
            val newName = binding.appNameInput.text.toString().trim()
            if (newName.isNotEmpty()) {
                selectedProfile?.let { profile ->
                    onClone(newName, profile.id)
                    dialog?.dismiss()
                }
            } else {
                binding.appNameInput.error = context.getString(R.string.name_required)
            }
        }

        // Create and show dialog
        dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        // Apply theme to dialog
        applyThemeToDialog(dialog)

        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    private suspend fun loadWebAppIcon(webApp: WebAppEntity): Bitmap? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Try webapp's stored icon
                com.prirai.android.nira.components.Components(context).webAppManager
                    .loadIconFromFile(webApp.iconUrl) ?: run {
                    // Fallback to cache
                    com.prirai.android.nira.utils.FaviconCache.getInstance(context)
                        .loadFavicon(webApp.url)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun applyThemeToDialog(dialog: AlertDialog?) {
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
    }
}

/**
 * Material 3 dialog for associating a web app with a profile
 */
class WebAppProfileDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val webApp: WebAppEntity,
    private val onAssociate: (profileId: String) -> Unit
) {

    private var dialog: AlertDialog? = null
    private var selectedProfile: BrowserProfile? = null

    fun show() {
        val binding = DialogWebappProfileBinding.inflate(LayoutInflater.from(context))

        // Setup app info
        binding.appTitle.text = context.getString(R.string.associate_profile)
        binding.appDescription.text = context.getString(
            R.string.associate_profile_message,
            webApp.name
        )

        // Load and set icon asynchronously
        lifecycleOwner.lifecycleScope.launch {
            val icon = loadWebAppIcon(webApp)
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

        // Find current profile
        val currentProfile = profiles.find { it.id == webApp.profileId }
            ?: profileManager.getActiveProfile()
        selectedProfile = currentProfile

        // Setup dropdown adapter
        val adapter = ArrayAdapter(
            context,
            R.layout.dropdown_menu_popup_item,
            profileNames
        )

        val dropdown = binding.profileDropdown
        dropdown.setAdapter(adapter)
        dropdown.setText(currentProfile.name, false)

        // Handle profile selection
        dropdown.setOnItemClickListener { _, _, position, _ ->
            selectedProfile = profiles[position]
        }

        // Setup buttons
        binding.cancelButton.setOnClickListener {
            dialog?.dismiss()
        }

        binding.associateButton.setOnClickListener {
            selectedProfile?.let { profile ->
                onAssociate(profile.id)
                dialog?.dismiss()
            }
        }

        // Create and show dialog
        dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        // Apply theme to dialog
        applyThemeToDialog(dialog)

        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    private suspend fun loadWebAppIcon(webApp: WebAppEntity): Bitmap? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Try webapp's stored icon
                com.prirai.android.nira.components.Components(context).webAppManager
                    .loadIconFromFile(webApp.iconUrl) ?: run {
                    // Fallback to cache
                    com.prirai.android.nira.utils.FaviconCache.getInstance(context)
                        .loadFavicon(webApp.url)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun applyThemeToDialog(dialog: AlertDialog?) {
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
    }
}

/**
 * Material 3 confirmation dialog for uninstalling a web app
 */
class WebAppUninstallDialog(
    private val context: Context,
    private val webApp: WebAppEntity,
    private val onConfirm: () -> Unit
) {

    fun show() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.uninstall_web_app)
            .setMessage(context.getString(R.string.uninstall_web_app_message, webApp.name))
            .setPositiveButton(R.string.uninstall) { dialog, which ->
                onConfirm()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

/**
 * Material 3 confirmation dialog for clearing web app data
 */
class WebAppClearDataDialog(
    private val context: Context,
    private val webApp: WebAppEntity,
    private val onConfirm: () -> Unit
) {

    fun show() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.clear_data)
            .setMessage(context.getString(R.string.clear_data_message, webApp.name))
            .setPositiveButton(R.string.clear_data) { dialog, which ->
                onConfirm()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

/**
 * Helper extension to get color from theme attribute
 */
private fun Context.getColorFromAttr(attr: Int): Int {
    val typedArray = obtainStyledAttributes(intArrayOf(attr))
    val color = typedArray.getColor(0, 0)
    typedArray.recycle()
    return color
}
