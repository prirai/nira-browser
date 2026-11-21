@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.addons

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.components.NiraLoadingIndicator
import com.prirai.android.nira.ui.components.NiraSearchField
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.translateName
import androidx.core.net.toUri

class ComposeAddonsFragment : Fragment() {

    private var showSortMenuTrigger: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.addons_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortMenuTrigger?.invoke()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

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
                
                // Edge-to-edge
                LaunchedEffect(Unit) {
                    val window = requireActivity().window
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !ThemeManager.isDarkMode(context)
                }
                
                NiraTheme(
                    darkTheme = ThemeManager.isDarkMode(context),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors
                ) {
                    AddonsScreen(
                        onSortMenuTriggerReady = { trigger -> showSortMenuTrigger = trigger }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonsScreen(
    onSortMenuTriggerReady: ((()-> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val enabledAddons = remember { mutableStateOf<List<Addon>>(emptyList()) }
    val featuredAddons = remember { mutableStateOf<List<Addon>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val selectedSort = remember { mutableStateOf("Name") }
    val showSortMenu = remember { mutableStateOf(false) }
    val query = remember { mutableStateOf("") }
    // null while a search request is in flight
    val searchResults = remember { mutableStateOf<List<Addon>?>(emptyList()) }

    LaunchedEffect(Unit) {
        onSortMenuTriggerReady?.invoke { showSortMenu.value = true }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val all = context.components.addonManager.getAddons()
                val installed = all.filter { it.isInstalled() && it.isEnabled() }
                enabledAddons.value = installed
                val installedIds = all.filter { it.isInstalled() }.map { it.id }.toSet()
                val featured = runCatching { AmoClient.featured() }.getOrDefault(emptyList())
                    .ifEmpty { all.filter { !it.isInstalled() } }
                featuredAddons.value = featured.filter { it.id !in installedIds }
            } catch (e: Exception) {
                // Leave the lists empty; the empty state covers it
            } finally {
                isLoading.value = false
            }
        }
    }

    val trimmedQuery = query.value.trim()
    val isSearching = trimmedQuery.length >= 2
    LaunchedEffect(trimmedQuery) {
        if (!isSearching) {
            searchResults.value = emptyList()
            return@LaunchedEffect
        }
        searchResults.value = null
        delay(300)
        val installed = enabledAddons.value.associateBy { it.id }
        searchResults.value = runCatching { AmoClient.search(trimmedQuery) }.getOrDefault(emptyList())
            .map { installed[it.id] ?: it }
    }

    val sortedEnabled = remember(selectedSort.value, enabledAddons.value) {
        when (selectedSort.value) {
            "Name" -> enabledAddons.value.sortedBy { it.translateName(context) }
            "Recently Updated" -> enabledAddons.value.sortedByDescending { it.updatedAt }
            else -> enabledAddons.value
        }
    }
    val sortedFeatured = remember(selectedSort.value, featuredAddons.value) {
        when (selectedSort.value) {
            "Name" -> featuredAddons.value.sortedBy { it.translateName(context) }
            "Rating" -> featuredAddons.value.sortedByDescending { it.rating?.average ?: 0f }
            "Recently Updated" -> featuredAddons.value.sortedByDescending { it.updatedAt }
            else -> featuredAddons.value
        }
    }

    val openAddon: (Addon) -> Unit = { addon ->
        val target = AddonDetailsActivity::class.java
        context.startActivity(Intent(context, target).putExtra("add_on", addon))
    }

    Box {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NiraSearchField(
                    query = query.value,
                    onQueryChange = { query.value = it },
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                val results = searchResults.value
                when {
                    isLoading.value && !isSearching -> NiraLoadingIndicator()
                    isSearching && results == null -> NiraLoadingIndicator()
                    isSearching && results.orEmpty().isEmpty() -> NiraEmptyState(
                        title = stringResource(R.string.no_extensions_found),
                        icon = Icons.Default.Extension,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (isSearching) {
                            item {
                                AddonSection(
                                    title = stringResource(R.string.search_results_section, results.orEmpty().size),
                                    addons = results.orEmpty(),
                                    onAddonClick = openAddon
                                )
                            }
                        } else {
                            if (sortedEnabled.isNotEmpty()) {
                                item {
                                    AddonSection(
                                        title = stringResource(R.string.enabled_section, sortedEnabled.size),
                                        addons = sortedEnabled,
                                        onAddonClick = openAddon
                                    )
                                }
                            }

                            item {
                                NiraCardRow(
                                    title = stringResource(R.string.all_extensions),
                                    isTopRow = true,
                                    isBottomRow = true,
                                    showDivider = false,
                                    minRowHeight = 56.dp,
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, "https://addons.mozilla.org/android/".toUri())
                                        )
                                    },
                                    leading = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.mozac_ic_extension_24),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailing = {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                )
                            }

                            if (sortedFeatured.isNotEmpty()) {
                                item {
                                    AddonSection(
                                        title = stringResource(R.string.featured_section, sortedFeatured.size),
                                        addons = sortedFeatured,
                                        onAddonClick = openAddon
                                    )
                                }
                            }

                            if (sortedEnabled.isEmpty() && sortedFeatured.isEmpty()) {
                                item {
                                    NiraEmptyState(
                                        title = stringResource(R.string.no_extensions_available),
                                        icon = Icons.Default.Extension,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp, horizontal = 32.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sort dropdown menu
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 16.dp) // Position below toolbar
        ) {
            DropdownMenu(
                expanded = showSortMenu.value,
                onDismissRequest = { showSortMenu.value = false }
            ) {
                listOf("Name", "Rating", "Recently Updated").forEach { sort ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (sort) {
                                    "Name" -> stringResource(R.string.sort_name)
                                    "Rating" -> stringResource(R.string.sort_rating)
                                    else -> stringResource(R.string.sort_recently_updated)
                                }
                            )
                        },
                        onClick = {
                            selectedSort.value = sort
                            showSortMenu.value = false
                        },
                        trailingIcon = {
                            if (selectedSort.value == sort) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check_circle),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AddonSection(
    title: String,
    addons: List<Addon>,
    onAddonClick: (Addon) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
        )

        Column {
            addons.forEachIndexed { index, addon ->
                AddonListItem(
                    addon = addon,
                    isTopRow = index == 0,
                    isBottomRow = index == addons.lastIndex,
                    onClick = { onAddonClick(addon) }
                )
            }
        }
    }
}

@Composable
fun AddonListItem(
    addon: Addon,
    isTopRow: Boolean,
    isBottomRow: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val summary = addon.translatableSummary.values.firstOrNull().orEmpty()
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        NiraAlertDialog(
            onDismissRequest = { showInfo = false },
            title = {
                Text(
                    text = addon.translateName(context),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            },
            text = {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfo = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.mozac_feature_prompts_ok))
                }
            },
        )
    }

    NiraCardRow(
        title = addon.translateName(context),
        isTopRow = isTopRow,
        isBottomRow = isBottomRow,
        showDivider = !isBottomRow,
        onClick = onClick,
        minRowHeight = 56.dp,
        dividerIndent = 60.dp,
        leading = {
            val iconToUse = addon.installedState?.icon ?: addon.icon
            val iconUrl = if (addon.installedState != null) "" else addon.iconUrl
            val model: Any? = iconToUse ?: iconUrl.takeIf { it.isNotEmpty() }
            if (model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).padding(4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailing = {
            if (summary.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.info_icon_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showInfo = true }
                        .padding(8.dp)
                        .size(20.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
    )
}
