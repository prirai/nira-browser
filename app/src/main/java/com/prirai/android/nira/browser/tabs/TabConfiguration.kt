package com.prirai.android.nira.browser.tabs

/**
 * Configuration for tab type (Normal/Private)
 */
data class Configuration(val browserTabType: BrowserTabType)

/**
 * Enum representing browser tab types
 */
enum class BrowserTabType {
    NORMAL,
    PRIVATE
}
