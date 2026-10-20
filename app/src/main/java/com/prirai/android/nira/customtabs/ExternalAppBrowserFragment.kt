package com.prirai.android.nira.customtabs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.FragmentBrowserBinding
import com.prirai.android.nira.downloads.DownloadService
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.integration.ContextMenuIntegration
import com.prirai.android.nira.integration.FindInPageIntegration
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.customtabs.CustomTabWindowFeature
import mozilla.components.feature.downloads.DownloadsFeature
import mozilla.components.feature.downloads.manager.FetchDownloadManager
import mozilla.components.feature.media.fullscreen.MediaSessionFullscreenFeature
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.feature.session.PictureInPictureFeature
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.session.SwipeRefreshFeature
import mozilla.components.feature.sitepermissions.SitePermissionsFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ActivityResultHandler
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import kotlin.math.pow

/**
 * Fragment for external app browsing with custom header UI but using Mozilla's core features.
 * Combines custom branding/UI with Mozilla's intent processing and session management.
 */
class ExternalAppBrowserFragment : Fragment(), UserInteractionHandler, ActivityResultHandler {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    
    var customTabSessionId: String? = null
    
    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()
    private val customTabWindowFeature = ViewBoundFeatureWrapper<CustomTabWindowFeature>()
    private val swipeRefreshFeature = ViewBoundFeatureWrapper<SwipeRefreshFeature>()
    private val promptsFeature = ViewBoundFeatureWrapper<PromptFeature>()
    private val findInPageIntegration = ViewBoundFeatureWrapper<FindInPageIntegration>()
    private val downloadsFeature = ViewBoundFeatureWrapper<DownloadsFeature>()
    private val contextMenuIntegration = ViewBoundFeatureWrapper<ContextMenuIntegration>()
    private val sitePermissionsFeature = ViewBoundFeatureWrapper<SitePermissionsFeature>()
    private val fullScreenMediaSessionFeature = ViewBoundFeatureWrapper<MediaSessionFullscreenFeature>()

    // Picture-in-picture: wired at onViewCreated once we have a session
    // id, invoked from the Minimize button in the header.
    private var pipFeature: PictureInPictureFeature? = null

    private var customTabHeader: LinearLayout? = null
    private var customTabTitle: TextView? = null
    private var customTabUrl: TextView? = null
    private var customTabMenuButton: ImageButton? = null
    private var customTabCloseButton: ImageButton? = null
    private var customTabMinimizeButton: ImageButton? = null

    // Upstream-style title/url observer state: once we have shown a
    // real title we stay in "showing a title" mode and fall back to
    // the URL only on subsequent URL changes with an empty title. This
    // avoids the stuck-"Loading..." symptom where the title briefly
    // emptied mid-load and we overwrote the just-shown real title.
    private var observedTitle: String? = null
    private var observedUrl: String? = null
    private var showedTitleOnce: Boolean = false
    
    private var engineView: EngineView? = null
    
    // Custom tab theming colors
    private var toolbarColor: Int = -1
    private var textColor: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        customTabSessionId = arguments?.getString("activeSessionId")
        
        toolbarColor = arguments?.getInt(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, -1) ?: -1
        
        if (toolbarColor != -1) {
            textColor = if (isColorLight(toolbarColor)) {
                "#000000".toColorInt()
            } else {
                "#FFFFFF".toColorInt()
            }
        }
        
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        customTabSessionId = customTabSessionId ?: arguments?.getString("activeSessionId")
        val sessionId = customTabSessionId ?: return
        val tab = requireContext().components.store.state.findCustomTab(sessionId) ?: return
        
        // Setup edge-to-edge
        setupEdgeToEdge()
        
        engineView = binding.engineView
        
        // Add custom header UI
        addCustomHeader(view, tab)
        
        // Adjust content padding for header
        setupCustomTabContentPadding(view)
        
        // Initialize features
        initializeFeatures(sessionId)
        
        // Setup Find in Page
        setupCustomFindInPage(sessionId)
        
        // Observe tab changes
        observeTabChanges(sessionId)
    }
    
    private fun setupEdgeToEdge() {
        // Let header handle its own insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.browserLayout) { _, insets ->
            insets
        }
    }
    
    private fun initializeFeatures(sessionId: String) {
        val components = requireContext().components
        
        // Session feature - connects engine to tab
        sessionFeature.set(
            feature = SessionFeature(
                components.store,
                components.sessionUseCases.goBack,
                components.sessionUseCases.goForward,
                binding.engineView,
                sessionId
            ),
            owner = this,
            view = binding.root
        )

        promptsFeature.set(
            feature = PromptFeature(
                fragment = this,
                store = components.store,
                customTabId = sessionId,
                tabsUseCases = components.tabsUseCases,
                fragmentManager = childFragmentManager,
                fileUploadsDirCleaner = components.fileUploadsDirCleaner,
                onNeedToRequestPermissions = { permissions ->
                    requestPermissions(permissions, REQUEST_CODE_PROMPT_PERMISSIONS)
                }
            ),
            owner = this,
            view = binding.root
        )

        sitePermissionsFeature.set(
            feature = SitePermissionsFeature(
                context = requireContext(),
                storage = components.permissionStorage,
                fragmentManager = childFragmentManager,
                // Same styling as the main BrowserFragment path so custom
                // tabs do not fall back to AC's default (uncentered, no
                // accent colors). Mirrors BaseBrowserFragment#initializeUI;
                // see that call-site for why shouldWidthMatchParent = true.
                promptsStyling = SitePermissionsFeature.PromptsStyling(
                    gravity = android.view.Gravity.CENTER,
                    shouldWidthMatchParent = true,
                    positiveButtonBackgroundColor = com.prirai.android.nira.R.color.secondary_icon,
                    positiveButtonTextColor = com.prirai.android.nira.R.color.photonWhite
                ),
                // Mirrors BaseBrowserFragment#initializeUI: pre-check the
                // "Remember decision for this site" box so the user's
                // choice persists by default. See the main-fragment call-
                // site for the full rationale.
                dialogConfig = SitePermissionsFeature.DialogConfig(
                    shouldPreselectDoNotAskAgain = true,
                ),
                sessionId = sessionId,
                onNeedToRequestPermissions = { permissions ->
                    requestPermissions(permissions, REQUEST_CODE_APP_PERMISSIONS)
                },
                onShouldShowRequestPermissionRationale = { shouldShowRequestPermissionRationale(it) },
                store = components.store
            ),
            owner = this,
            view = binding.root
        )

        fullScreenMediaSessionFeature.set(
            feature = MediaSessionFullscreenFeature(
                requireActivity(),
                components.store,
                sessionId
            ),
            owner = this,
            view = binding.root
        )

        // Picture-in-picture: construct eagerly so the Minimize menu
        // entry has a target without having to lazy-create. The feature
        // is a thin wrapper around the host Activity's enterPipMode
        // intents and does not retain references we need to tear down.
        pipFeature = PictureInPictureFeature(
            store = components.store,
            activity = requireActivity(),
            tabId = sessionId,
        )
        
        // Custom tab window feature - handles opening links in same window
        customTabWindowFeature.set(
            feature = CustomTabWindowFeature(
                activity = requireActivity(),
                store = components.store,
                sessionId = sessionId
            ),
            owner = this,
            view = binding.root
        )
        
        // Swipe refresh feature - enables pull to reload
        swipeRefreshFeature.set(
            feature = SwipeRefreshFeature(
                components.store,
                components.sessionUseCases.reload,
                binding.swipeRefresh,
                { },
                sessionId
            ),
            owner = this,
            view = binding.root
        )
        
        // Context menu feature - long press on links
        contextMenuIntegration.set(
            feature = ContextMenuIntegration(
                context = requireContext(),
                fragmentManager = parentFragmentManager,
                browserStore = components.store,
                tabsUseCases = components.tabsUseCases,
                contextMenuUseCases = components.contextMenuUseCases,
                parentView = binding.root,
                sessionId = sessionId,
                engineView = binding.engineView
            ),
            owner = this,
            view = binding.root
        )
        
        downloadsFeature.set(
            feature = DownloadsFeature(
                requireContext().applicationContext,
                store = components.store,
                useCases = components.downloadsUseCases,
                fragmentManager = childFragmentManager,
                shouldForwardToThirdParties = {
                    UserPreferences(requireContext()).promptExternalDownloader
                },
                downloadFileUtils = mozilla.components.support.utils.DefaultDownloadFileUtils(
                    context = requireContext().applicationContext,
                    downloadLocation = {
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        ).path
                    }
                ),
                downloadManager = FetchDownloadManager(
                    requireContext().applicationContext,
                    components.store,
                    DownloadService::class,
                    notificationsDelegate = components.notificationsDelegate
                ),
                tabId = sessionId,
                onNeedToRequestPermissions = { permissions ->
                    requestPermissions(permissions, REQUEST_CODE_DOWNLOAD_PERMISSIONS)
                }
            ),
            owner = this,
            view = binding.root
        )
        
        // Setup keyboard adjustment
        setupKeyboardAdjustment()
    }
    
    /**
     * Adjusts engine view bottom padding when keyboard appears to prevent content overlap.
     * Uses WindowInsets to detect keyboard visibility and automatically adjust layout.
     */
    private fun setupKeyboardAdjustment() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.browserLayout) { _, insets ->
            val imeInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val systemBarsInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())

            val keyboardHeight = (imeInsets.bottom - systemBarsInsets.bottom).coerceAtLeast(0)
            val layoutParams = binding.swipeRefresh.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
                ?: return@setOnApplyWindowInsetsListener insets

            if (layoutParams.bottomMargin != keyboardHeight) {
                layoutParams.bottomMargin = keyboardHeight
                binding.swipeRefresh.layoutParams = layoutParams
                binding.swipeRefresh.requestLayout()
            }

            insets
        }

        androidx.core.view.ViewCompat.requestApplyInsets(binding.browserLayout)
    }
    
    /**
     * Pad the browser content so it does not render under the custom-
     * tab header. The side on which padding is applied follows the
     * header's mount position (top vs. bottom), which in turn follows
     * [UserPreferences.shouldUseBottomToolbar].
     */
    private fun setupCustomTabContentPadding(view: View) {
        val browserWindow = view.findViewById<ViewGroup>(R.id.browserWindow) ?: return
        val browserLayout = binding.browserLayout

        browserWindow.post {
            val headerWrapper = browserWindow.findViewWithTag<View>("customTabHeaderWrapper")
                ?: return@post
            val headerHeight = headerWrapper.height
            val bottom = UserPreferences(requireContext()).shouldUseBottomToolbar
            if (bottom) {
                browserLayout.setPadding(0, 0, 0, headerHeight)
            } else {
                browserLayout.setPadding(0, headerHeight, 0, 0)
            }
        }
    }

    /**
     * Mount the custom-tab header at the top (default) or the bottom
     * of the fragment, depending on
     * [UserPreferences.shouldUseBottomToolbar]. The system-insets
     * listener pads the matching side (status-bar when top-mounted,
     * navigation-bar when bottom-mounted) so the header never sits
     * under a system bar.
     */
    private fun addCustomHeader(view: View, tab: SessionState) {
        val headerView = layoutInflater.inflate(R.layout.custom_tab_header, null)

        // Find the main constraint layout (browserWindow), not browserLayout
        val browserWindow = view.findViewById<ViewGroup>(R.id.browserWindow) ?: return

        val bottom = UserPreferences(requireContext()).shouldUseBottomToolbar

        val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            if (bottom) {
                bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        }
        headerView.layoutParams = layoutParams
        headerView.elevation = 8f
        headerView.tag = "customTabHeaderWrapper"
        headerView.id = View.generateViewId()

        browserWindow.addView(headerView)

        // Pad the system-bar side opposite the content. When the header
        // is at the top we pad its top for the status bar; when at the
        // bottom we pad its bottom for the navigation bar.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(headerView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            if (bottom) {
                v.setPadding(0, 0, 0, systemBars.bottom)
            } else {
                v.setPadding(0, systemBars.top, 0, 0)
            }
            insets
        }

        headerView.post {
            androidx.core.view.ViewCompat.requestApplyInsets(headerView)
        }

        customTabHeader = headerView.findViewById(R.id.customTabHeader)
        customTabTitle = headerView.findViewById(R.id.customTabTitle)
        customTabUrl = headerView.findViewById(R.id.customTabUrl)
        customTabMenuButton = headerView.findViewById(R.id.customTabMenuButton)
        customTabCloseButton = headerView.findViewById(R.id.customTabCloseButton)
        customTabMinimizeButton = headerView.findViewById(R.id.customTabMinimizeButton)

        applyCustomTabTheming()

        customTabCloseButton?.setOnClickListener {
            closeCustomTab()
        }
        customTabMenuButton?.setOnClickListener { anchor ->
            showCustomTabMenu(anchor)
        }
        customTabMinimizeButton?.setOnClickListener {
            pipFeature?.enterPipModeCompat()
        }

        updateHeaderDisplay(tab)
    }
    
    private fun observeTabChanges(sessionId: String) {
        requireContext().components.store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
            flow.mapNotNull { state -> state.findCustomTab(sessionId) }
                .collect { tab ->
                    updateHeaderDisplay(tab)
                }
        }
    }
    
    /**
     * Mirror of upstream `CustomTabSessionTitleObserver` applied to
     * our custom header's two labels. Rules:
     *   - When a non-empty title arrives, show it as the title line and
     *     latch `showedTitleOnce`.
     *   - When an empty title arrives but we have shown one before,
     *     fall back to the URL as the title line (to avoid the layout
     *     bouncing). This is the key difference from the previous
     *     implementation, which overwrote with "Loading..." every time
     *     the title momentarily emptied and left the user stuck on
     *     that string when the next non-empty title update was debounced
     *     by `flow.ifAnyChanged` elsewhere.
     *   - If we have never shown a title, keep the title line blank
     *     (we show the real URL/origin on the subtitle line, which is
     *     enough signal while the page starts loading).
     *   - The subtitle line is always the parsed host.
     *
     * Only mutate the TextView when the underlying string actually
     * changes to avoid redundant layout passes.
     */
    private fun updateHeaderDisplay(tab: SessionState) {
        val newTitle = tab.content.title
        val newUrl = tab.content.url

        if (newTitle != observedTitle) {
            if (newTitle.isNotEmpty()) {
                customTabTitle?.text = newTitle
                showedTitleOnce = true
            } else if (showedTitleOnce) {
                customTabTitle?.text = newUrl
            }
            observedTitle = newTitle
        }

        if (newUrl != observedUrl) {
            // If we already showed a title and the title is still empty,
            // keep the title line in sync with the URL fallback.
            if (showedTitleOnce && newTitle.isEmpty()) {
                customTabTitle?.text = newUrl
            }
            customTabUrl?.text = if (newUrl.isNotEmpty()) {
                try {
                    java.net.URL(newUrl).host
                } catch (e: Exception) {
                    newUrl
                }
            } else {
                ""
            }
            observedUrl = newUrl
        }
    }

    /**
     * Renders the overflow menu using the same [Material3BrowserMenu]
     * widget as the main browser, so the two surfaces share a visual
     * language. The item set is intentionally filtered to the
     * custom-tab-appropriate subset:
     *   - Find in page
     *   - Share
     *   - Open in Nira (promotes the current URL into a full browser
     *     tab)
     *
     * Minimize is NOT in the overflow menu anymore - it is a dedicated
     * header button so one-tap minimizing matches the chevron on the
     * main browser.
     *
     * Material3BrowserMenu accepts `preferBottom`; we flip it based on
     * whether the custom-tab header is mounted at the top or bottom so
     * the menu always pops away from the header.
     */
    private fun showCustomTabMenu(anchor: View) {
        val menuItems = buildList {
            add(
                com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                    id = "find_in_page",
                    title = getString(R.string.browser_menu_find_in_page),
                    iconRes = R.drawable.ic_baseline_find_in_page,
                    onClick = { findInPageIntegration.get()?.launch() },
                ),
            )
            add(
                com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                    id = "share",
                    title = getString(R.string.share),
                    iconRes = R.drawable.ios_share_24,
                    onClick = { shareCurrentUrl() },
                ),
            )
            add(com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Divider)
            add(
                com.prirai.android.nira.components.menu.Material3BrowserMenu.MenuItem.Action(
                    id = "open_in_browser",
                    title = getString(R.string.browser_menu_open_in_nira),
                    iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_external_link_24,
                    onClick = { openInBrowser() },
                ),
            )
        }

        // When the header is mounted at the bottom we want the menu to
        // pop upward (above the anchor); when at the top we want it to
        // pop downward (below the anchor). Material3BrowserMenu's
        // `preferBottom = true` means "prefer showing the menu below
        // the anchor", so we invert the shouldUseBottomToolbar flag.
        val headerAtBottom = UserPreferences(requireContext()).shouldUseBottomToolbar
        com.prirai.android.nira.components.menu.Material3BrowserMenu(
            requireContext(),
            menuItems,
        ).show(anchor, preferBottom = !headerAtBottom)
    }
    
    private fun applyCustomTabTheming() {
        if (toolbarColor == -1) return

        // Apply background color to header
        customTabHeader?.setBackgroundColor(toolbarColor)

        // Apply text colors
        customTabTitle?.setTextColor(textColor)
        customTabUrl?.setTextColor(textColor)

        // Apply icon tints
        customTabCloseButton?.setColorFilter(textColor)
        customTabMenuButton?.setColorFilter(textColor)
        customTabMinimizeButton?.setColorFilter(textColor)
    }
    
    private fun isColorLight(color: Int): Boolean {
        val red = android.graphics.Color.red(color) / 255.0
        val green = android.graphics.Color.green(color) / 255.0
        val blue = android.graphics.Color.blue(color) / 255.0
        
        val r = if (red <= 0.03928) red / 12.92 else ((red + 0.055) / 1.055).pow(2.4)
        val g = if (green <= 0.03928) green / 12.92 else ((green + 0.055) / 1.055).pow(2.4)
        val b = if (blue <= 0.03928) blue / 12.92 else ((blue + 0.055) / 1.055).pow(2.4)
        
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance > 0.5
    }
    
    /**
     * Wire the find-in-page affordance to a dynamically-inflated
     * stub inside the fragment layout. The previous implementation
     * attempted to cast `customTabHeader.parent` to a
     * `BrowserToolbar` and bailed out when the cast failed (our
     * header is a `LinearLayout`), leaving `findInPageIntegration`
     * never set and the overflow menu entry silently no-op.
     *
     * [FindInPageIntegration.ToolbarInfo.toolbar] has been widened to
     * accept a plain `View`, so we pass the header's root
     * `LinearLayout` directly - the only thing the integration reads
     * off `toolbar` is `height`, which every View exposes.
     *
     * `isToolbarPlacedAtTop` reflects our mount position so the
     * find bar's gravity / translation choices match the toolbar's.
     */
    private fun setupCustomFindInPage(sessionId: String) {
        val stub = view?.findViewById<ViewStub>(R.id.stubFindInPage) ?: return
        val header = customTabHeader ?: return
        val toolbarAtTop = !UserPreferences(requireContext()).shouldUseBottomToolbar

        findInPageIntegration.set(
            feature = FindInPageIntegration(
                store = requireContext().components.store,
                sessionId = sessionId,
                stub = stub,
                engineView = binding.engineView,
                toolbarInfo = FindInPageIntegration.ToolbarInfo(
                    toolbar = header,
                    isToolbarDynamic = false,
                    isToolbarPlacedAtTop = toolbarAtTop,
                ),
                prepareLayout = {},
                restorePreviousLayout = {},
            ),
            owner = this,
            view = requireView(),
        )
    }
    
    private fun shareCurrentUrl() {
        customTabSessionId?.let { sessionId ->
            val customTab = requireContext().components.store.state.findCustomTab(sessionId)
            customTab?.let { tab ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, tab.content.url)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }
        }
    }

    private fun openInBrowser() {
        customTabSessionId?.let { sessionId ->
            val customTab = requireContext().components.store.state.findCustomTab(sessionId)
            customTab?.let { tab ->
                // Create guest tab
                val newTab = mozilla.components.browser.state.state.TabSessionState(
                    id = java.util.UUID.randomUUID().toString(),
                    content = mozilla.components.browser.state.state.ContentState(
                        url = tab.content.url,
                        private = false
                    ),
                    contextId = null,
                    source = SessionState.Source.Internal.NewTab
                )
                
                // Mark as guest tab
                requireContext().components.profileMiddleware.markAsGuestTab(newTab.id)
                
                // Add to store
                requireContext().components.store.dispatch(
                    mozilla.components.browser.state.action.TabListAction.AddTabAction(newTab, select = true)
                )
                
                pauseCustomTabMedia(sessionId)
                requireContext().components.customTabsUseCases.remove(sessionId)
                
                // Launch main browser
                val intent = Intent(requireContext(), BrowserActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("activeSessionId", newTab.id)
                }
                startActivity(intent)
                requireActivity().finish()
            }
        }
    }

    private fun pauseCustomTabMedia(sessionId: String) {
        val tab = requireContext().components.store.state.findCustomTab(sessionId) ?: return
        tab.mediaSessionState?.controller?.pause()
    }

    private fun closeCustomTab() {
        val sessionId = customTabSessionId
        if (sessionId != null) {
            pauseCustomTabMedia(sessionId)
            requireContext().components.customTabsUseCases.remove(sessionId)
        }
        requireActivity().finish()
    }

    override fun onBackPressed(): Boolean {
        if (findInPageIntegration.onBackPressed()) {
            return true
        }
        if (promptsFeature.onBackPressed()) {
            return true
        }
        if (sessionFeature.onBackPressed()) {
            return true
        }
        closeCustomTab()
        return true
    }

    override fun onActivityResult(requestCode: Int, data: Intent?, resultCode: Int): Boolean {
        return promptsFeature.onActivityResult(requestCode, data, resultCode)
    }

    /**
     * Forwards the OS Picture-in-Picture lifecycle callback to
     * [PictureInPictureFeature] so AC can update its media-session
     * controls when the user flips in and out of PiP via the overflow
     * menu's "Minimize" entry (or the system-provided PiP gesture).
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        pipFeature?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_DOWNLOAD_PERMISSIONS -> {
                downloadsFeature.get()?.onPermissionsResult(permissions, grantResults)
            }
            REQUEST_CODE_PROMPT_PERMISSIONS -> {
                promptsFeature.get()?.onPermissionsResult(permissions, grantResults)
            }
            REQUEST_CODE_APP_PERMISSIONS -> {
                sitePermissionsFeature.get()?.onPermissionsResult(permissions, grantResults)
            }
        }
    }
    
    override fun onDestroyView() {
        // Lifecycle-aware features cleaned up automatically
        _binding = null
        super.onDestroyView()
    }
    
    companion object {
        private const val REQUEST_CODE_DOWNLOAD_PERMISSIONS = 1
        private const val REQUEST_CODE_PROMPT_PERMISSIONS = 2
        private const val REQUEST_CODE_APP_PERMISSIONS = 3
    }
}
