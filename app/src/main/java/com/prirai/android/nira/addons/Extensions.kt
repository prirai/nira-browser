package com.prirai.android.nira.addons

import java.text.NumberFormat
import java.util.Locale
import mozilla.components.browser.state.state.WebExtensionState
import mozilla.components.feature.addons.Addon

internal fun getFormattedAmount(amount: Int): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(amount)
}

internal fun WebExtensionState.toInstalledAddon(): Addon {
    val displayName = name?.takeIf { it.isNotBlank() } ?: id
    return Addon(
        id = id,
        translatableName = mapOf(Addon.DEFAULT_LOCALE to displayName),
        installedState = Addon.InstalledState(
            id = id,
            version = "",
            optionsPageUrl = null,
            enabled = enabled,
            allowedInPrivateBrowsing = allowedInPrivateBrowsing,
        ),
    )
}

internal fun Map<String, WebExtensionState>.installedUserAddons(
    privateBrowsing: Boolean = false,
): List<Addon> {
    return values
        .filter { !it.isBuiltIn }
        .filter { it.allowedInPrivateBrowsing || !privateBrowsing }
        .sortedBy { it.name ?: it.id }
        .map { it.toInstalledAddon() }
}
