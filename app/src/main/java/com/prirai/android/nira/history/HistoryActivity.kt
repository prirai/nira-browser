@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.components.NiraLoadingIndicator
import com.prirai.android.nira.ui.components.NiraSearchField
import com.prirai.android.nira.ui.components.animateNiraListItem
import com.prirai.android.nira.ui.theme.NiraTheme
import com.prirai.android.nira.utils.FaviconLoader
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.VisitInfo
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

/**
 * The full-screen browsing-history page, rendered with the shared Material 3
 * Expressive Compose stack: a [TopAppBar] with the back arrow, the [NiraSearchField]
 * and a grouped [NiraCardRow] list per date section. Tapping a row loads its URL
 * in the current tab; the trailing overflow menu carries the row actions the old
 * long-press dialog exposed (open in (private) tab, share, copy, remove).
 */
class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeMode()
        setContent {
            val context = LocalContext.current
            val prefs = UserPreferences(context)
            NiraTheme(
                darkTheme = ThemeManager.isDarkMode(context),
                amoledMode = prefs.amoledMode,
                dynamicColor = prefs.dynamicColors,
            ) {
                HistoryScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
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

    fun removeVisit(visit: VisitInfo) {
        scope.launch {
            components.historyStorage.deleteVisit(visit.url, visit.visitTime)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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

            when {
                history == null -> NiraLoadingIndicator()
                filtered.isEmpty() && query.isEmpty() -> NiraEmptyState(
                    title = stringResource(R.string.no_history),
                    icon = Icons.Rounded.History,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
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
                                onBack = onBack,
                                onRemove = { removeVisit(item.visitInfo) },
                                modifier = Modifier.then(animateNiraListItem()),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One history visit on the shared grouped-card idiom, with the favicon, the
 * relative time and the overflow menu the long-press dialog used to carry.
 */
@Composable
private fun HistoryCardRow(
    item: HistoryItem.Visit,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onBack: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val visit = item.visitInfo
    var menuOpen by remember { mutableStateOf(false) }

    NiraCardRow(
        title = visit.title?.takeIf(String::isNotEmpty) ?: visit.url.tryGetHostFromUrl(),
        isTopRow = isFirstInGroup,
        isBottomRow = isLastInGroup,
        showDivider = !isLastInGroup,
        onClick = {
            onBack()
            context.components.sessionUseCases.loadUrl(visit.url)
        },
        modifier = modifier,
        leading = {
            HistoryFavicon(url = visit.url)
        },
        trailing = {
            Text(
                text = HistoryTimeFormatter.getRelativeTimeString(context, visit.visitTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_new)) },
                        onClick = {
                            menuOpen = false
                            onBack()
                            context.components.tabsUseCases.addTab.invoke(
                                visit.url,
                                selectTab = true,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_new_private)) },
                        onClick = {
                            menuOpen = false
                            onBack()
                            context.components.tabsUseCases.addTab.invoke(
                                visit.url,
                                selectTab = true,
                                private = true,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mozac_selection_context_menu_share)) },
                        onClick = {
                            menuOpen = false
                            shareUrl(context, visit.url)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy)) },
                        onClick = {
                            menuOpen = false
                            val clipboard = ContextCompat.getSystemService(
                                context,
                                ClipboardManager::class.java,
                            )
                            clipboard?.setPrimaryClip(ClipData.newPlainText("URL", visit.url))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_history_item)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        },
    )
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
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.mozac_selection_context_menu_share),
        ),
    )
}
