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
import mozilla.components.support.base.facts.Facts
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient
import mozilla.components.support.base.facts.processor.LogFactProcessor
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import android.app.Application
import mozilla.components.support.webextensions.WebExtensionSupport
import java.util.concurrent.TimeUnit

class BrowserApp : Application() {

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
        // them via dlopen(). The Rust fxaclient needs NSS for PKCE crypto.
        try {
            System.loadLibrary("mozglue");
            System.loadLibrary("nss3");
            System.loadLibrary("freebl3");
            System.loadLibrary("softokn3");

            // Initialize Rust component infrastructure (NSS, logging, etc.)
            mozilla.components.support.AppServicesInitializer.init(
                mozilla.components.support.AppServicesInitializer.Config(
                    crashReporting = null,
                    logLevel = mozilla.components.support.base.log.Log.Priority.DEBUG,
                )
            )
            mozilla.components.support.rusthttp.RustHttpConfig.setClient(
                lazy { mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient() }
            )
        } catch (e: Exception) {
            android.util.Log.w("BrowserApp", "Rust/NSS init failed (sync may be unavailable)", e)
        }

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
        
        // Install the FxA WebChannel extension for OOB redirect handling
        applicationScope.launch(Dispatchers.Main) {
            try {
                components.engine.installBuiltInWebExtension(
                    url = "resource://android/assets/extensions/fxawebchannel/",
                    id = "fxa@mozac.org",
                    onSuccess = { ext ->
                        com.prirai.android.nira.browser.sync.FxaSyncManager.webChannelExtension = ext
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
        try {
            components.fxaAuthFeature
            logger.info("FxA auth feature initialized")
        } catch (_: Exception) { /* sync unavailable */ }

        // Initialize Firefox Sync (non-blocking — degrades gracefully if unavailable)
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
