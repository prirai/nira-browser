package com.prirai.android.nira

import android.app.Application
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.view.Choreographer
import com.prirai.android.nira.components.Components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
import mozilla.components.support.base.facts.Facts
import mozilla.components.support.base.facts.processor.LogFactProcessor
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.webextensions.WebExtensionSupport
import java.util.concurrent.TimeUnit

class BrowserApp : Application() {

    private val logger = Logger("BrowserApp")

    // Default to Dispatchers.Default so launches that don't explicitly ask for
    // Main don't pile onto the main looper right after draw. Sites that need
    // Main (GeckoView extension installs, store observers, flow collectors
    // that update UI) specify Dispatchers.Main explicitly.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val components by lazy { Components(this) }
    
    // Track startup timing
    private var appStartTime = 0L

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Pre-warm the three SharedPreferences files that BrowserActivity.onCreate
        // touches synchronously on Main (scw_preferences via UserPreferences,
        // browser_preferences via Components.preferences, profile_manager via
        // ProfileManager.getInstance). The in-process SharedPreferences cache
        // is a thread-safe singleton per-file; the first getSharedPreferences
        // call on *any* thread parses the XML off disk, and all subsequent
        // reads on any thread hit the in-memory map for free. By kicking the
        // first reads to a background thread here (before Application.onCreate
        // even runs), the Activity's first-touch Main-thread read almost
        // always finds the file already loaded.
        //
        // We swallow exceptions and ignore results: if the warm thread hasn't
        // finished by the time the Activity reads, we lose the race and the
        // Activity does the parse itself (same behaviour as before this
        // change). We never pay a correctness tax.
        if (base != null) {
            Thread({
                runCatching { base.getSharedPreferences("scw_preferences", MODE_PRIVATE) }
                runCatching { base.getSharedPreferences(Components.BROWSER_PREFERENCES, MODE_PRIVATE) }
                runCatching { base.getSharedPreferences("profile_manager", MODE_PRIVATE) }
            }, "nira-prefs-warmup").apply { isDaemon = true }.start()
        }
    }

    override fun onCreate() {
        appStartTime = System.currentTimeMillis()
        super.onCreate()

        if (!isMainProcess()) {
            return
        }

        // LogFactProcessor forwards every mozilla-components Fact to logcat
        // for the life of the process. That's useful while developing but it
        // is pure CPU + logcat spam in release builds (every URL load, every
        // engine action emits facts). Gate it behind BuildConfig.DEBUG so
        // release users don't pay for it.
        if (BuildConfig.DEBUG) {
            Facts.registerProcessor(LogFactProcessor())
        }

        // CRITICAL: Load NSS native libraries so the Rust megazord can find
        // them via dlopen(). The Rust fxaclient needs NSS for PKCE crypto.
        try {
            System.loadLibrary("mozglue");
            System.loadLibrary("nss3");
            System.loadLibrary("freebl3");
            System.loadLibrary("softokn3");

            // Initialize Rust component infrastructure (NSS, logging, etc.).
            // Use WARN in release so the Rust-side logging bridge is cheap
            // (DEBUG logs every HTTP header, every store action, every
            // syncable-store registration - ~2x the CPU of WARN under load).
            mozilla.components.support.AppServicesInitializer.init(
                mozilla.components.support.AppServicesInitializer.Config(
                    crashReporting = null,
                    logLevel = if (BuildConfig.DEBUG) {
                        mozilla.components.support.base.log.Log.Priority.DEBUG
                    } else {
                        mozilla.components.support.base.log.Log.Priority.WARN
                    },
                )
            )
            mozilla.components.support.rusthttp.RustHttpConfig.setClient(
                lazy { mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient() }
            )
        } catch (e: Exception) {
            android.util.Log.w("BrowserApp", "Rust/NSS init failed (sync may be unavailable)", e)
        }

        // Previously `components.engine.warmUp()` was called inline on Main.
        //
        // Resolving the `components.engine` lazy triggers a chain:
        //   engine -> runtime -> UserJsPreferences.applyTo (disk write) +
        //   GeckoRuntime.create (loads libxul, parses greprefs.js, allocates
        //   the content-process pool, inits NSS/profile dir) +
        //   WebCompatFeature.install (installs the webcompat extension).
        //
        // On a mid-range device cold start that chain is 150-400 ms, all on
        // Main. GeckoRuntime.create is documented as safe to call off the
        // Android main thread; `WebCompatFeature.install` internally uses
        // the engine's own threading, so it does not require Main either.
        //
        // We kick the chain off on a background Thread. Kotlin's `by lazy`
        // default `SYNCHRONIZED` mode guarantees that any later call to
        // `components.engine` from any thread either (a) returns the
        // already-resolved value for free, or (b) blocks on the lazy's
        // monitor until the background thread's resolution finishes. Both
        // paths see a fully-built engine, so `BrowserActivity.onCreateView`
        // -> `components.engine.createView` stays correct on Main - it just
        // might wait briefly for the background to finish.
        //
        // The warm-up call is placed after resolution because warmUp needs
        // the live engine; it is itself a cheap "run some async pre-warm
        // tasks on gecko threads" call once the runtime exists.
        Thread({
            try {
                components.engine.warmUp()
            } catch (t: Throwable) {
                android.util.Log.e("BrowserApp", "engine.warmUp failed", t)
            }
        }, "nira-engine-warmup").apply {
            // priority 1 above default so the lazy resolves promptly without
            // pre-empting UI work on dual-A53 class devices
            priority = Thread.NORM_PRIORITY + 1
            isDaemon = true
        }.start()

        // Theme + DynamicColors are applied in BrowserActivity.onCreate before
        // super.onCreate - the Application-level pass was redundant work
        // (AppCompatDelegate.setDefaultNightMode is a no-op on repeat calls
        // but still touches resources, and DynamicColors.applyToActivitiesIfAvailable
        // registered an ActivityLifecycleCallbacks that fires on every activity
        // create - we don't need both the Application-wide hook *and* the
        // per-Activity apply call).

        logger.info("App onCreate completed in ${System.currentTimeMillis() - appStartTime}ms")

        // CRITICAL: Restore browser state as early as possible but async
        // This ensures tabs are available quickly without blocking
        restoreBrowserState()

        // Defer heavy initialization until **after the first frame is drawn**.
        //
        // Previously this was `applicationScope.launch(Dispatchers.Main) { ... }`,
        // which only posts to the main looper's message queue; the runnable
        // runs on the next main-loop iteration, which is almost always
        // *before* the Activity's first draw completes. That made the
        // "post-first-frame" scheduling name a lie and caused the fxa auth
        // feature, extension installs, PSL prefetch, etc. to pile up on Main
        // in the same frame the Activity is trying to lay out.
        //
        // Choreographer.postFrameCallback fires at the start of the next
        // frame vsync, which is after at least one frame commit has happened
        // on the main thread. That is a real "after first frame" signal.
        // We hop to applicationScope.launch(Dispatchers.Main) inside the
        // callback so initializeAfterFirstFrame runs in a coroutine as before.
        Choreographer.getInstance().postFrameCallback {
            applicationScope.launch(Dispatchers.Main) {
                initializeAfterFirstFrame()
            }
        }
    }

    private fun initializeAfterFirstFrame() {
        logger.info("Starting deferred initialization (${System.currentTimeMillis() - appStartTime}ms after app start)")

        // Install store-level side-effects (icons engine observer,
        // webNotificationFeature delegate, MediaSessionFeature service bind,
        // sleepingTabsManager state-flow collector). All require Main and we
        // are already on Main here (Choreographer frame callback -> Main
        // launch). bootstrapStore is idempotent, so this is safe even if
        // some future caller forces components.store earlier.
        components.bootstrapStore()

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

        // Optional: install the Nira background-playback content script that
        // neutralises YouTube's Page Visibility handler so audio keeps playing
        // when the tab is deselected or the app is backgrounded. Off by default.
        applicationScope.launch(Dispatchers.Main) {
            try {
                val prefs = com.prirai.android.nira.preferences.UserPreferences(this@BrowserApp)
                if (prefs.backgroundPlaybackYoutube) {
                    components.engine.installBuiltInWebExtension(
                        url = "resource://android/assets/extensions/nira-bg-play/",
                        id = "nira-bg-play@prirai.android.nira",
                        onSuccess = { _ ->
                            logger.info("nira-bg-play extension installed")
                        },
                        onError = { err ->
                            android.util.Log.w("NiraBgPlay", "install FAILED", err)
                        }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("NiraBgPlay", "install exception", e)
            }
        }

        // fxaAuthFeature needs to be touched on Main before any FxA redirect
        // URL is processed by AppRequestInterceptor, because its lazy init
        // wires up `appRequestInterceptor.fxaInterceptor`. The old code did
        // this inline during initializeAfterFirstFrame, which forced a 30-100 ms
        // chain (FxaAccountManager construction, Rust appservices classload,
        // GlobalSyncableStoreProvider.configureStore x3) onto the Main-dispatched
        // continuation that runs the moment Choreographer fires.
        //
        // Routing it through visualCompletenessQueue.runIfReadyOrQueue
        // guarantees it runs on Main (the queue uses Dispatchers.Main
        // internally) but after .ready() is called further down in this
        // function - i.e. after everything else in initializeAfterFirstFrame
        // has at least been dispatched. The window between "frame drawn" and
        // "fxa auth feature ready" is still well under a second on any
        // real device, which is far faster than any plausible OAuth callback
        // the user could trigger.
        components.visualCompletenessQueue.runIfReadyOrQueue {
            try {
                components.fxaAuthFeature
                logger.info("FxA auth feature initialized")
            } catch (_: Exception) { /* sync unavailable */ }
        }

        // Initialize Firefox Sync (non-blocking — degrades gracefully if unavailable).
        // Pre-register the three syncable stores from IO so we don't pay the
        // three static-map puts + reflection overhead on Main inside the
        // fxaSyncManager lazy.
        applicationScope.launch(Dispatchers.IO) {
            try {
                components.registerSyncableStores()
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

        // Warm the Public Suffix List once per process on IO. Previously this
        // was done per-Activity-start on the main thread via view.post, which
        // forced the ~200 KB PSL parse into the first-frame budget.
        applicationScope.launch(Dispatchers.IO) {
            components.publicSuffixList.prefetch()
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
        // Previously went through visualCompletenessQueue.runIfReadyOrQueue
        // (which internally launches on Main) only to immediately launch
        // again on IO. We are already past the Choreographer first-frame
        // callback at this point, so one direct IO launch is enough.
        applicationScope.launch(Dispatchers.IO) {
            // Warm up available storage - resolving the lazy opens the
            // Places Rust DB.
            components.historyStorage
            logger.info("Storage warmup completed")
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
            // AC 156 added an explicit `isPrivate` boolean as the 5th
            // parameter (previously the private-flag was inferred from the
            // browsing mode). `selected` is the 4th param - respect the
            // extension's choice instead of hard-coding `selectTab = true`.
            onNewTabOverride = { _, engineSession, url, selected, isPrivate ->
                components.tabsUseCases.addTab(
                    url = url,
                    selectTab = selected,
                    engineSession = engineSession,
                    private = isPrivate,
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
                    applicationScope.launch(Dispatchers.IO) {
                        val profileId = com.prirai.android.nira.browser.profile.ProfileManager
                            .getInstance(this@BrowserApp).getActiveProfile().id
                        com.prirai.android.nira.browser.profile.ProfileAddonPolicy
                            .applyForProfile(this@BrowserApp, profileId)
                    }
                },
                onUpdatePermissionRequest = components.addonUpdater::onUpdatePermissionRequest,
            )
        } catch (e: UnsupportedOperationException) {
            Logger.error("Failed to initialize web extension support", e)
        }
    }

    private fun restoreBrowserState() {
        // Previously launched on Dispatchers.Main. tabsUseCases.restore does
        // its own suspending disk I/O and action dispatch, and
        // sessionStorage.autoSave(...).periodicallyInForeground(...) sets up a
        // periodic save timer that does not need the main thread. Running
        // this on Main meant deserialising the whole session file (potentially
        // multi-megabyte) on the UI thread, blocking the first frame. IO is
        // the correct dispatcher - the store itself is thread-safe and the
        // internal pipeline hops back to Main for state reductions.
        applicationScope.launch(Dispatchers.IO) {
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
            components.sleepingTabsManager.onTrimMemory(components.store, level)
        }
    }
}
