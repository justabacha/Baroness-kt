package com.baroness.app.utils

import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.*
import java.util.logging.Logger

private val timestampLogger = Logger.getLogger("DateUtils")
private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
private val timestampPattern = Regex(
    "^(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}(?::?\\d{2})?)$"
)

fun parseIsoToLong(isoString: String?): Long {
    if (isoString.isNullOrBlank()) return timestampFallback(isoString)

    val match = timestampPattern.matchEntire(isoString) ?: return timestampFallback(isoString)
    val date = match.groupValues[1]
    val time = match.groupValues[2]
    val fraction = match.groupValues[3]
    val offset = match.groupValues[4]
    val millis = fraction.padEnd(3, '0').take(3)
    val normalizedOffset = when {
        offset == "Z" -> "+0000"
        offset.length == 3 -> "${offset}00"
        else -> offset.replace(":", "")
    }
    val normalized = "$date" + "T" + "$time" + "." + "$millis" + normalizedOffset
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    val parsed = formatter.parse(normalized, position)
    return if (parsed != null && position.index == normalized.length) {
        parsed.time
    } else {
        timestampFallback(isoString)
    }
}

private fun timestampFallback(isoString: String?): Long {
    timestampLogger.warning("Unable to parse timestamp: $isoString")
    return System.currentTimeMillis()
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

fun formatChatTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

    return if (now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)
    ) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    } else if (now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - msgTime.get(Calendar.DAY_OF_YEAR) == 1
    ) {
        "Yesterday"
    } else {
        SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}
