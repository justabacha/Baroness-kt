package com.baroness.app.components.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CustomCalendar(
    selectedDate: String?,
    onSelectDate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentDate = remember { mutableStateOf(Calendar.getInstance()) }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = dateFormat.format(Date())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .width(280.dp)
                .shadow(16.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xCC000000)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentDate.value.add(Calendar.MONTH, -1) }) {
                        Text("‹", fontSize = 20.sp, color = Color.White)
                    }
                    Text(
                        text = "${currentDate.value.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US)} ${currentDate.value.get(Calendar.YEAR)}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { currentDate.value.add(Calendar.MONTH, 1) }) {
                        Text("›", fontSize = 20.sp, color = Color.White)
                    }
                }

                // Weekday headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                        Text(
                            text = day,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                // Days
                val cal = currentDate.value.clone() as Calendar
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val firstDay = cal.get(Calendar.DAY_OF_WEEK) - 1
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                for (week in 0 until 6) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (weekday in 0 until 7) {
                            val dayNumber = if (week == 0 && weekday < firstDay) null else {
                                val d = week * 7 + weekday - firstDay + 1
                                if (d in 1..daysInMonth) d else null
                            }
                            val dateStr = if (dayNumber != null) {
                                String.format(
                                    Locale.US,
                                    "%04d-%02d-%02d",
                                    currentDate.value.get(Calendar.YEAR),
                                    currentDate.value.get(Calendar.MONTH) + 1,
                                    dayNumber
                                )
                            } else null

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .then(
                                        if (dateStr == today) Modifier.border(1.dp, Color(0xFFEAFF00), CircleShape)
                                        else Modifier
                                    )
                                    .then(
                                        if (dateStr == selectedDate) Modifier.background(Color(0xFF00FF51), CircleShape)
                                        else Modifier
                                    )
                                    .clickable(enabled = dateStr != null) {
                                        if (dateStr != null) {
                                            onSelectDate(dateStr)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (dayNumber != null) {
                                    Text(
                                        text = dayNumber.toString(),
                                        color = if (dateStr == selectedDate) Color.Black else Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}