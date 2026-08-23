package com.prirai.android.nira.settings.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.SearchEngineList
import com.prirai.android.nira.browser.SearchEnginePreferences
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.HomepageChoice
import com.prirai.android.nira.utils.Utils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            Utils().isTablet(requireContext())
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

    private fun pickHomepage(){
        val startingChoice = UserPreferences(requireContext()).homepageType
        val singleItems = resources.getStringArray(R.array.homepage_types).toMutableList()
        val checkedItem = UserPreferences(requireContext()).homepageType

        MaterialAlertDialogBuilder(requireContext())
                .setTitle(resources.getString(R.string.homepage_type))
                .setNeutralButton(resources.getString(R.string.cancel)) { _, _ ->
                    UserPreferences(requireContext()).homepageType = startingChoice
                }
                .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                    Toast.makeText(context, requireContext().resources.getText(R.string.app_restart), Toast.LENGTH_LONG).show()
                }
                .setSingleChoiceItems(singleItems.toTypedArray(), checkedItem) { dialog, which ->
                    UserPreferences(requireContext()).homepageType = which
                }
                .show()
    }

    private fun pickSearchEngine(private: Boolean){
        val prefs = UserPreferences(requireContext())
        val engines = SearchEngineList(requireContext()).getEngines()
        val singleItems = engines.map { it.name }.toMutableList()
        if (private) {
            singleItems.add(0, getString(R.string.use_default_search_engine))
        } else {
            singleItems.add(getString(R.string.custom))
        }

        val checkedItem = if (private) {
            if (prefs.privateSearchEngineChoice >= 0) prefs.privateSearchEngineChoice + 1 else 0
        } else if (prefs.customSearchEngine) {
            singleItems.lastIndex
        } else {
            prefs.searchEngineChoice
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (private) getString(R.string.private_search_engine) else getString(R.string.search_engine))
            .setNeutralButton(resources.getString(R.string.cancel), null)
            .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    SearchEnginePreferences.apply(requireContext(), private = private)
                    if (!private && prefs.privateSearchEngineChoice < 0) {
                        requireContext().components.searchUseCases.clearPrivateSearchEngine()
                    }
                }
            }
            .setSingleChoiceItems(singleItems.toTypedArray(), checkedItem) { dialog, which ->
                if (private) {
                    prefs.privateSearchEngineChoice = if (which == 0) -1 else which - 1
                } else if (which == singleItems.lastIndex) {
                    customSearchEngineDialog()
                    dialog.cancel()
                } else {
                    prefs.customSearchEngine = false
                    prefs.searchEngineChoice = which
                }
            }
            .show()
    }

    fun customSearchEngineDialog(){
        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.custom_search_engine)
        builder.setMessage(R.string.custom_search_engine_details)

        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        input.setText(
            SearchEngineList.toUserFacingSearchUrl(
                UserPreferences(requireContext()).customSearchEngineURL
            )
        )

        builder.setPositiveButton(
            "OK"
        ) { dialog, which ->
            val entered = input.text.toString()
            if (SearchEngineList.isValidCustomSearchUrl(entered)) {
                UserPreferences(requireContext()).customSearchEngine = true
                UserPreferences(requireContext()).customSearchEngineURL =
                    SearchEngineList.normalizeCustomSearchUrl(entered)
                viewLifecycleOwner.lifecycleScope.launch {
                    SearchEnginePreferences.apply(requireContext(), private = false)
                }
            }
            else{
                Toast.makeText(context, R.string.custom_search_engine_error, Toast.LENGTH_LONG).show()
                customSearchEngineDialog()
            }
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which ->
            UserPreferences(requireContext()).customSearchEngine = false
        }

        builder.show()
    }
}