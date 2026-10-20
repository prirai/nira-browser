package com.prirai.android.nira.browser.profile

import java.util.UUID

/**
 * Represents a browser profile with isolated browsing data
 * Each profile has its own cookies, session storage, and tabs
 */
data class BrowserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Int,
    val emoji: String = "👤",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * The default profile that exists for all users
         * Replaces the old "Normal" mode
         */
        fun getDefaultProfile() = BrowserProfile(
            id = "default",
            name = "Default",
            color = com.prirai.android.nira.theme.ColorConstants.Profiles.DEFAULT_COLOR,
            emoji = "👤",
            isDefault = true
        )
        
        /**
         * Predefined colors for new profiles
         */
        val PROFILE_COLORS = com.prirai.android.nira.theme.ColorConstants.Profiles.COLORS
        
        /**
         * Predefined emoji for profiles
         * Using standard emoji characters for better display
         */
        val PROFILE_EMOJIS = listOf(
            "👤", // Person - default
            "🎓", // Scholar/Student  
            "💼", // Work/Briefcase
            "⚽", // Sports
            "🛒", // Shopping
            "🏦", // Bank
            "🎨", // Creative/Art
            "🎮", // Gaming
            "🔵", // Blue blob
            "🟢", // Green blob
            "🟡", // Yellow blob
            "🟣", // Purple blob
            "🟠", // Orange blob
            "🔴", // Red blob
        )
        
        /**
         * Get emoji for profile index
         */
        fun getEmojiForIndex(index: Int): String {
            return PROFILE_EMOJIS.getOrElse(index) { "👤" }
        }

        /**
         * Canonical mapping from a (profile, private-mode) tuple to the
         * `contextId` string we persist on `TabSessionState` and
         * `TabGroup` rows.
         *
         * The built-in default profile is deliberately mapped to `null`
         * so it shares Gecko's default storage partition with custom
         * tabs (external VIEW intents) - keeping cookies and site data
         * aligned across the two surfaces. See
         * [com.prirai.android.nira.browser.profile.DefaultProfileTabMigration]
         * for the migration that cleared the previous
         * `"profile_default"` sentinel.
         */
        fun contextIdFor(profileId: String, isPrivate: Boolean = false): String? = when {
            isPrivate -> "private"
            profileId == "default" -> null
            else -> "profile_$profileId"
        }

        /** @see contextIdFor */
        fun contextIdFor(profile: BrowserProfile, isPrivate: Boolean = false): String? =
            contextIdFor(profile.id, isPrivate)
    }
}
