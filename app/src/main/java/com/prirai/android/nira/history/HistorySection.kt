package com.prirai.android.nira.history

import androidx.annotation.StringRes
import com.prirai.android.nira.R
import mozilla.components.concept.storage.VisitInfo

/** The date bucket a visit falls into, rendered as the section header above its rows. */
enum class HistorySection(@StringRes val titleRes: Int) {
    TODAY(R.string.history_section_today),
    YESTERDAY(R.string.history_section_yesterday),
    THIS_WEEK(R.string.history_section_this_week),
    THIS_MONTH(R.string.history_section_this_month),
    OLDER(R.string.history_section_older),
}

sealed class HistoryItem {
    data class Header(val section: HistorySection) : HistoryItem()
    data class Visit(val visitInfo: VisitInfo) : HistoryItem()
}

object HistorySectionHelper {
    fun groupHistoryByDate(visits: List<VisitInfo>): List<HistoryItem> {
        val result = mutableListOf<HistoryItem>()
        var lastSection: HistorySection? = null

        for (visit in visits) {
            val section = sectionFor(visit.visitTime)
            if (section != lastSection) {
                result.add(HistoryItem.Header(section))
                lastSection = section
            }
            result.add(HistoryItem.Visit(visit))
        }

        return result
    }

    private fun sectionFor(timestamp: Long): HistorySection {
        val diff = System.currentTimeMillis() - timestamp
        val days = diff / (1000L * 60 * 60 * 24)

        return when {
            days == 0L -> HistorySection.TODAY
            days == 1L -> HistorySection.YESTERDAY
            days < 7 -> HistorySection.THIS_WEEK
            days < 30 -> HistorySection.THIS_MONTH
            else -> HistorySection.OLDER
        }
    }
}
