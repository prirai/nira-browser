@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.snackbar.Snackbar
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import java.lang.ref.WeakReference

/**
 * The toolbar long-press clipboard menu (copy / paste / paste-and-go).
 *
 * The PopupWindow mechanics are unchanged (drop-down over the toolbar, outside-touch
 * dismissal, transparent background, mozac menu elevation); the content now renders
 * in Compose with the house grouped-card menu idiom ([NiraCardRow], like the tab
 * menu). The `show(...)` signature BrowserToolbarView calls is untouched.
 */
object ToolbarPopupWindow {

    /** Fixed menu width, matching Material3BrowserMenu's menu. */
    private const val MENU_WIDTH_DP = 280

    /**
     * Standalone recomposer used only when no ComposeView has ever attached in the
     * anchor's window (every Compose toolbar surface disabled). Cached process-wide
     * and cancelled when the owning activity is destroyed.
     */
    @Volatile
    private var fallbackRecomposer: Recomposer? = null

    fun show(
        view: WeakReference<View>,
        customTabId: String? = null,
        handlePasteAndGo: (String) -> Unit,
        handlePaste: (String) -> Unit,
        copyVisible: Boolean = true
    ) {
        val anchor = view.get() ?: return
        val context = anchor.context
        val clipboard = context.components.clipboardHandler
        if (!copyVisible && clipboard.text.isNullOrEmpty()) return

        val isCustomTabSession = customTabId != null
        val showPasteRows = !clipboard.text.isNullOrEmpty() && !isCustomTabSession
        if (!copyVisible && !showPasteRows) return

        // ComponentActivity provides all three ViewTree owners (lifecycle,
        // view-model store, saved-state registry) the popup composition needs.
        val activity = findActivity(context) as? androidx.activity.ComponentActivity
        val compositionContext = resolveCompositionContext(anchor, activity)
        if (activity == null || compositionContext == null) {
            // The popup window cannot host a composition without a lifecycle and a
            // recomposer. Unreachable in practice: the anchor always lives in the
            // BrowserActivity window, which provides both.
            return
        }

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            // A PopupWindow renders its content in a separate window whose decor
            // view carries no ViewTree owners, so compose cannot resolve a
            // lifecycle or a recomposer through the normal parent chain (it
            // would throw "ViewTreeLifecycleOwner not found"). Bridge both
            // against the anchor's activity/window explicitly.
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setParentCompositionContext(compositionContext)
        }

        val density = context.resources.displayMetrics.density
        val menuWidthPx = (MENU_WIDTH_DP * density).toInt()

        val popupWindow = PopupWindow(composeView, menuWidthPx, 0, true)

        composeView.setContent {
            NiraTheme(amoledMode = com.prirai.android.nira.preferences.UserPreferences(context).amoledMode) {
                ToolbarClipboardMenu(
                    copyVisible = copyVisible,
                    pasteVisible = showPasteRows,
                    onCopy = {
                        popupWindow.dismiss()
                        clipboard.text = getUrlForClipboard(
                            context.components.store,
                            customTabId
                        )

                        view.get()?.let {
                            Snackbar.make(
                                it,
                                context.getString(R.string.copied),
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onPaste = {
                        popupWindow.dismiss()
                        handlePaste(clipboard.text!!)
                    },
                    onPasteAndGo = {
                        popupWindow.dismiss()
                        handlePasteAndGo(clipboard.text!!)
                    }
                )
            }
        }

        // Compose measures with the exact menu width so the popup height is
        // deterministic (same pattern as Material3BrowserMenu.show).
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        popupWindow.height = composeView.measuredHeight

        popupWindow.elevation =
            context.resources.getDimension(R.dimen.mozac_browser_menu_elevation)

        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        popupWindow.showAsDropDown(
            anchor,
            context.resources.getDimensionPixelSize(R.dimen.context_menu_x_offset),
            0,
            Gravity.START
        )
    }

    /**
     * The clipboard menu rows rendered with the house grouped-card idiom: one
     * surfaceContainerHigh card, rounded corners on the first/last row only and a
     * divider between rows (the same pattern as the tab-history chart and the
     * awesome bar).
     */
    @Composable
    private fun ToolbarClipboardMenu(
        copyVisible: Boolean,
        pasteVisible: Boolean,
        onCopy: () -> Unit,
        onPaste: () -> Unit,
        onPasteAndGo: () -> Unit
    ) {
        val copyLabel = stringResource(R.string.copy)
        val pasteLabel = stringResource(R.string.paste)
        val pasteAndGoLabel = stringResource(R.string.paste_and_go)

        val rows = buildList {
            if (copyVisible) {
                add(ClipboardMenuRow(copyLabel, Icons.Outlined.ContentCopy, onCopy))
            }
            if (pasteVisible) {
                add(ClipboardMenuRow(pasteLabel, Icons.Outlined.ContentPaste, onPaste))
                add(
                    ClipboardMenuRow(
                        pasteAndGoLabel,
                        Icons.AutoMirrored.Filled.ArrowForward,
                        onPasteAndGo
                    )
                )
            }
        }

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            rows.forEachIndexed { index, row ->
                NiraCardRow(
                    title = row.title,
                    isTopRow = index == 0,
                    isBottomRow = index == rows.lastIndex,
                    showDivider = index < rows.lastIndex,
                    onClick = row.onClick,
                    leading = {
                        // Decorative icon next to a labelled row.
                        Icon(imageVector = row.icon, contentDescription = null)
                    }
                )
            }
        }
    }

    private data class ClipboardMenuRow(
        val title: String,
        val icon: ImageVector,
        val onClick: () -> Unit
    )

    /**
     * Unwraps [ContextWrapper] chains to the hosting activity, which provides the
     * lifecycle / view-model / saved-state owners for the popup's composition.
     * Never casts directly - toolbar contexts are themed wrappers.
     */
    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    /**
     * The anchor window's Recomposer: the first ComposeView that attaches in a
     * window installs it as a composition-context tag on the window's content child
     * (androidx.compose.ui's WindowRecomposer mechanism). The popup's ComposeView
     * lives in its own window, so it reuses the anchor window's instead of trying -
     * and failing - to create one for the popup decor.
     */
    private fun resolveCompositionContext(
        anchor: View,
        activity: Activity?
    ): CompositionContext? {
        var current: View? = anchor
        while (current != null) {
            (current.getTag(androidx.compose.ui.R.id.androidx_compose_ui_view_composition_context)
                as? CompositionContext)?.let { return it }
            current = current.parent as? View
        }
        // No ComposeView ever attached in this window (every Compose toolbar
        // surface disabled): fall back to a standalone recomposer.
        return activity?.let { fallbackCompositionContext(it) }
    }

    private fun fallbackCompositionContext(activity: Activity): CompositionContext {
        fallbackRecomposer?.let { return it }

        val recomposer = Recomposer(AndroidUiDispatcher.CurrentThread)
        CoroutineScope(recomposer.effectCoroutineContext).launch {
            recomposer.runRecomposeAndApplyChanges()
        }
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    fallbackRecomposer = null
                    recomposer.cancel()
                }
            }
        )
        fallbackRecomposer = recomposer
        return recomposer
    }

    @VisibleForTesting
    internal fun getUrlForClipboard(
        store: BrowserStore,
        customTabId: String? = null
    ): String? {
        return if (customTabId != null) {
            val customTab = store.state.findCustomTab(customTabId)
            customTab?.content?.url
        } else {
            val selectedTab = store.state.selectedTab
            selectedTab?.readerState?.activeUrl ?: selectedTab?.content?.url
        }
    }
}
