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
import com.prirai.android.nira.utils.FaviconLoader
import kotlinx.coroutines.launch

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
            return FaviconLoader.loadFavicon(context, url)
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