package com.prirai.android.nira.addons

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.translateName

private fun Context.getColorFromAttr(attr: Int): Int {
    val typedValue = android.util.TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

class ExtensionsBottomSheetFragment : BottomSheetDialogFragment() {

    private var installedAddons: List<Addon> = emptyList()

    companion object {
        const val TAG = "ExtensionsBottomSheet"
        private var cachedAddons: List<Addon>? = null
        private var lastCacheTime: Long = 0
        private const val CACHE_DURATION = 5000L // 5 seconds
        
        fun newInstance() = ExtensionsBottomSheetFragment()
        
        fun clearCache() {
            cachedAddons = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = androidx.compose.ui.platform.LocalContext.current
                val addons = remember { mutableStateOf<List<Addon>>(emptyList()) }
                val isLoading = remember { mutableStateOf(cachedAddons == null) }

                LaunchedEffect(Unit) {
                    val currentTime = System.currentTimeMillis()
                    
                    // Use cache if available and not expired
                    if (cachedAddons != null && (currentTime - lastCacheTime) < CACHE_DURATION) {
                        addons.value = cachedAddons!!
                        installedAddons = cachedAddons!!
                        isLoading.value = false
                    } else {
                        withContext(Dispatchers.IO) {
                            try {
                                // Get all addons (this loads icons properly)
                                val allAddons = context.components.addonManager.getAddons()
                                // Filter for installed and enabled ones
                                installedAddons = allAddons.filter { it.isInstalled() && it.isEnabled() }
                                addons.value = installedAddons
                                // Update cache
                                cachedAddons = installedAddons
                                lastCacheTime = currentTime
                            } catch (e: Exception) {
                                addons.value = emptyList()
                            } finally {
                                isLoading.value = false
                            }
                        }
                    }
                }

                val prefs = UserPreferences(context)
                NiraTheme(
                    darkTheme = ThemeManager.isDarkMode(context),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors
                ) {
                    ExtensionsBottomSheetContent(
                        addons = addons.value,
                        isLoading = isLoading.value,
                        onAddonClick = { addon ->
                            handleAddonClick(context, addon)
                        },
                        onManageAddonsClick = {
                            val intent = Intent(requireContext(), AddonsActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            dismiss()
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    private fun handleAddonClick(context: Context, addon: Addon) {
        dismiss()
        
        // Get the web extension state from the store
        val store = context.components.store
        val webExtension = store.state.extensions[addon.id]
        val selectedTab = store.state.selectedTab
        
        // Try to find and invoke the extension action (browser action or page action)
        var actionInvoked = false
        
        webExtension?.browserAction?.let { browserAction ->
            // Get tab-specific overrides if available
            val tabAction = selectedTab?.extensionState?.get(addon.id)?.browserAction
            val finalAction = if (tabAction != null) {
                browserAction.copyWithOverride(tabAction)
            } else {
                browserAction
            }
            
            // Invoke the action's onClick handler
            finalAction.onClick?.invoke()
            actionInvoked = true
        }
        
        if (!actionInvoked) {
            webExtension?.pageAction?.let { pageAction ->
                val tabAction = selectedTab?.extensionState?.get(addon.id)?.pageAction
                val finalAction = if (tabAction != null) {
                    pageAction.copyWithOverride(tabAction)
                } else {
                    pageAction
                }
                
                // Check if page action is enabled before invoking
                if (finalAction.enabled == true) {
                    finalAction.onClick?.invoke()
                    actionInvoked = true
                }
            }
        }
        
        // If no action was invoked, show the settings page
        if (!actionInvoked) {
            val intent = Intent(context, InstalledAddonDetailsActivity::class.java)
            intent.putExtra("add_on", addon)
            context.startActivity(intent)
        }
    }



    override fun onResume() {
        super.onResume()
        dialog?.window?.let { win ->
            val userPreferences = UserPreferences(requireContext())
            val isDarkTheme = ThemeManager.isDarkMode(requireContext())

            val bgColor = if (userPreferences.amoledMode && isDarkTheme) {
                Color.BLACK
            } else {
                requireContext().getColorFromAttr(R.attr.colorSurface)
            }

            win.decorView.setBackgroundColor(bgColor)
            win.navigationBarColor = bgColor
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsBottomSheetContent(
    addons: List<Addon>,
    isLoading: Boolean,
    onAddonClick: (Addon) -> Unit,
    onManageAddonsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
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
            
            // Title
            Text(
                text = "Extensions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            // Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    addons.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "No extensions installed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onManageAddonsClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Browse Add-ons")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(addons) { addon ->
                                ExtensionItem(
                                    addon = addon,
                                    onClick = { onAddonClick(addon) }
                                )
                            }
                            
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                ManageAddonsButton(onClick = onManageAddonsClick)
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExtensionItem(
    addon: Addon,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = UserPreferences(context)
    
    // Get currently pinned extensions
    val pinnedExtensions = remember { 
        mutableStateOf(prefs.barAddonsList.split(",").filter { it.isNotEmpty() }.toSet())
    }
    val isPinned = pinnedExtensions.value.contains(addon.id)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
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
                    overflow = TextOverflow.Ellipsis
                )
                
                if (addon.version.isNotEmpty()) {
                    Text(
                        text = "Version ${addon.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            
            // Pin/Unpin button
            androidx.compose.material3.IconButton(
                onClick = {
                    val currentPinned = prefs.barAddonsList.split(",").filter { it.isNotEmpty() }.toMutableSet()
                    if (isPinned) {
                        // Unpin
                        currentPinned.remove(addon.id)
                    } else {
                        // Pin
                        currentPinned.add(addon.id)
                    }
                    prefs.barAddonsList = currentPinned.joinToString(",")
                    pinnedExtensions.value = currentPinned
                    
                    // Show toast notification
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.app_restart),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isPinned) {
                            R.drawable.ic_pin_filled
                        } else {
                            R.drawable.ic_pin_outline
                        }
                    ),
                    contentDescription = if (isPinned) "Unpin extension" else "Pin extension",
                    tint = if (isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    HorizontalDivider(
        modifier = Modifier.padding(start = 80.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun ManageAddonsButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.mozac_ic_extension_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Manage Add-ons",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
