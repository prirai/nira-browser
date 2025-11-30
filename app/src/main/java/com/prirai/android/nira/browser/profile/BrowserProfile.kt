package com.prirai.android.nira.browser.profile

import android.graphics.Color
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
            color = Color.parseColor("#6200EE"),
            emoji = "👤",
            isDefault = true
        )
        
        /**
         * Predefined colors for new profiles
         */
        val PROFILE_COLORS = listOf(
            Color.parseColor("#6200EE"), // Purple
            Color.parseColor("#03DAC5"), // Teal
            Color.parseColor("#FF6F00"), // Orange
            Color.parseColor("#C51162"), // Pink
            Color.parseColor("#00C853"), // Green
            Color.parseColor("#2979FF"), // Blue
            Color.parseColor("#D50000"), // Red
            Color.parseColor("#FFD600"), // Yellow
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
