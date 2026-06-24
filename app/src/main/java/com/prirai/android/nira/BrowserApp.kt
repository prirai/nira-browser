package com.prirai.android.nira

import com.prirai.android.nira.components.Components
import com.prirai.android.nira.theme.applyAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient
import mozilla.components.support.base.facts.Facts
import mozilla.components.support.base.facts.processor.LogFactProcessor
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.locale.LocaleAwareApplication
import mozilla.components.support.AppServicesInitializer
import mozilla.components.support.base.log.Log
import mozilla.components.support.rusthttp.RustHttpConfig
import mozilla.components.support.rustlog.RustLog
import mozilla.components.support.webextensions.WebExtensionSupport
import java.util.concurrent.TimeUnit

class BrowserApp : LocaleAwareApplication() {

    private val logger = Logger("BrowserApp")
    
    // SECURITY: Use proper application scope instead of GlobalScope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val components by lazy { Components(this) }
    
    // Track startup timing
    private var appStartTime = 0L

    override fun onCreate() {
        appStartTime = System.currentTimeMillis()
        super.onCreate()

        if (!isMainProcess()) {
            return
        }

        Facts.registerProcessor(LogFactProcessor())

        // CRITICAL: Load NSS native libraries so the Rust megazord can find
        // them via dlopen(). The Rust fxaclient needs NSS for PKCE crypto
        // during beginAuthentication(). Without pre-loading, dlopen fails and
        // we get "NSS has not initialized" despite RustComponentsInitializer.init().
        System.loadLibrary("mozglue")
        System.loadLibrary("nss3")
        System.loadLibrary("freebl3")
        System.loadLibrary("softokn3")

        // CRITICAL: Initialize Rust component infrastructure (NSS, logging, etc.).
        // RustComponentsInitializer.init() loads the megazord and calls the
        // native initialize() function, which initializes NSS for crypto ops.
        AppServicesInitializer.init(
            AppServicesInitializer.Config(
                crashReporting = null,
                logLevel = Log.Priority.DEBUG,
            )
        )
        RustHttpConfig.setClient(lazy { HttpURLConnectionClient() })

        // CRITICAL: Only warmUp the engine - don't trigger full initialization yet
        // This prepares GeckoView but doesn't block on heavy operations
        components.engine.warmUp()

        applyAppTheme(this)
        
        // Apply Material You dynamic colors if enabled
        if (com.prirai.android.nira.theme.ThemeManager.shouldUseDynamicColors(this)) {
            com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this)
        }

        logger.info("App onCreate completed in ${System.currentTimeMillis() - appStartTime}ms")

        // CRITICAL: Restore browser state as early as possible but async
        // This ensures tabs are available quickly without blocking
        restoreBrowserState()

        // OPTIMIZATION: Defer heavy initialization to after first frame is drawn
        applicationScope.launch(Dispatchers.Main) {
            initializeAfterFirstFrame()
        }
    }

    private fun initializeAfterFirstFrame() {
        logger.info("Starting deferred initialization (${System.currentTimeMillis() - appStartTime}ms after app start)")
        
        // Initialize web extensions - MUST run on Main thread due to GeckoView Handler requirements
        applicationScope.launch(Dispatchers.Main) {
            initializeWebExtensions()
        }

        // Install the FxA WebChannel extension and save a reference so
        // BaseBrowserFragment can register a per-session content handler.
        applicationScope.launch(Dispatchers.Main) {
            try {
                components.engine.installBuiltInWebExtension(
                    url = "resource://android/assets/extensions/fxawebchannel/",
                    id = "fxa@mozac.org",
                    onSuccess = { ext ->
                        com.prirai.android.nira.browser.sync.FxaSyncManager.webChannelExtension = ext
                        android.util.Log.d("FxaAuth", "FxA extension install OK")
                    },
                    onError = { err ->
                        android.util.Log.e("FxaAuth", "FxA extension install FAILED", err)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("FxaAuth", "FxA extension install exception", e)
            }
        }
        
        // CRITICAL: Eagerly init fxaAuthFeature on the Main thread so that
        // appRequestInterceptor.fxaInterceptor is set before any FxA redirect URL
        // can be processed. Doing this inside an IO coroutine causes a race condition
        // where the interceptor may not be ready when the OAuth callback arrives.
        // This also triggers fxaSyncManager lazy init which starts FxaAccountManager.
        try {
            components.fxaAuthFeature
            logger.info("FxA auth feature initialized")
        } catch (_: Exception) { /* sync unavailable */ }

        // Start the FxA account manager — this must complete before any
        // authentication call (beginAuthentication, finishAuthentication) can succeed.
        applicationScope.launch(Dispatchers.IO) {
            try {
                components.fxaSyncManager.start()
                logger.info("FxA sync manager start() completed")
            } catch (_: Exception) { /* sync unavailable */ }
        }

        // Collect incoming FxA tabs (e.g. Send Tab from another device)
        applicationScope.launch(Dispatchers.Main) {
            try {
                components.fxaSyncManager.incomingTabs.collect { tabReceived ->
                    val entries = tabReceived.entries
                    entries.forEach { tab ->
                        components.tabsUseCases.addTab(
                            url = tab.url,
                            selectTab = entries.size == 1,
                        )
                    }
                }
            } catch (_: Exception) { /* sync unavailable */ }
        }
        
        // Queue storage warming for after visual completeness
        queueStorageWarmup()
        
        // Warm up web app manifest storage in background
        applicationScope.launch(Dispatchers.IO) {
            components.webAppManifestStorage.warmUpScopes(System.currentTimeMillis())
        }
        
        // Restore downloads in background
        applicationScope.launch(Dispatchers.Main) {
            components.downloadsUseCases.restoreDownloads()
        }
        
        // Mark the visual completeness queue as ready - this will run all queued tasks
        applicationScope.launch(Dispatchers.Main) {
            components.visualCompletenessQueue.ready()
            logger.info("Visual completeness queue ready")
        }
    }
    
    private fun queueStorageWarmup() {
        components.visualCompletenessQueue.runIfReadyOrQueue {
            applicationScope.launch(Dispatchers.IO) {
                // Warm up available storage
                components.historyStorage
                logger.info("Storage warmup completed")
            }
        }
    }

    private fun initializeWebExtensions() {
        try {
            GlobalAddonDependencyProvider.initialize(
                components.addonManager,
                components.addonUpdater,
                onCrash = { logger.error("Addon dependency provider crashed", it) },
            )
            WebExtensionSupport.initialize(
                components.engine,
                components.store,
                onNewTabOverride = { _, engineSession, url ->
                    val shouldCreatePrivateSession =
                        components.store.state.selectedTab?.content?.private ?: false

                    components.tabsUseCases.addTab(
                        url = url,
                        selectTab = true,
                        engineSession = engineSession,
                        private = shouldCreatePrivateSession,
                    )
                },
                onCloseTabOverride = { _, sessionId ->
                    components.tabsUseCases.removeTab(sessionId)
                },
                onSelectTabOverride = { _, sessionId ->
                    components.tabsUseCases.selectTab(sessionId)
                },
                onExtensionsLoaded = { extensions ->
                    components.addonUpdater.registerForFutureUpdates(extensions)
                },
                onUpdatePermissionRequest = components.addonUpdater::onUpdatePermissionRequest,
            )
        } catch (e: UnsupportedOperationException) {
            Logger.error("Failed to initialize web extension support", e)
        }
    }

    private fun restoreBrowserState() {
        applicationScope.launch(Dispatchers.Main) {
            components.tabsUseCases.restore(components.sessionStorage)

            components.sessionStorage.autoSave(components.store)
                .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
                .whenGoingToBackground()
                .whenSessionsChange()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        runOnlyInMainProcess {
            components.icons.onTrimMemory(level)
            components.store.dispatch(SystemAction.LowMemoryAction(level))
        }
    }
}
