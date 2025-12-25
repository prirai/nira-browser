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
import mozilla.components.support.base.facts.processor.LogFactProcessor
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.locale.LocaleAwareApplication
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
