package com.prirai.android.nira.downloads

import com.prirai.android.nira.R
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.DefaultPackageNameProvider
import android.os.Environment
import mozilla.components.feature.downloads.DownloadEstimator
import mozilla.components.feature.downloads.FileSizeFormatter
import mozilla.components.feature.downloads.PackageNameProvider
import mozilla.components.feature.downloads.filewriter.DefaultDownloadFileWriter
import mozilla.components.feature.downloads.filewriter.DownloadFileWriter
import com.prirai.android.nira.ext.components
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.utils.DefaultDownloadFileUtils
import mozilla.components.support.utils.DownloadFileUtils

class DownloadService : AbstractFetchDownloadService() {
    override val httpClient by lazy { components.client }
    override val store: BrowserStore by lazy { components.store }
    override val style: Style by lazy { Style(R.color.photonBlue40) }
    override val fileSizeFormatter: FileSizeFormatter by lazy { components.fileSizeFormatter }
    override val downloadEstimator: DownloadEstimator by lazy { components.downloadEstimator }
    override val notificationsDelegate: NotificationsDelegate by lazy { components.notificationsDelegate }
    override val downloadFileUtils: DownloadFileUtils by lazy {
        DefaultDownloadFileUtils(
            context = applicationContext,
            downloadLocation = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
            }
        )
    }
    override val downloadFileWriter: DownloadFileWriter by lazy {
        DefaultDownloadFileWriter(
            context = applicationContext,
            downloadFileUtils = downloadFileUtils,
        )
    }
    override val packageNameProvider: PackageNameProvider by lazy { 
        DefaultPackageNameProvider(applicationContext)
    }
}
