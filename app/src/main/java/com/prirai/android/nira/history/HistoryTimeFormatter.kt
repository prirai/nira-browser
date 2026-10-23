package com.prirai.android.nira.history

import android.content.Context
import com.prirai.android.nira.R
import java.util.concurrent.TimeUnit

object HistoryTimeFormatter {

    fun getRelativeTimeString(context: Context, timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            minutes < 1 -> context.getString(R.string.history_time_just_now)
            minutes < 60 -> context.getString(R.string.history_time_minutes_ago, minutes)
            hours < 24 -> context.getString(R.string.history_time_hours_ago, hours)
            days < 7 -> context.getString(R.string.history_time_days_ago, days)
            else -> {
                val weeks = days / 7
                if (weeks < 4) context.getString(R.string.history_time_weeks_ago, weeks)
                else context.getString(R.string.history_time_months_ago, days / 30)
            }
        }
    }
}
