package com.baroness.app.utils

import java.text.SimpleDateFormat
import java.util.*

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun parseIsoToLong(isoString: String?): Long {
    if (isoString.isNullOrEmpty()) return System.currentTimeMillis()
    return try {
        isoFormat.parse(isoString)?.time ?: System.currentTimeMillis()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

fun formatLongToIso(timestamp: Long): String {
    return isoFormat.format(Date(timestamp))
}

fun formatDateLabel(dateStr: String?): String? {
    if (dateStr.isNullOrEmpty()) return null
    return try {
        val parts = dateStr.split("-")
        if (parts.size != 3) return null

        val year = parts[0].toInt()
        val month = parts[1].toInt() - 1
        val day = parts[2].toInt()

        val cal = Calendar.getInstance().apply { set(year, month, day) }

        val dayStr = String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val monthStr = months[cal.get(Calendar.MONTH)]
        val yearStr = cal.get(Calendar.YEAR).toString().takeLast(2)

        dayStr + " " + monthStr + " '" + yearStr
    } catch (_: Exception) {
        null
    }
}