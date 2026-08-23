package com.prirai.android.nira.browser.profile

import android.content.Context
import androidx.core.content.edit
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfileAddonPolicy {
    private const val PREFS = "profile_addons"
    private const val KEY_PREFIX = "enabled_"

    fun isEnabledForProfile(context: Context, profileId: String, addonId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + profileId
        if (!prefs.contains(key)) {
            return true
        }
        return prefs.getStringSet(key, emptySet()).orEmpty().contains(addonId)
    }

    fun setEnabledForProfile(context: Context, profileId: String, addonId: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + profileId
        val current = prefs.getStringSet(key, null)?.toMutableSet() ?: mutableSetOf(addonId)
        if (enabled) current.add(addonId) else current.remove(addonId)
        prefs.edit { putStringSet(key, current) }
    }

    suspend fun applyForProfile(context: Context, profileId: String) {
        val addons = withContext(Dispatchers.IO) {
            runCatching { context.components.addonManager.getAddons() }.getOrDefault(emptyList())
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + profileId
        if (!prefs.contains(key)) {
            val enabled = addons.filter { it.isEnabled() }.map { it.id }.toSet()
            prefs.edit { putStringSet(key, enabled) }
            return
        }
        val allowed = prefs.getStringSet(key, emptySet()).orEmpty()
        addons.forEach { addon ->
            val shouldEnable = allowed.contains(addon.id)
            if (shouldEnable && !addon.isEnabled()) {
                context.components.addonManager.enableAddon(addon)
            } else if (!shouldEnable && addon.isEnabled()) {
                context.components.addonManager.disableAddon(addon)
            }
        }
    }
}
