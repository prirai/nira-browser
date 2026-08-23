package com.prirai.android.nira.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.prirai.android.nira.R
import com.prirai.android.nira.utils.FaviconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.VisitInfo
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

open class HistoryItemRecyclerViewAdapter(
    private var values: List<VisitInfo>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private var historyItems: List<HistoryItem> = HistorySectionHelper.groupHistoryByDate(values)
    private var oldList: List<VisitInfo> = values

    open fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSequence: CharSequence): FilterResults {
                val query = charSequence.toString()
                val filtered = if (query.isEmpty()) {
                    oldList
                } else {
                    oldList.filter { row ->
                        row.url.contains(query, ignoreCase = true) ||
                            row.title?.contains(query, ignoreCase = true) == true
                    }
                }
                return FilterResults().apply { values = filtered }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(charSequence: CharSequence, filterResults: FilterResults) {
                values = filterResults.values as List<VisitInfo>
                historyItems = HistorySectionHelper.groupHistoryByDate(values)
                notifyDataSetChanged()
            }
        }
    }

    fun getVisitAt(position: Int): VisitInfo? {
        return (historyItems.getOrNull(position) as? HistoryItem.Visit)?.visitInfo
    }

    fun getItem(position: Int): VisitInfo {
        return getVisitAt(position) ?: values.first()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.history_section_header, parent, false)
            )
            else -> ViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.history_list_item, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = historyItems[position]) {
            is HistoryItem.Header -> {
                (holder as HeaderViewHolder).titleView.text = item.title
            }
            is HistoryItem.Visit -> {
                holder as ViewHolder
                val visitInfo = item.visitInfo
                holder.titleView.text = visitInfo.title?.takeIf(String::isNotEmpty)
                    ?: visitInfo.url.tryGetHostFromUrl()
                holder.timeView.text = HistoryTimeFormatter.getRelativeTimeString(
                    holder.itemView.context,
                    visitInfo.visitTime
                )
                applyCardShape(holder.cardView, position)
                holder.divider.visibility = if (isLastInGroup(position)) View.GONE else View.VISIBLE
                holder.bindFavicon(visitInfo.url)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ViewHolder) {
            holder.faviconJob?.cancel()
            holder.faviconView.setImageResource(R.drawable.ic_baseline_history)
        }
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = historyItems.size

    override fun getItemViewType(position: Int): Int {
        return when (historyItems[position]) {
            is HistoryItem.Header -> TYPE_HEADER
            is HistoryItem.Visit -> TYPE_ITEM
        }
    }

    private fun isFirstInGroup(position: Int): Boolean {
        return historyItems.getOrNull(position - 1) is HistoryItem.Header
    }

    private fun isLastInGroup(position: Int): Boolean {
        val next = historyItems.getOrNull(position + 1)
        return next == null || next is HistoryItem.Header
    }

    private fun applyCardShape(cardView: MaterialCardView, position: Int) {
        val radius = cardView.context.resources.getDimension(R.dimen.search_result_corner_radius)
        val first = isFirstInGroup(position)
        val last = isLastInGroup(position)
        cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(if (first) radius else 0f)
            .setTopRightCornerSize(if (first) radius else 0f)
            .setBottomLeftCornerSize(if (last) radius else 0f)
            .setBottomRightCornerSize(if (last) radius else 0f)
            .build()
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.sectionTitle)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view as MaterialCardView
        val titleView: TextView = view.findViewById(R.id.historyTitle)
        val timeView: TextView = view.findViewById(R.id.historyTime)
        val faviconView: ImageView = view.findViewById(R.id.historyFavicon)
        val divider: View = view.findViewById(R.id.resultDivider)
        var faviconJob: Job? = null

        fun bindFavicon(url: String) {
            faviconJob?.cancel()
            faviconView.setImageResource(R.drawable.ic_baseline_history)
            faviconJob = CoroutineScope(Dispatchers.Main).launch {
                val icon = FaviconLoader.loadFavicon(itemView.context, url)
                if (icon != null) {
                    faviconView.setImageBitmap(icon)
                    faviconView.clearColorFilter()
                }
            }
        }
    }
}
