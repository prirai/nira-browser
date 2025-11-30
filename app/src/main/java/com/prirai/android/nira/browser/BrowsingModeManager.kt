package com.prirai.android.nira.browser

import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.preferences.UserPreferences

enum class BrowsingMode {
    Normal, Private;

    val isPrivate get() = this == Private

    companion object {
        fun fromBoolean(isPrivate: Boolean) = if (isPrivate) Private else Normal
    }
}

interface BrowsingModeManager {
    var mode: BrowsingMode
    var currentProfile: BrowserProfile
}

class DefaultBrowsingModeManager(
    private var _mode: BrowsingMode,
    private var _currentProfile: BrowserProfile,
    private val userPreferences: UserPreferences,
    private val modeDidChange: (BrowsingMode) -> Unit,
    private val profileDidChange: (BrowserProfile) -> Unit
) : BrowsingModeManager {

    override var mode: BrowsingMode
        get() = _mode
        set(value) {
            _mode = value
            modeDidChange(value)
            userPreferences.lastKnownPrivate = value == BrowsingMode.Private
        }
    
    override var currentProfile: BrowserProfile
        get() = _currentProfile
        set(value) {
            _currentProfile = value
            profileDidChange(value)
        }
}
