package com.prirai.android.nira.settings.fragment

import android.content.Context.LAYOUT_INFLATER_SERVICE
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.R as MaterialR
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.Engine
import java.util.concurrent.TimeUnit


class PrivacyAndSecuritySettingsFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_privacy_and_security)

        clickablePreference(
            preference = resources.getString(R.string.key_clear_tabs),
            onClick = { clearTabs() }
        )
        clickablePreference(
            preference = resources.getString(R.string.key_clear_history),
            onClick = { clearHistory() }
        )
        clickablePreference(
            preference = resources.getString(R.string.key_clear_cookies),
            onClick = { clearCookies() }
        )
        clickablePreference(
            preference = resources.getString(R.string.key_clear_cache),
            onClick = { clearCache() }
        )
        clickablePreference(
            preference = resources.getString(R.string.key_clear_permissions),
            onClick = { clearPermissions() }
        )

        setupETP()
    }

    private fun setupETP() {
        val prefs = UserPreferences(requireContext())

        val etpLevelPref = findPreference<Preference>("etp_level")
        val customCategories = findPreference<PreferenceCategory>("etp_custom_categories")

        val initialLevel = prefs.etpLevel
        customCategories?.isVisible = initialLevel == 3

        etpLevelPref?.setOnPreferenceClickListener {
            showEtpLevelPicker(prefs, customCategories)
            true
        }

        // Wire up custom category switches to UserPreferences
        val switchDefs = listOf(
            "etp_tracking_ads" to { v: Boolean -> prefs.etpTrackingAds = v },
            "etp_tracking_content" to { v: Boolean -> prefs.etpTrackingContent = v },
            "etp_cryptominers" to { v: Boolean -> prefs.etpCryptominers = v },
            "etp_fingerprinters" to { v: Boolean -> prefs.etpFingerprinters = v },
            "etp_social_tracking" to { v: Boolean -> prefs.etpSocialTracking = v },
            "etp_email_tracking" to { v: Boolean -> prefs.etpEmailTracking = v },
        )

        for ((key, setter) in switchDefs) {
            findPreference<com.prirai.android.nira.settings.MaterialSwitchPreference>(key)?.let { pref ->
                val sp = preferenceManager.sharedPreferences
                if (sp != null && !sp.contains(key)) {
                    sp.edit()?.putBoolean(key, pref.isChecked)?.apply()
                }
                pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    setter(newValue as Boolean)
                    true
                }
            }
        }
    }

    private fun showEtpLevelPicker(prefs: UserPreferences, customCategories: PreferenceCategory?) {
        val ctx = requireContext()
        val currentLevel = prefs.etpLevel

        // Resolve Material 3 theme colours
        val primaryContainer = MaterialColors.getColor(ctx, MaterialR.attr.colorPrimaryContainer, 0)
        val tertiaryContainer = MaterialColors.getColor(ctx, MaterialR.attr.colorTertiaryContainer, 0)
        val surfaceVariant = MaterialColors.getColor(ctx, MaterialR.attr.colorSurfaceVariant, 0)

        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_etp_level_picker, null)

        data class CardInfo(
            val card: MaterialCardView,
            val checkView: View,
            val circleBg: Int,
        )

        val cards = listOf(
            CardInfo(
                card = view.findViewById(R.id.noneCard),
                checkView = view.findViewById(R.id.noneCheck),
                circleBg = surfaceVariant,
            ),
            CardInfo(
                card = view.findViewById(R.id.standardCard),
                checkView = view.findViewById(R.id.standardCheck),
                circleBg = primaryContainer,
            ),
            CardInfo(
                card = view.findViewById(R.id.strictCard),
                checkView = view.findViewById(R.id.strictCheck),
                circleBg = tertiaryContainer,
            ),
            CardInfo(
                card = view.findViewById(R.id.customCard),
                checkView = view.findViewById(R.id.customCheck),
                circleBg = surfaceVariant,
            ),
        )

        // Style each card
        cards.forEachIndexed { index, info ->
            val isSelected = index == currentLevel

            val circleFrame = when (index) {
                0 -> view.findViewById<View>(R.id.noneIcon)
                1 -> view.findViewById<View>(R.id.standardIcon)
                2 -> view.findViewById<View>(R.id.strictIcon)
                3 -> view.findViewById<View>(R.id.customIcon)
                else -> view.findViewById<View>(R.id.noneIcon)
            }
            val circleBgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(
                    (48 * ctx.resources.displayMetrics.density).toInt(),
                    (48 * ctx.resources.displayMetrics.density).toInt(),
                )
                setColor(info.circleBg)
            }
            circleFrame.background = circleBgDrawable

            updateCardAppearance(info.card, info.checkView, isSelected)
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.etp_level_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        cards.forEachIndexed { index, info ->
            info.card.setOnClickListener {
                val level = index
                prefs.etpLevel = level
                customCategories?.isVisible = level == 3

                // Update all cards
                cards.forEachIndexed { i, ci ->
                    val sel = i == level
                    updateCardAppearance(ci.card, ci.checkView, sel)
                }

                if (level == 2) {
                    MaterialAlertDialogBuilder(ctx)
                        .setTitle(R.string.etp_strict_title)
                        .setMessage(R.string.etp_strict_warning)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                }

                dialog.dismiss()
            }
        }
    }

    private fun updateCardAppearance(
        card: MaterialCardView,
        checkView: View,
        isSelected: Boolean,
    ) {
        if (isSelected) {
            card.setCardBackgroundColor(
                MaterialColors.getColor(card.context, MaterialR.attr.colorPrimaryContainer, 0)
            )
            card.strokeWidth = 0
            checkView.visibility = View.VISIBLE
        } else {
            card.setCardBackgroundColor(
                MaterialColors.getColor(card.context, MaterialR.attr.colorSurface, 0)
            )
            card.strokeWidth = 0
            checkView.visibility = View.GONE
        }
    }

    private fun clearTabs(){
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getString(R.string.clear_tabs))
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ -> }
            .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                lifecycleScope.launch {
                    requireContext().components.tabsUseCases.removeAllTabs.invoke()
                }
                Toast.makeText(context, R.string.tabs_cleared, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun clearHistory(){
        val inflater = requireContext().getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val layout: View = inflater.inflate(R.layout.dialog_clear_history, null)

        val timeArray: Array<Long> = arrayOf(
            System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30),
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365),
            0
        )

        val spinner: Spinner = layout.findViewById(R.id.timeSpinner)
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.history_delete_times,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        val historyDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(layout)
            .create()

        layout.findViewById<Button>(R.id.clearButton).setOnClickListener {
            lifecycleScope.launch {
                requireContext().components.historyStorage.deleteVisitsSince(timeArray[spinner.selectedItemPosition])
            }
            Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_LONG).show()
            historyDialog.dismiss()
        }

        layout.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            historyDialog.dismiss()
        }

        historyDialog.show()
    }

    private fun clearCookies(){
         requireContext().components.engine.clearData(
                Engine.BrowsingData.select(
                    Engine.BrowsingData.COOKIES,
                    Engine.BrowsingData.AUTH_SESSIONS
                )
            )
        Toast.makeText(context, R.string.cookies_cleared, Toast.LENGTH_LONG).show()
    }

    private fun clearCache(){
        requireContext().components.engine.clearData(
            Engine.BrowsingData.select(Engine.BrowsingData.ALL_CACHES)
        )
        Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_LONG).show()
    }

    private fun clearPermissions(){
        requireContext().components.engine.clearData(
            Engine.BrowsingData.select(Engine.BrowsingData.PERMISSIONS)
        )
        lifecycleScope.launch { components.permissionStorage.removeAll() }
        Toast.makeText(context, R.string.permissions_cleared, Toast.LENGTH_LONG).show()
    }
}
