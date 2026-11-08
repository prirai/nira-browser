package com.prirai.android.nira.settings.fragment

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.theme.NiraTheme

/**
 * Shows [content] as a Compose dialog from preference-fragment code.
 *
 * The composition is hosted inside a real [Dialog] window - the same
 * nesting the tab sheet's profile dialogs use - NOT attached to the
 * activity's content view. A ComposeView attached to the activity's
 * content view opens the inner `BasicAlertDialog` window *below* the
 * activity window in the input dispatch order on some device states:
 * the dialog renders, but no touch or key event ever reaches it (the
 * "settings dialogs don't respond to clicks" bug). Hosted inside a
 * dialog window, the inner dialog window is ordered normally and
 * receives input.
 *
 * [content] is wrapped in [NiraTheme] with the user's dark/AMOLED/
 * dynamic-color choices; calling [onDismiss] closes the host window,
 * which disposes the composition and the inner dialog with it.
 */
fun Fragment.showNiraDialog(content: @Composable (onDismiss: () -> Unit) -> Unit) {
    val activity = requireActivity()

    // ComponentDialog carries the ViewTree lifecycle/view-model/saved-state
    // owners a ComposeView needs when it attaches to the dialog's decor -
    // a plain Dialog's decor has none and composition crashes. This is the
    // same base Compose's own DialogWrapper uses.
    val host = ComponentDialog(activity)
    host.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val composeView = ComposeView(activity)
    composeView.setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
    )
    composeView.setContent {
        val prefs = UserPreferences(activity)
        NiraTheme(
            darkTheme = ThemeManager.isDarkMode(activity),
            amoledMode = prefs.amoledMode,
            dynamicColor = prefs.dynamicColors,
        ) {
            content { host.dismiss() }
        }
    }

    host.setContentView(composeView)
    // The host window is a transparent, zero-footprint shell: the visible
    // dialog is the inner BasicAlertDialog window the composition opens.
    // A transparent background keeps the shell from dimming or drawing.
    host.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
    host.show()
}

/**
 * The info-icon dialog: the option's title plus its explanation text,
 * dismissed with a single OK button. Hosted through the same
 * ComponentDialog shell as [showNiraDialog].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun showNiraInfoDialog(context: Context, title: String, textRes: Int) {
    if (textRes == 0) return
    // Preference contexts can be themed wrappers; the dialog needs the
    // hosting activity.
    var current: Context? = context
    val activity = run {
        while (current is ContextWrapper) {
            if (current is FragmentActivity) return@run current
            current = current.baseContext
        }
        current as? FragmentActivity
    } ?: return

    val host = ComponentDialog(activity)
    host.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val composeView = ComposeView(activity)
    composeView.setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
    )
    composeView.setContent {
        val prefs = UserPreferences(activity)
        NiraTheme(
            darkTheme = ThemeManager.isDarkMode(activity),
            amoledMode = prefs.amoledMode,
            dynamicColor = prefs.dynamicColors,
        ) {
            NiraAlertDialog(
                onDismissRequest = { host.dismiss() },
                title = {
                    Text(
                        text = title,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                text = {
                    Text(
                        text = stringResource(textRes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { host.dismiss() },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.mozac_feature_prompts_ok))
                    }
                },
            )
        }
    }

    host.setContentView(composeView)
    host.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    host.show()
}
