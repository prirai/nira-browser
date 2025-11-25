package com.prirai.android.nira.browser.home

import com.prirai.android.nira.browser.BrowsingMode

sealed class Mode {
    data object Normal : Mode()
    data object Private : Mode()

    companion object {
        fun fromBrowsingMode(browsingMode: BrowsingMode) = when (browsingMode) {
            BrowsingMode.Normal -> Normal
            BrowsingMode.Private -> Private
        }
    }
}
