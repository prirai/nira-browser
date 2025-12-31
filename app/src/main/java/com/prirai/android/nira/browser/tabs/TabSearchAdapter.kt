package com.prirai.android.nira.browser.tabs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.prirai.android.nira.R

class TabSearchAdapter(
    private val items: List<SearchResultItem>,
    private val onItemClick: (SearchResultItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SUB_HEADER = 1
        private const val TYPE_TAB = 2
        private const val TYPE_BOOKMARK = 3
        private const val TYPE_HISTORY = 4
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchResultItem.HeaderResult -> TYPE_HEADER
            is SearchResultItem.SubHeaderResult -> TYPE_SUB_HEADER
            is SearchResultItem.TabResult -> TYPE_TAB
            is SearchResultItem.BookmarkResult -> TYPE_BOOKMARK
            is SearchResultItem.HistoryResult -> TYPE_HISTORY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_search_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_SUB_HEADER -> {
                val view = inflater.inflate(R.layout.item_search_subheader, parent, false)
                SubHeaderViewHolder(view)
            }
            TYPE_TAB -> {
                val view = inflater.inflate(R.layout.item_tab_search_result, parent, false)
                TabResultViewHolder(view)
            }
            TYPE_BOOKMARK -> {
                val view = inflater.inflate(R.layout.item_bookmark_search_result, parent, false)
                BookmarkResultViewHolder(view)
            }
            TYPE_HISTORY -> {
                val view = inflater.inflate(R.layout.item_history_search_result, parent, false)
                HistoryResultViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as SearchResultItem.HeaderResult)
            is SubHeaderViewHolder -> holder.bind(item as SearchResultItem.SubHeaderResult)
            is TabResultViewHolder -> {
                val isFirst = isFirstInSubGroup(position)
                val isLast = isLastInSubGroup(position)
                val showDivider = isLast && !isLastInMainGroup(position)
                holder.bind(item as SearchResultItem.TabResult, onItemClick, isFirst, isLast, showDivider)
            }
            is BookmarkResultViewHolder -> {
                val isFirst = isFirstInSubGroup(position)
                val isLast = isLastInSubGroup(position)
                val showDivider = isLast && !isLastInMainGroup(position)
                holder.bind(item as SearchResultItem.BookmarkResult, onItemClick, isFirst, isLast, showDivider)
            }
            is HistoryResultViewHolder -> {
                val isFirst = isFirstInSubGroup(position)
                val isLast = isLastInSubGroup(position)
                val showDivider = isLast && !isLastInMainGroup(position)
                holder.bind(item as SearchResultItem.HistoryResult, onItemClick, isFirst, isLast, showDivider)
            }
        }
    }

    override fun getItemCount(): Int = items.size
    
    private fun isFirstInGroup(position: Int): Boolean {
        if (position == 0) return false
        val prevItem = items.getOrNull(position - 1)
        return prevItem is SearchResultItem.HeaderResult
    }
    
    private fun isLastInGroup(position: Int): Boolean {
        if (position == items.size - 1) return true
        val nextItem = items.getOrNull(position + 1)
        return nextItem is SearchResultItem.HeaderResult || nextItem is SearchResultItem.SubHeaderResult || nextItem == null
    }
    
    private fun isFirstInSubGroup(position: Int): Boolean {
        if (position == 0) return false
        val prevItem = items.getOrNull(position - 1)
        return prevItem is SearchResultItem.SubHeaderResult
    }
    
    private fun isLastInSubGroup(position: Int): Boolean {
        if (position == items.size - 1) return true
        val nextItem = items.getOrNull(position + 1)
        return nextItem is SearchResultItem.SubHeaderResult || nextItem is SearchResultItem.HeaderResult || nextItem == null
    }
    
    private fun isLastInMainGroup(position: Int): Boolean {
        if (position == items.size - 1) return true
        val nextItem = items.getOrNull(position + 1)
        return nextItem is SearchResultItem.HeaderResult || nextItem == null
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerText: TextView = itemView.findViewById(R.id.headerText)
        
        fun bind(item: SearchResultItem.HeaderResult) {
            headerText.text = item.title
        }
    }
    
    class SubHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val subHeaderText: TextView = itemView.findViewById(R.id.subHeaderText)
        private val chipView: Chip = itemView.findViewById(R.id.subHeaderChip)
        
        fun bind(item: SearchResultItem.SubHeaderResult) {
            subHeaderText.visibility = View.GONE
            chipView.text = item.title
        }
    }

    class TabResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.tabTitle)
        private val urlText: TextView = itemView.findViewById(R.id.tabUrl)
        private val chipGroup: ChipGroup = itemView.findViewById(R.id.tabChipGroup)
        private val typeIcon: ImageView = itemView.findViewById(R.id.resultTypeIcon)
        private val divider: View = itemView.findViewById(R.id.resultDivider)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(item: SearchResultItem.TabResult, onClick: (SearchResultItem) -> Unit, isFirst: Boolean, isLast: Boolean, showDivider: Boolean) {
            titleText.text = item.tab.content.title.ifEmpty { item.tab.content.url }
            urlText.text = item.tab.content.url
            
            typeIcon.setImageResource(R.drawable.ic_tab)
            
            chipGroup.removeAllViews()
            chipGroup.visibility = View.GONE
            
            // Only show group chip if it exists (profile is in sub-header now)

            
            // Show/hide divider
            divider.visibility = if (showDivider) View.VISIBLE else View.GONE
            
            // Adjust corner radius based on position - only top or bottom, not full
            val cornerRadius = itemView.context.resources.getDimension(R.dimen.search_result_corner_radius)
            cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(if (isFirst) cornerRadius else 0f)
                .setTopRightCornerSize(if (isFirst) cornerRadius else 0f)
                .setBottomLeftCornerSize(if (isLast) cornerRadius else 0f)
                .setBottomRightCornerSize(if (isLast) cornerRadius else 0f)
                .build()
            
            // Adjust margins
            val params = cardView.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = 0
            params.bottomMargin = 0
            cardView.layoutParams = params
            
            itemView.setOnClickListener { onClick(item) }
        }

        private fun createChip(text: String): Chip {
            return Chip(itemView.context).apply {
                this.text = text
                isClickable = false
                isCheckable = false
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
                chipStrokeWidth = 2f
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    itemView.context.getColor(R.color.chip_stroke_color_themed)
                )
                setTextColor(itemView.context.getColor(R.color.chip_text_color_themed))
                textSize = 11f
            }
        }
    }

    class BookmarkResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.bookmarkTitle)
        private val urlText: TextView = itemView.findViewById(R.id.bookmarkUrl)
        private val typeIcon: ImageView = itemView.findViewById(R.id.resultTypeIcon)
        private val divider: View = itemView.findViewById(R.id.resultDivider)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(item: SearchResultItem.BookmarkResult, onClick: (SearchResultItem) -> Unit, isFirst: Boolean, isLast: Boolean, showDivider: Boolean) {
            titleText.text = item.title
            urlText.text = item.url
            
            typeIcon.setImageResource(R.drawable.ic_baseline_bookmark)
            
            // Show/hide divider
            divider.visibility = if (showDivider) View.VISIBLE else View.GONE
            
            // Adjust corner radius based on position - only top or bottom, not full
            val cornerRadius = itemView.context.resources.getDimension(R.dimen.search_result_corner_radius)
            cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(if (isFirst) cornerRadius else 0f)
                .setTopRightCornerSize(if (isFirst) cornerRadius else 0f)
                .setBottomLeftCornerSize(if (isLast) cornerRadius else 0f)
                .setBottomRightCornerSize(if (isLast) cornerRadius else 0f)
                .build()
            
            // Adjust margins
            val params = cardView.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = 0
            params.bottomMargin = 0
            cardView.layoutParams = params
            
            itemView.setOnClickListener { onClick(item) }
        }
    }

    class HistoryResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.historyTitle)
        private val urlText: TextView = itemView.findViewById(R.id.historyUrl)
        private val typeIcon: ImageView = itemView.findViewById(R.id.resultTypeIcon)
        private val divider: View = itemView.findViewById(R.id.resultDivider)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(item: SearchResultItem.HistoryResult, onClick: (SearchResultItem) -> Unit, isFirst: Boolean, isLast: Boolean, showDivider: Boolean) {
            titleText.text = item.title
            urlText.text = item.url
            
            typeIcon.setImageResource(R.drawable.ic_baseline_history)
            
            // Show/hide divider
            divider.visibility = if (showDivider) View.VISIBLE else View.GONE
            
            // Adjust corner radius based on position - only top or bottom, not full
            val cornerRadius = itemView.context.resources.getDimension(R.dimen.search_result_corner_radius)
            cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(if (isFirst) cornerRadius else 0f)
                .setTopRightCornerSize(if (isFirst) cornerRadius else 0f)
                .setBottomLeftCornerSize(if (isLast) cornerRadius else 0f)
                .setBottomRightCornerSize(if (isLast) cornerRadius else 0f)
                .build()
            
            // Adjust margins
            val params = cardView.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = 0
            params.bottomMargin = 0
            cardView.layoutParams = params
            
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
