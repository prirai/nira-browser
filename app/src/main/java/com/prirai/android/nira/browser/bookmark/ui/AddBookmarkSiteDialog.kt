@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.bookmark.ui

import android.content.Context
import android.content.DialogInterface
import android.view.ViewGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentActivity
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.theme.NiraTheme

/**
 * View-framework entry point for adding (or editing) a single bookmark, kept
 * for callers that still reach the bookmark feature from fragment code —
 * BrowserFragment's "Add Bookmark" menu action constructs it with a context,
 * a title and a URL.
 *
 * The class keeps the constructor, `setOnClickListener` and `show` surface of
 * the old View-based dialog; `show` hosts the Compose
 * [BookmarkSiteEditDialog] in a [ComposeView] attached to the activity
 * content — the same hosting pattern TabsBottomSheetFragment uses for its
 * profile dialogs — and removes the view again once the dialog is dismissed
 * or saved.
 */
class AddBookmarkSiteDialog private constructor(
    private val context: Context,
    private val manager: BookmarkManager?,
    private val item: BookmarkSiteItem?,
    private val initialTitle: String,
    private val initialUrl: String,
) {
    constructor(context: Context, manager: BookmarkManager, item: BookmarkSiteItem) :
        this(context, manager, item, item.title ?: "", item.url)

    constructor(context: Context, title: String, url: String) :
        this(context, null, null, title, url)

    private var clickListener: DialogInterface.OnClickListener? = null

    fun setOnClickListener(l: DialogInterface.OnClickListener): AddBookmarkSiteDialog {
        clickListener = l
        return this
    }

    inline fun setOnClickListener(crossinline l: (DialogInterface, Int) -> Unit): AddBookmarkSiteDialog {
        return setOnClickListener(DialogInterface.OnClickListener { dialog, which -> l(dialog, which) })
    }

    fun show() {
        val activity = context as? FragmentActivity ?: return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val composeView = ComposeView(activity)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors,
            ) {
                BookmarkSiteEditDialog(
                    manager = manager ?: BookmarkManager.getInstance(context),
                    item = item,
                    initialTitle = initialTitle,
                    initialUrl = initialUrl,
                    onDismiss = { detach(composeView) },
                    onSaved = {
                        detach(composeView)
                        clickListener?.onClick(DialogSentinel, DialogInterface.BUTTON_POSITIVE)
                    },
                )
            }
        }
        content.addView(composeView)
    }

    private fun detach(composeView: ComposeView) {
        (composeView.parent as? ViewGroup)?.removeView(composeView)
    }

    /**
     * The Compose dialog replaces the platform dialog whose reference the
     * old listener signature carried; the shipped callers ignore it, so a
     * no-op satisfies the interface.
     */
    private object DialogSentinel : DialogInterface {
        override fun dismiss() {
        }

        override fun cancel() {
        }
    }
}
