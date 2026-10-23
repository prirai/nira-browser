@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.bookmark.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.utils.BookmarkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The add/edit bookmark dialog of both the bookmarks sheet and the legacy
 * [AddBookmarkSiteDialog] entry point: a [NiraAlertDialog] with title/URL
 * fields, and — when creating a new bookmark — the target folder picker and
 * the "add at top of list" option of the original View dialog.
 *
 * Editing mutates the item in place; creating adds a fresh [BookmarkSiteItem]
 * to the (preference-defaulted, user-changeable) target folder. On a
 * successful save the bookmark is also mirrored to the Gecko places storage,
 * exactly as the old View-based dialog did.
 */
@Composable
internal fun BookmarkSiteEditDialog(
    manager: BookmarkManager,
    item: BookmarkSiteItem?,
    initialTitle: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val editing = item
    val isCreate = editing == null
    var title by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var showTitleError by remember { mutableStateOf(false) }
    var showUrlError by remember { mutableStateOf(false) }
    var targetFolder by remember { mutableStateOf(defaultBookmarkFolder(context, manager)) }
    var addToTop by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }

    fun save() {
        showTitleError = title.isBlank()
        showUrlError = url.isBlank()
        if (showTitleError || showUrlError) return

        val trimmedUrl = url.trim()
        if (editing == null) {
            val newItem = BookmarkSiteItem(title, trimmedUrl, BookmarkUtils.getNewId())
            if (addToTop) {
                manager.addFirst(targetFolder, newItem)
            } else {
                manager.add(targetFolder, newItem)
            }
        } else {
            editing.title = title
            editing.url = trimmedUrl
        }

        if (manager.save()) {
            Toast.makeText(context, R.string.successful, Toast.LENGTH_SHORT).show()
            syncBookmarkToPlaces(context, trimmedUrl, title)
            onSaved()
        } else {
            Toast.makeText(context, R.string.failed, Toast.LENGTH_LONG).show()
        }
    }

    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isCreate) R.string.add_bookmark else R.string.edit_bookmark))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        showTitleError = false
                    },
                    label = { Text(stringResource(R.string.title)) },
                    isError = showTitleError,
                    supportingText = if (showTitleError) {
                        { Text(stringResource(R.string.title_empty_mes)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        showUrlError = false
                    },
                    label = { Text(stringResource(R.string.url)) },
                    isError = showUrlError,
                    supportingText = if (showUrlError) {
                        { Text(stringResource(R.string.url_empty_mes)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isCreate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.folder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showFolderPicker = true }) {
                            Text(
                                text = folderDisplayName(context, manager, targetFolder),
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = addToTop,
                                onValueChange = { addToTop = it },
                                role = Role.Checkbox,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.add_to_top),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(checked = addToTop, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { save() },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (showFolderPicker) {
        FolderPickerDialog(
            manager = manager,
            startFolder = targetFolder,
            excludeItems = emptyList(),
            onDismiss = { showFolderPicker = false },
            onSelect = { folder ->
                targetFolder = folder
                showFolderPicker = false
            },
        )
    }
}

/**
 * The add/edit folder dialog: a [NiraAlertDialog] with the folder name field
 * and the "add at top of list" option (create only), ported from the old
 * View-based add-folder dialog.
 */
@Composable
internal fun FolderEditDialog(
    manager: BookmarkManager,
    parent: BookmarkFolderItem?,
    item: BookmarkFolderItem?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val editing = item
    val isCreate = editing == null
    var name by remember { mutableStateOf(editing?.title.orEmpty()) }
    var showNameError by remember { mutableStateOf(false) }
    var addToTop by remember { mutableStateOf(false) }

    fun save() {
        showNameError = name.isBlank()
        if (showNameError) return

        val targetParent = parent ?: manager.root
        if (editing == null) {
            val newFolder = BookmarkFolderItem(name, targetParent, BookmarkUtils.getNewId())
            if (addToTop) {
                targetParent.addAtStart(newFolder)
            } else {
                targetParent.add(newFolder)
            }
        } else {
            editing.title = name
        }

        if (manager.save()) {
            Toast.makeText(context, R.string.successful, Toast.LENGTH_SHORT).show()
            onSaved()
        } else {
            Toast.makeText(context, R.string.failed, Toast.LENGTH_LONG).show()
        }
    }

    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isCreate) R.string.add_folder else R.string.edit_folder))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showNameError = false
                    },
                    label = { Text(stringResource(R.string.new_folder_name)) },
                    isError = showNameError,
                    supportingText = if (showNameError) {
                        { Text(stringResource(R.string.title_empty_mes)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isCreate) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = addToTop,
                                onValueChange = { addToTop = it },
                                role = Role.Checkbox,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.add_to_top),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(checked = addToTop, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { save() },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * The folder picker of the original View-based folder-selection sheet as a
 * [NiraAlertDialog]: navigate the folder tree (up button, folder rows), create
 * a subfolder in place, and confirm the folder currently displayed. Folders
 * matching [excludeItems] (or descendants of an excluded folder) are hidden,
 * which is what kept a move target from being the moved folder itself.
 */
@Composable
internal fun FolderPickerDialog(
    manager: BookmarkManager,
    startFolder: BookmarkFolderItem,
    excludeItems: List<BookmarkItem>,
    onDismiss: () -> Unit,
    onSelect: (BookmarkFolderItem) -> Unit,
) {
    var currentFolder by remember { mutableStateOf(startFolder) }
    var revision by remember { mutableIntStateOf(0) }
    var showCreateFolder by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val childFolders = remember(currentFolder, revision) {
        currentFolder.itemList
            .filterIsInstance<BookmarkFolderItem>()
            .filterNot { folder ->
                excludeItems.any { excluded ->
                    folder === excluded ||
                        (excluded is BookmarkFolderItem && isDescendantOf(folder, excluded))
                }
            }
    }

    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_folder)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = folderDisplayName(context, manager, currentFolder),
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (currentFolder.parent != null) {
                        IconButton(onClick = { currentFolder.parent?.let { currentFolder = it } }) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                    IconButton(onClick = { showCreateFolder = true }) {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = stringResource(R.string.create_folder),
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    itemsIndexed(childFolders, key = { index, folder -> "${folder.id}-$index" }) { _, folder ->
                        ListItem(
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.clickable { currentFolder = folder },
                        ) {
                            Text(
                                text = folder.title
                                    ?: stringResource(R.string.bookmark_unnamed_folder),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(currentFolder) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.select_folder_confirm, folderDisplayName(context, manager, currentFolder)))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (showCreateFolder) {
        FolderEditDialog(
            manager = manager,
            parent = currentFolder,
            item = null,
            onDismiss = { showCreateFolder = false },
            onSaved = {
                showCreateFolder = false
                revision++
            },
        )
    }
}

/**
 * Confirmation dialog for deleting bookmark items, with the destructive
 * confirm button tinted from the error role.
 */
@Composable
internal fun ConfirmBookmarksDeleteDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Confirmation dialog for flattening a folder.
 */
@Composable
internal fun ConfirmFlattenFolderDialog(
    folderTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.flatten_folder)) },
        text = {
            Text(stringResource(R.string.flatten_folder_confirm_formatted, folderTitle))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.flatten_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * The sort dialog of the original sheet. All three options perform the same
 * title-ascending sort: the View implementation mapped every entry to
 * `BookmarkSortType.A_Z` (URL and date ordering were never implemented), and
 * that behavior is preserved verbatim.
 */
@Composable
internal fun SortBookmarksDialog(
    onDismiss: () -> Unit,
    onSortPicked: () -> Unit,
) {
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SortOption(label = stringResource(R.string.sort_by_name), onClick = onSortPicked)
                SortOption(label = stringResource(R.string.sort_by_url), onClick = onSortPicked)
                SortOption(label = stringResource(R.string.sort_by_date_added), onClick = onSortPicked)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SortOption(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLargeEmphasized)
    }
}

/**
 * The default folder for a newly added bookmark: the folder pinned in
 * preferences when set, otherwise the root. Port of the old dialog's
 * `getRootPosition()`.
 */
internal fun defaultBookmarkFolder(context: Context, manager: BookmarkManager): BookmarkFolderItem {
    val prefs = UserPreferences(context)
    if (prefs.bookmarkFolder) {
        val item = manager[prefs.bookmarkFolderId]
        if (item is BookmarkFolderItem) {
            return item
        }
    }
    return manager.root
}

/**
 * Mirrors a saved bookmark into the Gecko places storage, as the original
 * add-bookmark dialog did after every successful save.
 */
internal fun syncBookmarkToPlaces(context: Context, url: String, title: String?) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            context.components.bookmarksStorage.addItem(
                "toolbar_____",
                url,
                title ?: url,
                UInt.MAX_VALUE,
            )
        } catch (_: Exception) {
        }
    }
}

internal fun isDescendantOf(folder: BookmarkFolderItem, ancestor: BookmarkFolderItem): Boolean {
    var current = folder.parent
    while (current != null) {
        if (current === ancestor) return true
        current = current.parent
    }
    return false
}

internal fun folderDisplayName(context: Context, manager: BookmarkManager, folder: BookmarkFolderItem): String =
    if (folder === manager.root) {
        context.getString(R.string.bookmark_root_folder)
    } else {
        folder.title ?: context.getString(R.string.bookmark_unnamed_folder)
    }

/**
 * Shares a bookmark URL through the system chooser, as the old popup menu's
 * share action did.
 */
internal fun shareBookmarkUrl(context: Context, url: String) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, url)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
}
