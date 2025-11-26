package com.prirai.android.nira.components.toolbar

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ssl.showSslDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.browser.toolbar.display.DisplayToolbar
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.feature.toolbar.ToolbarBehaviorController
import mozilla.components.feature.toolbar.ToolbarFeature
import mozilla.components.feature.toolbar.ToolbarPresenter
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.ktx.android.content.getColorFromAttr
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged

@ExperimentalCoroutinesApi
abstract class ToolbarIntegration(
    context: Context,
    toolbar: BrowserToolbar,
    toolbarMenu: ToolbarMenu,
    sessionId: String?,
    isPrivate: Boolean,
    renderStyle: ToolbarFeature.RenderStyle
) : LifecycleAwareFeature {

    val store = context.components.store
    private val toolbarPresenter: ToolbarPresenter = ToolbarPresenter(
        toolbar,
        store,
        sessionId,
        false,
        ToolbarFeature.UrlRenderConfiguration(
            PublicSuffixList(context),
            ContextCompat.getColor(context, R.color.primary_icon),
            renderStyle = renderStyle
        )
    )

    private val toolbarController = ToolbarBehaviorController(toolbar, store, sessionId)

    private val menuPresenter =
        MenuPresenter(toolbar, context.components.store, sessionId)

    private var securityBackgroundScope: CoroutineScope? = null
    protected val toolbar: BrowserToolbar = toolbar
    protected val context: Context = context

    init {
        // Always hide menu button from address bar (it's shown in the contextual toolbar instead)
        toolbar.display.menuBuilder = null
        toolbar.private = isPrivate
        
        // Set security icon click listener
        toolbar.display.setOnSiteInfoClickedListener {
            context.showSslDialog()
        }
    }

    override fun start() {
        menuPresenter.start()
        toolbarPresenter.start()
        toolbarController.start()
        startSecurityBackgroundObserver()
    }

    override fun stop() {
        menuPresenter.stop()
        toolbarPresenter.stop()
        toolbarController.stop()
        securityBackgroundScope?.cancel()
    }

    private fun startSecurityBackgroundObserver() {
        securityBackgroundScope = store.flowScoped { flow ->
            flow.mapNotNull { state -> state.selectedTab }
                .ifAnyChanged { tab ->
                    arrayOf(tab.content.securityInfo)
                }
                .collect { tab ->
                    updateSecurityBackground(tab.content.securityInfo?.secure ?: false)
                }
        }
    }

    private fun updateSecurityBackground(isSecure: Boolean) {
        // Always use the normal toolbar background, don't change based on security
        val background = AppCompatResources.getDrawable(context, R.drawable.toolbar_background)
        toolbar.display.setUrlBackground(background)

        // Keep consistent text color
        val textColor = context.getColorFromAttr(android.R.attr.textColorPrimary)

        toolbar.display.colors = toolbar.display.colors.copy(
            text = textColor,
            hint = 0x1E15141a
        )
    }

    fun invalidateMenu() {
        menuPresenter.invalidateActions()
    }
}

@ExperimentalCoroutinesApi
class DefaultToolbarIntegration(
    context: Context,
    toolbar: BrowserToolbar,
    toolbarMenu: ToolbarMenu,
    historyStorage: HistoryStorage,
    lifecycleOwner: LifecycleOwner,
    sessionId: String? = null,
    isPrivate: Boolean,
    interactor: BrowserToolbarViewInteractor,
    engine: Engine
) : ToolbarIntegration(
    context = context,
    toolbar = toolbar,
    toolbarMenu = toolbarMenu,
    sessionId = sessionId,
    isPrivate = isPrivate,
    renderStyle = ToolbarFeature.RenderStyle.UncoloredUrl
) {

    init {
        // Always hide menu button from address bar (it's shown in the contextual toolbar instead)
        toolbar.display.menuBuilder = null
        toolbar.private = isPrivate

        toolbar.display.indicators =
            listOf(
                DisplayToolbar.Indicators.SECURITY,
                DisplayToolbar.Indicators.HIGHLIGHT
            )


        toolbar.display.colors = toolbar.display.colors.copy(
            siteInfoIconInsecure = 0xFFd9534f.toInt(),
            siteInfoIconSecure = 0xFF5cb85c.toInt(),
            text = context.getColorFromAttr(android.R.attr.textColorPrimary),
            menu = context.getColorFromAttr(android.R.attr.textColorPrimary),
            separator = 0x1E15141a,
            trackingProtection = 0xFF20123a.toInt(),
            emptyIcon = 0xFF20123a.toInt(),
            hint = 0x1E15141a
        )

        toolbar.edit.colors = toolbar.edit.colors.copy(
            text = context.getColorFromAttr(android.R.attr.textColorPrimary),
            clear = context.getColorFromAttr(android.R.attr.textColorPrimary),
            icon = context.getColorFromAttr(android.R.attr.textColorPrimary)
        )

        // Initial background - will be updated by security observer
        if (isPrivate) {
            toolbar.display.setUrlBackground(
                AppCompatResources.getDrawable(
                    context,
                    R.drawable.toolbar_background_private
                )
            )
        } else {
            toolbar.display.setUrlBackground(
                AppCompatResources.getDrawable(
                    context,
                    R.drawable.toolbar_background
                )
            )
        }

        // Tab counter is not added to the address bar (it's shown in the contextual toolbar instead)
    }
}
