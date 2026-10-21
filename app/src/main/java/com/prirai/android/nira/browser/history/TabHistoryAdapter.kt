package com.prirai.android.nira.browser.history

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.prirai.android.nira.R
import com.prirai.android.nira.utils.FaviconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

/**
 * One row of the tab-history popup: an [index] into the session's
 * [mozilla.components.browser.state.state.content.HistoryState] plus its
 * display data.
 *
 * The row for the current page has [isCurrent] set; it renders with a
 * filled dot and does not respond to clicks (Chrome/Fenix behaviour).
 */
data class TabHistoryRow(
    val index: Int,
    val title: String,
    val uri: String,
    val isCurrent: Boolean,
)

/**
 * Row adapter for the long-press back/forward tab-history popup.
 *
 * Mirrors the grouped history-list card style: rows stack edge-to-edge,
 * only the first/last rows keep the corner radius, and a divider is drawn
 * between rows. The current page row is shown but disabled.
 */
class TabHistoryAdapter(
    private val onItemClicked: (TabHistoryRow) -> Unit
) : RecyclerView.Adapter<TabHistoryAdapter.ViewHolder>() {

    private var rows: List<TabHistoryRow> = emptyList()

    fun submitRows(newRows: List<TabHistoryRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_tab_history, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rows[position], position, rows.size, onItemClicked)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.faviconJob?.cancel()
        holder.faviconView.setImageResource(R.drawable.ic_language)
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = rows.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView: MaterialCardView = view as MaterialCardView
        private val titleView: TextView = view.findViewById(R.id.tabHistoryTitle)
        val faviconView: ImageView = view.findViewById(R.id.tabHistoryFavicon)
        private val dotView: View = view.findViewById(R.id.tabHistoryDot)
        private val divider: View = view.findViewById(R.id.tabHistoryDivider)
        var faviconJob: Job? = null

        fun bind(
            row: TabHistoryRow,
            position: Int,
            count: Int,
            onItemClicked: (TabHistoryRow) -> Unit
        ) {
            titleView.text = row.title.ifBlank { row.uri.tryGetHostFromUrl() }

            applyCardShape(cardView, isFirst = position == 0, isLast = position == count - 1)
            divider.visibility = if (position == count - 1) View.GONE else View.VISIBLE

            if (row.isCurrent) {
                // Current page: filled dot + grayed-out title; tapping no-ops.
                dotView.visibility = View.VISIBLE
                dotView.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        MaterialColors.getColor(
                            dotView,
                            android.R.attr.colorPrimary
                        )
                    )
                }
                titleView.setTextColor(
                    MaterialColors.getColor(
                        titleView,
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
                cardView.isEnabled = false
                cardView.setOnClickListener(null)
            } else {
                dotView.visibility = View.GONE
                titleView.setTextColor(
                    MaterialColors.getColor(
                        titleView,
                        com.google.android.material.R.attr.colorOnSurface
                    )
                )
                cardView.isEnabled = true
                cardView.setOnClickListener { onItemClicked(row) }
            }

            bindFavicon(row.uri)
        }

        /**
         * Flatten the grouped-card look: only the first and last rows keep
         * the rounded corners, matching the grouped history list style.
         */
        private fun applyCardShape(cardView: MaterialCardView, isFirst: Boolean, isLast: Boolean) {
            val radius = cardView.context.resources.getDimension(R.dimen.search_result_corner_radius)
            cardView.shapeAppearanceModel = cardView.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(if (isFirst) radius else 0f)
                .setTopRightCornerSize(if (isFirst) radius else 0f)
                .setBottomLeftCornerSize(if (isLast) radius else 0f)
                .setBottomRightCornerSize(if (isLast) radius else 0f)
                .build()
        }

        /**
         * Memory-cached favicon for instant first paint; falls back to an
         * async load (disk cache -> BrowserIcons) when not cached yet.
         */
        private fun bindFavicon(url: String) {
            faviconJob?.cancel()
            val context = itemView.context
            val cached = FaviconLoader.getFromMemorySync(context, url)
            if (cached != null) {
                faviconView.setImageBitmap(cached)
                faviconView.clearColorFilter()
            } else {
                faviconView.setImageResource(R.drawable.ic_language)
                faviconJob = CoroutineScope(Dispatchers.Main).launch {
                    val icon = FaviconLoader.loadFavicon(context, url)
                    if (icon != null) {
                        faviconView.setImageBitmap(icon)
                        faviconView.clearColorFilter()
                    }
                }
            }
        }
    }
}
