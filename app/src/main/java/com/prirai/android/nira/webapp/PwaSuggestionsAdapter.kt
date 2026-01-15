package com.prirai.android.nira.webapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.prirai.android.nira.R
import com.prirai.android.nira.utils.FaviconLoader
import kotlinx.coroutines.launch

/**
 * Adapter for displaying PWA suggestions in grid layout
 * Uses centralized FaviconLoader for consistent fast icon loading
 */
class PwaSuggestionsAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val context: android.content.Context,
    private val onInstallClick: (PwaSuggestionManager.PwaSuggestion) -> Unit
) : ListAdapter<PwaSuggestionManager.PwaSuggestion, PwaSuggestionsAdapter.PwaSuggestionViewHolder>(
    PwaSuggestionDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PwaSuggestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pwa_suggestion_grid, parent, false)
        return PwaSuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: PwaSuggestionViewHolder, position: Int) {
        val pwa = getItem(position)
        holder.bind(pwa)
    }

    inner class PwaSuggestionViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val card: MaterialCardView = itemView.findViewById(R.id.container)
        private val favicon: ImageView = itemView.findViewById(R.id.favicon)
        private val title: TextView = itemView.findViewById(R.id.title)

        fun bind(pwa: PwaSuggestionManager.PwaSuggestion) {
            // Set PWA name
            title.text = pwa.name

            // Try instant memory cache first
            val cachedIcon = FaviconLoader.getFromMemorySync(context, pwa.url)
            if (cachedIcon != null) {
                // Instant display from memory cache
                favicon.setImageBitmap(cachedIcon)
                favicon.imageTintList = null
            } else {
                // Show default icon while loading
                favicon.setImageResource(R.drawable.ic_language)
                favicon.imageTintList = null
                
                // Load favicon asynchronously using Google service for speed
                lifecycleOwner.lifecycleScope.launch {
                    val icon = FaviconLoader.loadFaviconForPwaWithRetry(context, pwa.url, size = 64)
                    if (icon != null) {
                        favicon.setImageBitmap(icon)
                        favicon.imageTintList = null
                    }
                }
            }

            // Click listener for installation
            card.setOnClickListener { onInstallClick(pwa) }
            title.setOnClickListener { onInstallClick(pwa) }
        }
    }

    private class PwaSuggestionDiffCallback : DiffUtil.ItemCallback<PwaSuggestionManager.PwaSuggestion>() {
        override fun areItemsTheSame(
            oldItem: PwaSuggestionManager.PwaSuggestion,
            newItem: PwaSuggestionManager.PwaSuggestion
        ): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(
            oldItem: PwaSuggestionManager.PwaSuggestion,
            newItem: PwaSuggestionManager.PwaSuggestion
        ): Boolean {
            return oldItem == newItem
        }
    }
}
