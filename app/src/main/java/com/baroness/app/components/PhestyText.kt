package com.baroness.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.baroness.app.data.EmojiMap

@Composable
fun PhestyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val density = LocalDensity.current
    val emojiMap = EmojiMap.map

    // Build annotated string with inline content for each emoji
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val chars = text.toCharArray()
        while (currentIndex < chars.size) {
            var matched = false
            // Try matching multi-character emojis first (length 3 down to 1)
            for (len in 3 downTo 1) {
                if (currentIndex + len <= chars.size) {
                    val substring = text.substring(currentIndex, currentIndex + len)
                    if (emojiMap.containsKey(substring)) {
                        appendInlineContent(substring, substring)
                        currentIndex += len
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                append(chars[currentIndex].toString())
                currentIndex++
            }
        }
    }

// Build the inline content map
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    emojiMap.forEach { (emoji, drawableId) ->
        val sizeInDp = with(density) { (fontSize * 1.2f).toDp() }
        inlineContent[emoji] = InlineTextContent(
            placeholder = Placeholder(
                width = fontSize * 1.2f,
                height = fontSize * 1.2f,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            Image(
                painter = painterResource(id = drawableId),
                contentDescription = emoji,
                modifier = Modifier.size(sizeInDp)
            )
        }
    }

    Text(
        text = annotatedString,
        style = style,
        color = color,
        fontSize = fontSize,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent
    )
}