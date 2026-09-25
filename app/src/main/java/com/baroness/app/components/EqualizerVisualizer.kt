package com.baroness.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baroness.app.media.BaronessPlayerManager
import kotlin.math.sin

@Composable
fun EqualizerVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 6,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    maxBarHeight: Dp = 20.dp,
    activeColor: Color = Color(0xFFFFD700)
) {
    val context = LocalContext.current
    val playerManager = BaronessPlayerManager.getInstance(context)
    val amplitudes by playerManager.audioAmplitudes.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val totalWidth = (barWidth + barGap) * barCount

    Canvas(
        modifier = modifier
            .width(totalWidth)
            .height(maxBarHeight)
    ) {
        val widthPx = barWidth.toPx()
        val gapPx = barGap.toPx()
        val maxHeightPx = maxBarHeight.toPx()
        val minHeightPx = 3.dp.toPx()

        for (i in 0 until barCount) {
            val barPhase = phase + (i * 0.7f)
            val realAmp = amplitudes.getOrNull(i) ?: 0.15f

            val factor = if (isPlaying) {
                if (realAmp > 0.15f) {
                    realAmp
                } else {
                    ((sin(barPhase.toDouble()) + 1) / 2f).toFloat().coerceIn(0.15f, 1f)
                }
            } else {
                0.15f
            }

            val currentBarHeight = minHeightPx + (maxHeightPx - minHeightPx) * factor
            val x = i * (widthPx + gapPx)
            val y = maxHeightPx - currentBarHeight

            drawRoundRect(
                color = if (isPlaying) activeColor else activeColor.copy(alpha = 0.4f),
                topLeft = Offset(x, y),
                size = Size(widthPx, currentBarHeight),
                cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f)
            )
        }
    }
}
