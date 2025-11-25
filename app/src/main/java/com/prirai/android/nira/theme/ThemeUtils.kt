package com.prirai.android.nira.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.ThemeChoice

fun applyAppTheme(choice: Int) {
    val mode = when (choice) {
        ThemeChoice.SYSTEM.ordinal -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeChoice.LIGHT.ordinal -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_YES
    }
    AppCompatDelegate.setDefaultNightMode(mode)
}

fun applyAppTheme(context: Context) {
    applyAppTheme(UserPreferences(context).appThemeChoice)
}