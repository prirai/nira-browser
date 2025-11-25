package com.prirai.android.nira.downloads

import com.prirai.android.nira.R
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.DefaultPackageNameProvider
import mozilla.components.feature.downloads.PackageNameProvider
import com.prirai.android.nira.ext.components
import mozilla.components.support.base.android.NotificationsDelegate

class DownloadService : AbstractFetchDownloadService() {
    override val httpClient by lazy { components.client }
    override val store: BrowserStore by lazy { components.store }
    override val style: Style by lazy { Style(R.color.photonBlue40) }
    override val fileSizeFormatter by lazy { components.fileSizeFormatter }
    override val downloadEstimator by lazy { components.downloadEstimator }
    override val notificationsDelegate: NotificationsDelegate by lazy { components.notificationsDelegate }
    override val packageNameProvider: PackageNameProvider by lazy { 
        DefaultPackageNameProvider(applicationContext)
    }
}
