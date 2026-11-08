@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.SearchEngineList
import com.prirai.android.nira.browser.SearchEnginePreferences
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.utils.Utils
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.DefaultDesktopModeAction


class GeneralSettingsFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_general)

        switchPreference(
                preference = requireContext().resources.getString(R.string.key_javascript_enabled),
                isChecked = UserPreferences(requireContext()).javaScriptEnabled,
                onCheckChange = {
                    UserPreferences(requireContext()).javaScriptEnabled = it
                    Toast.makeText(context, requireContext().resources.getText(R.string.app_restart), Toast.LENGTH_LONG).show()
                }
        )

        clickablePreference(
                preference = resources.getString(R.string.key_search_engine),
                onClick = { pickSearchEngine(private = false) }
        )

        clickablePreference(
                preference = resources.getString(R.string.key_private_search_engine),
                onClick = { pickSearchEngine(private = true) }
        )

        val desktopDefault = if (UserPreferences(requireContext()).hasDesktopModeDefault()) {
            UserPreferences(requireContext()).desktopModeDefault
        } else {
            Utils.isTablet(requireContext())
        }
        switchPreference(
            preference = resources.getString(R.string.key_desktop_mode_default),
            isChecked = desktopDefault,
            onCheckChange = {
                UserPreferences(requireContext()).desktopModeDefault = it
                requireContext().components.store.dispatch(
                    DefaultDesktopModeAction.DesktopModeUpdated(it)
                )
            }
        )

        switchPreference(
                preference = resources.getString(R.string.key_translations_enabled),
                isChecked = UserPreferences(requireContext()).translationsEnabled,
                onCheckChange = {
                    UserPreferences(requireContext()).translationsEnabled = it
                }
        )

        // "Background audio on YouTube" global toggle used to live here;
        // it has been replaced by a per-site "Background playback"
        // permission managed from the Connection Info -> Site Permissions
        // sheet. See BackgroundPlaybackAllowlist for storage, and the
        // migration in BrowserApp.initializeAfterFirstFrame that seeds
        // the allow-list with YouTube origins when the legacy global
        // pref was previously enabled.

        switchPreference(
                preference = requireContext().resources.getString(R.string.key_search_suggestions_enabled),
                isChecked = UserPreferences(requireContext()).searchSuggestionsEnabled,
                onCheckChange = {
                    UserPreferences(requireContext()).searchSuggestionsEnabled = it
                    Toast.makeText(context, requireContext().resources.getText(R.string.app_restart), Toast.LENGTH_LONG).show()
                }
        )

        seekbarPreference(
            preference = requireContext().resources.getString(R.string.key_search_suggestion_count)
        ) {
            UserPreferences(requireContext()).searchSuggestionCount = it.coerceIn(1, 10)
        }?.apply {
            value = UserPreferences(requireContext()).searchSuggestionCount.coerceIn(1, 10)
        }

        switchPreference(
                preference = requireContext().resources.getString(R.string.key_safe_browsing),
                isChecked = UserPreferences(requireContext()).safeBrowsing,
                onCheckChange = {
                    UserPreferences(requireContext()).safeBrowsing = it
                    Toast.makeText(context, requireContext().resources.getText(R.string.app_restart), Toast.LENGTH_LONG).show()
                }
        )

        switchPreference(
                preference = requireContext().resources.getString(R.string.key_tracking_protection),
                isChecked = UserPreferences(requireContext()).trackingProtection,
                onCheckChange = {
                    UserPreferences(requireContext()).trackingProtection = it
                    Toast.makeText(context, requireContext().resources.getText(R.string.app_restart), Toast.LENGTH_LONG).show()
                }
        )

        clickablePreference(
                preference = resources.getString(R.string.key_homepage_type),
                onClick = { pickHomepage() }
        )

    }

    private fun pickHomepage() {
        val prefs = UserPreferences(requireContext())
        val startingChoice = prefs.homepageType
        val choices = resources.getStringArray(R.array.homepage_types)

        showNiraDialog { onDismiss ->
            HomepageTypeDialog(
                choices = choices,
                initialChoice = startingChoice,
                onChoice = { prefs.homepageType = it },
                onConfirm = {
                    Toast.makeText(context, requireContext().resources.getText(R.string.app_restart), Toast.LENGTH_LONG).show()
                },
                onCancel = { prefs.homepageType = startingChoice },
                onDismiss = onDismiss,
            )
        }
    }

    private fun pickSearchEngine(private: Boolean) {
        val prefs = UserPreferences(requireContext())
        val engines = SearchEngineList(requireContext()).getEngines()
        val labels = engines.map { it.name }.toMutableList()
        if (private) {
            labels.add(0, getString(R.string.use_default_search_engine))
        } else {
            labels.add(getString(R.string.custom))
        }

        val initialSelection = if (private) {
            if (prefs.privateSearchEngineChoice >= 0) prefs.privateSearchEngineChoice + 1 else 0
        } else if (prefs.customSearchEngine) {
            labels.lastIndex
        } else {
            prefs.searchEngineChoice
        }

        showNiraDialog { onDismiss ->
            SearchEngineDialog(
                title = if (private) getString(R.string.private_search_engine) else getString(R.string.search_engine),
                labels = labels.toTypedArray(),
                initialSelection = initialSelection,
                onChoice = { which ->
                    if (private) {
                        prefs.privateSearchEngineChoice = if (which == 0) -1 else which - 1
                    } else if (which == labels.lastIndex) {
                        onDismiss()
                        customSearchEngineDialog()
                    } else {
                        prefs.customSearchEngine = false
                        prefs.searchEngineChoice = which
                    }
                },
                onConfirm = {
                    viewLifecycleOwner.lifecycleScope.launch {
                        SearchEnginePreferences.apply(requireContext(), private = private)
                        if (!private && prefs.privateSearchEngineChoice < 0) {
                            requireContext().components.searchUseCases.clearPrivateSearchEngine()
                        }
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }

    fun customSearchEngineDialog() {
        val prefs = UserPreferences(requireContext())
        showNiraDialog { onDismiss ->
            CustomSearchEngineDialog(
                initialUrl = SearchEngineList.toUserFacingSearchUrl(prefs.customSearchEngineURL),
                onDismiss = onDismiss,
                onSave = { entered ->
                    if (SearchEngineList.isValidCustomSearchUrl(entered)) {
                        prefs.customSearchEngine = true
                        prefs.customSearchEngineURL = SearchEngineList.normalizeCustomSearchUrl(entered)
                        viewLifecycleOwner.lifecycleScope.launch {
                            SearchEnginePreferences.apply(requireContext(), private = false)
                        }
                        true
                    } else {
                        Toast.makeText(context, R.string.custom_search_engine_error, Toast.LENGTH_LONG).show()
                        false
                    }
                },
                onCancel = { prefs.customSearchEngine = false },
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
private fun HomepageTypeDialog(
    choices: Array<String>,
    initialChoice: Int,
    onChoice: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(initialChoice) }
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.homepage_type),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                choices.forEachIndexed { index, label ->
                    ChoiceRow(
                        label = label,
                        selected = index == selected,
                        onClick = {
                            selected = index
                            onChoice(index)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.mozac_feature_prompts_ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onCancel()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SearchEngineDialog(
    title: String,
    labels: Array<String>,
    initialSelection: Int,
    onChoice: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(initialSelection) }
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
                labels.forEachIndexed { index, label ->
                    ChoiceRow(
                        label = label,
                        selected = index == selected,
                        onClick = {
                            selected = index
                            onChoice(index)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
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
private fun CustomSearchEngineDialog(
    initialUrl: String,
    onSave: (String) -> Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.custom_search_engine),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.custom_search_engine_details))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (onSave(url)) onDismiss() },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.mozac_feature_prompts_ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onCancel()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
