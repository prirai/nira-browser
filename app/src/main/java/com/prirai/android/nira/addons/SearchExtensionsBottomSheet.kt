@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.addons

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.components.NiraLoadingIndicator
import com.prirai.android.nira.ui.components.NiraSearchField
import com.prirai.android.nira.ui.components.NiraSheetHeader
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.translateName

class SearchExtensionsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val prefs = UserPreferences(context)
                
                NiraTheme(
                    darkTheme = com.prirai.android.nira.theme.ThemeManager.isDarkMode(context),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors
                ) {
                    SearchExtensionsContent(
                        onDismiss = { dismiss() },
                        onExtensionClick = { addon ->
                            val intent = if (addon.isInstalled()) {
                                Intent(context, InstalledAddonDetailsActivity::class.java)
                            } else {
                                Intent(context, AddonDetailsActivity::class.java)
                            }
                            intent.putExtra("add_on", addon)
                            context.startActivity(intent)
                            dismiss()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchExtensionsContent(
    onDismiss: () -> Unit,
    onExtensionClick: (Addon) -> Unit
) {
    val context = LocalContext.current
    val searchQuery = remember { mutableStateOf("") }
    val allAddons = remember { mutableStateOf<List<Addon>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val addons = context.components.addonManager.getAddons()
                allAddons.value = addons
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }
    }

    val query = searchQuery.value.trim()
    val filteredAddons = remember(query, allAddons.value) {
        if (query.length < 2) {
            emptyList()
        } else {
            allAddons.value.filter { addon ->
                addon.translateName(context).contains(query, ignoreCase = true) ||
                    addon.translatableSummary.values.any { it.contains(query, ignoreCase = true) }
            }
        }
    }

    // The dialog theme's rounded_bottom_sheet provides the sheet chrome
    // (28dp top corners, colorSurface); the content draws no second surface.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
            NiraSheetHeader(
                title = stringResource(R.string.search_extensions),
                onClose = onDismiss,
                
                dividerAlpha = null,
            )

            // Search field
            NiraSearchField(
                query = searchQuery.value,
                onQueryChange = { searchQuery.value = it },
                placeholder = stringResource(R.string.search_by_name_or_description),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            // Results
            if (isLoading.value) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    NiraLoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (query.length < 2) {
                        item {
                            NiraEmptyState(
                                title = stringResource(R.string.type_to_search_extensions),
                                icon = Icons.Default.Search,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                            )
                        }
                    } else if (filteredAddons.isEmpty()) {
                        item {
                            NiraEmptyState(
                                title = stringResource(R.string.no_extensions_found),
                                icon = Icons.Default.Extension,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                            )
                        }
                    } else {
                        items(filteredAddons) { addon ->
                            SearchExtensionItem(
                                addon = addon,
                                onClick = { onExtensionClick(addon) }
                            )
                        }
                    }
                }
            }
        }
}

@Composable
fun SearchExtensionItem(
    addon: Addon,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Extension icon
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                val iconToUse = addon.installedState?.icon ?: addon.icon
                val iconUrlToUse = if (addon.installedState != null) "" else addon.iconUrl
                
                when {
                    iconToUse != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(iconToUse)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                    iconUrlToUse.isNotEmpty() -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(iconUrlToUse)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Extension info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = addon.translateName(context),
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (addon.isInstalled()) {
                    Text(
                        text = stringResource(R.string.status_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    addon.translatableSummary.values.firstOrNull()?.let { summary ->
                        if (summary.isNotEmpty()) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
