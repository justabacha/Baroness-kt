package com.baroness.app.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.baroness.app.components.PhestyText
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.models.SettingsOptions
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import androidx.compose.ui.layout.ContentScale
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    participant: Participant? = null,
    settingsViewModel: SettingsViewModel? = null,
    activeThemeId: String = SettingsOptions.DEFAULT_THEME_ID,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    onLongPress: ((Message, IntOffset) -> Unit)? = null
) {
    val typography = rememberChatTypography(settingsViewModel)
    val bubbleTextStyle = typography.body.copy(
        fontSize = 15.sp,
        lineHeight = 18.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )
    val haptic = LocalHapticFeedback.current
    var bubblePosition = IntOffset.Zero

    // Master Style for Metadata (Independent System font for functional clarity)
    val masterMetaStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 10.sp,
        color = Color.White.copy(alpha = 0.7f),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    val theme = SettingsOptions.themes.find { it.id == activeThemeId } ?: SettingsOptions.LavenderTheme
    val isFriday = message.senderId == "friday"

    val density = LocalDensity.current
    val bubbleShape: Shape = remember(isOwn, density) {
        val r = with(density) { 20.dp.toPx() }
        val tw = with(density) { 16.dp.toPx() } // Longer Tail Extension
        val th = with(density) { 14.dp.toPx() } // Proportional Bend Height

        GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            if (isOwn) {
                // Own: Symmetric Rounded Rect (20dp all round)
                moveTo(r, 0f)
                lineTo(w - r, 0f)
                arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
                lineTo(w, h - r)
                arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
                lineTo(r, h)
                arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
                lineTo(0f, r)
                arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            } else {
                // Other: Beak at bottom-left pointing into avatar
                moveTo(r + tw, 0f)
                lineTo(w - r, 0f)
                arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
                lineTo(w, h - r)
                arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
                lineTo(0f, h) // The Beak Point (Extended bottom line)
                quadraticTo(tw, h, tw, h - th) // The Bend meeting the vertical wall
                lineTo(tw, r)
                arcTo(Rect(tw, 0f, tw + 2 * r, 2 * r), 180f, 90f, false)
            }
            close()
        }
    }

    // Styles for "Standout" on busy HD wallpaper
    val backgroundColor = when {
        isOwn -> theme.glowColor.copy(alpha = 0.9f)
        isFriday -> Color.Black.copy(alpha = 0.3f) // Obsidian Smoke
        else -> Color.White.copy(alpha = 0.12f) // Crystal Glass
    }

    val shadowColor = when {
        isOwn -> theme.glowColor // Color Glow
        isFriday -> Color.Black.copy(alpha = 0.4f) // Deep shadow for AI weight
        else -> Color.Black.copy(alpha = 0.2f) // Fine dark drop shadow for Human
    }

    val shadowElevation = if (isFriday) 12.dp else 8.dp

    val horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start

    val horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = horizontalAlignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = horizontalArrangement
        ) {
            if (!isOwn) {
                Box(contentAlignment = Alignment.BottomStart) {
                    // 1. The Bubble Column (Drawn first)
                    Column(
                        modifier = Modifier
                            .padding(start = 20.dp) // Offset to align wall (at x=16dp) with avatar edge (at x=36dp)
                            .widthIn(max = 280.dp)
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                bubblePosition = IntOffset(pos.x.toInt(), pos.y.toInt())
                            }
                            .shadow(
                                elevation = shadowElevation,
                                shape = bubbleShape,
                                ambientColor = shadowColor,
                                spotColor = shadowColor
                            )
                            .clip(bubbleShape)
                            .then(
                                if (hazeState != null) {
                                    Modifier.hazeEffect(state = hazeState) {
                                        blurEffect {
                                            blurRadius = if (isFriday) 25.dp else 15.dp
                                            colorEffects = listOf(HazeColorEffect.tint(backgroundColor))
                                        }
                                    }
                                } else {
                                    Modifier.background(backgroundColor, bubbleShape)
                                }
                            )
                            .then(
                                if (!isFriday) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = bubbleShape
                                    )
                                } else Modifier
                            )
                            .combinedClickable(
                                onClick = { /* Handle click */ },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPress?.invoke(message, bubblePosition)
                                }
                            )
                            .padding(start = 28.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        ChatTextWithMetaLayout(
                            text = {
                                PhestyText(
                                    text = message.content,
                                    style = bubbleTextStyle,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            },
                            meta = {
                                // Shrunken Independent Column
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.width(IntrinsicSize.Min)
                                ) {
                                    Text(
                                        text = formatTime(message.timestamp),
                                        style = masterMetaStyle
                                    )
                                }
                            }
                        )
                    }

                    // 2. The Avatar (Drawn second to sit on top of the tail)
                    AvatarIsland(
                        participant = participant,
                        isFriday = isFriday
                    )
                }
            } else {
                // Own Message Layout (Symmetric)
                Column(horizontalAlignment = Alignment.End) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                bubblePosition = IntOffset(pos.x.toInt(), pos.y.toInt())
                            }
                            .shadow(
                                elevation = shadowElevation,
                                shape = bubbleShape,
                                ambientColor = shadowColor,
                                spotColor = shadowColor
                            )
                            .clip(bubbleShape)
                            .background(backgroundColor, bubbleShape)
                            .combinedClickable(
                                onClick = { /* Handle click */ },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPress?.invoke(message, bubblePosition)
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        ChatTextWithMetaLayout(
                            text = {
                                PhestyText(
                                    text = message.content,
                                    style = bubbleTextStyle,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            },
                            meta = {
                                // Shrunken Independent Column (Master of its own style)
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.width(IntrinsicSize.Min)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatTime(message.timestamp),
                                            style = masterMetaStyle
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        StatusIndicator(status = message.status)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Reactions outside the avatar-bubble box for proper alignment
        Column(
            modifier = Modifier.padding(start = if (isOwn) 0.dp else 28.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            ReactionRow(reactionsJson = message.reactions)
        }
    }
}

@Composable
fun ChatTextWithMetaLayout(
    text: @Composable () -> Unit,
    meta: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Layout(
        content = {
            text()
            meta()
        },
        modifier = modifier
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(constraints)
        val metaPlaceable = measurables[1].measure(constraints)

        val textWidth = textPlaceable.width
        val textHeight = textPlaceable.height
        val metaWidth = metaPlaceable.width
        val metaHeight = metaPlaceable.height

        val spacing = with(density) { 32.dp.roundToPx() } // The "Naturally Long" gap (Reduced from 64)
        val fitsOnSameLine = (textWidth + metaWidth + spacing) <= constraints.maxWidth

        val totalWidth: Int
        val totalHeight: Int

        if (fitsOnSameLine) {
            totalWidth = max(textWidth + spacing + metaWidth, constraints.minWidth)
            totalHeight = textHeight + with(density) { 6.dp.roundToPx() } // Drop Room
        } else {
            totalWidth = max(textWidth, metaWidth)
            totalHeight = textHeight + metaHeight - with(density) { 2.dp.roundToPx() } // Tucked Below
        }

        layout(totalWidth, totalHeight) {
            textPlaceable.placeRelative(0, 0)

            // The "Pendant Drop": Top of meta sits level with bottom of text
            val x = totalWidth - metaWidth
            val y = textHeight - with(density) { 2.dp.roundToPx() }
            metaPlaceable.placeRelative(x, y)
        }
    }
}

@Composable
private fun AvatarIsland(
    participant: Participant?,
    isFriday: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isFriday) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
    val borderWidth = if (isFriday) 1.5.dp else 1.dp

    Box(
        modifier = modifier
            .size(36.dp) // Increased size to match standard header dimensions (36dp)
            .border(borderWidth, borderColor, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
    ) {
        if (participant?.avatarUrl != null) {
            AsyncImage(
                model = participant.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isFriday) {
            AsyncImage(
                model = "https://img.icons8.com/fluency/48/artificial-intelligence.png",
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun StatusIndicator(status: String) {
    when (status) {
        "PENDING" -> {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Pending",
                modifier = Modifier.size(12.dp),
                tint = Color.White.copy(alpha = 0.4f)
            )
        }
        "SENT" -> {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Sent",
                modifier = Modifier.size(12.dp),
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
        "DELIVERED" -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                modifier = Modifier.size(12.dp),
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
        "READ" -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                modifier = Modifier.size(12.dp),
                tint = Color(0xFF80D8FF) // Light blue for read
            )
        }
        "FAILED" -> {
            Text(text = "!", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewMessageBubbleOwn() {
    MessageBubble(
        message = Message(
            id = "1",
            conversationId = "baroness",
            senderId = "phesty_official",
            content = "Hey Baroness, did you see the new update? 🚀",
            timestamp = System.currentTimeMillis(),
            status = "READ"
        ),
        isOwn = true,
        settingsViewModel = null
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewMessageBubbleOther() {
    MessageBubble(
        message = Message(
            id = "2",
            conversationId = "baroness",
            senderId = "baroness_official",
            content = "Yes! It looks amazing. The glassmorphism is spot on. ✨",
            timestamp = System.currentTimeMillis(),
            status = "SENT"
        ),
        isOwn = false,
        settingsViewModel = null
    )
}
