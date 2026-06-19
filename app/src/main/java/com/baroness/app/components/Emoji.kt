package com.baroness.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.data.EmojiMap

@Composable
fun Emoji(
    emoji: String,
    size: Dp = 18.dp,
    tint: Color? = null,
    modifier: Modifier = Modifier
) {
    val drawableId = EmojiMap.map[emoji]
    if (drawableId != null) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = emoji,
            modifier = modifier.size(size),
            colorFilter = tint?.let { ColorFilter.tint(it) }
        )
    } else {
        Text(
            text = emoji,
            fontSize = (size.value * 1.5f).sp,
            color = tint ?: Color.Unspecified,
            modifier = modifier
        )
    }
}