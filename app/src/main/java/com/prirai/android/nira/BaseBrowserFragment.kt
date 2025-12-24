package com.prirai.android.nira

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.prirai.android.nira.R
import com.prirai.android.nira.addons.WebExtensionPromptFeature
import com.prirai.android.nira.browser.BrowsingMode
import com.prirai.android.nira.settings.HomepageChoice
import com.prirai.android.nira.browser.SwipeGestureLayout
// import com.prirai.android.nira.browser.home.HomeFragmentDirections // Removed - using BrowserFragment for homepage
import com.prirai.android.nira.components.StoreProvider
import com.prirai.android.nira.components.toolbar.BrowserFragmentStore
import com.prirai.android.nira.components.toolbar.BrowserFragmentState
import com.prirai.android.nira.components.toolbar.BrowserInteractor
import com.prirai.android.nira.components.toolbar.BrowserToolbarViewInteractor
import com.prirai.android.nira.components.toolbar.DefaultBrowserToolbarController
import com.prirai.android.nira.components.toolbar.DefaultBrowserToolbarMenuController
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.databinding.FragmentBrowserBinding
import com.prirai.android.nira.downloads.DownloadService
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.components.FindInPageComponent
import com.prirai.android.nira.integration.ContextMenuIntegration
import com.prirai.android.nira.integration.ReaderModeIntegration
import com.prirai.android.nira.integration.ReloadStopButtonIntegration
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.findTabOrCustomTab
import mozilla.components.browser.state.selector.findTabOrCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.CustomTabSessionState
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.app.links.AppLinksFeature
import mozilla.components.feature.downloads.DownloadsFeature
import mozilla.components.feature.downloads.manager.FetchDownloadManager
import mozilla.components.feature.intent.ext.EXTRA_SESSION_ID
import mozilla.components.feature.media.fullscreen.MediaSessionFullscreenFeature
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.feature.search.SearchFeature
import mozilla.components.feature.session.FullScreenFeature
import mozilla.components.feature.session.PictureInPictureFeature
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.session.SwipeRefreshFeature
import mozilla.components.feature.sitepermissions.SitePermissionsFeature
import com.prirai.android.nira.auth.PasskeyAuthFeature
import mozilla.components.lib.state.ext.consumeFlow
import mozilla.components.support.base.feature.ActivityResultHandler
import mozilla.components.support.base.feature.PermissionsFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.base.log.logger.Logger.Companion.debug
import mozilla.components.support.ktx.android.view.enterImmersiveMode
import mozilla.components.support.ktx.android.view.exitImmersiveMode
import mozilla.components.support.ktx.android.view.hideKeyboard
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged
import mozilla.components.support.locale.ActivityContextWrapper
import mozilla.components.support.utils.ext.requestInPlacePermissions
import com.prirai.android.nira.browser.home.SharedViewModel
import java.lang.ref.WeakReference
import mozilla.components.ui.widgets.behavior.EngineViewClippingBehavior as OldEngineViewClippingBehavior
import mozilla.components.ui.widgets.behavior.ViewPosition as OldToolbarPosition

/**
 * Base fragment extended by [BrowserFragment].
 * This class only contains shared code focused on the main browsing content.
 * UI code specific to the app or to custom tabs can be found in the subclasses.
 */
@ExperimentalCoroutinesApi
@Suppress("TooManyFunctions", "LargeClass")
abstract class BaseBrowserFragment : Fragment(), UserInteractionHandler, ActivityResultHandler,
    AccessibilityManager.AccessibilityStateChangeListener {

    private lateinit var browserFragmentStore: BrowserFragmentStore
    private lateinit var browserAnimator: BrowserAnimator

    private var _browserInteractor: BrowserToolbarViewInteractor? = null
    protected val browserInteractor: BrowserToolbarViewInteractor
        get() = _browserInteractor!!

    // Unified Toolbar System
    @VisibleForTesting
    @Suppress("VariableNaming")
    internal var _unifiedToolbar: com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar? = null

    @VisibleForTesting
    internal val unifiedToolbar: com.prirai.android.nira.components.toolbar.unified.UnifiedToolbar?
        get() = _unifiedToolbar

    protected val thumbnailsFeature = ViewBoundFeatureWrapper<BrowserThumbnails>()

    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()
    private val contextMenuIntegration = ViewBoundFeatureWrapper<ContextMenuIntegration>()
    private val downloadsFeature = ViewBoundFeatureWrapper<DownloadsFeature>()
    private val appLinksFeature = ViewBoundFeatureWrapper<AppLinksFeature>()
    private val promptsFeature = ViewBoundFeatureWrapper<PromptFeature>()
    private var findInPageComponent: FindInPageComponent? = null
    private val sitePermissionsFeature = ViewBoundFeatureWrapper<SitePermissionsFeature>()
    private val fullScreenFeature = ViewBoundFeatureWrapper<FullScreenFeature>()
    
    // Web content position manager for toolbar and keyboard handling
    private var webContentPositionManager: com.prirai.android.nira.browser.WebContentPositionManager? = null
    private val swipeRefreshFeature = ViewBoundFeatureWrapper<SwipeRefreshFeature>()
    private val webExtensionPromptFeature = ViewBoundFeatureWrapper<WebExtensionPromptFeature>()
    private var fullScreenMediaSessionFeature =
        ViewBoundFeatureWrapper<MediaSessionFullscreenFeature>()
    private val searchFeature = ViewBoundFeatureWrapper<SearchFeature>()
    private val passkeyAuthFeature = ViewBoundFeatureWrapper<PasskeyAuthFeature>()
    private var pipFeature: PictureInPictureFeature? = null
    val readerViewFeature = ViewBoundFeatureWrapper<ReaderModeIntegration>()
    private val reloadStopButtonFeature = ViewBoundFeatureWrapper<ReloadStopButtonIntegration>()

    var customTabSessionId: String? = null

    @VisibleForTesting
    internal var browserInitialized: Boolean = false
    private var initUIJob: Job? = null
    protected var webAppToolbarShouldBeVisible = true

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var _binding: FragmentBrowserBinding? = null
    protected val binding get() = _binding!!

    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // For custom tabs: read EXTRA_SESSION_ID (used by external apps)
        // For normal browsing: customTabSessionId should be null so SessionFeature follows selected tab
        customTabSessionId = requireArguments().getString(EXTRA_SESSION_ID)

        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        val view = binding.root
        
        // Note: activeSessionId is provided during navigation but we DON'T need to select it again here
        // because it's already selected in the calling fragment (ComposeHomeFragment) before navigation
        // getCurrentTab() will return the already-selected tab which is what we want

        val activity = activity as BrowserActivity
        val originalContext = ActivityContextWrapper.getOriginalContext(activity)
        binding.engineView.setActivityContext(originalContext)

        browserFragmentStore = StoreProvider.get(this) {
            BrowserFragmentStore(
                BrowserFragmentState()
            )
        }

        return view
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initializeUI(view)

        if (customTabSessionId == null) {
            // We currently only need this observer to navigate to home
            // in case all tabs have been removed on startup. No need to
            // this if we have a known session to display.
            observeRestoreComplete(requireContext().components.store, findNavController())
        }

        observeTabSelection(requireContext().components.store)
    }

    private fun initializeUI(view: View) {
        val tab = getCurrentTab()
        browserInitialized = if (tab != null) {
            initializeUI(view, tab)
            true
        } else {
            false
        }
    }

    @Suppress("ComplexMethod", "LongMethod")
    @CallSuper
    internal open fun initializeUI(view: View, tab: SessionState) {
        val context = requireContext()
        val store = context.components.store
        val activity = requireActivity() as BrowserActivity

        // Setup edge-to-edge for fragment - don't apply padding to browserLayout
        setupEdgeToEdgeForFragment()

        val toolbarHeight = resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)

        browserAnimator = BrowserAnimator(
            fragment = WeakReference(this),
            engineView = WeakReference(binding.engineView),
            swipeRefresh = WeakReference(binding.swipeRefresh),
            viewLifecycleScope = WeakReference(viewLifecycleOwner.lifecycleScope)
        ).apply {
            beginAnimateInIfNecessary()
        }

        val openInFenixIntent = Intent(context, IntentReceiverActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(BrowserActivity.OPEN_TO_BROWSER, true)
        }

        val browserToolbarController = DefaultBrowserToolbarController(
            store = store,
            activity = activity,
            navController = findNavController(),
            engineView = binding.engineView,
            customTabSessionId = customTabSessionId,
            onTabCounterClicked = {
                thumbnailsFeature.get()?.requestScreenshot()

                // Show the new bottom sheet tabs dialog
                val tabsBottomSheet =
                    com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.newInstance()
                tabsBottomSheet.show(
                    parentFragmentManager,
                    com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment.TAG
                )
            }
        )
        val browserToolbarMenuController = DefaultBrowserToolbarMenuController(
            activity = activity,
            navController = findNavController(),
            findInPageLauncher = { findInPageComponent?.show() },
            browserAnimator = browserAnimator,
            customTabSessionId = customTabSessionId,
            store = store,
        )

        _browserInteractor = BrowserInteractor(
            browserToolbarController,
            browserToolbarMenuController
        )

        // Initialize UnifiedToolbar for subclasses
        initializeUnifiedToolbar(view, tab)

        // Set up custom Find in Page component
        setupFindInPage(view)

        contextMenuIntegration.set(
            feature = ContextMenuIntegration(
                context = requireContext(),
                fragmentManager = parentFragmentManager,
                browserStore = components.store,
                tabsUseCases = components.tabsUseCases,
                contextMenuUseCases = components.contextMenuUseCases,
                parentView = view,
                sessionId = customTabSessionId
            ),
            owner = this,
            view = view
        )

        // Setup reader view and reload/stop button features using unifiedToolbar's browser toolbar
        unifiedToolbar?.getBrowserToolbar()?.let { toolbar ->
            readerViewFeature.set(
                feature = ReaderModeIntegration(
                    requireContext(),
                    components.engine,
                    components.store,
                    toolbar,
                    binding.readerViewBar,
                    binding.readerViewAppearanceButton
                ),
                owner = this,
                view = view
            )

            // Reload/Stop button is now handled by UnifiedToolbar
            // This avoids duplicate reload buttons when UnifiedToolbar is used
            // reloadStopButtonFeature.set(
            //     feature = ReloadStopButtonIntegration(
            //         context = requireContext(),
            //         store = components.store,
            //         toolbar = toolbar,
            //         onReload = { components.sessionUseCases.reload() },
            //         onStop = {
            //             components.store.state.selectedTab?.let {
            //                 components.sessionUseCases.stopLoading.invoke(it.id)
            //             }
            //         }
            //     ),
            //     owner = this,
            //     view = view
            // )
        }

        promptsFeature.set(
            PromptFeature(
                fragment = this,
                store = components.store,
                tabsUseCases = components.tabsUseCases,
                fragmentManager = parentFragmentManager,
                fileUploadsDirCleaner = components.fileUploadsDirCleaner,
                onNeedToRequestPermissions = { permissions ->
                    requestInPlacePermissions(REQUEST_KEY_PROMPT_PERMISSIONS, permissions) { result ->
                        promptsFeature.get()?.onPermissionsResult(
                            result.keys.toTypedArray(),
                            result.values.map {
                                when (it) {
                                    true -> PackageManager.PERMISSION_GRANTED
                                    false -> PackageManager.PERMISSION_DENIED
                                }
                            }.toIntArray(),
                        )
                    }
                },
            ),
            this,
            view,
        )

        fullScreenMediaSessionFeature.set(
            feature = MediaSessionFullscreenFeature(
                requireActivity(),
                context.components.store,
                customTabSessionId
            ),
            owner = this,
            view = view
        )

        pipFeature = PictureInPictureFeature(
            store = store,
            activity = requireActivity(),
            tabId = customTabSessionId
        )

        appLinksFeature.set(
            feature = AppLinksFeature(
                context,
                store = store,
                sessionId = customTabSessionId,
                fragmentManager = parentFragmentManager,
                launchInApp = { UserPreferences(context).launchInApp },
                loadUrlUseCase = context.components.sessionUseCases.loadUrl
            ),
            owner = this,
            view = view
        )

        sessionFeature.set(
            feature = SessionFeature(
                requireContext().components.store,
                requireContext().components.sessionUseCases.goBack,
                requireContext().components.sessionUseCases.goForward,
                binding.engineView,
                customTabSessionId
            ),
            owner = this,
            view = view
        )

        searchFeature.set(
            feature = SearchFeature(store, customTabSessionId) { request, tabId ->
                val parentSession = store.state.findTabOrCustomTab(tabId)
                val useCase = if (request.isPrivate) {
                    requireContext().components.searchUseCases.newPrivateTabSearch
                } else {
                    requireContext().components.searchUseCases.newTabSearch
                }

                if (parentSession is CustomTabSessionState) {
                    useCase.invoke(request.query)
                    requireActivity().startActivity(openInFenixIntent)
                } else {
                    useCase.invoke(request.query, parentSessionId = parentSession?.id)
                }
            },
            owner = this,
            view = view
        )

        passkeyAuthFeature.set(
            feature = PasskeyAuthFeature(
                engine = requireContext().components.engine,
                activity = requireActivity(),
                store = store,
                onGetTabId = { store.state.selectedTabId }
            ),
            owner = this,
            view = view
        )

        val accentHighContrastColor = R.color.secondary_icon

        sitePermissionsFeature.set(
            feature = SitePermissionsFeature(
                context = context,
                storage = context.components.permissionStorage,
                fragmentManager = parentFragmentManager,
                promptsStyling = SitePermissionsFeature.PromptsStyling(
                    gravity = getAppropriateLayoutGravity(),
                    shouldWidthMatchParent = true,
                    positiveButtonBackgroundColor = accentHighContrastColor,
                    positiveButtonTextColor = R.color.photonWhite
                ),
                sessionId = customTabSessionId,
                onNeedToRequestPermissions = { permissions ->
                    requestPermissions(permissions, REQUEST_CODE_APP_PERMISSIONS)
                },
                onShouldShowRequestPermissionRationale = {
                    shouldShowRequestPermissionRationale(
                        it
                    )
                },
                store = store
            ),
            owner = this,
            view = view
        )

        downloadsFeature.set(
            feature = DownloadsFeature(
                requireContext().applicationContext,
                store = components.store,
                useCases = components.downloadsUseCases,
                fragmentManager = childFragmentManager,
                shouldForwardToThirdParties = { UserPreferences(requireContext()).promptExternalDownloader },
                onDownloadStopped = { download, id, status ->
                    debug("Download ID#$id $download with status $status is done.")
                },
                downloadManager = FetchDownloadManager(
                    requireContext().applicationContext,
                    components.store,
                    DownloadService::class,
                    notificationsDelegate = components.notificationsDelegate
                ),
                tabId = customTabSessionId,
                onNeedToRequestPermissions = { permissions ->
                    requestPermissions(permissions, REQUEST_CODE_DOWNLOAD_PERMISSIONS)
                }),
            owner = this,
            view = view,
        )

        fullScreenFeature.set(
            feature = FullScreenFeature(
                requireContext().components.store,
                requireContext().components.sessionUseCases,
                customTabSessionId,
                ::viewportFitChange,
                ::fullScreenChanged
            ),
            owner = this,
            view = view
        )

        expandToolbarOnNavigation(store)

        binding.swipeRefresh.isEnabled = shouldPullToRefreshBeEnabled()

        if (binding.swipeRefresh.isEnabled) {
            val primaryTextColor = ContextCompat.getColor(context, R.color.primary_icon)
            binding.swipeRefresh.setColorSchemeColors(primaryTextColor)
            swipeRefreshFeature.set(
                feature = SwipeRefreshFeature(
                    requireContext().components.store,
                    context.components.sessionUseCases.reload,
                    binding.swipeRefresh,
                    ({}),
                    customTabSessionId
                ),
                owner = this,
                view = view
            )
        }

        webExtensionPromptFeature.set(
            feature = WebExtensionPromptFeature(
                store = components.store,
                provideAddons = ::provideAddons,
                context = requireContext(),
                fragmentManager = parentFragmentManager,
                onLinkClicked = { url, _ ->
                    components.tabsUseCases.addTab.invoke(url, selectTab = true)
                },
                view = view,
            ),
            owner = this,
            view = view,
        )

        initializeEngineView(toolbarHeight)
        
        // Defer heavy feature initialization to visual completeness queue
        context.components.visualCompletenessQueue.runIfReadyOrQueue {
            // Future: Additional feature initialization can go here
        }
    }
    
    private fun setupFindInPage(view: View) {
        val sessionId = customTabSessionId
        val tabId = sessionId ?: requireContext().components.store.state.selectedTabId ?: return
        val rootLayout = view.findViewById<ViewGroup>(R.id.gestureLayout) ?: return
        
        // Create and attach the Find in Page component
        findInPageComponent = FindInPageComponent(
            context = requireContext(),
            store = requireContext().components.store,
            sessionId = tabId,
            lifecycleOwner = viewLifecycleOwner,
            isCustomTab = sessionId != null
        )
        findInPageComponent?.attach(rootLayout)
    }

    private suspend fun provideAddons(): List<Addon> {
        return withContext(IO) {
            val addons = requireContext().components.addonManager.getAddons(allowCache = false)
            addons
        }
    }

    @VisibleForTesting
    internal fun expandToolbarOnNavigation(store: BrowserStore) {
        consumeFlow(store) { flow ->
            flow.mapNotNull { state ->
                state.findCustomTabOrSelectedTab(customTabSessionId)
            }
                .ifAnyChanged { tab ->
                    arrayOf(tab.content.url, tab.content.loadRequest)
                }
                .collect {
                    findInPageComponent?.onBackPressed()
                    unifiedToolbar?.expand()
                }
        }
    }

    /**
     * The tab associated with this fragment.
     */
    val tab: SessionState
        get() = customTabSessionId?.let { components.store.state.findTabOrCustomTab(it) }
        // Workaround for tab not existing temporarily.
            ?: createTab("about:blank")

    /**
     * Re-initializes [DynamicDownloadDialog] if the user hasn't dismissed the dialog
     * before navigating away from it's original tab.
     * onTryAgain it will use [ContentAction.UpdateDownloadAction] to re-enqueue the former failed
     * download, because [DownloadsFeature] clears any queued downloads onStop.
     * */
    @VisibleForTesting
    internal fun resumeDownloadDialogState(
        sessionId: String?
    ) {
        val savedDownloadState =
            sharedViewModel.downloadDialogState[sessionId]

        if (savedDownloadState == null || sessionId == null) {
            return
        }

        unifiedToolbar?.expand()
    }

    @VisibleForTesting
    internal fun shouldPullToRefreshBeEnabled(): Boolean {
        return UserPreferences(requireContext()).swipeToRefresh
    }

    @VisibleForTesting
    internal fun initializeEngineView(toolbarHeight: Int) {
        val context = requireContext()

        if (UserPreferences(context).hideBarWhileScrolling) {
            // CRITICAL: With UnifiedToolbar, we use simple translation, not dynamic toolbar
            // Dynamic toolbar reserves space in Gecko causing black bars
            // Always set to 0 regardless of toolbar position
            binding.engineView.setDynamicToolbarMaxHeight(0)
            
            // Reset swipeRefresh margins
            val swipeRefreshParams = binding.swipeRefresh.layoutParams as CoordinatorLayout.LayoutParams
            swipeRefreshParams.topMargin = 0
            swipeRefreshParams.bottomMargin = 0
            binding.swipeRefresh.layoutParams = swipeRefreshParams
        } else {
            binding.engineView.setDynamicToolbarMaxHeight(0)

            val swipeRefreshParams =
                binding.swipeRefresh.layoutParams as CoordinatorLayout.LayoutParams
            if (UserPreferences(context).shouldUseBottomToolbar) {
                swipeRefreshParams.bottomMargin = toolbarHeight
                swipeRefreshParams.topMargin = 0
            } else {
                swipeRefreshParams.topMargin = toolbarHeight
                swipeRefreshParams.bottomMargin = 0
            }
            binding.swipeRefresh.layoutParams = swipeRefreshParams
            binding.swipeRefresh.requestLayout()
        }
    }

    @VisibleForTesting
    internal fun observeRestoreComplete(store: BrowserStore, navController: NavController) {
        val activity = activity as BrowserActivity
        consumeFlow(store) { flow ->
            flow.map { state -> state.restoreComplete }
                .distinctUntilChanged()
                .collect { restored ->
                    if (restored) {
                        // Synchronize LRU manager with restored tabs
                        val lruManager = com.prirai.android.nira.browser.tabs.TabLRUManager.getInstance(requireContext())
                        val currentTabIds = store.state.tabs.map { it.id }
                        lruManager.synchronizeWithTabs(currentTabIds)
                        
                        val tabs =
                            store.state.getNormalOrPrivateTabs(
                                activity.browsingModeManager.mode.isPrivate
                            )
                        if (tabs.isEmpty() || store.state.selectedTabId == null) {
                            // Navigate to home fragment first
                            try {
                                if (navController.currentDestination?.id != R.id.homeFragment) {
                                    navController.navigate(R.id.homeFragment)
                                }
                            } catch (e: Exception) {
                                // Navigation failed, create tab as fallback
                            }
                            
                            // Then create a new tab based on homepage preference
                            when (UserPreferences(requireContext()).homepageType) {
                                HomepageChoice.VIEW.ordinal -> {
                                    // Load about:homepage in BrowserFragment (HTML homepage)
                                    components.tabsUseCases.addTab.invoke(
                                        "about:homepage",
                                        selectTab = true
                                    )
                                }

                                HomepageChoice.BLANK_PAGE.ordinal -> {
                                    components.tabsUseCases.addTab.invoke(
                                        "about:blank",
                                        selectTab = true
                                    )
                                }

                                HomepageChoice.CUSTOM_PAGE.ordinal -> {
                                    components.tabsUseCases.addTab.invoke(
                                        UserPreferences(requireContext()).customHomepageUrl,
                                        selectTab = true
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    @VisibleForTesting
    internal fun observeTabSelection(store: BrowserStore) {
        consumeFlow(store) { flow ->
            flow.distinctUntilChangedBy {
                it.selectedTabId
            }
                .mapNotNull {
                    it.selectedTab
                }
                .collect {
                    handleTabSelected(it)
                }
        }
        
        // Also observe when all tabs are closed
        val activity = activity as? BrowserActivity
        consumeFlow(store) { flow ->
            flow.map { state -> 
                state.getNormalOrPrivateTabs(
                    activity?.browsingModeManager?.mode?.isPrivate ?: false
                ).size
            }
            .distinctUntilChanged()
            .collect { tabCount ->
                if (tabCount == 0 && isAdded && view != null) {
                    // All tabs closed, navigate to home and create a new tab
                    try {
                        val navController = findNavController()
                        if (navController.currentDestination?.id != R.id.homeFragment) {
                            navController.navigate(R.id.homeFragment)
                        }
                        
                        // Create a new tab
                        when (UserPreferences(requireContext()).homepageType) {
                            HomepageChoice.VIEW.ordinal -> {
                                components.tabsUseCases.addTab.invoke("about:homepage", selectTab = true)
                            }
                            HomepageChoice.BLANK_PAGE.ordinal -> {
                                components.tabsUseCases.addTab.invoke("about:blank", selectTab = true)
                            }
                            HomepageChoice.CUSTOM_PAGE.ordinal -> {
                                components.tabsUseCases.addTab.invoke(
                                    UserPreferences(requireContext()).customHomepageUrl,
                                    selectTab = true
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore navigation errors
                    }
                }
            }
        }
    }

    private fun handleTabSelected(selectedTab: TabSessionState) {
        if (!this.isRemoving) {
            updateThemeForSession(selectedTab)
        }

        if (browserInitialized) {
            view?.let { view ->
                fullScreenChanged(false)
                // Expand unified toolbar instead of legacy toolbar
                unifiedToolbar?.expand()
                resumeDownloadDialogState(selectedTab.id)
            }
        } else {
            view?.let { view -> initializeUI(view) }
        }
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        val components = requireContext().components

        val preferredColorScheme = components.darkEnabled()
        if (components.engine.settings.preferredColorScheme != preferredColorScheme) {
            components.engine.settings.preferredColorScheme = preferredColorScheme
            components.sessionUseCases.reload()
        }
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()

        components.store.state.findTabOrCustomTabOrSelectedTab(customTabSessionId)?.let {
            updateThemeForSession(it)
        }
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        if (findNavController().currentDestination?.id != R.id.searchDialogFragment) {
            view?.hideKeyboard()
        }
    }

    @CallSuper
    override fun onStop() {
        super.onStop()
        initUIJob?.cancel()

        requireContext().components.store.state.findTabOrCustomTabOrSelectedTab(customTabSessionId)
            ?.let { session ->
                // If we didn't enter PiP, exit full screen on stop
                if (!session.content.pictureInPictureEnabled && fullScreenFeature.onBackPressed()) {
                    fullScreenChanged(false)
                }
            }
    }

    @CallSuper
    override fun onBackPressed(): Boolean {
        // Check features in order of priority
        if (readerViewFeature.onBackPressed()) return true
        if (findInPageComponent?.onBackPressed() == true) return true
        if (fullScreenFeature.onBackPressed()) return true
        if (promptsFeature.onBackPressed()) return true
        if (sessionFeature.onBackPressed()) return true
        
        // As fallback, check if current tab can go back and handle it manually
        val currentTab = requireContext().components.store.state.findTabOrCustomTabOrSelectedTab(customTabSessionId)
        if (currentTab?.content?.canGoBack == true) {
            requireContext().components.sessionUseCases.goBack.invoke(currentTab.id)
            return true
        }
        
        return removeSessionIfNeeded()
    }

    /**
     * Saves the external app session ID to be restored later in [onViewStateRestored].
     */
    final override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CUSTOM_TAB_SESSION_ID, customTabSessionId)
    }

    /**
     * Retrieves the external app session ID saved by [onSaveInstanceState].
     */
    final override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getString(KEY_CUSTOM_TAB_SESSION_ID)?.let {
            if (requireContext().components.store.state.findCustomTab(it) != null) {
                customTabSessionId = it
            }
        }
    }

    /**
     * Forwards permission grant results to one of the features.
     */
    final override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        val feature: PermissionsFeature? = when (requestCode) {
            REQUEST_CODE_DOWNLOAD_PERMISSIONS -> downloadsFeature.get()
            REQUEST_CODE_PROMPT_PERMISSIONS -> promptsFeature.get()
            REQUEST_CODE_APP_PERMISSIONS -> sitePermissionsFeature.get()
            else -> null
        }
        feature?.onPermissionsResult(permissions, grantResults)
    }

    /**
     * Forwards activity results to the [ActivityResultHandler] features.
     */
    override fun onActivityResult(requestCode: Int, data: Intent?, resultCode: Int): Boolean {
        return listOf(
            promptsFeature
        ).any { it.onActivityResult(requestCode, data, resultCode) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        promptsFeature.withFeature { it.onActivityResult(requestCode, data, resultCode) }
    }

    /**
     * Removes the session if it was opened by an ACTION_VIEW intent
     * or if it has a parent session and no more history
     */
    protected open fun removeSessionIfNeeded(): Boolean {
        getCurrentTab()?.let { session ->
            return if (session.source is SessionState.Source.External && !session.restored) {
                activity?.finish()
                requireContext().components.tabsUseCases.removeTab(session.id)
                true
            } else {
                val hasParentSession = session is TabSessionState && session.parentId != null
                if (hasParentSession) {
                    requireContext().components.tabsUseCases.removeTab(session.id, selectParentIfExists = true)
                }
                // We want to return to home if this session didn't have a parent session to select.
                val goToOverview = !hasParentSession
                !goToOverview
            }
        }
        return false
    }

    /**
     * Returns the layout [android.view.Gravity] for the quick settings and ETP dialog.
     */
    protected fun getAppropriateLayoutGravity(): Int =
        UserPreferences(requireContext()).toolbarPositionType.androidGravity

    /**
     * Set the activity normal/private theme to match the current session.
     */
    @VisibleForTesting
    internal fun updateThemeForSession(session: SessionState) {
        val sessionMode = BrowsingMode.fromBoolean(session.content.private)
        (activity as BrowserActivity).browsingModeManager.mode = sessionMode
    }

    @VisibleForTesting
    internal fun getCurrentTab(): SessionState? {
        return requireContext().components.store.state.findCustomTabOrSelectedTab(customTabSessionId)
    }

    override fun onHomePressed() = pipFeature?.onHomePressed() ?: false

    /**
     * Exit fullscreen mode when exiting PIP mode
     */
    private fun pipModeChanged(session: SessionState) {
        if (!session.content.pictureInPictureEnabled && session.content.fullScreen) {
            onBackPressed()
            fullScreenChanged(false)
        }
    }

    final override fun onPictureInPictureModeChanged(enabled: Boolean) {
        pipFeature?.onPictureInPictureModeChanged(enabled)
    }

    private fun viewportFitChange(layoutInDisplayCutoutMode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val layoutParams = activity?.window?.attributes
            layoutParams?.layoutInDisplayCutoutMode = layoutInDisplayCutoutMode
            activity?.window?.attributes = layoutParams
        }
    }

    @VisibleForTesting
    internal open fun fullScreenChanged(inFullScreen: Boolean) {
        if (inFullScreen) {
            // Close find in page bar if opened
            findInPageComponent?.onBackPressed()

            requireActivity().enterImmersiveMode(
                setOnApplyWindowInsetsListener = { key: String, listener: OnApplyWindowInsetsListener ->
                    binding.engineView.addWindowInsetsListener(key, listener)
                },
            )
            (view as? SwipeGestureLayout)?.isSwipeEnabled = false

            // Completely hide and collapse toolbar
            unifiedToolbar?.collapse()
            unifiedToolbar?.visibility = View.GONE
            
            // Reset engine view layout - remove all margins and behaviors
            val browserEngine = binding.swipeRefresh.layoutParams as CoordinatorLayout.LayoutParams
            browserEngine.bottomMargin = 0
            browserEngine.topMargin = 0
            browserEngine.behavior = null
            binding.swipeRefresh.translationY = 0f
            binding.swipeRefresh.layoutParams = browserEngine
            
            // Remove padding from swipeRefresh for true fullscreen
            binding.swipeRefresh.setPadding(0, 0, 0, 0)

            // Disable dynamic toolbar
            binding.engineView.setDynamicToolbarMaxHeight(0)
            binding.engineView.setVerticalClipping(0)
            
            // Notify subclasses about fullscreen change (for modern toolbar system)
            onFullScreenModeChanged(true)
        } else {
            requireActivity().exitImmersiveMode(
                unregisterOnApplyWindowInsetsListener = binding.engineView::removeWindowInsetsListener,
            )
            (view as? SwipeGestureLayout)?.isSwipeEnabled = true

            if (webAppToolbarShouldBeVisible) {
                unifiedToolbar?.visibility = View.VISIBLE
                val toolbarHeight = resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)
                initializeEngineView(toolbarHeight)
                unifiedToolbar?.expand()
            }
            
            // Restore window insets padding to swipeRefresh
            binding.swipeRefresh.requestApplyInsets()
            
            // Notify subclasses about fullscreen change (for modern toolbar system)
            onFullScreenModeChanged(false)
        }

        binding.swipeRefresh.isEnabled = shouldPullToRefreshBeEnabled()
    }
    
    /**
     * Hook for subclasses to handle fullscreen mode changes
     * (e.g., BrowserFragment can hide/show modern toolbar system)
     */
    protected open fun onFullScreenModeChanged(inFullScreen: Boolean) {
        // Default: do nothing, subclasses can override
    }
    
    /**
     * Setup edge-to-edge for the fragment.
     * Ensures proper inset handling for web content including:
     * - System bars (status bar, navigation bar)
     * - Toolbars (address bar, tab bar, contextual bar)
     * - Keyboard (IME)
     */
    private fun setupEdgeToEdgeForFragment() {
        // Initialize web content position manager for comprehensive handling
        // Uses callback functions to get toolbar heights dynamically
        webContentPositionManager = com.prirai.android.nira.browser.WebContentPositionManager(
            engineView = binding.engineView,
            swipeRefreshView = binding.swipeRefresh,
            rootView = binding.root,
            getTopToolbarHeight = { getTopToolbarHeight() },
            getBottomToolbarHeight = { getBottomToolbarHeight() }
        )
        webContentPositionManager?.initialize()
        
        // Make browserLayout pass insets through to children
        ViewCompat.setOnApplyWindowInsetsListener(binding.browserLayout) { _, insets ->
            insets
        }
    }
    
    /**
     * Get the current VISIBLE height of top toolbars (accounting for scroll/collapse)
     */
    private fun getTopToolbarHeight(): Int {
        val prefs = UserPreferences(requireContext())
        return if (prefs.toolbarPosition == ToolbarPosition.TOP.ordinal) {
            val toolbarView = unifiedToolbar?.getToolbarView()
            if (toolbarView != null && toolbarView.height > 0) {
                // Account for translationY - when toolbar scrolls up, translationY is negative
                val visibleHeight = toolbarView.height + toolbarView.translationY.toInt()
                // Ensure we don't return negative values
                maxOf(0, visibleHeight)
            } else {
                resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)
            }
        } else {
            0
        }
    }
    
    /**
     * Get the current height of bottom toolbars
     */
    private fun getBottomToolbarHeight(): Int {
        val prefs = UserPreferences(requireContext())
        val isTopToolbar = prefs.toolbarPosition != ToolbarPosition.BOTTOM.ordinal
        
        return if (isTopToolbar) {
            // In TOP mode: only bottom components (tab bar + contextual toolbar) are at bottom
            var height = 0
            unifiedToolbar?.getTabGroupBar()?.takeIf { it.visibility == View.VISIBLE }?.let {
                height += it.height
            }
            unifiedToolbar?.getContextualToolbar()?.takeIf { it.visibility == View.VISIBLE }?.let {
                height += it.height
            }
            
            // If not measured yet, estimate
            if (height == 0) {
                resources.getDimensionPixelSize(R.dimen.contextual_toolbar_height)
            } else {
                height
            }
        } else {
            // In BOTTOM mode: address bar + tab bar + contextual toolbar all at bottom
            var height = 0
            unifiedToolbar?.getToolbarView()?.let { height += it.height }
            unifiedToolbar?.getTabGroupBar()?.takeIf { it.visibility == View.VISIBLE }?.let {
                height += it.height
            }
            unifiedToolbar?.getContextualToolbar()?.takeIf { it.visibility == View.VISIBLE }?.let {
                height += it.height
            }
            
            // If not measured yet, estimate
            if (height == 0) {
                resources.getDimensionPixelSize(R.dimen.browser_toolbar_height) +
                resources.getDimensionPixelSize(R.dimen.contextual_toolbar_height)
            } else {
                height
            }
        }
    }
    
    /**
     * Request web content position update - should be called when toolbar visibility/size changes
     */
    protected fun requestWebContentPositionUpdate() {
        webContentPositionManager?.requestUpdate()
    }

    /*
     * Dereference these views when the fragment view is destroyed to prevent memory leaks
     */
    /**
     * Initialize UnifiedToolbar - can be overridden by subclasses for custom behavior
     */
    protected open fun initializeUnifiedToolbar(view: View, tab: SessionState) {
        // Default implementation - subclasses can override
        // This is intentionally empty in the base class to allow subclasses full control
    }

    override fun onDestroyView() {
        super.onDestroyView()

        findInPageComponent?.destroy()
        findInPageComponent = null
        webContentPositionManager?.destroy()
        webContentPositionManager = null
        binding.engineView.setActivityContext(null)
        _browserInteractor = null
        _unifiedToolbar = null
        _binding = null
    }

    companion object {
        private const val KEY_CUSTOM_TAB_SESSION_ID = "custom_tab_session_id"
        private const val REQUEST_KEY_PROMPT_PERMISSIONS = "promptFeature"
        private const val REQUEST_CODE_DOWNLOAD_PERMISSIONS = 1
        private const val REQUEST_CODE_PROMPT_PERMISSIONS = 2
        private const val REQUEST_CODE_APP_PERMISSIONS = 3
    }

    override fun onAccessibilityStateChanged(enabled: Boolean) {
        // Toolbar behavior is now handled by UnifiedToolbar
        // No action needed here as UnifiedToolbar handles scroll behavior internally
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Dismiss menu from unified toolbar's browser toolbar
        unifiedToolbar?.getBrowserToolbar()?.dismissMenu()
    }

    // This method is called in response to native web extension messages from
    // content scripts (e.g the reader view extension). By the time these
    // messages are processed the fragment/view may no longer be attached.
    internal fun safeInvalidateBrowserToolbarView() {
        context?.let {
            unifiedToolbar?.getBrowserToolbar()?.invalidateActions()
        }
    }
}
