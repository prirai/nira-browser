package com.prirai.android.nira.components

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.app.NotificationManagerCompat
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.downloads.DownloadService
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.media.MediaSessionService
import com.prirai.android.nira.middleware.EnhancedStateCaptureMiddleware
import com.prirai.android.nira.middleware.FaviconMiddleware
import com.prirai.android.nira.perf.VisualCompletenessQueue
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.request.AppRequestInterceptor
import com.prirai.android.nira.settings.ThemeChoice
import com.prirai.android.nira.share.SaveToPDFMiddleware
import com.prirai.android.nira.utils.ClipboardHandler
import com.prirai.android.nira.utils.FaviconCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.gecko.ext.toContentBlockingSetting
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.browser.engine.gecko.permission.GeckoSitePermissionsStorage
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.engine.middleware.SessionPrioritizationMiddleware
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.thumbnails.ThumbnailsMiddleware
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.mediaquery.PreferredColorScheme
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.amo.AMOAddonsProvider
import mozilla.components.feature.addons.migration.DefaultSupportedAddonsChecker
import mozilla.components.feature.addons.update.DefaultAddonUpdater
import mozilla.components.feature.app.links.AppLinksInterceptor
import mozilla.components.feature.app.links.AppLinksUseCases
import mozilla.components.feature.contextmenu.ContextMenuUseCases
import mozilla.components.feature.customtabs.store.CustomTabsServiceStore
import mozilla.components.feature.downloads.DefaultDateTimeProvider
import mozilla.components.feature.downloads.DefaultFileSizeFormatter
import mozilla.components.feature.downloads.DownloadEstimator
import mozilla.components.feature.downloads.DownloadMiddleware
import mozilla.components.feature.downloads.DownloadsUseCases
import mozilla.components.feature.media.MediaSessionFeature
import mozilla.components.feature.media.middleware.RecordingDevicesMiddleware
import mozilla.components.feature.prompts.PromptMiddleware
import mozilla.components.feature.prompts.file.FileUploadsDirCleaner
import mozilla.components.feature.pwa.ManifestStorage
import mozilla.components.feature.pwa.WebAppInterceptor
import mozilla.components.feature.pwa.WebAppShortcutManager
import mozilla.components.feature.pwa.WebAppUseCases
import mozilla.components.feature.readerview.ReaderViewMiddleware
import mozilla.components.feature.search.SearchUseCases
import mozilla.components.feature.search.middleware.SearchMiddleware
import mozilla.components.feature.search.region.RegionMiddleware
import mozilla.components.feature.session.HistoryDelegate
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.session.middleware.LastAccessMiddleware
import mozilla.components.feature.session.middleware.undo.UndoMiddleware
import mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.webcompat.WebCompatFeature
import mozilla.components.feature.webnotifications.WebNotificationFeature
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.service.location.LocationService
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.base.worker.Frequency
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.util.concurrent.TimeUnit


private const val DAY_IN_MINUTES = 24 * 60L

open class Components(private val applicationContext: Context) {
    
    // Visual completeness queue for deferred initialization
    val visualCompletenessQueue = VisualCompletenessQueue()
    companion object {
        const val BROWSER_PREFERENCES = "browser_preferences"
        const val PREF_LAUNCH_EXTERNAL_APP = "launch_external_app"
    }

    val publicSuffixList by lazy { PublicSuffixList(applicationContext) }

    val clipboardHandler by lazy { ClipboardHandler(applicationContext) }

    val faviconCache by lazy { FaviconCache.getInstance(applicationContext) }

    val tabGroupManager by lazy {
        com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager.getInstance(applicationContext)
    }

    val fileSizeFormatter by lazy { DefaultFileSizeFormatter(applicationContext) }

    val dateTimeProvider by lazy { DefaultDateTimeProvider() }

    val downloadEstimator by lazy { DownloadEstimator(dateTimeProvider) }

    val packageNameProvider: () -> String by lazy { { applicationContext.packageName } }

    val preferences: SharedPreferences =
            applicationContext.getSharedPreferences(BROWSER_PREFERENCES, Context.MODE_PRIVATE)


    fun darkEnabled(url: String? = null): PreferredColorScheme {
        val darkOn =
                (applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
        
        // Check per-site override first
        if (url != null) {
            val siteDarkMode = applicationContext.getSharedPreferences("site_dark_mode_override", Context.MODE_PRIVATE)
            if (siteDarkMode.contains(url)) {
                val override = siteDarkMode.getString(url, "")
                return when (override) {
                    "dark" -> PreferredColorScheme.Dark
                    "light" -> PreferredColorScheme.Light
                    else -> PreferredColorScheme.Dark // Fallback
                }
            }
        }
        
        // Use global web theme
        return when {
            UserPreferences(applicationContext).webThemeChoice == ThemeChoice.DARK.ordinal -> PreferredColorScheme.Dark
            UserPreferences(applicationContext).webThemeChoice == ThemeChoice.LIGHT.ordinal -> PreferredColorScheme.Light
            darkOn -> PreferredColorScheme.Dark
            else -> PreferredColorScheme.Light
        }
    }

    val appRequestInterceptor by lazy {
        AppRequestInterceptor(applicationContext)
    }

    // Engine Settings
    private val engineSettings: DefaultSettings
        get() = DefaultSettings().apply {
            historyTrackingDelegate = HistoryDelegate(lazyHistoryStorage)
            requestInterceptor = appRequestInterceptor
            remoteDebuggingEnabled = false // SECURITY: Remote debugging disabled
            supportMultipleWindows = true
            enterpriseRootsEnabled = false // SECURITY: Third-party certs disabled
            if(!UserPreferences(applicationContext).autoFontSize){
                fontSizeFactor = UserPreferences(applicationContext).fontSizeFactor
                automaticFontSizeAdjustment = false
            }
            preferredColorScheme = darkEnabled()
            javascriptEnabled = UserPreferences(applicationContext).javaScriptEnabled
        }

    private val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

    val notificationsDelegate: NotificationsDelegate by lazy {
        NotificationsDelegate(
            notificationManagerCompat,
        )
    }

    val addonUpdater =
            DefaultAddonUpdater(applicationContext, Frequency(1, TimeUnit.DAYS), notificationsDelegate)

    // Engine
    open val engine: Engine by lazy {
        GeckoEngine(applicationContext, engineSettings, runtime).also {
            WebCompatFeature.install(it)
        }
    }

    open val client: Client by lazy { GeckoViewFetchClient(applicationContext, runtime) }

    val icons by lazy { BrowserIcons(applicationContext, client) }

    // Storage
    private val lazyHistoryStorage = lazy { PlacesHistoryStorage(applicationContext) }
    val historyStorage by lazy { lazyHistoryStorage.value }

    val sessionStorage by lazy { SessionStorage(applicationContext, engine) }

    val permissionStorage by lazy { GeckoSitePermissionsStorage(runtime, OnDiskSitePermissionsStorage(applicationContext)) }

    val thumbnailStorage by lazy { ThumbnailStorage(applicationContext) }
    
    val profileManager by lazy { 
        com.prirai.android.nira.browser.profile.ProfileManager.getInstance(applicationContext)
    }
    
    val profileMiddleware by lazy {
        com.prirai.android.nira.browser.profile.ProfileMiddleware(profileManager)
    }

    val store by lazy {
        BrowserStore(
                middleware = listOf(
                        DownloadMiddleware(applicationContext, DownloadService::class.java, { true }),
                        ReaderViewMiddleware(),
                        ThumbnailsMiddleware(thumbnailStorage),
                        FaviconMiddleware(faviconCache),
                        UndoMiddleware(),
                        RegionMiddleware(
                                applicationContext,
                                LocationService.default()
                        ),
                        SearchMiddleware(applicationContext),
                        RecordingDevicesMiddleware(applicationContext, notificationsDelegate),
                        PromptMiddleware(),
                        LastAccessMiddleware(),
                        SaveToPDFMiddleware(applicationContext),
                        com.prirai.android.nira.browser.tabgroups.TabGroupMiddleware(tabGroupManager),
                        profileMiddleware,  // Use the exposed instance
                        SessionPrioritizationMiddleware(),
                        EnhancedStateCaptureMiddleware(
                            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
                            maxTabsToCapture = 3
                        )
                ) + EngineMiddleware.create(
                    engine,
                    trimMemoryAutomatically = false,
                )
        ).apply{
            icons.install(engine, this)

            WebNotificationFeature(
                    applicationContext, engine, icons, R.drawable.ic_notification,
                    permissionStorage, BrowserActivity::class.java,
                notificationsDelegate = notificationsDelegate
            )

            MediaSessionFeature(applicationContext, MediaSessionService::class.java, this).start()
        }
    }

    val sessionUseCases by lazy { SessionUseCases(store) }

    // Addons
    val addonManager by lazy {
        AddonManager(store, engine, addonCollectionProvider, addonUpdater)
    }

    val addonCollectionProvider by lazy {
        if(UserPreferences(applicationContext).customAddonCollection){
            AMOAddonsProvider(
                    applicationContext,
                    client,
                    collectionUser = UserPreferences(applicationContext).customAddonCollectionUser,
                    collectionName = UserPreferences(applicationContext).customAddonCollectionName,
                    maxCacheAgeInMinutes = 0,
            )
        }
        else{
            // Use Iceraven's comprehensive addon collection (158 extensions)
            AMOAddonsProvider(
                    applicationContext,
                    client,
                    collectionUser = "16201230",
                    collectionName = "What-I-want-on-Fenix",
                    maxCacheAgeInMinutes = DAY_IN_MINUTES,
                    serverURL = "https://addons.mozilla.org"
            )
        }
    }

    val supportedAddonsChecker by lazy {
        DefaultSupportedAddonsChecker(
                applicationContext, Frequency(
                1,
                TimeUnit.DAYS
        )
        )
    }

    val searchUseCases by lazy {
        SearchUseCases(store, tabsUseCases, sessionUseCases)
    }

    val defaultSearchUseCase by lazy {
        { searchTerms: String ->
            searchUseCases.defaultSearch.invoke(
                    searchTerms = searchTerms,
                    searchEngine = null,
                    parentSessionId = null
            )
        }
    }
    val appLinksUseCases by lazy { AppLinksUseCases(applicationContext) }

    val appLinksInterceptor by lazy {
        AppLinksInterceptor(
                applicationContext,
                launchInApp = {
                    applicationContext.components.preferences.getBoolean(
                            PREF_LAUNCH_EXTERNAL_APP,
                            false
                    )
                }
        )
    }

    val webAppInterceptor by lazy {
        WebAppInterceptor(
                applicationContext,
                webAppManifestStorage
        )
    }

    val fileUploadsDirCleaner: FileUploadsDirCleaner by lazy {
        FileUploadsDirCleaner { applicationContext.cacheDir }
    }

    private val runtime by lazy {
        val builder = GeckoRuntimeSettings.Builder()

        val runtimeSettings = builder
            .aboutConfigEnabled(true)
            .extensionsProcessEnabled(true)
            .debugLogging(false) // SECURITY: Debug logging disabled
            .extensionsWebAPIEnabled(true)
            .contentBlocking(trackingPolicy.toContentBlockingSetting())
            .build()

        runtimeSettings.contentBlocking.setSafeBrowsing(safeBrowsingPolicy)

        if(UserPreferences(applicationContext).safeBrowsing){
            runtimeSettings.contentBlocking.setSafeBrowsingProviders(
                ContentBlocking.GOOGLE_SAFE_BROWSING_PROVIDER,
                ContentBlocking.GOOGLE_LEGACY_SAFE_BROWSING_PROVIDER
            )
            runtimeSettings.contentBlocking.setSafeBrowsingMalwareTable(
                "goog-malware-proto",
                "goog-unwanted-proto"
            )
            runtimeSettings.contentBlocking.setSafeBrowsingPhishingTable(
                "goog-phish-proto"
            )
        }
        else {
            runtimeSettings.contentBlocking.setSafeBrowsingProviders()
            runtimeSettings.contentBlocking.setSafeBrowsingMalwareTable()
            runtimeSettings.contentBlocking.setSafeBrowsingPhishingTable()
        }

        GeckoRuntime.create(applicationContext, runtimeSettings)
    }

    private val trackingPolicy by lazy{
        if(UserPreferences(applicationContext).trackingProtection) EngineSession.TrackingProtectionPolicy.recommended()
        else EngineSession.TrackingProtectionPolicy.none()
    }

    private val safeBrowsingPolicy by lazy{
        if(UserPreferences(applicationContext).safeBrowsing) ContentBlocking.SafeBrowsing.DEFAULT
        else ContentBlocking.SafeBrowsing.NONE
    }

    val webAppManifestStorage by lazy { ManifestStorage(applicationContext) }
    private val webAppShortcutManager by lazy { WebAppShortcutManager(
            applicationContext,
            client,
            webAppManifestStorage
    ) }
    val webAppUseCases by lazy { WebAppUseCases(applicationContext, store, webAppShortcutManager) }
    val webAppManager by lazy { com.prirai.android.nira.webapp.WebAppManager(applicationContext) }
    val webAppNotificationManager by lazy { com.prirai.android.nira.webapp.WebAppNotificationManager(applicationContext) }
    val webAppInstallationManager by lazy { com.prirai.android.nira.webapp.WebAppInstallationManager(applicationContext) }
    val webAppUpdateManager by lazy { com.prirai.android.nira.webapp.WebAppUpdateManager(applicationContext) }
    val pwaSuggestionManager by lazy { com.prirai.android.nira.webapp.PwaSuggestionManager(applicationContext) }

    val tabsUseCases: TabsUseCases by lazy { TabsUseCases(store) }
    val downloadsUseCases: DownloadsUseCases by lazy { DownloadsUseCases(store, applicationContext) }
    val contextMenuUseCases: ContextMenuUseCases by lazy { ContextMenuUseCases(store) }
    
    // Custom Tabs
    val customTabsStore by lazy { CustomTabsServiceStore() }
    
    val customTabsUseCases by lazy { 
        mozilla.components.feature.tabs.CustomTabsUseCases(store, sessionUseCases.loadUrl) 
    }
}