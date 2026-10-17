package com.baroness.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.baroness.app.data.EmojiSegment
import com.baroness.app.data.EmojiSegments

@Composable
fun PhestyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 16.sp,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val density = LocalDensity.current
    val segments = EmojiSegments.split(text)
    val inlineContent = mutableMapOf<String, InlineTextContent>()

    val annotatedString = buildAnnotatedString {
        segments.forEach { segment ->
            when (segment) {
                is EmojiSegment.Plain -> append(segment.text)
                is EmojiSegment.Emoji -> {
                    val key = "emoji_${segment.resId}_${segment.rawChars.hashCode()}"
                    appendInlineContent(id = key, alternateText = segment.rawChars)

                    if (!inlineContent.containsKey(key)) {
                        val sizeInDp = with(density) { (fontSize * 1.2f).toDp() }
                        inlineContent[key] = InlineTextContent(
                            placeholder = Placeholder(
                                width = fontSize * 1.1f,
                                height = fontSize * 1.1f,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        ) {
                            Image(
                                painter = painterResource(id = segment.resId),
                                contentDescription = segment.rawChars,
                                modifier = Modifier.size(sizeInDp)
                            )
                        }
                    }
                }
            }
        }
    }

    Text(
        text = annotatedString,
        style = style,
        color = color,
        fontSize = fontSize,
        fontFamily = fontFamily ?: style.fontFamily,
        textAlign = textAlign ?: style.textAlign,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent
    )
}
