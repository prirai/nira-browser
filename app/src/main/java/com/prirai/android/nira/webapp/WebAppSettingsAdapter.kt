package com.prirai.android.nira.webapp

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.ItemWebappSettingBinding
import com.prirai.android.nira.components.Components
import com.prirai.android.nira.utils.FaviconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.IconRequest

/**
 * Adapter for displaying PWA settings items
 */
class WebAppSettingsAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val context: android.content.Context,
    private val onWebAppClick: (WebAppEntity) -> Unit,
    private val onUninstallClick: (WebAppEntity) -> Unit,
    private val onEnableToggle: (WebAppEntity, Boolean) -> Unit,
    private val onClearDataClick: (WebAppEntity) -> Unit,
    private val onUpdateCacheClick: (WebAppEntity) -> Unit,
    private val onAddShortcutClick: (WebAppEntity) -> Unit
) : ListAdapter<WebAppEntity, WebAppSettingsAdapter.WebAppViewHolder>(WebAppDiffCallback()) {

    private val viewLifecycleOwner = lifecycleOwner

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebAppViewHolder {
        val binding = ItemWebappSettingBinding.inflate(
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

    inner class WebAppViewHolder(private val binding: ItemWebappSettingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(webApp: WebAppEntity) {
            binding.apply {
                // Set PWA name
                webAppName.text = webApp.name

                // Set PWA URL
                webAppUrl.text = webApp.url

                // Set icon - try multiple sources
                viewLifecycleOwner.lifecycleScope.launch {
                    val icon = loadWebAppIcon(webApp)
                    if (icon != null) {
                        webAppIcon.setImageBitmap(icon)
                    } else {
                        // Use default icon with proper tint
                        webAppIcon.setImageResource(R.drawable.ic_language)
                        // Let the theme handle the tint
                    }
                }

                // Set enabled state
                enabledSwitch.isChecked = webApp.isEnabled

                // Set click listeners
                root.setOnClickListener { onWebAppClick(webApp) }
                uninstallButton.setOnClickListener { onUninstallClick(webApp) }
                clearDataButton.setOnClickListener { onClearDataClick(webApp) }
                updateCacheButton.setOnClickListener { onUpdateCacheClick(webApp) }
                addShortcutButton.setOnClickListener { onAddShortcutClick(webApp) }

                enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                    onEnableToggle(webApp, isChecked)
                }

                // Set offline status indicator
                val offlineStatus = if (webApp.isEnabled) {
                    "Online" // Would check actual offline capability
                } else {
                    "Offline not available"
                }
                offlineStatusText.text = binding.root.context.getString(R.string.pwa_offline_status, offlineStatus)

                // Show usage stats
                val lastUsedText = if (webApp.lastUsedDate > 0) {
                    val daysAgo = ((System.currentTimeMillis() - webApp.lastUsedDate) / (1000 * 60 * 60 * 24)).toInt()
                    if (daysAgo == 0) {
                        "Today"
                    } else if (daysAgo == 1) {
                        "1 day ago"
                    } else {
                        "$daysAgo days ago"
                    }
                } else {
                    "Never used"
                }

                usageInfo.text = "Launches: ${webApp.launchCount} • Last used: $lastUsedText"
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

    /**
     * Load webapp icon from multiple sources
     */
    private suspend fun loadWebAppIcon(webApp: WebAppEntity): Bitmap? {
        return withContext(Dispatchers.IO) {
            // 1. Try webapp's stored icon
            Components(context).webAppManager.loadIconFromFile(webApp.iconUrl)?.let { return@withContext it }
            
            // 2. Try favicon cache
            FaviconCache.getInstance(context).loadFavicon(webApp.url)?.let { return@withContext it }
            
            // 3. Try fetching from browser icons (BrowserIcons component)
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