package com.baroness.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.data.EmojiMap

@Composable
fun Emoji(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color? = null
) {
    val drawableId = EmojiMap.map[emoji]
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (drawableId != null) {
            Image(
                painter = painterResource(id = drawableId),
                contentDescription = emoji,
                modifier = Modifier.fillMaxSize(),
                colorFilter = tint?.let { ColorFilter.tint(it) }
            )
        } else {
            Text(
                text = emoji,
                fontSize = (size.value * 0.9f).sp, // Calibrated 0.9x ratio
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = tint ?: Color.Unspecified
            )
        }
    }
}