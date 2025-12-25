package com.prirai.android.nira.history

import android.content.Context
import java.util.concurrent.TimeUnit

object HistoryTimeFormatter {
    
    fun getRelativeTimeString(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> {
                val weeks = days / 7
                if (weeks < 4) "${weeks}w ago"
                else {
                    val months = days / 30
                    "${months}mo ago"
                }
            }
        }
    }
}
