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
import com.prirai.android.nira.ssl.showSslDialog
import com.prirai.android.nira.utils.ToolbarPopupWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.CustomTabSessionState
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.browser.toolbar.display.DisplayToolbar
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.util.URLStringUtils.toDisplayUrl
import mozilla.components.ui.widgets.behavior.EngineViewScrollingBehavior
import mozilla.components.support.ktx.android.content.res.resolveAttribute
import java.lang.ref.WeakReference
import mozilla.components.ui.widgets.behavior.ViewPosition as MozacToolbarPosition
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
@SuppressWarnings("LargeClass")
class BrowserToolbarView(
    private val container: ViewGroup,
    private val toolbarPosition: ToolbarPosition,
    private val interactor: BrowserToolbarViewInteractor,
    private val customTabSession: CustomTabSessionState?,
    private val lifecycleOwner: LifecycleOwner
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
        
    // Access to integrated components for bottom toolbar layout
    internal val integratedTabGroupBar: View? = if (toolbarLayout == R.layout.component_bottom_browser_toolbar) {
        layout.findViewById(R.id.tabGroupBarIntegrated)
    } else null
    
    internal val integratedContextualToolbar: View? = if (toolbarLayout == R.layout.component_bottom_browser_toolbar) {
        layout.findViewById<View>(R.id.contextualBottomToolbarIntegrated)?.also { toolbar ->
            val showContextualToolbar = settings.showContextualToolbar
            toolbar.visibility = if (showContextualToolbar) View.VISIBLE else View.GONE
            
            if (!showContextualToolbar) {
                addTabCountAndMenuToToolbar()
            }
        }
    } else null
    
    // Get the actual container for bottom toolbar
    private val toolbarContainer: View? = if (toolbarLayout == R.layout.component_bottom_browser_toolbar) {
        val container = layout.findViewById<View>(R.id.toolbarContainer)
        container ?: layout
    } else null

    val toolbarIntegration: ToolbarIntegration

    @VisibleForTesting
    internal val isPwaTabOrTwaTab: Boolean
        get() = false

    init {
        val isCustomTabSession = customTabSession != null

        view.display.setOnUrlLongClickListener {
            ToolbarPopupWindow.show(
                WeakReference(view),
                customTabSession?.id,
                interactor::onBrowserToolbarPasteAndGo,
                interactor::onBrowserToolbarPaste
            )
            true
        }

        with(container.context) {
            val isPinningSupported = components.webAppUseCases.isPinningSupported()

            view.apply {
                setToolbarBehavior()

                elevation = resources.getDimension(R.dimen.browser_fragment_toolbar_elevation)

                if (!isCustomTabSession) {
                    display.setUrlBackground(ContextCompat.getDrawable(context, R.drawable.address_bar_background))
                }

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
    
    private fun addTabCountAndMenuToToolbar() {
        val context = container.context
        
        val tabCountAction = object : mozilla.components.concept.toolbar.Toolbar.Action {
            private var countText: android.widget.TextView? = null
            
            override fun createView(parent: ViewGroup): View {
                val frameLayout = android.widget.FrameLayout(parent.context)
                frameLayout.layoutParams = ViewGroup.LayoutParams(
                    (56 * parent.context.resources.displayMetrics.density).toInt(),
                    (56 * parent.context.resources.displayMetrics.density).toInt()
                )
                frameLayout.setPadding(
                    (4 * parent.context.resources.displayMetrics.density).toInt(),
                    (4 * parent.context.resources.displayMetrics.density).toInt(),
                    (4 * parent.context.resources.displayMetrics.density).toInt(),
                    (4 * parent.context.resources.displayMetrics.density).toInt()
                )
                
                val backgroundView = android.widget.ImageView(parent.context)
                backgroundView.setImageDrawable(ContextCompat.getDrawable(parent.context, R.drawable.tab_number_background))
                backgroundView.scaleType = android.widget.ImageView.ScaleType.FIT_XY
                backgroundView.setColorFilter(
                    ContextCompat.getColor(parent.context, R.color.primary_icon),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                val size = (28 * parent.context.resources.displayMetrics.density).toInt()
                
                countText = android.widget.TextView(parent.context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(size, size).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    gravity = android.view.Gravity.CENTER
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(parent.context, R.color.primary_icon))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 1
                }
                
                frameLayout.addView(backgroundView, android.widget.FrameLayout.LayoutParams(size, size).apply {
                    gravity = android.view.Gravity.CENTER
                })
                frameLayout.addView(countText)
                
                frameLayout.setOnClickListener {
                    interactor.onTabCounterClicked()
                }
                
                val backgroundResource = parent.context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless)
                frameLayout.setBackgroundResource(backgroundResource)
                
                return frameLayout
            }
            
            override fun bind(view: View) {
                val tabCount = context.components.store.state.tabs.size
                countText?.text = if (tabCount > 99) "99+" else tabCount.toString()
            }
        }
        
        val menuAction = object : mozilla.components.concept.toolbar.Toolbar.Action {
            override fun createView(parent: ViewGroup): View {
                val imageView = android.widget.ImageView(parent.context)
                imageView.setImageDrawable(ContextCompat.getDrawable(parent.context, R.drawable.ic_ios_menu))
                val padding = (16 * parent.context.resources.displayMetrics.density).toInt()
                imageView.setPadding(padding, padding, padding, padding)
                imageView.setOnClickListener {
                    val menuToolbar = BrowserMenu(
                        context = parent.context,
                        store = context.components.store,
                        onItemTapped = {
                            it.performHapticIfNeeded(view)
                            interactor.onBrowserToolbarMenuItemTapped(it)
                        },
                        lifecycleOwner = lifecycleOwner,
                        isPinningSupported = context.components.webAppUseCases.isPinningSupported(),
                        shouldReverseItems = settings.toolbarPosition == ToolbarPosition.TOP.ordinal
                    )
                    menuToolbar.menuBuilder.build(parent.context).show(anchor = imageView)
                }
                val backgroundResource = parent.context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless)
                imageView.setBackgroundResource(backgroundResource)
                return imageView
            }
            
            override fun bind(view: View) {}
        }
        
        view.addBrowserAction(tabCountAction)
        view.addBrowserAction(menuAction)
        
        context.components.store.flowScoped(lifecycleOwner) { flow ->
            flow.mapNotNull { it.tabs.size }
                .distinctUntilChanged()
                .collect { 
                    view.invalidateActions()
                }
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
            (behavior as? EngineViewScrollingBehavior)?.forceExpand(targetView)
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
            (behavior as? EngineViewScrollingBehavior)?.forceCollapse(targetView)
        }
    }

    fun dismissMenu() {
        view.dismissMenu()
    }

    /**
     * Sets whether the toolbar will have a dynamic behavior (to be scrolled) or not.
     *
     * This will intrinsically check and disable the dynamic behavior if
     *  - this is disabled in app settings
     *  - toolbar is placed at the bottom and tab shows a PWA or TWA
     *
     *  Also if the user has not explicitly set a toolbar position and has a screen reader enabled
     *  the toolbar will be placed at the top and in a fixed position.
     *
     * @param shouldDisableScroll force disable of the dynamic behavior irrespective of the intrinsic checks.
     */
    fun setToolbarBehavior(shouldDisableScroll: Boolean = false) {
        
        when (settings.toolbarPosition) {
            ToolbarPosition.BOTTOM.ordinal -> {
                if (settings.hideBarWhileScrolling && !isPwaTabOrTwaTab) {
                    setDynamicToolbarBehavior(MozacToolbarPosition.BOTTOM)
                } else {
                    expandToolbarAndMakeItFixed()
                }
            }
            ToolbarPosition.TOP.ordinal -> {
                if (!settings.hideBarWhileScrolling ||
                    shouldDisableScroll
                ) {
                    expandToolbarAndMakeItFixed()
                } else {
                    setDynamicToolbarBehavior(MozacToolbarPosition.TOP)
                }
            }
        }
    }

    @VisibleForTesting
    internal fun expandToolbarAndMakeItFixed() {
        expand()
        // Remove behavior from appropriate container
        val targetView = if (toolbarLayout == R.layout.component_bottom_browser_toolbar) {
            toolbarContainer ?: layout
        } else {
            view
        }
        
        (targetView.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            behavior = null
        }
    }

    @VisibleForTesting
    internal fun setDynamicToolbarBehavior(toolbarPosition: MozacToolbarPosition) {
        // Apply behavior to the correct view based on toolbar layout
        val targetView = if (toolbarLayout == R.layout.component_bottom_browser_toolbar) {
            toolbarContainer ?: layout
        } else {
            view
        }
        
        
        (targetView.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            behavior = EngineViewScrollingBehavior(targetView.context, null, toolbarPosition)
        } ?: android.util.Log.w("BrowserToolbar", "Failed to apply behavior - layoutParams is not CoordinatorLayout.LayoutParams")
    }

    @Suppress("ComplexCondition")
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
            val uri = android.net.Uri.parse(url.toString())
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
        } catch (e: Exception) {
            // Fallback to original toDisplayUrl function
            toDisplayUrl(url)
        }
    }
}