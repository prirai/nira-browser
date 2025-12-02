package com.prirai.android.nira.webapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.databinding.ItemPwaSuggestionGridBinding
import com.prirai.android.nira.components.Components
import com.prirai.android.nira.utils.FaviconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.IconRequest

/**
 * Adapter for displaying PWA suggestions in grid layout
 */
class PwaSuggestionsAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val context: android.content.Context,
    private val onInstallClick: (PwaSuggestionManager.PwaSuggestion) -> Unit,
    private val onLearnMoreClick: (PwaSuggestionManager.PwaSuggestion) -> Unit
) : ListAdapter<PwaSuggestionManager.PwaSuggestion, PwaSuggestionsAdapter.PwaSuggestionViewHolder>(PwaSuggestionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PwaSuggestionViewHolder {
        val binding = ItemPwaSuggestionGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PwaSuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PwaSuggestionViewHolder, position: Int) {
        val pwa = getItem(position)
        holder.bind(pwa)
    }

    inner class PwaSuggestionViewHolder(private val binding: ItemPwaSuggestionGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(pwa: PwaSuggestionManager.PwaSuggestion) {
            binding.apply {
                // Set PWA name
                title.text = pwa.name

                // Load favicon
                lifecycleOwner.lifecycleScope.launch {
                    val icon = loadFavicon(pwa.url)
                    if (icon != null) {
                        favicon.setImageBitmap(icon)
                        favicon.imageTintList = null
                    } else {
                        favicon.setImageResource(R.drawable.ic_language)
                    }
                }

                // Title click installs the app
                title.setOnClickListener { onInstallClick(pwa) }
                container.setOnClickListener { onInstallClick(pwa) }

                // Info button shows details
                infoButton.setOnClickListener { onLearnMoreClick(pwa) }
            }
        }
        
        private suspend fun loadFavicon(url: String): android.graphics.Bitmap? {
            return withContext(Dispatchers.IO) {
                try {
                    // Extract domain from URL
                    val domain = try {
                        java.net.URL(url).host
                    } catch (e: Exception) {
                        url
                    }
                    
                    // 1. Try favicon cache first
                    FaviconCache.getInstance(context).loadFavicon(url)?.let { return@withContext it }
                    
                    // 2. Try Google's favicon service (most reliable)
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
                            FaviconCache.getInstance(context).saveFavicon(url, bitmap)
                            return@withContext bitmap
                        }
                    } catch (e: Exception) {
                        // Fallback to browser icons
                    }
                    
                    // 3. Try fetching from browser icons as final fallback
                    val iconRequest = IconRequest(url = url)
                    val icon = Components(context).icons.loadIcon(iconRequest).await()
                    icon.bitmap?.let {
                        // Save to cache for future use
                        FaviconCache.getInstance(context).saveFavicon(url, it)
                        return@withContext it
                    }
                    
                    null
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private class PwaSuggestionDiffCallback : DiffUtil.ItemCallback<PwaSuggestionManager.PwaSuggestion>() {
        override fun areItemsTheSame(oldItem: PwaSuggestionManager.PwaSuggestion, newItem: PwaSuggestionManager.PwaSuggestion): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: PwaSuggestionManager.PwaSuggestion, newItem: PwaSuggestionManager.PwaSuggestion): Boolean {
            return oldItem == newItem
        }
    }
}