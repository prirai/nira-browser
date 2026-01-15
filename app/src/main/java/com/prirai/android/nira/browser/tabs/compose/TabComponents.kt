package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState

/**
 * Legacy TabFaviconImage - now redirects to new FaviconImage
 * @deprecated Use FaviconImage directly instead
 */
@Deprecated("Use FaviconImage instead", ReplaceWith("FaviconImage(tab, size = 24.dp, modifier)"))
@Composable
fun TabFaviconImage(
    tab: TabSessionState,
    modifier: Modifier = Modifier
) {
    FaviconImage(tab = tab, size = 24.dp, modifier = modifier)
}
