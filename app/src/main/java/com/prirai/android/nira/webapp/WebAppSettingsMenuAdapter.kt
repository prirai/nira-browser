package com.prirai.android.nira.webapp

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.ItemWebappSettingMenuBinding
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.utils.FaviconLoader
import kotlinx.coroutines.launch

/**
 * Adapter for displaying PWA settings items with three-dot menu
 * Uses centralized FaviconLoader for consistent fast icon loading
 */
class WebAppSettingsMenuAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val context: android.content.Context,
    private val onWebAppClick: (WebAppEntity) -> Unit,
    private val onEnableToggle: (WebAppEntity, Boolean) -> Unit,
    private val onAddShortcut: (WebAppEntity) -> Unit,
    private val onAssociateProfile: (WebAppEntity) -> Unit,
    private val onClone: (WebAppEntity) -> Unit,
    private val onClearData: (WebAppEntity) -> Unit,
    private val onUninstall: (WebAppEntity) -> Unit
) : ListAdapter<WebAppEntity, WebAppSettingsMenuAdapter.WebAppViewHolder>(WebAppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebAppViewHolder {
        val binding = ItemWebappSettingMenuBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WebAppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WebAppViewHolder, position: Int) {
        val webApp = getItem(position)
        holder.bind(webApp)
    }

    inner class WebAppViewHolder(private val binding: ItemWebappSettingMenuBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(webApp: WebAppEntity) {
            binding.apply {
                // Set PWA name
                webAppName.text = webApp.name

                // Set PWA URL
                webAppUrl.text = webApp.url

                // Try instant memory cache first
                val cachedIcon = FaviconLoader.getFromMemorySync(context, webApp.url)
                if (cachedIcon != null) {
                    // Instant display from memory cache
                    webAppIcon.setImageBitmap(cachedIcon)
                } else {
                    // Show default icon while loading
                    webAppIcon.setImageResource(R.drawable.ic_language)
                    
                    // Load icon asynchronously
                    lifecycleOwner.lifecycleScope.launch {
                        val icon = loadWebAppIconFast(webApp)
                        if (icon != null) {
                            webAppIcon.setImageBitmap(icon)
                        }
                    }
                }

                // Show profile badge
                val profileManager = ProfileManager.getInstance(context)
                val profile = profileManager.getAllProfiles().find { it.id == webApp.profileId }
                if (profile != null) {
                    profileBadge.text = context.getString(R.string.current_profile, profile.name)
                } else {
                    profileBadge.text = context.getString(R.string.current_profile, "Default")
                }

                // Set enabled state
                enabledSwitch.isChecked = webApp.isEnabled

                // Show usage stats
                val lastUsedText = if (webApp.lastUsedDate > 0) {
                    val daysAgo = ((System.currentTimeMillis() - webApp.lastUsedDate) / (1000 * 60 * 60 * 24)).toInt()
                    when {
                        daysAgo == 0 -> "Today"
                        daysAgo == 1 -> "1 day ago"
                        else -> "$daysAgo days ago"
                    }
                } else {
                    "Never used"
                }
                usageInfo.text = "Launches: ${webApp.launchCount} • Last: $lastUsedText"

                // Set click listeners
                header.setOnClickListener { onWebAppClick(webApp) }

                enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                    onEnableToggle(webApp, isChecked)
                }

                // Three-dot menu
                menuButton.setOnClickListener { view ->
                    val popup = PopupMenu(context, view)
                    popup.menuInflater.inflate(R.menu.webapp_menu, popup.menu)
                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.add_shortcut -> {
                                onAddShortcut(webApp)
                                true
                            }

                            R.id.associate_profile -> {
                                onAssociateProfile(webApp)
                                true
                            }

                            R.id.clone_webapp -> {
                                onClone(webApp)
                                true
                            }

                            R.id.clear_data -> {
                                onClearData(webApp)
                                true
                            }

                            R.id.uninstall -> {
                                onUninstall(webApp)
                                true
                            }

                            else -> false
                        }
                    }
                    popup.show()
                }
            }
        }

        /**
         * Fast webapp icon loading: stored icon first, then centralized FaviconLoader
         */
        private suspend fun loadWebAppIconFast(webApp: WebAppEntity): android.graphics.Bitmap? {
            // Try webapp's stored icon first
            context.components.webAppManager.loadIconFromFile(webApp.iconUrl)?.let {
                return it
            }

            // Use centralized FaviconLoader for consistent caching
            return FaviconLoader.loadFavicon(context, webApp.url)
        }
    }

    private class WebAppDiffCallback : DiffUtil.ItemCallback<WebAppEntity>() {
        override fun areItemsTheSame(oldItem: WebAppEntity, newItem: WebAppEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WebAppEntity, newItem: WebAppEntity): Boolean {
            return oldItem == newItem
        }
    }
}
