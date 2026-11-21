package com.prirai.android.nira.browser.profile

import android.content.Context
import androidx.core.content.edit
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfileAddonPolicy {
    private const val PREFS = "profile_addons"
    private const val KEY_PREFIX = "enabled_"
    private const val KNOWN_PREFIX = "known_"

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
        val current = prefs.getStringSet(key, null)?.toMutableSet() ?: seedAllowlist(context)
        if (enabled) current.add(addonId) else current.remove(addonId)
        val known = prefs.getStringSet(KNOWN_PREFIX + profileId, null)?.let { it + addonId }
        prefs.edit {
            putStringSet(key, current)
            if (known != null) putStringSet(KNOWN_PREFIX + profileId, known)
        }
    }

    private fun seedAllowlist(context: Context): MutableSet<String> {
        return context.components.store.state.extensions
            .filterValues { !it.isBuiltIn && it.enabled }
            .keys
            .toMutableSet()
    }

    suspend fun applyForProfile(context: Context, profileId: String) {
        val addons = withContext(Dispatchers.IO) {
            runCatching {
                context.components.addonManager.getAddons().filter { it.isInstalled() }
            }.getOrNull()
        } ?: return
        if (addons.isEmpty()) {
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + profileId
        val knownKey = KNOWN_PREFIX + profileId
        if (!prefs.contains(key)) {
            val enabled = addons.filter { it.isEnabled() }.map { it.id }.toSet()
            prefs.edit {
                putStringSet(key, enabled)
                putStringSet(knownKey, addons.map { it.id }.toSet())
            }
            return
        }
        var allowed = prefs.getStringSet(key, emptySet()).orEmpty()
        // Extensions installed after the allowlist was created (e.g. sideloaded) are new to this
        // profile: allow them, otherwise they would be disabled on the next launch. On first run
        // of this logic every existing extension counts as known so explicit disables are kept.
        val known = prefs.getStringSet(knownKey, null) ?: addons.map { it.id }.toSet()
        val fresh = addons.map { it.id }.filter { it !in known }
        if (fresh.isNotEmpty() || !prefs.contains(knownKey)) {
            allowed = allowed + fresh
            prefs.edit {
                putStringSet(key, allowed)
                putStringSet(knownKey, known + addons.map { it.id })
            }
        }
        withContext(Dispatchers.Main) {
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
}
