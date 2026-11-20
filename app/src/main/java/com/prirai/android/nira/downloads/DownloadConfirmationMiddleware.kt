package com.prirai.android.nira.downloads

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.feature.downloads.DownloadStorage
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import mozilla.components.lib.state.ext.flow
import kotlin.coroutines.CoroutineContext

/**
 * Intercepts [DownloadAction.AddDownloadAction] (dispatched by
 * [mozilla.components.feature.downloads.manager.FetchDownloadManager] once the user has approved
 * the "Download file?" prompt) and starts the download through [DownloadController], which picks
 * the serial or parallel service.
 *
 * By not calling [next] for [DownloadAction.AddDownloadAction], [DownloadMiddleware] never
 * starts its own download service; the download is instead added to state via
 * [DownloadAction.UpdateDownloadAction] and handed to [DownloadController].
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
            // Add download to state without calling next(), which keeps
            // DownloadMiddleware from starting a second service for the same download.
            store.dispatch(DownloadAction.UpdateDownloadAction(download))
            // Store reductions are asynchronous: wait until the download is visible in state so
            // the service can look it up by id, and until the tab has consumed the pending
            // engine download. Fetching the same URL while Gecko still holds the original
            // response stalls the range requests.
            scope.launch {
                store.flow().first { state ->
                    download.id in state.downloads &&
                        (state.tabs.map { it.content.download } + state.customTabs.map { it.content.download })
                            .none { it?.id == download.id }
                }
                DownloadController.start(applicationContext, download)
            }
        } else {
            next(action)
        }
    }
}
