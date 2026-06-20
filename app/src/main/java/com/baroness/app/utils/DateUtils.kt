package com.baroness.app.utils

import java.util.Locale

fun formatDateLabel(dateStr: String?): String? {
    if (dateStr == null) return null
    return try {
        val parts = dateStr.split("-")
        val year = parts[0].takeLast(2)
        val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[parts[1].toInt() - 1]
        val day = parts[2].padStart(2, '0')
        "$day $month '$year"
    } catch (_: Exception) {
        dateStr
    }
}