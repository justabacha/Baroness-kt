package com.baroness.app.components

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopWarningBanner(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val baseIconSize = 24.dp
    val baseSpacing = 12.dp
    val baseHorizPadding = 16.dp
    val baseFontSize = 14.sp

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val containerWidthPx = screenWidthPx * 0.9f

    val basePaddingPx = with(density) { baseHorizPadding.toPx() }
    val baseIconPx = with(density) { baseIconSize.toPx() }
    val baseSpacingPx = with(density) { baseSpacing.toPx() }
    val baseFontPx = with(density) { baseFontSize.toPx() }

    val textMeasurer = rememberTextMeasurer()
    val textLayoutResult = remember(message) {
        textMeasurer.measure(
            text = message,
            style = TextStyle(
                fontSize = baseFontSize,
                fontWeight = FontWeight.Medium
            )
        )
    }
    val textWidthPx = textLayoutResult.size.width.toFloat()

    // Available width minus 2% safety margin
    val availableWidthPx = (containerWidthPx - (basePaddingPx * 2)) * 0.98f

    val requiredWidthPx = baseIconPx + baseSpacingPx + textWidthPx

    val scaleFactor = if (requiredWidthPx > availableWidthPx) {
        (availableWidthPx / requiredWidthPx).coerceIn(0.65f, 1f)
    } else {
        1f
    }

    val iconSize = baseIconSize * scaleFactor
    val spacing = baseSpacing * scaleFactor
    val horizPadding = baseHorizPadding * scaleFactor
    val fontSize = baseFontSize * scaleFactor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Color(0xFF00f8db)), RoundedCornerShape(16.dp))
            ) {
                // Blurred Background
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(20.dp)
                            } else Modifier
                        )
                        .background(Color.Black.copy(alpha = 0.7f))
                )

                // Content (Crisp, uniformly scaled)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizPadding, vertical = 14.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(iconSize)
                    )
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}