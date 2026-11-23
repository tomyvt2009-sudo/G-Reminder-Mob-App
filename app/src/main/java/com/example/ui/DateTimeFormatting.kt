package com.example.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatScheduledTime(epochMillis: Long?): String {
    if (epochMillis == null) return "No alert time"
    val targetCal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val nowCal = Calendar.getInstance()

    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))

    val isSameDay = targetCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
        targetCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) {
        return "Today, $timeStr"
    }

    val tomorrowCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val isTomorrow = targetCal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR) &&
        targetCal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)
    if (isTomorrow) {
        return "Tomorrow, $timeStr"
    }

    val dateStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(epochMillis))
    return "$dateStr • $timeStr"
}

fun formatElapsedSeconds(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
