package com.baroness.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
fun AudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    barWidth: Dp = 2.5.dp,
    barGap: Dp = 2.dp,
    activeColor: Color = Color(0xFF64B5F6)
) {
    val context = LocalContext.current
    val playerManager = BaronessPlayerManager.getInstance(context)
    val amplitudes by playerManager.audioAmplitudes.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val widthPx = barWidth.toPx()
        val gapPx = barGap.toPx()
        val maxHeightPx = height.toPx()
        val minHeightPx = 3.dp.toPx()

        val barCount = (size.width / (widthPx + gapPx)).toInt().coerceAtLeast(1)

        for (i in 0 until barCount) {
            val ampIndex = i % amplitudes.size
            val realAmp = amplitudes.getOrNull(ampIndex) ?: 0.15f
            val barPhase = phase + (i * 0.35f)

            val factor = if (isPlaying) {
                if (realAmp > 0.18f) {
                    realAmp
                } else {
                    ((sin(barPhase.toDouble()) + 1) / 2f).toFloat().coerceIn(0.15f, 0.85f)
                }
            } else {
                0.12f
            }

            val currentBarHeight = minHeightPx + (maxHeightPx - minHeightPx) * factor
            val x = i * (widthPx + gapPx)
            val y = (maxHeightPx - currentBarHeight) / 2f

            drawRoundRect(
                color = if (isPlaying) activeColor else activeColor.copy(alpha = 0.3f),
                topLeft = Offset(x, y),
                size = Size(widthPx, currentBarHeight),
                cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f)
            )
        }
    }
}
