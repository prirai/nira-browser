package com.prirai.android.nira.integration

import android.content.Context
import androidx.core.content.ContextCompat
import com.prirai.android.nira.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.ktx.android.content.getColorFromAttr
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged

/**
 * Integration that adds a reload/stop button to the toolbar that changes based on loading state
 */
@ExperimentalCoroutinesApi
class ReloadStopButtonIntegration(
    private val context: Context,
    private val store: BrowserStore,
    private val toolbar: BrowserToolbar,
    private val onReload: () -> Unit,
    private val onStop: () -> Unit
) : LifecycleAwareFeature {

    private var scope: CoroutineScope? = null

    private val reloadStopButton = BrowserToolbar.TwoStateButton(
        primaryImage = ContextCompat.getDrawable(context, R.drawable.ic_refresh)!!.mutate().apply {
            setTint(context.getColorFromAttr(android.R.attr.textColorPrimary))
        },
        primaryContentDescription = context.getString(R.string.reload),
        secondaryImage = ContextCompat.getDrawable(context, R.drawable.ic_round_close)!!.mutate().apply {
            setTint(context.getColorFromAttr(android.R.attr.textColorPrimary))
        },
        secondaryContentDescription = context.getString(R.string.stop),
        isInPrimaryState = { !store.state.selectedTab?.content?.loading.orFalse() },
        disableInSecondaryState = false
    ) {
        if (store.state.selectedTab?.content?.loading == true) {
            onStop()
        } else {
            onReload()
        }
    }

    init {
        toolbar.addPageAction(reloadStopButton)
    }

    override fun start() {
        scope = store.flowScoped { flow ->
            flow.mapNotNull { state -> state.selectedTab }
                .ifAnyChanged { tab ->
                    arrayOf(tab.content.loading)
                }
                .collect {
                    toolbar.invalidateActions()
                }
        }
    }

    override fun stop() {
        scope?.cancel()
    }

    private fun Boolean?.orFalse(): Boolean = this ?: false
}
