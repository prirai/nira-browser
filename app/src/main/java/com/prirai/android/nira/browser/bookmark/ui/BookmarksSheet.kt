@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.bookmark.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.ui.res.painterResource
import com.prirai.android.nira.ui.components.NiraSearchField
import com.prirai.android.nira.ui.components.NiraSelectionBar
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.browser.tabs.compose.FaviconImageFromUrl
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.components.NiraSheetHeader
import java.util.Locale
import mozilla.components.browser.state.selector.selectedTab

internal sealed interface BookmarksDeleteRequest {
    data class Single(val item: BookmarkItem) : BookmarksDeleteRequest
    data class Multiple(val items: List<BookmarkItem>) : BookmarksDeleteRequest
}

internal sealed interface BookmarksMoveRequest {
    data class Single(val item: BookmarkItem) : BookmarksMoveRequest
    data class Multiple(val items: List<BookmarkItem>) : BookmarksMoveRequest
}

/**
 * The bookmarks bottom sheet body: the grouped-card folder/list view, the
 * multiselect header, breadcrumb folder navigation and every bookmark and
 * folder dialog, on the shared Material 3 Expressive components.
 *
 * The bookmark tree in [BookmarkManager] is a mutable in-place model, so every
 * mutation bumps [revision] to force the list to re-read the folder contents —
 * the Compose counterpart of the old adapter re-creation in `setBookmarkList`.
 */
@Composable
internal fun BookmarksSheet(
    manager: BookmarkManager,
    initialFolder: BookmarkFolderItem,
    onOpenBookmark: (BookmarkSiteItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var currentFolder by remember { mutableStateOf(initialFolder) }
    var revision by remember { mutableIntStateOf(0) }
    val selectedItems = remember { mutableStateListOf<BookmarkItem>() }
    val isMultiSelect = selectedItems.isNotEmpty()
    var query by remember { mutableStateOf("") }

    var showAddBookmark by remember { mutableStateOf(false) }
    var editSite by remember { mutableStateOf<BookmarkSiteItem?>(null) }
    var showAddFolder by remember { mutableStateOf(false) }
    var editFolder by remember { mutableStateOf<BookmarkFolderItem?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var deleteRequest by remember { mutableStateOf<BookmarksDeleteRequest?>(null) }
    var moveRequest by remember { mutableStateOf<BookmarksMoveRequest?>(null) }
    var flattenTarget by remember { mutableStateOf<BookmarkFolderItem?>(null) }

    val items = remember(currentFolder, revision, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            currentFolder.itemList.toList()
        } else {
            // Searching walks every subfolder; results are shown as a flat list.
            descendants(currentFolder).filter { item ->
                item.title?.contains(needle, ignoreCase = true) == true ||
                    (item as? BookmarkSiteItem)?.url?.contains(needle, ignoreCase = true) == true
            }
        }
    }

    fun parentOf(item: BookmarkItem): BookmarkFolderItem =
        findParent(currentFolder, item) ?: currentFolder

    fun refresh() {
        revision++
    }

    fun openInBackground(url: String) {
        context.components.tabsUseCases.addTab.invoke(url = url, selectTab = false)
    }

    fun exitMultiSelect() {
        selectedItems.clear()
    }

    fun openSelectedInBackground() {
        val sites = selectedItems.filterIsInstance<BookmarkSiteItem>()
        if (sites.isEmpty()) {
            Toast.makeText(context, R.string.bookmark_no_sites_selected, Toast.LENGTH_SHORT).show()
            return
        }
        sites.forEach { site -> openInBackground(site.url) }
        Toast.makeText(
            context,
            context.getString(R.string.bookmark_opened_tabs_background, sites.size),
            Toast.LENGTH_SHORT,
        ).show()
        exitMultiSelect()
    }

    fun moveItems(itemsToMove: List<BookmarkItem>, targetFolder: BookmarkFolderItem) {
        try {
            itemsToMove.forEach { item ->
                parentOf(item).itemList.remove(item)
                targetFolder.add(item)
                if (item is BookmarkFolderItem) {
                    item.parent = targetFolder
                }
            }
            manager.save()
            refresh()
            val targetName = folderDisplayName(context, manager, targetFolder)
            val message = if (itemsToMove.size == 1) {
                context.getString(
                    R.string.bookmark_moved_to,
                    itemsToMove.first().title.orEmpty(),
                    targetName,
                )
            } else {
                context.getString(R.string.bookmark_moved_items_to, itemsToMove.size, targetName)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            val messageRes = if (itemsToMove.size == 1) {
                R.string.bookmark_error_moving
            } else {
                R.string.bookmark_error_moving_items
            }
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteItems(itemsToDelete: List<BookmarkItem>) {
        if (itemsToDelete.size == 1) {
            try {
                parentOf(itemsToDelete.first()).itemList.remove(itemsToDelete.first())
                manager.save()
                refresh()
            } catch (e: Exception) {
                Toast.makeText(context, R.string.bookmark_error_deleting, Toast.LENGTH_SHORT).show()
            }
        } else {
            try {
                itemsToDelete.forEach { parentOf(it).itemList.remove(it) }
                manager.save()
                refresh()
                Toast.makeText(
                    context,
                    context.getString(R.string.bookmark_deleted_items, itemsToDelete.size),
                    Toast.LENGTH_SHORT,
                ).show()
                exitMultiSelect()
            } catch (e: Exception) {
                Toast.makeText(context, R.string.bookmark_error_deleting_items, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun flatten(folder: BookmarkFolderItem) {
        try {
            manager.flattenFolder(parentOf(folder), folder)
            manager.save()
            refresh()
            Toast.makeText(
                context,
                context.getString(R.string.bookmark_flattened, folder.title.orEmpty()),
                Toast.LENGTH_SHORT,
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, R.string.bookmark_error_flattening, Toast.LENGTH_SHORT).show()
        }
    }

    fun sortFolder(order: BookmarkSortOrder) {
        val list = currentFolder.itemList
        when (order) {
            BookmarkSortOrder.NAME -> list.sortBy { it.title?.lowercase(Locale.getDefault()) }
            BookmarkSortOrder.URL -> list.sortBy { (it as? BookmarkSiteItem)?.url?.lowercase(Locale.getDefault()) ?: "" }
            BookmarkSortOrder.DATE_ADDED -> list.sortBy { it.id }
        }
        manager.save()
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 640.dp),
    ) {
        run {
            val breadcrumbSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
            NiraSheetHeader(
                title = if (currentFolder === manager.root) {
                    stringResource(R.string.action_bookmarks)
                } else {
                    currentFolder.title.orEmpty()
                },
                onBack = if (currentFolder !== manager.root) {
                    { currentFolder = currentFolder.parent ?: manager.root }
                } else {
                    null
                },
                onClose = onDismiss,
                dividerAlpha = null,
                actions = {
                    IconButton(onClick = {
                        val all = currentFolder.itemList.toList()
                        if (selectedItems.size == all.size) {
                            selectedItems.clear()
                        } else {
                            selectedItems.clear()
                            selectedItems.addAll(all)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.SelectAll,
                            contentDescription = stringResource(R.string.bookmark_select_items),
                        )
                    }
                    IconButton(onClick = { showSort = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = stringResource(R.string.sort),
                        )
                    }
                    IconButton(onClick = { showAddFolder = true }) {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = stringResource(R.string.create_folder),
                        )
                    }
                    IconButton(onClick = { showAddBookmark = true }) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = stringResource(R.string.action_add_bookmark),
                        )
                    }
                },
            )
            AnimatedVisibility(
                visible = currentFolder !== manager.root,
                enter = expandVertically(breadcrumbSpec),
            ) {
                FolderBreadcrumbs(
                    root = manager.root,
                    currentFolder = currentFolder,
                    onNavigate = { currentFolder = it },
                )
            }
        }

        AnimatedContent(
            targetState = isMultiSelect,
            label = "bookmarksActionBar",
            modifier = Modifier.fillMaxWidth(),
        ) { selecting ->
            if (selecting) {
                BookmarkSelectionBar(
                    selected = selectedItems.toList(),
                    onClear = { exitMultiSelect() },
                    onOpenInBackground = { openSelectedInBackground() },
                    onEdit = { item ->
                        if (item is BookmarkSiteItem) editSite = item else editFolder = item as? BookmarkFolderItem
                    },
                    onMove = { moveRequest = BookmarksMoveRequest.Multiple(selectedItems.toList()) },
                    onFlatten = { flattenTarget = it },
                    onDelete = { deleteRequest = BookmarksDeleteRequest.Multiple(selectedItems.toList()) },
                )
            } else {
                NiraSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.search_bookmarks),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
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

        if (items.isEmpty()) {
            NiraEmptyState(
                title = stringResource(R.string.no_bookmarks),
                subtitle = stringResource(R.string.no_bookmarks_description),
                icon = Icons.Rounded.BookmarkBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                itemsIndexed(items, key = { index, item -> "${item.id}-$index" }) { index, item ->
                    BookmarkRow(
                        item = item,
                        isTopRow = index == 0,
                        isBottomRow = index == items.lastIndex,
                        showDivider = index < items.lastIndex,
                        isSelected = selectedItems.contains(item),
                        onClick = {
                            when {
                                isMultiSelect -> {
                                    if (selectedItems.contains(item)) {
                                        selectedItems.remove(item)
                                    } else {
                                        selectedItems.add(item)
                                    }
                                }
                                item is BookmarkFolderItem -> currentFolder = item
                                item is BookmarkSiteItem -> onOpenBookmark(item)
                            }
                        },
                        onLongClick = {
                            if (!selectedItems.contains(item)) selectedItems.add(item)
                        },
                    )
                }
            }
        }
    }

    if (showAddBookmark) {
        val tab = context.components.store.state.selectedTab
        BookmarkSiteEditDialog(
            manager = manager,
            item = null,
            initialTitle = tab?.content?.title.orEmpty(),
            initialUrl = tab?.content?.url.orEmpty(),
            onDismiss = { showAddBookmark = false },
            onSaved = {
                showAddBookmark = false
                refresh()
            },
        )
    }

    editSite?.let { site ->
        BookmarkSiteEditDialog(
            manager = manager,
            item = site,
            initialTitle = site.title.orEmpty(),
            initialUrl = site.url,
            onDismiss = { editSite = null },
            onSaved = {
                editSite = null
                refresh()
            },
        )
    }

    if (showAddFolder) {
        FolderEditDialog(
            manager = manager,
            parent = currentFolder,
            item = null,
            onDismiss = { showAddFolder = false },
            onSaved = {
                showAddFolder = false
                refresh()
            },
        )
    }

    editFolder?.let { folder ->
        FolderEditDialog(
            manager = manager,
            parent = folder.parent,
            item = folder,
            onDismiss = { editFolder = null },
            onSaved = {
                editFolder = null
                refresh()
            },
        )
    }

    if (showSort) {
        SortBookmarksDialog(
            onDismiss = { showSort = false },
            onSortPicked = { order ->
                showSort = false
                sortFolder(order)
            },
        )
    }

    deleteRequest?.let { request ->
        val message = when (request) {
            is BookmarksDeleteRequest.Single -> when (val item = request.item) {
                is BookmarkSiteItem -> stringResource(
                    R.string.delete_bookmark_confirm,
                    item.title.orEmpty(),
                )
                is BookmarkFolderItem -> stringResource(
                    R.string.delete_folder_confirm,
                    item.title.orEmpty(),
                )
                else -> stringResource(R.string.delete_confirm)
            }
            is BookmarksDeleteRequest.Multiple -> stringResource(
                R.string.bookmark_delete_items_confirm,
                request.items.size,
            )
        }
        ConfirmBookmarksDeleteDialog(
            message = message,
            onDismiss = { deleteRequest = null },
            onConfirm = {
                val itemsToDelete = when (request) {
                    is BookmarksDeleteRequest.Single -> listOf(request.item)
                    is BookmarksDeleteRequest.Multiple -> request.items
                }
                deleteRequest = null
                deleteItems(itemsToDelete)
            },
        )
    }

    moveRequest?.let { request ->
        val itemsToMove = when (request) {
            is BookmarksMoveRequest.Single -> listOf(request.item)
            is BookmarksMoveRequest.Multiple -> request.items
        }
        FolderPickerDialog(
            manager = manager,
            startFolder = manager.root,
            excludeItems = itemsToMove,
            onDismiss = { moveRequest = null },
            onSelect = { folder ->
                moveRequest = null
                moveItems(itemsToMove, folder)
            },
        )
    }

    flattenTarget?.let { folder ->
        ConfirmFlattenFolderDialog(
            folderTitle = folder.title.orEmpty(),
            onDismiss = { flattenTarget = null },
            onConfirm = {
                flattenTarget = null
                flatten(folder)
            },
        )
    }
}

private fun descendants(folder: BookmarkFolderItem): List<BookmarkItem> =
    folder.itemList.flatMap { item ->
        if (item is BookmarkFolderItem) listOf(item) + descendants(item) else listOf(item)
    }

private fun findParent(folder: BookmarkFolderItem, target: BookmarkItem): BookmarkFolderItem? {
    if (folder.itemList.any { it === target }) return folder
    return folder.itemList.filterIsInstance<BookmarkFolderItem>()
        .firstNotNullOfOrNull { findParent(it, target) }
}

/**
 * One bookmark or folder row of the list: the shared grouped-card row with a
 * favicon (or folder glyph) as the leading element. A long press starts
 * selection; the actions live in [BookmarkSelectionBar].
 */
@Composable
private fun BookmarkRow(
    item: BookmarkItem,
    isTopRow: Boolean,
    isBottomRow: Boolean,
    showDivider: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    NiraCardRow(
        title = when (item) {
            is BookmarkSiteItem -> item.title ?: item.url
            else -> item.title.orEmpty()
        },
        isTopRow = isTopRow,
        isBottomRow = isBottomRow,
        showDivider = showDivider,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = isSelected,
        leading = {
            when {
                isSelected -> Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                item is BookmarkSiteItem -> FaviconImageFromUrl(
                    url = item.url,
                    title = item.title.orEmpty(),
                    size = 24.dp,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                else -> Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    )
}

/** Contextual action bar that replaces the search field while bookmarks are selected. */
@Composable
private fun BookmarkSelectionBar(
    selected: List<BookmarkItem>,
    onClear: () -> Unit,
    onOpenInBackground: () -> Unit,
    onEdit: (BookmarkItem) -> Unit,
    onMove: () -> Unit,
    onFlatten: (BookmarkFolderItem) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val sites = selected.filterIsInstance<BookmarkSiteItem>()
    val single = selected.singleOrNull()

    NiraSelectionBar(onClear = onClear) {
        if (sites.isNotEmpty()) {
            IconButton(onClick = onOpenInBackground) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.open_in_background_tabs))
            }
            IconButton(onClick = {
                shareBookmarkUrl(context, sites.joinToString("\n") { it.url })
                onClear()
            }) {
                Icon(
                    painter = painterResource(R.drawable.ios_share_24),
                    contentDescription = stringResource(R.string.share),
                )
            }
        }
        if (single != null) {
            IconButton(onClick = { onEdit(single) }) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(
                        if (single is BookmarkSiteItem) R.string.edit_bookmark else R.string.edit_folder,
                    ),
                )
            }
            if (single is BookmarkFolderItem) {
                IconButton(onClick = { onFlatten(single) }) {
                    Icon(Icons.Rounded.UnfoldLess, contentDescription = stringResource(R.string.flatten_folder))
                }
            }
        }
        IconButton(onClick = onMove) {
            Icon(
                Icons.AutoMirrored.Rounded.DriveFileMove,
                contentDescription = stringResource(R.string.move_selected),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.delete_selected),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The breadcrumb path from the root folder down to [currentFolder]; every
 * ancestor is a navigation link. The old sheet left its path view permanently
 * empty (a TODO in the View implementation), so this is the first version
 * that actually renders the folder path.
 */
@Composable
private fun FolderBreadcrumbs(
    root: BookmarkFolderItem,
    currentFolder: BookmarkFolderItem,
    onNavigate: (BookmarkFolderItem) -> Unit,
) {
    val path = remember(currentFolder) {
        buildList {
            var node: BookmarkFolderItem? = currentFolder
            while (node != null && node !== root) {
                add(node)
                node = node.parent
            }
        }.asReversed()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.action_bookmarks),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { onNavigate(root) },
        )
        path.forEachIndexed { index, folder ->
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = folder.title.orEmpty(),
                style = if (index == path.lastIndex) {
                    MaterialTheme.typography.bodyMediumEmphasized
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (index == path.lastIndex) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (index == path.lastIndex) {
                    Modifier
                } else {
                    Modifier.clickable { onNavigate(folder) }
                },
            )
        }
    }
}
