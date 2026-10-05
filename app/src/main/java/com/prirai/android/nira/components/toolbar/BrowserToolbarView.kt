package com.prirai.android.nira.components.toolbar

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.utils.ToolbarPopupWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.CustomTabSessionState
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.browser.toolbar.display.DisplayToolbar
import mozilla.components.support.ktx.util.URLStringUtils.toDisplayUrl
import mozilla.components.ui.widgets.behavior.EngineViewScrollingGesturesBehavior
import java.lang.ref.WeakReference
import mozilla.components.ui.widgets.behavior.DependencyGravity as MozacToolbarPosition
import androidx.core.net.toUri

interface BrowserToolbarViewInteractor {
    fun onBrowserToolbarPaste(text: String)
    fun onBrowserToolbarPasteAndGo(text: String)
    fun onBrowserToolbarClicked()
    fun onBrowserToolbarMenuItemTapped(item: ToolbarMenu.Item)
    fun onTabCounterClicked()
    fun onScrolled(offset: Int)
}

@ExperimentalCoroutinesApi
class BrowserToolbarView(
    private val container: ViewGroup,
    private val toolbarPosition: ToolbarPosition,
    private val interactor: BrowserToolbarViewInteractor,
    private val customTabSession: CustomTabSessionState?,
    private val lifecycleOwner: LifecycleOwner,
    private val engineView: mozilla.components.concept.engine.EngineView? = null
) {

    private val settings = UserPreferences(container.context)

    @LayoutRes
    private val toolbarLayout = when (settings.toolbarPosition) {
        ToolbarPosition.BOTTOM.ordinal -> R.layout.component_bottom_browser_toolbar
        else -> R.layout.component_browser_top_toolbar
    }

    private val layout = if (toolbarLayout == R.layout.component_bottom_browser_toolbar && container is CoordinatorLayout) {
        // For bottom toolbar, inflate without adding to container first, then add with proper params
        val inflatedView = LayoutInflater.from(container.context).inflate(toolbarLayout, null, false)
        val toolbarContainer = inflatedView.findViewById<View>(R.id.toolbarContainer) ?: inflatedView
        
        // Create proper CoordinatorLayout.LayoutParams
        val layoutParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
        }
        
        container.addView(toolbarContainer, layoutParams)
        
        inflatedView
    } else {
        // For other layouts, use normal inflation
        LayoutInflater.from(container.context).inflate(toolbarLayout, container, true)
    }

    @VisibleForTesting
    internal var view: BrowserToolbar = layout
        .findViewById(R.id.toolbar)

    // Get the actual container for bottom toolbar
    private val toolbarContainer: View? = if (toolbarLayout == R.layout.component_bottom_browser_toolbar) {
        val container = layout.findViewById<View>(R.id.toolbarContainer)
        container ?: layout
    } else null

    val toolbarIntegration: ToolbarIntegration

    /**
     * Vertical swipe callback on the address bar.
     *
     * `directionUp = true`  -> user swiped up on the bar
     * `directionUp = false` -> user swiped down on the bar
     *
     * BrowserFragment wires this to UnifiedToolbar's minimal-state toggle:
     * swipe down enters minimal, swipe up exits minimal. Taps still open
     * search; long-press still shows the paste menu.
     *
     * The detector fires on either (a) a sustained vertical drag of at
     * least `swipeThresholdPx` with a dominant vertical component, OR
     * (b) a fling with velocity over `swipeVelocityThresholdPx` per second.
     */
    var onToolbarSwipe: ((directionUp: Boolean) -> Unit)? = null

    @VisibleForTesting
    internal val isPwaTabOrTwaTab: Boolean
        get() = false

    // 16 dp is just under a typical system-touch-slop threshold on a
    // high-density display and gives the gesture a snappy feel without
    // firing on accidental finger wobble during a tap.
    private val swipeThresholdPx: Float =
        16f * container.context.resources.displayMetrics.density
    // 300 dp/s is Android's own default swipe-velocity threshold used by
    // ViewPager, SwipeRefreshLayout, etc. Chosen so an intentional fling
    // registers even if the drag distance is small.
    private val swipeVelocityThresholdPx: Float =
        300f * container.context.resources.displayMetrics.density

    init {
        view.display.setOnUrlLongClickListener {
            ToolbarPopupWindow.show(
                WeakReference(view),
                customTabSession?.id,
                interactor::onBrowserToolbarPasteAndGo,
                interactor::onBrowserToolbarPaste
            )
            true
        }

        // Vertical swipe detector. We install this as a non-consuming
        // OnTouchListener on the toolbar root so that tap/long-press flow
        // through to AC's internal handlers unchanged - we only observe
        // the stream and emit a swipe signal on fling or sustained drag.
        val swipeGesture = android.view.GestureDetector(
            container.context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 == null) return false
                    val dx = kotlin.math.abs(e2.x - e1.x)
                    val dy = kotlin.math.abs(e2.y - e1.y)
                    // Only treat predominantly vertical motion as a swipe,
                    // otherwise horizontal gestures (e.g. tab-switch swipe
                    // on the toolbar) would be hijacked.
                    if (dy <= dx) return false
                    if (kotlin.math.abs(velocityY) < swipeVelocityThresholdPx) return false
                    // velocityY > 0 means the finger moved downwards.
                    val directionUp = velocityY < 0
                    onToolbarSwipe?.invoke(directionUp)
                    return true
                }

                override fun onScroll(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    distanceX: Float,
                    distanceY: Float,
                ): Boolean {
                    if (e1 == null) return false
                    val totalDy = e2.y - e1.y
                    val totalDx = e2.x - e1.x
                    // Same horizontal-vs-vertical check as above, applied
                    // to the total drag from the ACTION_DOWN to right now.
                    if (kotlin.math.abs(totalDy) <= kotlin.math.abs(totalDx)) return false
                    if (kotlin.math.abs(totalDy) < swipeThresholdPx) return false
                    val directionUp = totalDy < 0
                    onToolbarSwipe?.invoke(directionUp)
                    // Return true once we have emitted the signal so
                    // GestureDetector marks the gesture handled and does
                    // not fire again until the next ACTION_DOWN. We still
                    // leave the OnTouchListener non-consuming so the AC
                    // click/long-click pipeline keeps receiving the up event.
                    return true
                }
            }
        )
        layout.setOnTouchListener { v, event ->
            swipeGesture.onTouchEvent(event)
            // Return false so AC's own click/long-click detection on the
            // display toolbar continues to receive this event. A swipe
            // large enough to trigger our detector also drags the finger
            // away from the URL view, so AC's onClick threshold is not
            // crossed and no accidental tap fires.
            false
        }

        with(container.context) {
            val isPinningSupported = components.webAppUseCases.isPinningSupported()

            view.apply {
                setToolbarBehavior()

                // Match Fenix's BrowserToolbarView: elevate the toolbar by
                // browser_fragment_toolbar_elevation (16dp) so it casts a
                // shadow over the EngineView, and let the AC display toolbar
                // draw the inner URL pill via display.setUrlBackground(...)
                // pointing at the same rounded ?attr/colorSurfaceContainerHigh
                // shape Fenix uses (search_url_background). This is the single
                // frictionless integration point the upstream toolbar exposes;
                // any custom padding/margin overrides in the layout XML fight
                // AC's baked mozac_browser_toolbar_displaytoolbar.xml.
                elevation = resources.getDimension(R.dimen.browser_fragment_toolbar_elevation)

                display.setUrlBackground(
                    androidx.appcompat.content.res.AppCompatResources.getDrawable(
                        container.context,
                        R.drawable.toolbar_background
                    )
                )

                // Inset the URL pill 8dp on each side. Nira does not populate
                // the navigation-actions or browser-actions containers on the
                // address bar, so AC's ActionContainer collapses each of them
                // to View.GONE, and the URL background ImageView pins flush
                // against the parent edges (rounded corners invisible).
                // setUrlBackgroundMargins is AC's first-class API for exactly
                // this case: it applies layout_goneMarginStart / goneMarginEnd
                // to the URL background, so ConstraintLayout inserts the
                // requested inset only when the neighbouring action containers
                // are GONE. The progress bar keeps its own edge-to-edge
                // constraint (constraintStart/End="parent"), so this does not
                // shorten the loading indicator.
                val pillInsetPx = (8f * resources.displayMetrics.density).toInt()
                display.setUrlBackgroundMargins(
                    mozilla.components.browser.toolbar.display.DisplayToolbar.DisplayMargins(
                        goneStartMargin = pillInsetPx,
                        goneEndMargin = pillInsetPx,
                    )
                )

                display.onUrlClicked = {
                    interactor.onBrowserToolbarClicked()
                    false
                }

                display.progressGravity = when (toolbarPosition) {
                    ToolbarPosition.BOTTOM -> DisplayToolbar.Gravity.TOP
                    ToolbarPosition.TOP -> DisplayToolbar.Gravity.BOTTOM
                }

                val primaryTextColor = ContextCompat.getColor(
                    container.context,
                    R.color.primary_icon
                )
                val secondaryTextColor = ContextCompat.getColor(
                    container.context,
                    R.color.secondary_icon
                )
                val separatorColor = ContextCompat.getColor(
                    container.context,
                    R.color.primary_icon
                )

                display.urlFormatter =
                    if (UserPreferences(context).showUrlProtocol) {
                            url -> url
                    } else {
                            url -> smartUrlDisplay(url)
                    }

                display.colors = display.colors.copy(
                    text = primaryTextColor,
                    siteInfoIconSecure = primaryTextColor,
                    siteInfoIconInsecure = primaryTextColor,
                    menu = primaryTextColor,
                    hint = secondaryTextColor,
                    separator = separatorColor,
                    trackingProtection = primaryTextColor
                )


                display.hint = context.getString(R.string.search)
            }

            val menuToolbar: ToolbarMenu
            BrowserMenu(
                context = this,
                store = components.store,
                onItemTapped = {
                    it.performHapticIfNeeded(view)
                    interactor.onBrowserToolbarMenuItemTapped(it)
                },
                lifecycleOwner = lifecycleOwner,
                isPinningSupported = isPinningSupported,
                shouldReverseItems = settings.toolbarPosition == ToolbarPosition.TOP.ordinal
            ).also { menuToolbar = it }

            view.display.setMenuDismissAction {
                view.invalidateActions()
            }

            toolbarIntegration = DefaultToolbarIntegration(
                    this,
                    view,
                    menuToolbar,
                    components.historyStorage,
                    lifecycleOwner,
                    sessionId = null,
                    isPrivate = components.store.state.selectedTab?.content?.private ?: false,
                    interactor = interactor,
                    engine = components.engine
                )
        }
    }

    fun expand() {
        // expand only for normal tabs and custom tabs not for PWA or TWA
        if (isPwaTabOrTwaTab) {
            return
        }

        val targetView = if (toolbarLayout == R.layout.component_bottom_browser_toolbar) {
            toolbarContainer ?: layout
        } else {
            view
        }
        
        (targetView.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            (behavior as? EngineViewScrollingGesturesBehavior)?.forceExpand()
        }
    }

    fun collapse() {
        // collapse only for normal tabs and custom tabs not for PWA or TWA. Mirror expand()
        if (isPwaTabOrTwaTab) {
            return
        }

        val targetView = if (toolbarLayout == R.layout.component_bottom_browser_toolbar) {
            toolbarContainer ?: layout
        } else {
            view
        }
        
        (targetView.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            (behavior as? EngineViewScrollingGesturesBehavior)?.forceCollapse()
        }
    }

    /**
     * Pins the address bar to a fixed position - it never scrolls off-screen.
     *
     * The address bar is always the user's one guaranteed anchor to tap into
     * search, see the URL, and swipe to toggle minimal state. Previously this
     * installed AC's `EngineViewScrollingGesturesBehavior` which translated
     * the whole toolbar off-screen 1:1 with page scroll; that is intentionally
     * removed so page scrolling now only toggles the auxiliary bars via the
     * UnifiedToolbar's minimal state (see ModernScrollBehavior).
     *
     * The @param is kept for source compatibility with existing call sites
     * but no longer influences behavior.
     */
    fun setToolbarBehavior(shouldDisableScroll: Boolean = false) {
        expandToolbarAndMakeItFixed()
    }

    @VisibleForTesting
    internal fun expandToolbarAndMakeItFixed() {
        expand()
        // Remove any pre-existing scroll behavior from the container so the
        // address bar stays pinned to its edge. All page-scroll driven hiding
        // is now the responsibility of UnifiedToolbar's minimal state.
        val targetView = if (toolbarLayout == R.layout.component_bottom_browser_toolbar) {
            toolbarContainer ?: layout
        } else {
            view
        }

        (targetView.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            behavior = null
        }
    }

    private fun ToolbarMenu.Item.performHapticIfNeeded(view: View) {
        if (this is ToolbarMenu.Item.Reload && this.bypassCache ||
            this is ToolbarMenu.Item.Back && this.viewHistory ||
            this is ToolbarMenu.Item.Forward && this.viewHistory
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Smart URL display that shows base domain + shortened path
     * Example: https://en.wikipedia.org/wiki/Sarojini_Naidu -> en.wikipedia.org/w/Sarojini_Naidu
     */
    private fun smartUrlDisplay(url: CharSequence): CharSequence {
        return try {
            val uri = url.toString().toUri()
            val host = uri.host ?: return toDisplayUrl(url)
            val path = uri.path ?: return host
            
            // Remove protocol and www prefix for display
            val cleanHost = host.removePrefix("www.")
            
            if (path == "/" || path.isEmpty()) {
                return cleanHost
            }
            
            // Smart path shortening logic: abbreviate path segments to first letter
            val pathSegments = path.split("/").filter { it.isNotEmpty() }
            
            when {
                // Handle Wikipedia URLs: /wiki/Article_Name -> /w/Article_Name
                pathSegments.size >= 2 && pathSegments[0] == "wiki" -> {
                    "$cleanHost/w/${pathSegments[1]}"
                }
                // Handle paths with 2 or more segments: abbreviate middle segments to first letter
                pathSegments.size >= 2 -> {
                    val abbreviatedSegments = mutableListOf<String>()
                    
                    // First segment: abbreviate to first letter
                    abbreviatedSegments.add(pathSegments[0].take(1))
                    
                    // Middle segments: abbreviate to first letter 
                    for (i in 1 until pathSegments.size - 1) {
                        abbreviatedSegments.add(pathSegments[i].take(1))
                    }
                    
                    // Last segment: keep full name
                    abbreviatedSegments.add(pathSegments.last())
                    
                    "$cleanHost/${abbreviatedSegments.joinToString("/")}"
                }
                // Show full path for single segment
                else -> {
                    "$cleanHost$path"
                }
            }
        } catch (_: Exception) {
            // Fallback to original toDisplayUrl function
            toDisplayUrl(url)
        }
    }
}
