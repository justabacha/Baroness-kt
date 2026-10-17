package com.baroness.app.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.baroness.app.data.EmojiSegment
import com.baroness.app.data.EmojiSegments
import com.baroness.app.data.QuoteMarkup
import com.baroness.app.ui.theme.Colors
import com.baroness.app.ui.theme.LocalBaronessTypography
import java.io.File
import kotlin.math.abs
import kotlin.math.min

@Composable
fun QuoteCard(
    date: String,
    text: String?,
    author: String,
    imageUrl: String?,
    cardWidth: Dp,
    cardHeight: Dp,
    tiltDeg: Float = 0f,
    modifier: Modifier = Modifier,
    bitmap: Bitmap? = null
) {
    val context = LocalContext.current

    val cardWidthPx = cardWidth.value
    val dateFont: TextUnit = min(12f, cardWidthPx * 0.06f).sp
    val quoteFont: TextUnit = min(18f, cardWidthPx * 0.08f).sp
    val authorFont: TextUnit = min(14f, cardWidthPx * 0.06f).sp

    val pad: Dp = (abs(tiltDeg) * 3.5f + 10f).dp

    LaunchedEffect(imageUrl) {
        println("QuoteCard imageUrl: $imageUrl")
        println("Is file: ${imageUrl?.startsWith("/")}")
        println("File exists: ${imageUrl?.let { File(it).exists() }}")
    }

    Box(
        modifier = modifier
            .width(cardWidth + pad * 2)
            .height(cardHeight + pad * 2),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .rotate(tiltDeg)
                .shadow(
                    elevation = 35.dp,
                    shape = RoundedCornerShape(15.dp),
                    ambientColor = Color.Black.copy(alpha = 0.7f),
                    spotColor = Color.Black.copy(alpha = 0.7f)
                )
                .clip(RoundedCornerShape(15.dp))
                .border(1.dp, Color.White.copy(alpha = 0.95f), RoundedCornerShape(15.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (!imageUrl.isNullOrBlank()) {
                val imageSource = if (imageUrl.startsWith("/") || imageUrl.contains("/data/")) {
                    File(imageUrl)
                } else {
                    imageUrl // Web URL fallback
                }

                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(imageSource)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder when no image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )

            Text(
                text = date.uppercase(),
                color = Colors.textDim,
                fontSize = dateFont,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 20.dp)
            )

            val baseStyle = LocalTextStyle.current
            val activeFontFamily = LocalBaronessTypography.current.title.fontFamily
            val baseFontSize = quoteFont
            val density = LocalDensity.current

            val paragraphs = remember(text) {
                QuoteMarkup.parse(
                    source = text ?: "",
                    defaultFontFamily = null
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                paragraphs.forEach { paragraph ->
                    if (paragraph.isLine) {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth(0.2f)
                                .padding(vertical = 2.dp),
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    } else {
                        val paragraphStyle = paragraph.indentDp?.let {
                            TextStyle(textIndent = TextIndent(firstLine = it.sp))
                        } ?: TextStyle.Default

                        val inlineContentMap = remember(paragraph.spans, baseFontSize) {
                            mutableMapOf<String, InlineTextContent>()
                        }

                        val annotated = remember(paragraph.spans, baseFontSize) {
                            buildAnnotatedString {
                                paragraph.spans.forEach { span ->
                                    if (span.text.isEmpty()) return@forEach
                                    val segments = EmojiSegments.split(span.text)
                                    segments.forEach { segment ->
                                        when (segment) {
                                            is EmojiSegment.Plain -> {
                                                if (span.fontFamily == null && span.sizeScale == 1.0f) {
                                                    append(segment.text)
                                                } else {
                                                    withStyle(
                                                        SpanStyle(
                                                            fontFamily = span.fontFamily ?: activeFontFamily,
                                                            fontSize = baseFontSize * span.sizeScale
                                                        )
                                                    ) {
                                                        append(segment.text)
                                                    }
                                                }
                                            }
                                            is EmojiSegment.Emoji -> {
                                                val emojiSizeSp = baseFontSize.value * span.sizeScale * 1.1f
                                                val key = "emoji_${segment.resId}_${emojiSizeSp.toInt()}"
                                                appendInlineContent(id = key, alternateText = segment.rawChars)

                                                if (!inlineContentMap.containsKey(key)) {
                                                    val sizeDp = with(density) { emojiSizeSp.sp.toDp() }
                                                    inlineContentMap[key] = InlineTextContent(
                                                        placeholder = Placeholder(
                                                            width = emojiSizeSp.sp,
                                                            height = emojiSizeSp.sp,
                                                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                                                        )
                                                    ) {
                                                        Image(
                                                            painter = painterResource(id = segment.resId),
                                                            contentDescription = segment.rawChars,
                                                            modifier = Modifier.size(sizeDp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = annotated,
                            color = Color.White,
                            fontSize = baseFontSize,
                            lineHeight = baseFontSize * 1.35f,
                            fontWeight = FontWeight.Medium,
                            fontStyle = FontStyle.Italic,
                            textAlign = paragraph.textAlign ?: TextAlign.Center,
                            style = baseStyle.merge(paragraphStyle).copy(
                                fontFamily = activeFontFamily
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            inlineContent = inlineContentMap
                        )
                    }
                }
            }

            Text(
                text = author,
                color = Colors.textDim,
                fontSize = authorFont,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 25.dp)
            )
        }
    }
}
