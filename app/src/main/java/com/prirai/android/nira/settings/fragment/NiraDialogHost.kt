package com.prirai.android.nira.settings.fragment

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.theme.NiraTheme

/**
 * Shows [content] as a Compose dialog from preference-fragment code: a themed
 * [ComposeView] is attached to the activity's content view — the same hosting
 * pattern AddBookmarkSiteDialog and TabsBottomSheetFragment use — and removed
 * again when the dialog calls [onDismiss]. The content is wrapped in [NiraTheme]
 * with the user's dark/AMOLED/dynamic-color choices.
 */
fun Fragment.showNiraDialog(content: @Composable (onDismiss: () -> Unit) -> Unit) {
    val activity = requireActivity()
    val contentView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
    val composeView = ComposeView(activity)
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        val prefs = UserPreferences(activity)
        NiraTheme(
            darkTheme = ThemeManager.isDarkMode(activity),
            amoledMode = prefs.amoledMode,
            dynamicColor = prefs.dynamicColors,
        ) {
            content { (composeView.parent as? ViewGroup)?.removeView(composeView) }
        }
    }
    contentView.addView(composeView)
}
