package com.prirai.android.nira.settings.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.HomepageBackgroundChoice
import com.prirai.android.nira.theme.applyAppTheme


class CustomizationSettingsFragment : BaseSettingsFragment() {

    private lateinit var getBackgroundImageUri: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getBackgroundImageUri =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    // Handle the returned Uri
                    if (uri != null) {
                        requireContext().contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        UserPreferences(requireContext()).homepageBackgroundUrl = uri.toString()
                        Toast.makeText(
                            context,
                            requireContext().resources.getText(R.string.successful),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        UserPreferences(requireContext()).homepageBackgroundChoice =
                            HomepageBackgroundChoice.NONE.ordinal
                        Toast.makeText(
                            context,
                            requireContext().resources.getText(R.string.failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_customization)

        // Dynamic colors preference
        switchPreference(
            preference = "dynamic_colors",
            isChecked = UserPreferences(requireContext()).dynamicColors,
            onCheckChange = {
                UserPreferences(requireContext()).dynamicColors = it
                // Apply dynamic colors immediately
                if (it && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(requireActivity().application)
                }
                // Recreate activity to apply changes
                requireActivity().recreate()
            }
        )

        // AMOLED mode preference
        switchPreference(
            preference = "amoled_mode",
            isChecked = UserPreferences(requireContext()).amoledMode,
            onCheckChange = {
                UserPreferences(requireContext()).amoledMode = it
                // Apply AMOLED mode immediately by updating system bars and recreating
                com.prirai.android.nira.theme.ThemeManager.applySystemBarsTheme(requireActivity(), false)
                requireActivity().recreate()
            }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_move_navbar),
            isChecked = !UserPreferences(requireContext()).shouldUseBottomToolbar,
            onCheckChange = {
                UserPreferences(requireContext()).shouldUseBottomToolbar = !it
            }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_swipe_refresh),
            isChecked = UserPreferences(requireContext()).swipeToRefresh,
            onCheckChange = {
                UserPreferences(requireContext()).swipeToRefresh = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )


        switchPreference(
            preference = requireContext().resources.getString(R.string.key_show_protocol),
            isChecked = UserPreferences(requireContext()).showUrlProtocol,
            onCheckChange = {
                UserPreferences(requireContext()).showUrlProtocol = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )


        switchPreference(
            preference = requireContext().resources.getString(R.string.key_shortcuts_visible),
            isChecked = UserPreferences(requireContext()).showShortcuts,
            onCheckChange = {
                UserPreferences(requireContext()).showShortcuts = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_stack_from_bottom),
            isChecked = UserPreferences(requireContext()).stackFromBottom,
            onCheckChange = {
                UserPreferences(requireContext()).stackFromBottom = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_bar_addon_list),
            onClick = { pickBarAddonList() }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_load_shortcut_icons),
            isChecked = UserPreferences(requireContext()).loadShortcutIcons,
            onCheckChange = {
                UserPreferences(requireContext()).loadShortcutIcons = it
            }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_app_theme_type),
            onClick = { pickAppTheme() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_web_theme_type),
            onClick = { pickWebTheme() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_homepage_background_image),
            onClick = { pickHomepageBackground() },
            summary = resources.getStringArray(R.array.homepage_background_image_types)[UserPreferences(
                requireContext()
            ).homepageBackgroundChoice]
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_auto_font_size),
            isChecked = UserPreferences(requireContext()).autoFontSize,
            onCheckChange = {
                UserPreferences(requireContext()).autoFontSize = it
                preferenceScreen.findPreference<SeekBarPreference>(resources.getString(R.string.key_font_size_factor))!!.isEnabled =
                    !it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        seekbarPreference(
            preference = requireContext().resources.getString(R.string.key_font_size_factor),
            isEnabled = !UserPreferences(requireContext()).autoFontSize,
            onStateChanged = {
                UserPreferences(requireContext()).fontSizeFactor = it.toFloat() / 100
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        clickablePreference(
            preference = "toolbar_icon_size",
            onClick = { showIconSizeDialog() }
        )

        clickablePreference(
            preference = "interface_font_scale",
            onClick = { showFontScaleDialog() }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_show_contextual_toolbar),
            isChecked = UserPreferences(requireContext()).showContextualToolbar,
            onCheckChange = {
                UserPreferences(requireContext()).showContextualToolbar = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )



    }

    private fun pickBarAddonList() {
        val context = requireContext()
        val userPreferences = UserPreferences(context)

        val allAddons = context.components.store.state.extensions.filter { it.value.enabled }
            .filter { it.value.browserAction != null || it.value.pageAction != null }

        // Get currently allowed add-on IDs
        val allowedAddonIds =
            if (UserPreferences(requireContext()).showAddonsInBar) {
                allAddons.map { it.value.id }
            } else userPreferences.barAddonsList.split(",").filter { it.isNotEmpty() }

        // Prepare the list for the dialog
        val addonNames = allAddons.map { it.value.name }
        val checkedItems = allAddons.map { allowedAddonIds.contains(it.value.id) }.toBooleanArray()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.bar_addon_list)
            .setMultiChoiceItems(addonNames.toTypedArray(), checkedItems) { _, _, _ ->
                // We'll handle the selection when the user clicks OK
            }
            .setPositiveButton(R.string.mozac_feature_prompts_ok) { _, _ ->
                if (UserPreferences(requireContext()).showAddonsInBar) {
                    UserPreferences(requireContext()).showAddonsInBar = false
                }

                // Save the selected add-ons
                val selectedAddonIds =
                    allAddons
                        .map { it.value }
                        .filterIndexed { index, _ -> checkedItems[index] }
                        .joinToString(",") { it.id }

                userPreferences.barAddonsList = selectedAddonIds

                Toast.makeText(
                    context,
                    R.string.app_restart,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()


    }

    private fun pickHomepageBackground() {
        val startingChoice = UserPreferences(requireContext()).homepageBackgroundChoice
        val checkedItem = UserPreferences(requireContext()).homepageBackgroundChoice

        val dialogView = layoutInflater.inflate(R.layout.dialog_homepage_background_picker, null)
        val noneCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.noneCard)
        val urlCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.urlCard)
        val galleryCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.galleryCard)
        
        // Set initial checked state
        when (checkedItem) {
            HomepageBackgroundChoice.NONE.ordinal -> noneCard.isChecked = true
            HomepageBackgroundChoice.URL.ordinal -> urlCard.isChecked = true
            HomepageBackgroundChoice.GALLERY.ordinal -> galleryCard.isChecked = true
        }
        
        var selectedChoice = checkedItem
        
        noneCard.setOnClickListener {
            noneCard.isChecked = true
            urlCard.isChecked = false
            galleryCard.isChecked = false
            selectedChoice = HomepageBackgroundChoice.NONE.ordinal
        }
        
        urlCard.setOnClickListener {
            noneCard.isChecked = false
            urlCard.isChecked = true
            galleryCard.isChecked = false
            selectedChoice = HomepageBackgroundChoice.URL.ordinal
        }
        
        galleryCard.setOnClickListener {
            noneCard.isChecked = false
            urlCard.isChecked = false
            galleryCard.isChecked = true
            selectedChoice = HomepageBackgroundChoice.GALLERY.ordinal
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getString(R.string.homepage_background_image))
            .setView(dialogView)
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
                UserPreferences(requireContext()).homepageBackgroundChoice = startingChoice
            }
            .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                UserPreferences(requireContext()).homepageBackgroundChoice = selectedChoice
                val singleItems = resources.getStringArray(R.array.homepage_background_image_types)
                preferenceScreen.findPreference<Preference>(requireContext().resources.getString(R.string.key_homepage_background_image))!!.summary =
                    singleItems[selectedChoice]
                    
                when (selectedChoice) {
                    HomepageBackgroundChoice.URL.ordinal -> {
                        showUrlInputDialog()
                    }
                    HomepageBackgroundChoice.GALLERY.ordinal -> {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        }
                        getBackgroundImageUri.launch(intent)
                    }
                }
            }
            .show()
    }
    
    private fun showUrlInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edittext, null)
        val inputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout)
        val editText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_text)
        
        inputLayout.hint = resources.getString(R.string.url)
        editText.setText(UserPreferences(requireContext()).homepageBackgroundUrl)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getString(R.string.homepage_background_image))
            .setView(dialogView)
            .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                if (editText.text.toString().isNotEmpty()) {
                    UserPreferences(requireContext()).homepageBackgroundUrl = editText.text.toString()
                    Toast.makeText(
                        context,
                        requireContext().resources.getText(R.string.successful),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.NONE.ordinal
                    Toast.makeText(
                        context,
                        requireContext().resources.getText(R.string.failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
                UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.NONE.ordinal
            }
            .show()
    }

    private fun pickAppTheme() {
        val startingChoice = UserPreferences(requireContext()).appThemeChoice
        val checkedItem = UserPreferences(requireContext()).appThemeChoice

        val dialogView = layoutInflater.inflate(R.layout.dialog_theme_picker, null)
        val lightCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.lightThemeCard)
        val darkCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.darkThemeCard)
        val systemCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.systemThemeCard)
        
        // Set initial checked state
        when (checkedItem) {
            0 -> lightCard.isChecked = true
            1 -> darkCard.isChecked = true
            2 -> systemCard.isChecked = true
        }
        
        var selectedChoice = checkedItem
        
        lightCard.setOnClickListener {
            lightCard.isChecked = true
            darkCard.isChecked = false
            systemCard.isChecked = false
            selectedChoice = 0
        }
        
        darkCard.setOnClickListener {
            lightCard.isChecked = false
            darkCard.isChecked = true
            systemCard.isChecked = false
            selectedChoice = 1
        }
        
        systemCard.setOnClickListener {
            lightCard.isChecked = false
            darkCard.isChecked = false
            systemCard.isChecked = true
            selectedChoice = 2
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getString(R.string.theme))
            .setView(dialogView)
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
                UserPreferences(requireContext()).appThemeChoice = startingChoice
            }
            .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                UserPreferences(requireContext()).appThemeChoice = selectedChoice
                applyAppTheme(selectedChoice)
                requireActivity().recreate()
            }
            .show()
    }

    private fun showIconSizeDialog() {
        val userPreferences = UserPreferences(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_size_picker, null)
        val slider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.sizeSlider)
        val previewIcon = dialogView.findViewById<android.widget.ImageView>(R.id.previewIcon)
        val previewText = dialogView.findViewById<android.widget.TextView>(R.id.previewText)
        
        slider.value = userPreferences.toolbarIconSize
        slider.valueFrom = 0.8f
        slider.valueTo = 1.5f
        slider.stepSize = 0.1f
        
        previewText.text = getString(R.string.toolbar_icon_size)
        updateIconPreview(previewIcon, userPreferences.toolbarIconSize)
        
        slider.addOnChangeListener { _, value, _ ->
            updateIconPreview(previewIcon, value)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.toolbar_icon_size)
            .setView(dialogView)
            .setPositiveButton(R.string.mozac_feature_prompts_ok) { _, _ ->
                userPreferences.toolbarIconSize = slider.value
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showFontScaleDialog() {
        val userPreferences = UserPreferences(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_size_picker, null)
        val slider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.sizeSlider)
        val previewIcon = dialogView.findViewById<android.widget.ImageView>(R.id.previewIcon)
        val previewText = dialogView.findViewById<android.widget.TextView>(R.id.previewText)
        
        slider.value = userPreferences.interfaceFontScale
        slider.valueFrom = 0.8f
        slider.valueTo = 1.3f
        slider.stepSize = 0.1f
        
        previewIcon.visibility = android.view.View.GONE
        previewText.text = getString(R.string.interface_font_scale_preview)
        updateFontPreview(previewText, userPreferences.interfaceFontScale)
        
        slider.addOnChangeListener { _, value, _ ->
            updateFontPreview(previewText, value)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.interface_font_scale)
            .setView(dialogView)
            .setPositiveButton(R.string.mozac_feature_prompts_ok) { _, _ ->
                userPreferences.interfaceFontScale = slider.value
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun updateIconPreview(icon: android.widget.ImageView, scale: Float) {
        val baseSize = (48 * resources.displayMetrics.density).toInt()
        val scaledSize = (baseSize * scale).toInt()
        val layoutParams = icon.layoutParams
        layoutParams.width = scaledSize
        layoutParams.height = scaledSize
        icon.layoutParams = layoutParams
    }
    
    private fun updateFontPreview(textView: android.widget.TextView, scale: Float) {
        val baseSize = 16f
        textView.textSize = baseSize * scale
    }

    private fun pickWebTheme() {
        val startingChoice = UserPreferences(requireContext()).webThemeChoice
        val checkedItem = UserPreferences(requireContext()).webThemeChoice

        val dialogView = layoutInflater.inflate(R.layout.dialog_web_theme_picker, null)
        val lightCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.lightThemeCard)
        val darkCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.darkThemeCard)
        val systemCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.systemThemeCard)
        
        // Set initial checked state
        when (checkedItem) {
            0 -> lightCard.isChecked = true
            1 -> darkCard.isChecked = true
            2 -> systemCard.isChecked = true
        }
        
        var selectedChoice = checkedItem
        
        lightCard.setOnClickListener {
            lightCard.isChecked = true
            darkCard.isChecked = false
            systemCard.isChecked = false
            selectedChoice = 0
        }
        
        darkCard.setOnClickListener {
            lightCard.isChecked = false
            darkCard.isChecked = true
            systemCard.isChecked = false
            selectedChoice = 1
        }
        
        systemCard.setOnClickListener {
            lightCard.isChecked = false
            darkCard.isChecked = false
            systemCard.isChecked = true
            selectedChoice = 2
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getString(R.string.web_theme))
            .setView(dialogView)
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
                UserPreferences(requireContext()).webThemeChoice = startingChoice
            }
            .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                UserPreferences(requireContext()).webThemeChoice = selectedChoice
            }
            .show()
    }

}
