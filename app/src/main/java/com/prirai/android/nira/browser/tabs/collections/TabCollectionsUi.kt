package com.prirai.android.nira.browser.tabs.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.ui.components.NiraAlertDialog
import mozilla.components.feature.tab.collections.Tab
import mozilla.components.feature.tab.collections.TabCollection

/** Save the current tabs as a new collection, or add them to an existing one. */
@Composable
fun SaveToCollectionDialog(
    tabCount: Int,
    collections: List<TabCollection>,
    onCreate: (name: String) -> Unit,
    onAddTo: (TabCollection) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }

    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collection_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = pluralStringResource(R.plurals.collection_save_summary, tabCount, tabCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.collection_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (collections.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.collection_add_to_existing),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(collections, key = { it.id }) { collection ->
                            Text(
                                text = collection.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddTo(collection) }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.collection_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/** Lists collections with restore / rename / delete, and per-tab restore / remove. */
@Composable
fun CollectionsDialog(
    collections: List<TabCollection>,
    onRestoreAll: (TabCollection) -> Unit,
    onRestoreTab: (Tab) -> Unit,
    onRemoveTab: (TabCollection, Tab) -> Unit,
    onRename: (TabCollection, String) -> Unit,
    onDelete: (TabCollection) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<TabCollection?>(null) }
    var deleting by remember { mutableStateOf<TabCollection?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }

    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collections_title)) },
        text = {
            if (collections.isEmpty()) {
                Text(stringResource(R.string.collections_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(collections, key = { it.id }) { collection ->
                        val expanded = expandedId == collection.id
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedId = if (expanded) null else collection.id },
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(
                                        text = collection.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.collection_tab_count,
                                            collection.tabs.size,
                                            collection.tabs.size,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onRestoreAll(collection) }) {
                                    Icon(Icons.Rounded.Restore, stringResource(R.string.collection_restore_all))
                                }
                                IconButton(onClick = { renaming = collection }) {
                                    Icon(Icons.Rounded.Edit, stringResource(R.string.collection_rename))
                                }
                                IconButton(onClick = { deleting = collection }) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.collection_delete))
                                }
                                Icon(
                                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                )
                            }
                            if (expanded) {
                                collection.tabs.forEach { tab ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onRestoreTab(tab) }
                                            .padding(start = 16.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                                            Text(
                                                text = tab.title.ifBlank { tab.url },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                text = tab.url,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { onRemoveTab(collection, tab) }) {
                                            Icon(Icons.Rounded.Close, stringResource(R.string.collection_remove_tab))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )

    renaming?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.title) }
        NiraAlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.collection_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.collection_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onRename(target, newName.trim())
                        renaming = null
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    deleting?.let { target ->
        NiraAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.collection_delete)) },
            text = { Text(stringResource(R.string.collection_delete_confirm, target.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    deleting = null
                }) { Text(stringResource(R.string.collection_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
