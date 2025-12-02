package com.prirai.android.nira.history

import mozilla.components.concept.storage.VisitInfo

sealed class HistoryItem {
    data class Header(val title: String) : HistoryItem()
    data class Visit(val visitInfo: VisitInfo) : HistoryItem()
}

object HistorySectionHelper {
    fun groupHistoryByDate(visits: List<VisitInfo>): List<HistoryItem> {
        val result = mutableListOf<HistoryItem>()
        var lastSection: String? = null
        
        for (visit in visits) {
            val section = getSectionTitle(visit.visitTime)
            if (section != lastSection) {
                result.add(HistoryItem.Header(section))
                lastSection = section
            }
            result.add(HistoryItem.Visit(visit))
        }
        
        return result
    }
    
    private fun getSectionTitle(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val days = diff / (1000 * 60 * 60 * 24)
        
        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> "This Week"
            days < 30 -> "This Month"
            days < 365 -> "Older"
            else -> "Long Time Ago"
        }
    }
}
