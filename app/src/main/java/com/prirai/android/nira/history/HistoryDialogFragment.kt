@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.history

import mozilla.components.support.ktx.android.content.share
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.components.NiraLoadingIndicator
import com.prirai.android.nira.ui.components.NiraSearchField
import com.prirai.android.nira.ui.components.NiraSelectionBar
import com.prirai.android.nira.ui.components.animateNiraListItem
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.components.NiraWindowDialogFragment
import com.prirai.android.nira.utils.FaviconLoader
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.VisitInfo
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

/**
 * The browsing-history floating window, rendered with the shared Material 3 Expressive Compose
 * stack: a header with a close button, the [NiraSearchField] and a grouped [NiraCardRow] list per
 * date section. Tapping a row loads its URL in the current tab; the trailing overflow menu carries
 * the row actions (open in (private) tab, share, copy, remove).
 */
class HistoryDialogFragment : NiraWindowDialogFragment() {

    @Composable
    override fun WindowContent() {
        HistoryScreen(onBack = { dismiss() })
    }

    companion object {
        const val TAG = "HistoryDialog"

        fun show(fragmentManager: FragmentManager) {
            showOnce(fragmentManager, TAG) { HistoryDialogFragment() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val scope = rememberCoroutineScope()

    // null while the first load is still in flight - the loading indicator's slot.
    var history by remember { mutableStateOf<List<VisitInfo>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        history = components.historyStorage.getDetailedVisits(0).reversed()
    }

    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    val selectionMode = selectedKeys.isNotEmpty()

    fun removeVisits(visits: List<VisitInfo>) {
        scope.launch {
            visits.forEach { components.historyStorage.deleteVisit(it.url, it.visitTime) }
            history = components.historyStorage.getDetailedVisits(0).reversed()
        }
    }

    val filtered = remember(history, query) {
        if (query.isEmpty()) {
            history.orEmpty()
        } else {
            history.orEmpty().filter { visit ->
                visit.url.contains(query, ignoreCase = true) ||
                    visit.title?.contains(query, ignoreCase = true) == true
            }
        }
    }
    val items = remember(filtered) { HistorySectionHelper.groupHistoryByDate(filtered) }
    val selectedVisits = history.orEmpty().filter { visitKey(it) in selectedKeys }

    run {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 640.dp),
        ) {
            NiraSheetHeader(
                title = stringResource(R.string.action_history),
                onClose = onBack,
            )
            AnimatedContent(
                targetState = selectionMode,
                label = "historyActionBar",
                modifier = Modifier.fillMaxWidth(),
            ) { selecting ->
                if (selecting) {
                    HistorySelectionBar(
                        selected = selectedVisits,
                        onClear = { selectedKeys = emptySet() },
                        onOpenDone = onBack,
                        onRemove = {
                            removeVisits(selectedVisits)
                            selectedKeys = emptySet()
                        },
                    )
                } else {
                    NiraSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.history_search_hint),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            when {
                history == null -> NiraLoadingIndicator()
                filtered.isEmpty() && query.isEmpty() -> NiraEmptyState(
                    title = stringResource(R.string.no_history),
                    icon = Icons.Rounded.History,
                )
                else -> LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(
                        items,
                        key = { index, item ->
                            when (item) {
                                is HistoryItem.Header -> "header-${item.section.name}"
                                is HistoryItem.Visit -> "visit-${item.visitInfo.visitTime}-$index"
                            }
                        },
                    ) { index, item ->
                        when (item) {
                            is HistoryItem.Header -> Text(
                                text = stringResource(item.section.titleRes),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                                    .then(animateNiraListItem()),
                            )
                            is HistoryItem.Visit -> HistoryCardRow(
                                item = item,
                                isFirstInGroup = items.getOrNull(index - 1) is HistoryItem.Header,
                                isLastInGroup = items.getOrNull(index + 1)?.let { next ->
                                    next is HistoryItem.Header
                                } ?: true,
                                selected = visitKey(item.visitInfo) in selectedKeys,
                                onClick = {
                                    val key = visitKey(item.visitInfo)
                                    if (selectionMode) {
                                        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
                                    } else {
                                        onBack()
                                        context.components.sessionUseCases.loadUrl(item.visitInfo.url)
                                    }
                                },
                                onLongClick = { selectedKeys = selectedKeys + visitKey(item.visitInfo) },
                                modifier = Modifier.then(animateNiraListItem()),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One history visit on the shared grouped-card idiom; long-press starts selection. */
@Composable
private fun HistoryCardRow(
    item: HistoryItem.Visit,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val visit = item.visitInfo

    NiraCardRow(
        title = visit.title?.takeIf(String::isNotEmpty) ?: visit.url.tryGetHostFromUrl(),
        isTopRow = isFirstInGroup,
        isBottomRow = isLastInGroup,
        showDivider = !isLastInGroup,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        modifier = modifier,
        leading = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                HistoryFavicon(url = visit.url)
            }
        },
        trailing = {
            Text(
                text = HistoryTimeFormatter.getRelativeTimeString(context, visit.visitTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private fun visitKey(visit: VisitInfo) = "${visit.visitTime}|${visit.url}"

/** Contextual action bar that replaces the search field while visits are selected. */
@Composable
private fun HistorySelectionBar(
    selected: List<VisitInfo>,
    onClear: () -> Unit,
    onOpenDone: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val urls = selected.map { it.url }.distinct()

    NiraSelectionBar(onClear = onClear) {
        IconButton(onClick = {
            urls.forEach { context.components.tabsUseCases.addTab.invoke(it, selectTab = false) }
            onOpenDone()
        }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.open_in_background_tabs))
        }
        IconButton(onClick = {
            urls.forEach { context.components.tabsUseCases.addTab.invoke(it, selectTab = false, private = true) }
            onOpenDone()
        }) {
            Icon(
                painter = painterResource(mozilla.components.ui.icons.R.drawable.mozac_ic_private_mode_24),
                contentDescription = stringResource(R.string.open_new_private),
            )
        }
        IconButton(onClick = {
            shareUrl(context, urls.joinToString("\n"))
            onClear()
        }) {
            Icon(
                painter = painterResource(R.drawable.ios_share_24),
                contentDescription = stringResource(R.string.share),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.remove_history_item),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The row favicon on the history page's loader - memory, disk, then `BrowserIcons`
 * - with the history glyph as its placeholder while the load is in flight.
 */
@Composable
private fun HistoryFavicon(url: String) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(initialValue = null, url) {
        value = FaviconLoader.loadFavicon(context, url)
    }

    if (icon != null) {
        Image(
            bitmap = icon!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_history),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun shareUrl(context: Context, url: String) {
    context.share(url)
}
