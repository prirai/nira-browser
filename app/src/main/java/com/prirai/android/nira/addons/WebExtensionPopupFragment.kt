package com.prirai.android.nira.addons

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.components.NiraWindowDialogFragment
import mozilla.components.concept.engine.EngineView
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.CustomTabListAction
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.state.CustomTabSessionState
import mozilla.components.browser.state.state.EngineState
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.createCustomTab
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.concept.engine.window.WindowRequest
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.lib.state.ext.consumeFrom
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.utils.ext.requestInPlacePermissions


/**
 * Hosts an extension's popup page in the same floating window Downloads, History and
 * Bookmarks use. A centered card keeps the popup fully on screen regardless of where
 * the toolbar sits or how tall the page is.
 */
class WebExtensionPopupFragment : NiraWindowDialogFragment(), UserInteractionHandler, EngineSession.Observer {
    private val promptsFeature = ViewBoundFeatureWrapper<PromptFeature>()

    protected var session: SessionState? = null
    protected var engineSession: EngineSession? = null
    private var canGoBack: Boolean = false
    private var engineView: EngineView? = null
    private var rendered = false

    private lateinit var webExtensionId: String

    @Composable
    override fun WindowContent() {
        val context = LocalContext.current
        val title = remember { context.components.store.state.extensions[webExtensionId]?.name.orEmpty() }
        val height = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            NiraSheetHeader(
                title = title,
                onClose = { dismiss() },
                showDragHandle = false,
                titleMaxLines = 1,
            )
            AndroidView(
                factory = { ctx ->
                    components.engine.createView(ctx).also {
                        engineView = it
                        tryRender()
                    }.asView()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .padding(horizontal = 12.dp),
            )
        }
    }

    private fun tryRender() {
        val view = engineView ?: return
        val es = engineSession ?: return
        if (rendered) return
        rendered = true
        view.render(es)
        consumePopupSession()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        webExtensionId = requireNotNull(arguments?.getString("web_extension_id"))

        components.store.state.extensions[webExtensionId]?.popupSession?.let {
            initializeSession(it)
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        session?.let {
            promptsFeature.set(
                PromptFeature(
                    fragment = this,
                    store = components.store,
                    customTabId = it.id,
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
                view
            )
        }

        if (engineSession == null) {
            consumeFrom(requireContext().components.store) { state ->
                state.extensions[webExtensionId]?.popupSession?.let {
                    if (engineSession == null) {
                        it.register(this, view)
                        engineSession = it
                        tryRender()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        engineView = null
        engineSession?.close()
        session?.let {
            components.store.dispatch(CustomTabListAction.RemoveCustomTabAction(it.id))
        }
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        engineSession?.register(this)
    }

    override fun onStop() {
        super.onStop()
        engineSession?.unregister(this)
    }

    protected fun initializeSession(fromEngineSession: EngineSession? = null) {
        engineSession = fromEngineSession ?: components.engine.createSession()
        session = createCustomTab("").copy(engineState = EngineState(engineSession))
        components.store.dispatch(CustomTabListAction.AddCustomTabAction(session as CustomTabSessionState))
    }

    override fun onWindowRequest(windowRequest: WindowRequest) {
        if (windowRequest.type == WindowRequest.Type.CLOSE) {
            dismiss()
        } else {
            engineSession?.loadUrl(windowRequest.url)
        }
    }

    private fun consumePopupSession() {
        components.store.dispatch(
            WebExtensionAction.UpdatePopupSessionAction(webExtensionId, popupSession = null)
        )
    }

    override fun onBackPressed(): Boolean {
        return if (this.canGoBack) {
            engineSession?.goBack()
            true
        } else {
            false
        }
    }

    override fun onPromptRequest(promptRequest: PromptRequest) {
        session?.let { session ->
            components.store.dispatch(
                    ContentAction.UpdatePromptRequestAction(
                            session.id,
                            promptRequest
                    )
            )
        }
    }

    override fun onNavigationStateChange(canGoBack: Boolean?, canGoForward: Boolean?) {
        canGoBack?.let { this.canGoBack = canGoBack }
    }


    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_PROMPT_PERMISSIONS -> promptsFeature.get()?.onPermissionsResult(permissions, grantResults)
        }
    }

    companion object {
        fun create(webExtensionId: String) = WebExtensionPopupFragment().apply {
            arguments = Bundle().apply {
                putString("web_extension_id", webExtensionId)
            }
        }
        private const val REQUEST_CODE_PROMPT_PERMISSIONS = 1
        private const val REQUEST_KEY_PROMPT_PERMISSIONS = "promptFeature"
    }
}