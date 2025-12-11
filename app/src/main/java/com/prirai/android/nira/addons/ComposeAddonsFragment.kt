package com.prirai.android.nira.addons

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.translateName

class ComposeAddonsFragment : Fragment() {

    private var showSortMenuTrigger: (() -> Unit)? = null
    private var showSearchTrigger: (() -> Unit)? = null

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
            R.id.action_search -> {
                showSearchTrigger?.invoke()
                true
            }
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
                        onSearchTriggerReady = { trigger -> showSearchTrigger = trigger },
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
    onSearchTriggerReady: ((()-> Unit) -> Unit)? = null,
    onSortMenuTriggerReady: ((()-> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val allAddonsOriginal = remember { mutableStateOf<List<Addon>>(emptyList()) }
    val recommendedAddons = remember { mutableStateOf<List<Addon>>(emptyList()) }
    val enabledAddons = remember { mutableStateOf<List<Addon>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val selectedSort = remember { mutableStateOf("Name") }
    val showSortMenu = remember { mutableStateOf(false) }
    
    // Wire menu triggers
    LaunchedEffect(Unit) {
        onSearchTriggerReady?.invoke {
            val searchSheet = SearchExtensionsBottomSheet()
            searchSheet.show((context as androidx.fragment.app.FragmentActivity).supportFragmentManager, "search_extensions")
        }
        onSortMenuTriggerReady?.invoke {
            showSortMenu.value = true
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val allAddons = context.components.addonManager.getAddons()
                allAddonsOriginal.value = allAddons
                
                val installed = allAddons.filter { it.isInstalled() && it.isEnabled() }
                val recommended = allAddons.filter { !it.isInstalled() }
                
                enabledAddons.value = installed
                recommendedAddons.value = recommended
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }
    }

    // Apply sort
    val filteredEnabled = remember(selectedSort.value, enabledAddons.value) {
        var result = enabledAddons.value
        
        // Apply sort
        result = when (selectedSort.value) {
            "Name" -> result.sortedBy { it.translateName(context) }
            "Recently Updated" -> result.sortedByDescending { it.updatedAt }
            else -> result
        }
        
        result
    }
    
    val filteredRecommended = remember(selectedSort.value, recommendedAddons.value) {
        var result = recommendedAddons.value
        
        // Apply sort
        result = when (selectedSort.value) {
            "Name" -> result.sortedBy { it.translateName(context) }
            "Rating" -> result.sortedByDescending { it.rating?.average ?: 0f }
            "Recently Updated" -> result.sortedByDescending { it.updatedAt }
            else -> result
        }
        
        result
    }

    Box {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            if (isLoading.value) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Enabled section
                if (filteredEnabled.isNotEmpty()) {
                    item {
                        AddonSection(
                            title = "Enabled (${filteredEnabled.size})",
                            addons = filteredEnabled,
                            onAddonClick = { addon ->
                                val intent = Intent(context, InstalledAddonDetailsActivity::class.java)
                                intent.putExtra("add_on", addon)
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                // Recommended section
                if (filteredRecommended.isNotEmpty()) {
                    item {
                        AddonSection(
                            title = "Recommended (${filteredRecommended.size})",
                            addons = filteredRecommended,
                            onAddonClick = { addon ->
                                val intent = Intent(context, AddonDetailsActivity::class.java)
                                intent.putExtra("add_on", addon)
                                context.startActivity(intent)
                            }
                        )
                    }
                }
                
                // "All extensions" link
                item {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://addons.mozilla.org/android/"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "All extensions",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Empty state
                if (filteredEnabled.isEmpty() && filteredRecommended.isEmpty() && !isLoading.value) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No extensions available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        text = { Text(sort) },
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        // iOS-style list
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Column {
                addons.forEachIndexed { index, addon ->
                    AddonListItem(
                        addon = addon,
                        onClick = { onAddonClick(addon) },
                        isFirst = index == 0,
                        isLast = index == addons.lastIndex
                    )
                    
                    if (index < addons.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 80.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddonListItem(
    addon: Addon,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Extension icon
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
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
                                .padding(8.dp)
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
                                .padding(8.dp)
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Extension info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = addon.translateName(context),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                addon.translatableSummary.values.firstOrNull()?.let { summary ->
                    if (summary.isNotEmpty()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
