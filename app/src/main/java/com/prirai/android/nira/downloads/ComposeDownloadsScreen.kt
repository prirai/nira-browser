@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.downloads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.core.content.FileProvider
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.components.NiraSearchField
import com.prirai.android.nira.ui.components.NiraSelectionBar
import com.prirai.android.nira.ui.components.NiraSheetHeader
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.lib.state.ext.observeAsComposableState
import java.util.UUID

private val ACTIVE_STATUSES = setOf(
    DownloadState.Status.INITIATED,
    DownloadState.Status.DOWNLOADING,
    DownloadState.Status.PAUSED,
)

@Composable
fun DownloadsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = context.components.store
    val downloadsState by store.observeAsComposableState { it.downloads }
    val downloads = downloadsState ?: emptyMap()
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var renameTarget by remember { mutableStateOf<DownloadState?>(null) }

    val selected = downloads.values.filter { it.id in selectedIds }
    val selectionMode = selected.isNotEmpty()

    val matchesQuery: (DownloadState) -> Boolean = { download ->
        val needle = query.trim()
        needle.isEmpty() ||
            (download.fileName?.contains(needle, ignoreCase = true) == true) ||
            download.url.contains(needle, ignoreCase = true)
    }

    val activeDownloads = downloads.values
        .filter { it.status in ACTIVE_STATUSES && matchesQuery(it) }
        .sortedByDescending { it.createdTime }

    val completedDownloads = downloads.values
        .filter { it.status !in ACTIVE_STATUSES && matchesQuery(it) }
        .sortedByDescending { it.createdTime }

    val toggleSelection: (DownloadState) -> Unit = { dl ->
        selectedIds = if (dl.id in selectedIds) selectedIds - dl.id else selectedIds + dl.id
    }
    val startSelection: (DownloadState) -> Unit = { dl -> selectedIds = selectedIds + dl.id }

    // The hosting DownloadsDialogFragment draws the rounded surface; the content draws none.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 640.dp)
    ) {
        NiraSheetHeader(
            title = stringResource(R.string.downloads_title),
            onClose = onDismiss,
            dividerAlpha = null,
            actions = {
                IconButton(onClick = { openDownloadsFolder(context) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.open_downloads_folder))
                }
                if (completedDownloads.isNotEmpty()) {
                    IconButton(onClick = {
                        completedDownloads.forEach {
                            context.components.store.dispatch(DownloadAction.RemoveDownloadAction(it.id))
                        }
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_completed))
                    }
                }
            },
        )

        AnimatedContent(
            targetState = selectionMode,
            label = "downloadsActionBar",
            modifier = Modifier.fillMaxWidth(),
        ) { selecting ->
            if (selecting) {
                DownloadSelectionBar(
                    selected = selected,
                    onClear = { selectedIds = emptySet() },
                    onRename = { renameTarget = selected.singleOrNull() },
                    onDone = { selectedIds = emptySet() },
                )
            } else {
                NiraSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.search_downloads),
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        if (downloads.isEmpty()) {
            NiraEmptyState(
                title = stringResource(R.string.no_downloads_yet),
                icon = Icons.Default.InsertDriveFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (activeDownloads.isNotEmpty()) {
                    item(key = "active") {
                        DownloadSection(
                            title = stringResource(R.string.active_downloads, activeDownloads.size),
                            downloads = activeDownloads,
                            selectedIds = selectedIds,
                            selectionMode = selectionMode,
                            onItemClick = { /* active items don't open */ },
                            onToggleSelection = toggleSelection,
                            onLongClick = startSelection,
                        )
                    }
                }

                if (completedDownloads.isNotEmpty()) {
                    item(key = "completed") {
                        DownloadSection(
                            title = stringResource(R.string.completed_section, completedDownloads.size),
                            downloads = completedDownloads,
                            selectedIds = selectedIds,
                            selectionMode = selectionMode,
                            onItemClick = { dl -> onCompletedItemClick(context, dl) },
                            onToggleSelection = toggleSelection,
                            onLongClick = startSelection,
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDownloadDialog(
            download = target,
            onDismiss = { renameTarget = null },
            onRenamed = {
                renameTarget = null
                selectedIds = emptySet()
            },
        )
    }
}

private fun onCompletedItemClick(context: Context, dl: DownloadState) {
    when (dl.status) {
        DownloadState.Status.COMPLETED -> openDownloadedFile(context, dl)
        DownloadState.Status.FAILED -> {
            DownloadController.start(context, dl)
            Toast.makeText(context, context.getString(R.string.retrying_download), Toast.LENGTH_SHORT).show()
        }
        DownloadState.Status.CANCELLED -> {
            context.components.store.dispatch(DownloadAction.RemoveDownloadAction(dl.id))
            val freshDownload = DownloadState(
                id = UUID.randomUUID().toString(),
                url = dl.url,
                fileName = dl.fileName,
                contentType = dl.contentType,
                contentLength = dl.contentLength,
                userAgent = dl.userAgent,
                referrerUrl = dl.referrerUrl,
                status = DownloadState.Status.INITIATED
            )
            context.components.store.dispatch(DownloadAction.AddDownloadAction(freshDownload))
            Toast.makeText(context, context.getString(R.string.download_requeued), Toast.LENGTH_SHORT).show()
        }
        else -> {}
    }
}

/** Contextual action bar that replaces the search field while downloads are selected. */
@Composable
private fun DownloadSelectionBar(
    selected: List<DownloadState>,
    onClear: () -> Unit,
    onRename: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val shareable = selected.mapNotNull { shareUriFor(context, it) }
    val canRename = selected.size == 1 && selected.single().status == DownloadState.Status.COMPLETED

    NiraSelectionBar(onClear = onClear) {
        if (shareable.isNotEmpty()) {
            IconButton(onClick = {
                shareDownloads(context, shareable)
                onDone()
            }) {
                Icon(
                    painter = painterResource(R.drawable.ios_share_24),
                    contentDescription = stringResource(R.string.share),
                )
            }
        }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("Download URL", selected.joinToString("\n") { it.url }),
            )
            Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
            onDone()
        }) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_link))
        }
        if (canRename) {
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_action))
            }
        }
        IconButton(onClick = {
            selected.forEach { deleteDownload(context, it) }
            onDone()
        }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RenameDownloadDialog(
    download: DownloadState,
    onDismiss: () -> Unit,
    onRenamed: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(download.id) { mutableStateOf(download.fileName ?: "") }

    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_file)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !name.contains('/'),
                onClick = {
                    val file = download.filePath?.let { java.io.File(it) }
                    if (file != null) {
                        val renamed = java.io.File(file.parentFile, name.trim())
                        if (file.renameTo(renamed)) {
                            context.components.store.dispatch(
                                DownloadAction.UpdateDownloadAction(
                                    download.copy(fileName = renamed.name),
                                ),
                            )
                        }
                    }
                    onRenamed()
                },
            ) { Text(stringResource(R.string.rename_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun DownloadSection(
    title: String,
    downloads: List<DownloadState>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onItemClick: (DownloadState) -> Unit,
    onToggleSelection: (DownloadState) -> Unit,
    onLongClick: (DownloadState) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 6.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Column {
                downloads.forEachIndexed { index, download ->
                    DownloadListItem(
                        download = download,
                        selected = download.id in selectedIds,
                        onClick = {
                            if (selectionMode) onToggleSelection(download) else onItemClick(download)
                        },
                        onLongClick = { onLongClick(download) },
                    )
                    if (index < downloads.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadListItem(
    download: DownloadState,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val isActive = download.status in ACTIVE_STATUSES
    val fileName = download.fileName ?: urlToFileName(download.url)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                selected -> LeadingTile {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.selected),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                isActive -> {
                    val paused = download.status == DownloadState.Status.PAUSED
                    FilledTonalIconButton(
                        onClick = {
                            if (paused) {
                                DownloadController.resume(context, download)
                            } else {
                                DownloadController.pause(context, download.id)
                            }
                        },
                        enabled = download.status != DownloadState.Status.INITIATED,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = stringResource(if (paused) R.string.resume else R.string.pause),
                        )
                    }
                }
                else -> LeadingTile {
                    Icon(
                        imageVector = fileIconForMimeType(download.contentType),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isActive) {
                    ActiveDownloadProgress(download)
                } else {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        download.contentLength?.takeIf { it > 0 }?.let { size ->
                            Text(
                                text = formatBytes(size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (download.status != DownloadState.Status.COMPLETED) {
                            StatusChip(status = download.status)
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            when (download.status) {
                DownloadState.Status.COMPLETED -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.completed),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                DownloadState.Status.FAILED, DownloadState.Status.CANCELLED -> IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.retry),
                        tint = if (download.status == DownloadState.Status.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                else -> IconButton(onClick = { DownloadController.cancel(context, download) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeadingTile(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Full-width progress bar plus percentage, transferred size and live speed. */
@Composable
private fun ActiveDownloadProgress(download: DownloadState) {
    val total = download.contentLength?.takeIf { it > 0L }
    val copied = download.currentBytesCopied
    val paused = download.status == DownloadState.Status.PAUSED
    val starting = download.status == DownloadState.Status.INITIATED
    val fraction = total?.let { (copied.toFloat() / it).coerceIn(0f, 1f) }
    val speed = rememberDownloadSpeed(download)

    Spacer(Modifier.height(8.dp))
    if (fraction != null && !starting) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = if (paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    Spacer(Modifier.height(4.dp))

    val left = buildList {
        if (starting) {
            add(stringResource(R.string.status_starting))
        } else {
            fraction?.let { add("${(it * 100).toInt()}%") }
            add(
                if (total != null) "${formatBytes(copied)} / ${formatBytes(total)}" else formatBytes(copied),
            )
        }
    }.joinToString(" · ")
    val right = when {
        paused -> stringResource(R.string.status_paused)
        speed > 0L -> {
            val remaining = total?.let { (it - copied).coerceAtLeast(0L) / speed }
            listOfNotNull("${formatBytes(speed)}/s", remaining?.let { formatDuration(it) }).joinToString(" · ")
        }
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = left,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (right != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = right,
                style = MaterialTheme.typography.bodySmall,
                color = if (paused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

internal fun formatDuration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m left"
    seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s left"
    else -> "${seconds}s left"
}

/** Bytes/second derived from successive progress updates, sampled at most twice a second. */
@Composable
private fun rememberDownloadSpeed(download: DownloadState): Long {
    var speed by remember(download.id) { mutableLongStateOf(0L) }
    var lastSample by remember(download.id) { mutableStateOf<Pair<Long, Long>?>(null) }
    val copied = download.currentBytesCopied
    val downloading = download.status == DownloadState.Status.DOWNLOADING

    LaunchedEffect(copied, downloading) {
        if (!downloading) {
            speed = 0L
            lastSample = null
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        val previous = lastSample
        if (previous == null) {
            lastSample = now to copied
        } else {
            val elapsed = now - previous.first
            if (elapsed >= 500L) {
                speed = ((copied - previous.second) * 1000L / elapsed).coerceAtLeast(0L)
                lastSample = now to copied
            }
        }
    }
    return speed
}

private fun openDownloadsFolder(context: Context) {
    try {
        context.startActivity(
            Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        Toast.makeText(context, R.string.no_app_to_open_file, Toast.LENGTH_SHORT).show()
    }
}

private fun deleteDownload(context: Context, download: DownloadState) {
    if (download.status in ACTIVE_STATUSES) {
        DownloadController.cancel(context, download)
        return
    }
    context.components.store.dispatch(DownloadAction.RemoveDownloadAction(download.id))
    download.filePath?.let { path ->
        try { java.io.File(path).delete() } catch (e: Exception) { /* ignore */ }
    }
}

private fun shareUriFor(context: Context, download: DownloadState): Uri? {
    if (download.status != DownloadState.Status.COMPLETED) return null
    val file = download.filePath?.let { java.io.File(it) }?.takeIf { it.exists() } ?: return null
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun shareDownloads(context: Context, uris: List<Uri>) {
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uris.single())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun StatusChip(status: DownloadState.Status) {
    val (labelRes, containerColor, contentColor) = when (status) {
        DownloadState.Status.DOWNLOADING -> Triple(
            R.string.status_downloading,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        DownloadState.Status.INITIATED -> Triple(
            R.string.status_starting,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        DownloadState.Status.FAILED -> Triple(
            R.string.status_failed,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error
        )
        DownloadState.Status.PAUSED -> Triple(
            R.string.status_paused,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        DownloadState.Status.CANCELLED -> Triple(
            R.string.status_cancelled,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Triple(
            null,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    AssistChip(
        onClick = {},
        label = {
            Text(
                text = labelRes?.let { stringResource(it) } ?: status.name,
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = contentColor
        ),
        border = null,
        modifier = Modifier.height(22.dp)
    )
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun fileIconForMimeType(mimeType: String?): ImageVector {
    if (mimeType == null) return Icons.Default.InsertDriveFile
    return when {
        mimeType.startsWith("image/") -> Icons.Default.Image
        mimeType.startsWith("video/") -> Icons.Default.VideoFile
        mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        mimeType.contains("zip") || mimeType.contains("archive") ||
                mimeType.contains("compressed") || mimeType.contains("tar") ||
                mimeType.contains("gzip") || mimeType.contains("7z") ||
                mimeType.contains("rar") -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}

internal fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}

private fun urlToFileName(url: String): String {
    return url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
}

private fun openDownloadedFile(context: android.content.Context, download: DownloadState) {
    context.components.downloadsUseCases.openAlreadyDownloadedFile(
        download.sessionId ?: "",
        download,
        download.filePath
    )
}
