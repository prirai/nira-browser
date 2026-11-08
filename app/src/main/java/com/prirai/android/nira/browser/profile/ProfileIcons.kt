package com.prirai.android.nira.browser.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Profile avatars are Material icons, not emoji: the `emoji` field on
 * [BrowserProfile] (persisted as a plain String, unchanged schema) now
 * stores a stable icon KEY from [KEYS]. Profiles created before the icon
 * system carry emoji characters; [iconFor] maps every legacy emoji to its
 * icon so existing profiles upgrade in place, and any unknown value falls
 * back to the default person icon.
 */
object ProfileIcons {

    /** The stable icon keys a profile can store. Order = picker order. */
    val KEYS = listOf(
        "person",
        "school",
        "work",
        "sports",
        "shopping",
        "bank",
        "art",
        "gaming",
        "trophy",
    )

    /** Legacy emoji -> icon key, so pre-icon profiles upgrade in place. */
    private val legacyEmojiMap: Map<String, String> = mapOf(
        "👤" to "person",
        "🎓" to "school",
        "💼" to "work",
        "⚽" to "sports",
        "🛒" to "shopping",
        "🏦" to "bank",
        "🎨" to "art",
        "🎮" to "gaming",
        "🔵" to "person",
        "🟢" to "person",
        "🟡" to "person",
        "🟣" to "person",
        "🟠" to "person",
        "🔴" to "person",
    )

    private val icons: Map<String, ImageVector> = mapOf(
        "person" to Icons.Filled.Person,
        "school" to Icons.Filled.School,
        "work" to Icons.Filled.Work,
        "sports" to Icons.Filled.SportsSoccer,
        "shopping" to Icons.Filled.ShoppingCart,
        "bank" to Icons.Filled.Paid,
        "art" to Icons.Filled.Brush,
        "gaming" to Icons.Filled.SportsEsports,
        "trophy" to Icons.Filled.EmojiEvents,
    )

    /** The icon a stored profile value renders; never null. */
    fun iconFor(stored: String?): ImageVector =
        icons[normalize(stored)] ?: Icons.Filled.Person

    private fun normalize(stored: String?): String =
        legacyEmojiMap[stored] ?: stored ?: "person"
}
