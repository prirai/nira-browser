package com.prirai.android.nira.integration

import android.content.Context
import android.view.View
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.ui.widgets.SnackbarDelegate

/**
 * Context menu candidate for copying link text (the visible text of a link, not the URL).
 * This appears for any link element that has both a URL and visible text.
 */
fun createCopyLinkTextCandidate(
    context: Context,
    parentView: View,
    snackbarDelegate: SnackbarDelegate
): ContextMenuCandidate {
    return ContextMenuCandidate(
        id = "copy-link-text",
        label = "Copy link",
        showFor = { _, hitResult ->
            // Show for images and links
            when (hitResult) {
                is HitResult.IMAGE_SRC -> true
                is HitResult.IMAGE -> true
                else -> false
            }
        },
        action = { _, hitResult ->
            val linkText = when (hitResult) {
                is HitResult.IMAGE_SRC -> hitResult.uri
                is HitResult.IMAGE -> hitResult.src
                else -> ""
            }
            
            if (linkText.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("link", linkText)
                clipboard.setPrimaryClip(clip)
                
                snackbarDelegate.show(
                    snackBarParentView = parentView,
                    text = "Link copied",
                    duration = 3000
                )
            }
        }
    )
}
