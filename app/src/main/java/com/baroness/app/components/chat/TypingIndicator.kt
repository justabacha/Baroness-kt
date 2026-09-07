package com.baroness.app.components.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.ui.theme.rememberChatTypography

@Composable
fun TypingIndicator(
    displayName: String,
    modifier: Modifier = Modifier
) {
    val typography = rememberChatTypography()
    
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        JumpingDots()
        
        Text(
            text = "$displayName is typing...",
            style = typography.meta,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun JumpingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    
    val dot1Offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0)
        ),
        label = "dot1"
    )
    
    val dot2Offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(150)
        ),
        label = "dot2"
    )
    
    val dot3Offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(300)
        ),
        label = "dot3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(offset = dot1Offset)
        Dot(offset = dot2Offset)
        Dot(offset = dot3Offset)
    }
}

@Composable
private fun Dot(offset: Float) {
    Box(
        modifier = Modifier
            .size(4.dp)
            .offset(y = offset.dp)
            .background(Color.White.copy(alpha = 0.6f), CircleShape)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewTypingIndicator() {
    TypingIndicator(displayName = "Friday")
}
