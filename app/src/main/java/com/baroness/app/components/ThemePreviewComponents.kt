package com.baroness.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatBubble(
    text: String,
    alignLeft: Boolean,
    bubbleColor: Color,
    textColor: Color,
    isGlass: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignLeft) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (alignLeft) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (alignLeft) 4.dp else 16.dp,
                        bottomEnd = if (alignLeft) 16.dp else 4.dp
                    )
                )
                .background(bubbleColor)
                .then(
                    if (isGlass) Modifier.border(
                        0.5.dp,
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp
                        )
                    ) else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 13.sp
            )
        }

        if (!alignLeft) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
