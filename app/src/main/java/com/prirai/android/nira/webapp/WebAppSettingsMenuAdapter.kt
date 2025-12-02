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
import com.prirai.android.nira.components.Components
import com.prirai.android.nira.utils.FaviconCache
import com.prirai.android.nira.browser.profile.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.IconRequest

/**
 * Adapter for displaying PWA settings items with three-dot menu
 */
class WebAppSettingsMenuAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val context: android.content.Context,
    private val onWebAppClick: (WebAppEntity) -> Unit,
    private val onEnableToggle: (WebAppEntity, Boolean) -> Unit,
    private val onAddShortcut: (WebAppEntity) -> Unit,
    private val onAssociateProfile: (WebAppEntity) -> Unit,
    private val onClone: (WebAppEntity) -> Unit,
    private val onUpdateCache: (WebAppEntity) -> Unit,
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

                // Set icon - try multiple sources
                lifecycleOwner.lifecycleScope.launch {
                    val icon = loadWebAppIcon(webApp)
                    if (icon != null) {
                        webAppIcon.setImageBitmap(icon)
                    } else {
                        webAppIcon.setImageResource(R.drawable.ic_language)
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
                            R.id.update_cache -> {
                                onUpdateCache(webApp)
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

        private suspend fun loadWebAppIcon(webApp: WebAppEntity): android.graphics.Bitmap? {
            return withContext(Dispatchers.IO) {
                // Extract domain from URL
                val domain = try {
                    java.net.URL(webApp.url).host
                } catch (e: Exception) {
                    webApp.url
                }
                
                // 1. Try webapp's stored icon
                Components(context).webAppManager.loadIconFromFile(webApp.iconUrl)?.let { return@withContext it }
                
                // 2. Try favicon cache
                FaviconCache.getInstance(context).loadFavicon(webApp.url)?.let { return@withContext it }
                
                // 3. Try Google's favicon service (most reliable)
                try {
                    val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
                    val connection = java.net.URL(faviconUrl).openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    val inputStream = connection.getInputStream()
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    
                    if (bitmap != null) {
                        // Save to cache for future use
                        FaviconCache.getInstance(context).saveFavicon(webApp.url, bitmap)
                        return@withContext bitmap
                    }
                } catch (e: Exception) {
                    // Continue to fallback
                }
                
                // 4. Try fetching from browser icons as final fallback
                try {
                    val iconRequest = IconRequest(url = webApp.url)
                    val icon = Components(context).icons.loadIcon(iconRequest).await()
                    icon.bitmap
                } catch (e: Exception) {
                    null
                }
            }
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
