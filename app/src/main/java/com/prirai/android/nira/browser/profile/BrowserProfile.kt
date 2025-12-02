package com.prirai.android.nira.browser.profile

import android.graphics.Color
import androidx.core.graphics.toColorInt
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
            color = "#6200EE".toColorInt(),
            emoji = "👤",
            isDefault = true
        )
        
        /**
         * Predefined colors for new profiles
         */
        val PROFILE_COLORS = listOf(
            "#6200EE".toColorInt(), // Purple
            "#03DAC5".toColorInt(), // Teal
            "#FF6F00".toColorInt(), // Orange
            "#C51162".toColorInt(), // Pink
            "#00C853".toColorInt(), // Green
            "#2979FF".toColorInt(), // Blue
            "#D50000".toColorInt(), // Red
            "#FFD600".toColorInt(), // Yellow
        )
        
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
    }
}
