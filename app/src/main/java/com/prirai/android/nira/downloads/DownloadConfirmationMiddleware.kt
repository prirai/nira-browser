package com.prirai.android.nira.downloads

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.feature.downloads.DownloadStorage
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import kotlin.coroutines.CoroutineContext

/**
 * Intercepts [DownloadAction.AddDownloadAction] (dispatched by
 * [mozilla.components.feature.downloads.manager.FetchDownloadManager] when a download starts)
 * and holds the download in INITIATED state until the user explicitly confirms in the
 * downloads sheet.
 *
 * By not calling [next] for [DownloadAction.AddDownloadAction], [DownloadMiddleware] never
 * starts the download service. The download is instead added to state via
 * [DownloadAction.UpdateDownloadAction] so [BrowserFragment.observeDownloadsForSheet] opens
 * the sheet and the INITIATED row shows the confirmation prompt.
 *
 * On confirmation the UI starts the service directly (bypassing this middleware).
 * On cancellation the UI dispatches [DownloadAction.RemoveDownloadAction].
 */
class DownloadConfirmationMiddleware(
    private val applicationContext: Context,
    coroutineContext: CoroutineContext = Dispatchers.IO,
) : Middleware<BrowserState, BrowserAction> {

    private val scope = CoroutineScope(coroutineContext)
    private val downloadStorage = DownloadStorage(applicationContext)

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        if (action is DownloadAction.AddDownloadAction) {
            val download = action.download
            // Persist to storage so that DownloadMiddleware.updateDownload works correctly
            // when the service later dispatches UpdateDownloadAction with new statuses.
            if (!download.private) {
                scope.launch { downloadStorage.add(download) }
            }
            // Add download to state (INITIATED) without calling next(), which prevents
            // DownloadMiddleware from sending the service intent that would start the download.
            store.dispatch(DownloadAction.UpdateDownloadAction(download))
        } else {
            next(action)
        }
    }
}
