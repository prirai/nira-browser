package com.prirai.android.nira.integration

import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentManager
import com.prirai.android.nira.databinding.FragmentBrowserBinding
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.app.links.AppLinksUseCases
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.contextmenu.ContextMenuCandidate.Companion.createAddContactCandidate
import mozilla.components.feature.contextmenu.ContextMenuCandidate.Companion.createCopyEmailAddressCandidate
import mozilla.components.feature.contextmenu.ContextMenuCandidate.Companion.createCopyImageLocationCandidate
import mozilla.components.feature.contextmenu.ContextMenuCandidate.Companion.createCopyLinkCandidate
import mozilla.components.feature.contextmenu.ContextMenuCandidate.Companion.createOpenImageInNewTabCandidate
import mozilla.components.feature.contextmenu.ContextMenuCandidate.Companion.createSaveImageCandidate
import mozilla.components.feature.contextmenu.ContextMenuCandidate.Companion.createShareEmailAddressCandidate
import mozilla.components.feature.contextmenu.ContextMenuCandidate.Companion.createShareLinkCandidate
import mozilla.components.feature.contextmenu.ContextMenuFeature
import mozilla.components.feature.contextmenu.ContextMenuUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.ui.widgets.DefaultSnackbarDelegate

class ContextMenuIntegration(
    context: Context,
    fragmentManager: FragmentManager,
    browserStore: BrowserStore,
    tabsUseCases: TabsUseCases,
    contextMenuUseCases: ContextMenuUseCases,
    parentView: View,
    sessionId: String? = null,
    engineView: EngineView? = null
) : LifecycleAwareFeature {

    val candidates = run {
        if (sessionId != null) {
            val snackbarDelegate = DefaultSnackbarDelegate()
            listOf(
                createCopyLinkCandidate(context, parentView, snackbarDelegate),
                createCopyLinkTextCandidate(context, parentView, snackbarDelegate),
                createShareLinkCandidate(context),
                createOpenImageInNewTabCandidate(context, tabsUseCases, parentView, snackbarDelegate),
                createSaveImageCandidate(context, contextMenuUseCases),
                createCopyImageLocationCandidate(context, parentView, snackbarDelegate),
                createAddContactCandidate(context),
                createShareEmailAddressCandidate(context),
                createCopyEmailAddressCandidate(context, parentView, snackbarDelegate)
            )
        } else {
            val appLinksCandidate = ContextMenuCandidate.createOpenInExternalAppCandidate(
                context = context,
                appLinksUseCases = AppLinksUseCases(
                    context = context,
                    launchInApp = { true }
                )
            )
            val snackbarDelegate = DefaultSnackbarDelegate()
            ContextMenuCandidate.defaultCandidates(
                context,
                tabsUseCases,
                contextMenuUseCases,
                parentView
            ) + appLinksCandidate + createCopyLinkTextCandidate(context, parentView, snackbarDelegate)
        }
    }

    private val feature = ContextMenuFeature(
        fragmentManager, 
        browserStore, 
        candidates, 
        engineView ?: FragmentBrowserBinding.bind(parentView).engineView, 
        contextMenuUseCases,
        tabId = sessionId
    )

    override fun start() {
        feature.start()
    }

    override fun stop() {
        feature.stop()
    }
}
