package com.baroness.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.baroness.app.ui.theme.Colors
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val cardWidthPx = cardWidth.value
    val dateFont: TextUnit = min(12f, cardWidthPx * 0.06f).sp
    val quoteFont: TextUnit = min(18f, cardWidthPx * 0.08f).sp
    val authorFont: TextUnit = min(14f, cardWidthPx * 0.06f).sp

    val pad: Dp = (abs(tiltDeg) * 3.5f + 10f).dp

    // Debug: log what we're receiving
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
                .background(Color.Black)
        ) {
            // Handle null/empty imageUrl
            if (!imageUrl.isNullOrBlank()) {
                val imageSource = if (imageUrl.startsWith("/") || imageUrl.contains("/data/user/")) {
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
                        .background(Color.DarkGray)
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

            Text(
                text = text ?: "",
                color = Color.White,
                fontSize = quoteFont,
                lineHeight = quoteFont * 1.4,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
            )

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