package com.baroness.app.components.wishlist

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import java.util.*

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")
private const val DAY_SIZE = 32

@Composable
fun CustomCalendar(
    visible: Boolean,
    anchorPosition: Offset?,
    selectedDate: String?,
    onSelectDate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentDate by remember { mutableStateOf(Calendar.getInstance()) }
    val scale = remember { Animatable(0.8f) }
    val translateX = remember { Animatable(-10f) }
    val opacity = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            opacity.snapTo(0f)
            scale.snapTo(0.8f)
            translateX.snapTo(-10f)
            coroutineScope.launch {
                scale.animateTo(targetValue = 1f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 40f))
            }
            coroutineScope.launch {
                translateX.animateTo(targetValue = 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 40f))
            }
            coroutineScope.launch {
                opacity.animateTo(targetValue = 1f, animationSpec = tween(200))
            }
        } else {
            coroutineScope.launch {
                opacity.animateTo(0f, tween(140))
                scale.animateTo(0.85f, tween(140))
            }
        }
    }

    if (!visible && opacity.value <= 0.01f) return

    val year = currentDate.get(Calendar.YEAR)
    val month = currentDate.get(Calendar.MONTH)
    val firstDayOfMonth = Calendar.getInstance().apply { set(year, month, 1) }.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = currentDate.getActualMaximum(Calendar.DAY_OF_MONTH)
    val todayStr = String.format(
        Locale.US, "%04d-%02d-%02d",
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH) + 1,
        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    )

    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss)
                .zIndex(998f)
        )
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(
            x = (anchorPosition?.x?.toInt() ?: 0) - 20,
            y = (anchorPosition?.y?.toInt() ?: 0) + 8
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = EnterTransition.None,
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f, animationSpec = tween(140), transformOrigin = TransformOrigin(0.5f, 0f))
        ) {
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .graphicsLayer {
                        this.scaleX = scale.value
                        this.scaleY = scale.value
                        this.translationX = translateX.value
                        this.alpha = opacity.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .background(color = Color(0x94141420), shape = RoundedCornerShape(24.dp))
                    .border(width = 1.dp, color = Color(0x2E959595), shape = RoundedCornerShape(24.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(45.dp, 30.dp)
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                                .clickable {
                                    currentDate = Calendar.getInstance().apply { set(year, month - 1, 1) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("‹", color = Color.White, fontSize = 20.sp, lineHeight = 22.sp)
                        }

                        Text(
                            text = "${MONTH_NAMES[month]} $year",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Box(
                            modifier = Modifier
                                .size(45.dp, 30.dp)
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                                .clickable {
                                    currentDate = Calendar.getInstance().apply { set(year, month + 1, 1) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("›", color = Color.White, fontSize = 20.sp, lineHeight = 22.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(15.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WEEKDAYS.forEachIndexed { idx, label ->
                            val isWeekend = idx == 0 || idx == 6
                            Box(
                                modifier = Modifier
                                    .size(DAY_SIZE.dp)
                                    .background(
                                        if (isWeekend) Color.White.copy(alpha = 0.82f) else Color(0x8EBF5AF2),
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        if (isWeekend) Color.Black.copy(alpha = 0.10f) else Color(0x4DBF5AF2),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isWeekend) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        var dayCounter = 1
                        for (week in 0 until 6) {
                            if (dayCounter > daysInMonth && week > 0) break
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (weekday in 0 until 7) {
                                    val cellDay = when {
                                        week == 0 && weekday < firstDayOfMonth -> null
                                        dayCounter > daysInMonth -> null
                                        else -> dayCounter++
                                    }
                                    val dateStr = cellDay?.let {
                                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, it)
                                    }
                                    val isToday = dateStr == todayStr
                                    val isSelected = dateStr == selectedDate

                                    Box(
                                        modifier = Modifier
                                            .size(DAY_SIZE.dp)
                                            .then(
                                                if (cellDay != null) {
                                                    Modifier
                                                        .background(
                                                            if (isSelected) Color(0xFF00FF51) else Color.Transparent,
                                                            CircleShape
                                                        )
                                                        .border(
                                                            width = if (isToday && !isSelected) 1.dp else if (isSelected) 0.dp else 1.dp,
                                                            color = when {
                                                                isSelected -> Color(0xFF00FF51)
                                                                isToday -> Color(0xFFEAFF00)
                                                                else -> Color(0x88DEDADA)
                                                            },
                                                            CircleShape
                                                        )
                                                        .clickable { if (dateStr != null) onSelectDate(dateStr) }
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (cellDay != null) {
                                            Text(
                                                text = cellDay.toString(),
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
    }
}