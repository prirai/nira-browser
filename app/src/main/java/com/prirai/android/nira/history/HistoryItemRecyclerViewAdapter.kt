package com.prirai.android.nira.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.VisitInfo
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl
import java.text.DateFormat.getDateTimeInstance
import java.util.ArrayList
import java.util.Date
import java.util.Locale

open class HistoryItemRecyclerViewAdapter(
    private var values: List<VisitInfo>
)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private var historyItems: List<HistoryItem> = HistorySectionHelper.groupHistoryByDate(values)
    lateinit var filtered: MutableList<VisitInfo>
    lateinit var oldList: MutableList<VisitInfo>

    open fun getFilter(): Filter? {
        return object : Filter() {
            override fun performFiltering(charSequence: CharSequence): FilterResults {
                val charString = charSequence.toString()

                filtered = if (charString.isEmpty()) {
                    oldList
                } else {
                    val filteredList: MutableList<VisitInfo> = ArrayList<VisitInfo>()
                    for (row in oldList) {
                        if (row.url.lowercase(Locale.getDefault()).contains(charString.lowercase(Locale.getDefault())) || row.title?.lowercase(
                                Locale.getDefault()
                            )
                                ?.contains(
                                charString.lowercase(Locale.getDefault())
                            ) == true) {
                            filteredList.add(row)
                        }
                    }
                    filteredList
                }
                val filterResults = FilterResults()
                filterResults.values = filtered
                return filterResults
            }

            override fun publishResults(charSequence: CharSequence, filterResults: FilterResults) {
                values = filterResults.values as MutableList<VisitInfo>
                historyItems = HistorySectionHelper.groupHistoryByDate(values)
                notifyDataSetChanged()
            }
        }
    }

    fun getItem(position: Int): VisitInfo {
        return when (val item = historyItems[position]) {
            is HistoryItem.Visit -> item.visitInfo
            is HistoryItem.Header -> values[0]
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        oldList = values as MutableList<VisitInfo>
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.history_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.history_list_item, parent, false)
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(200).setStartDelay((Math.min(5, viewType) * 50).toLong()).start()
                ViewHolder(view)
            }
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
                val title = visitInfo.title ?.takeIf(String::isNotEmpty) ?: visitInfo.url.tryGetHostFromUrl()
                val relativeTime = HistoryTimeFormatter.getRelativeTimeString(holder.itemView.context, visitInfo.visitTime)

                holder.titleView.text = title
                holder.urlView.text = visitInfo.url
                holder.timeView.text = relativeTime
                
                // Load favicon from cache
                CoroutineScope(Dispatchers.Main).launch {
                    val context = holder.itemView.context
                    val faviconCache = context.components.faviconCache
                    val cachedIcon = faviconCache.loadFavicon(visitInfo.url)
                    if (cachedIcon != null) {
                        holder.faviconView.setImageBitmap(cachedIcon)
                    } else {
                        holder.faviconView.setImageDrawable(
                            ContextCompat.getDrawable(context, R.drawable.ic_baseline_history)
                        )
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = historyItems.size
    
    override fun getItemViewType(position: Int): Int {
        return when (historyItems[position]) {
            is HistoryItem.Header -> TYPE_HEADER
            is HistoryItem.Visit -> TYPE_ITEM
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.sectionTitle)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.historyTitle)
        val urlView: TextView = view.findViewById(R.id.historyUrl)
        val timeView: TextView = view.findViewById(R.id.historyTime)
        val faviconView: ImageView = view.findViewById(R.id.historyFavicon)
    }
}