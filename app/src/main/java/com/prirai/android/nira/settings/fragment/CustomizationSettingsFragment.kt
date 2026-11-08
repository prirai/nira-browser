@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.settings.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.HomepageBackgroundChoice
import com.prirai.android.nira.theme.applyAppTheme
import com.prirai.android.nira.ui.components.NiraAlertDialog
import kotlin.math.roundToInt


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
                // Show toast to restart app
                Toast.makeText(
                    requireContext(),
                    getString(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
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

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_show_tab_group_bar),
            isChecked = UserPreferences(requireContext()).showTabGroupBar,
            onCheckChange = {
                UserPreferences(requireContext()).showTabGroupBar = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

    }

    private fun pickHomepageBackground() {
        val items = arrayOf(
            getString(R.string.none),
            getString(R.string.homepage_background_gallery),
            getString(R.string.homepage_background_url)
        )

        showNiraDialog { onDismiss ->
            HomepageBackgroundDialog(
                title = getString(R.string.homepage_background_image),
                items = items,
                onItem = { which ->
                    when (which) {
                        0 -> {
                            // None - immediate action
                            UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.NONE.ordinal
                            updateHomepageBackgroundSummary()
                            Toast.makeText(
                                context,
                                requireContext().resources.getText(R.string.successful),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        1 -> {
                            // Choose from gallery - launch picker immediately
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "image/*"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            }
                            UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.GALLERY.ordinal
                            getBackgroundImageUri.launch(intent)
                        }
                        2 -> {
                            // Enter URL - show input dialog immediately
                            showUrlInputDialog()
                        }
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun updateHomepageBackgroundSummary() {
        val singleItems = resources.getStringArray(R.array.homepage_background_image_types)
        val currentChoice = UserPreferences(requireContext()).homepageBackgroundChoice
        preferenceScreen.findPreference<Preference>(
            requireContext().resources.getString(R.string.key_homepage_background_image)
        )?.summary = singleItems[currentChoice]
    }

    private fun showUrlInputDialog() {
        showNiraDialog { onDismiss ->
            UrlInputDialog(
                initialUrl = UserPreferences(requireContext()).homepageBackgroundUrl,
                onConfirm = { url ->
                    if (url.isNotEmpty()) {
                        UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.URL.ordinal
                        UserPreferences(requireContext()).homepageBackgroundUrl = url
                        updateHomepageBackgroundSummary()
                        Toast.makeText(
                            context,
                            requireContext().resources.getText(R.string.successful),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.NONE.ordinal
                        updateHomepageBackgroundSummary()
                        Toast.makeText(
                            context,
                            requireContext().resources.getText(R.string.failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun pickAppTheme() {
        val items = resources.getStringArray(R.array.theme_types)
        val initialChoice = UserPreferences(requireContext()).appThemeChoice

        showNiraDialog { onDismiss ->
            ThemeChoiceDialog(
                title = getString(R.string.theme),
                items = items,
                initialChoice = initialChoice,
                onConfirm = { choice ->
                    UserPreferences(requireContext()).appThemeChoice = choice
                    applyAppTheme(choice)
                    requireActivity().recreate()
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun showIconSizeDialog() {
        val userPreferences = UserPreferences(requireContext())
        showNiraDialog { onDismiss ->
            SizePickerDialog(
                title = getString(R.string.toolbar_icon_size),
                previewLabel = getString(R.string.toolbar_icon_size),
                initialValue = userPreferences.toolbarIconSize,
                valueRange = 0.8f..1.5f,
                steps = 6,
                showIconPreview = true,
                onConfirm = { value ->
                    userPreferences.toolbarIconSize = value
                    Toast.makeText(
                        context,
                        requireContext().resources.getText(R.string.app_restart),
                        Toast.LENGTH_LONG
                    ).show()
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun showFontScaleDialog() {
        val userPreferences = UserPreferences(requireContext())
        showNiraDialog { onDismiss ->
            SizePickerDialog(
                title = getString(R.string.interface_font_scale),
                previewLabel = getString(R.string.interface_font_scale_preview),
                initialValue = userPreferences.interfaceFontScale,
                valueRange = 0.8f..1.3f,
                steps = 4,
                showIconPreview = false,
                onConfirm = { value ->
                    userPreferences.interfaceFontScale = value
                    Toast.makeText(
                        context,
                        requireContext().resources.getText(R.string.app_restart),
                        Toast.LENGTH_LONG
                    ).show()
                },
                onDismiss = onDismiss,
            )
        }
    }

    private fun pickWebTheme() {
        val items = resources.getStringArray(R.array.theme_types)
        val initialChoice = UserPreferences(requireContext()).webThemeChoice

        showNiraDialog { onDismiss ->
            ThemeChoiceDialog(
                title = getString(R.string.web_theme),
                items = items,
                initialChoice = initialChoice,
                onConfirm = { choice ->
                    UserPreferences(requireContext()).webThemeChoice = choice
                },
                onDismiss = onDismiss,
            )
        }
    }

}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun HomepageBackgroundDialog(
    title: String,
    items: Array<String>,
    onItem: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onItem(index)
                            }
                            .padding(vertical = 14.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLargeEmphasized,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun UrlInputDialog(
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.homepage_background_image),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm(url)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.mozac_feature_prompts_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ThemeChoiceDialog(
    title: String,
    items: Array<String>,
    initialChoice: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(initialChoice) }
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, label ->
                    ChoiceRow(
                        label = label,
                        selected = index == selected,
                        onClick = { selected = index },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm(selected)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.mozac_feature_prompts_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * The slider picker behind both "Toolbar Icon Size" and "Interface Font Size":
 * a live preview (a primary-tinted icon or a text sample) above a stepped
 * [Slider] bracketed by the "Small"/"Large" labels.
 */
@Composable
private fun SizePickerDialog(
    title: String,
    previewLabel: String,
    initialValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    showIconPreview: Boolean,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (showIconPreview) {
                    Icon(
                        painter = painterResource(R.drawable.ic_palette),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp * value),
                    )
                    Text(
                        text = previewLabel,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                } else {
                    Text(
                        text = previewLabel,
                        fontSize = (16 * value).sp,
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                    )
                }
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = valueRange,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.small),
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.large),
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm((value * 10).roundToInt() / 10f)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.mozac_feature_prompts_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
