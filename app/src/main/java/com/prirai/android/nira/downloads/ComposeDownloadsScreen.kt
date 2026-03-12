package com.prirai.android.nira.downloads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prirai.android.nira.downloads.DownloadService
import com.prirai.android.nira.ext.components
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID
import mozilla.components.lib.state.ext.observeAsComposableState
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = context.components.store
    val downloadsState by store.observeAsComposableState { it.downloads }
    val downloads = downloadsState ?: emptyMap()

    val activeStatuses = setOf(
        DownloadState.Status.INITIATED,
        DownloadState.Status.DOWNLOADING,
        DownloadState.Status.PAUSED,
    )

    val activeDownloads = downloads.values
        .filter { it.status in activeStatuses }
        .sortedByDescending { it.createdTime }

    val completedDownloads = downloads.values
        .filter { it.status == DownloadState.Status.COMPLETED || it.status == DownloadState.Status.FAILED || it.status == DownloadState.Status.CANCELLED }
        .sortedByDescending { it.createdTime }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 640.dp)
        ) {
            // Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }

            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No downloads yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (activeDownloads.isNotEmpty()) {
                        item {
                            DownloadSection(
                                title = "Active Downloads (${activeDownloads.size})",
                                downloads = activeDownloads,
                                onItemClick = { /* active items don't open */ },
                                onCancelClick = { dl ->
                                    val intent = Intent(AbstractFetchDownloadService.ACTION_CANCEL).apply {
                                        setPackage(context.packageName)
                                        putExtra(INTENT_EXTRA_DOWNLOAD_ID, dl.id)
                                    }
                                    context.sendBroadcast(intent)
                                    context.components.store.dispatch(DownloadAction.RemoveDownloadAction(dl.id))
                                },
                                onDelete = { id ->
                                    context.components.store.dispatch(DownloadAction.RemoveDownloadAction(id))
                                },
                                showClearButton = false,
                                onClearClick = {}
                            )
                        }
                    }

                    if (completedDownloads.isNotEmpty()) {
                        item {
                            DownloadSection(
                                title = "Completed (${completedDownloads.size})",
                                downloads = completedDownloads,
                                onItemClick = { dl ->
                                    when (dl.status) {
                                        DownloadState.Status.COMPLETED -> openDownloadedFile(context, dl)
                                        DownloadState.Status.FAILED -> {
                                            val intent = Intent(AbstractFetchDownloadService.ACTION_TRY_AGAIN).apply {
                                                setPackage(context.packageName)
                                                putExtra(INTENT_EXTRA_DOWNLOAD_ID, dl.id)
                                            }
                                            context.sendBroadcast(intent)
                                            Toast.makeText(context, "Retrying download…", Toast.LENGTH_SHORT).show()
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
                                            Toast.makeText(context, "Download requeued", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                },
                                onCancelClick = null,
                                onDelete = { id ->
                                    context.components.store.dispatch(DownloadAction.RemoveDownloadAction(id))
                                },
                                showClearButton = false,
                                onClearClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadSection(
    title: String,
    downloads: List<DownloadState>,
    onItemClick: (DownloadState) -> Unit,
    onCancelClick: ((DownloadState) -> Unit)?,
    onDelete: (String) -> Unit,
    showClearButton: Boolean,
    onClearClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (showClearButton) {
                TextButton(onClick = onClearClick) {
                    Text("Clear all")
                }
            }
        }

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
                        onItemClick = { onItemClick(download) },
                        onCancelClick = onCancelClick?.let { { it(download) } },
                        onDelete = onDelete
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

@Composable
fun DownloadListItem(
    download: DownloadState,
    onItemClick: () -> Unit,
    onCancelClick: (() -> Unit)? = null,
    onDelete: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(download.fileName ?: "") }
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onItemClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = fileIconForMimeType(download.contentType),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            when (download.status) {
                DownloadState.Status.INITIATED -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download this file?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = download.fileName ?: urlToFileName(download.url),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    ContextCompat.startForegroundService(
                                        context,
                                        Intent(context, DownloadService::class.java).apply {
                                            putExtra(
                                                android.app.DownloadManager.EXTRA_DOWNLOAD_ID,
                                                download.id,
                                            )
                                        },
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Download")
                            }
                            TextButton(
                                onClick = {
                                    context.components.store.dispatch(
                                        DownloadAction.RemoveDownloadAction(download.id),
                                    )
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                DownloadState.Status.DOWNLOADING -> {
                    val progress = if ((download.contentLength ?: 0L) > 0L) {
                        (download.currentBytesCopied.toFloat() / download.contentLength!!.toFloat()).coerceIn(0f, 1f)
                    } else -1f

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = download.fileName ?: urlToFileName(download.url),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        if (progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        val sizeText = if ((download.contentLength ?: 0L) > 0L) {
                            "${formatBytes(download.currentBytesCopied)} / ${formatBytes(download.contentLength!!)}"
                        } else {
                            formatBytes(download.currentBytesCopied)
                        }
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (onCancelClick != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = onCancelClick,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                DownloadState.Status.COMPLETED -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = download.fileName ?: urlToFileName(download.url),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        download.contentLength?.let { size ->
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatBytes(size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }

                else -> {
                    // FAILED, PAUSED, CANCELLED — show status chip
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = download.fileName ?: urlToFileName(download.url),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            download.contentLength?.let { size ->
                                Text(
                                    text = formatBytes(size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            StatusChip(status = download.status)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    when (download.status) {
                        DownloadState.Status.FAILED -> Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        DownloadState.Status.PAUSED -> {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Paused",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                            if (onCancelClick != null) {
                                Spacer(Modifier.width(4.dp))
                                TextButton(
                                    onClick = onCancelClick,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        DownloadState.Status.CANCELLED -> Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        else -> {}
                    }
                }
            }

            // Three-dot overflow menu — hidden while awaiting download confirmation
            if (download.status != DownloadState.Status.INITIATED) {
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                showMenu = false
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(download.filePath ?: download.url))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share file"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy link") },
                            onClick = {
                                showMenu = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Download URL", download.url)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete(download.id)
                                download.filePath?.let { path ->
                                    try { java.io.File(path).delete() } catch (e: Exception) { /* ignore */ }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    download.filePath?.let { path ->
                        try {
                            val file = java.io.File(path)
                            val newFile = java.io.File(file.parent, renameText)
                            file.renameTo(newFile)
                        } catch (e: Exception) { /* ignore */ }
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StatusChip(status: DownloadState.Status) {
    val (label, containerColor, contentColor) = when (status) {
        DownloadState.Status.DOWNLOADING -> Triple(
            "Downloading",
            Color(0xFF1565C0).copy(alpha = 0.15f),
            Color(0xFF1565C0)
        )
        DownloadState.Status.INITIATED -> Triple(
            "Starting",
            Color(0xFF1565C0).copy(alpha = 0.15f),
            Color(0xFF1565C0)
        )
        DownloadState.Status.FAILED -> Triple(
            "Failed",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error
        )
        DownloadState.Status.PAUSED -> Triple(
            "Paused",
            Color(0xFFE65100).copy(alpha = 0.15f),
            Color(0xFFE65100)
        )
        DownloadState.Status.CANCELLED -> Triple(
            "Cancelled",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Triple(
            status.name,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    AssistChip(
        onClick = {},
        label = {
            Text(
                text = label,
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

private fun formatBytes(bytes: Long): String {
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
