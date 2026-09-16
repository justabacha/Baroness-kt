package com.baroness.app.components.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    participant: Participant? = null,
    settingsViewModel: SettingsViewModel? = null,
    activeThemeId: String = SettingsOptions.DEFAULT_THEME_ID,
    hazeState: HazeState? = null,
    isFocusedMode: Boolean = false,
    isPreviewMode: Boolean = false, // New: Forces left alignment for sheets
    modifier: Modifier = Modifier,
    onLongPress: ((Message, IntOffset, IntSize) -> Unit)? = null,
    onReactionClick: ((Message, IntOffset, IntSize) -> Unit)? = null
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
    var bubblePosition by remember { mutableStateOf(IntOffset.Zero) }
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    var isExpanded by remember { mutableStateOf(false) }

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
    val bubbleShape: Shape = remember(isOwn) {
        GenericShape { size, _ ->
            val h = size.height
            val w = size.width
            // Dynamic Radius
            val r = min(with(density) { 20.dp.toPx() }, h / 2f)
            val tw = with(density) { 16.dp.toPx() } 
            val th = with(density) { 14.dp.toPx() } 

            if (isOwn) {
                // Own: Symmetric Adaptive Rounded Rect
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
                // Other: Identity Beak with Adaptive non-tail side
                moveTo(r + tw, 0f)
                lineTo(w - r, 0f)
                arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
                lineTo(w, h - r)
                arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
                lineTo(0f, h) // The Beak Point
                quadraticTo(tw, h, tw, h - th) // Beak curve
                lineTo(tw, r)
                arcTo(Rect(tw, 0f, tw + 2 * r, 2 * r), 180f, 90f, false)
            }
            close()
        }
    }

    val backgroundColor = when {
        isOwn -> theme.glowColor.copy(alpha = 0.9f)
        isFriday -> Color.Black.copy(alpha = 0.3f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    val shadowColor = when {
        isOwn -> theme.glowColor
        isFriday -> Color.Black.copy(alpha = 0.4f)
        else -> Color.Black.copy(alpha = 0.2f)
    }

    val shadowElevation = if (isFriday) 12.dp else 8.dp
    val horizontalAlignment = if (isOwn && !isPreviewMode) Alignment.End else Alignment.Start
    val horizontalArrangement = if (isOwn && !isPreviewMode) Arrangement.End else Arrangement.Start

    // Shared content block to ensure 1:1 twin fidelity between list and focus
    val bubbleContent = @Composable {
        if (message.isDeleted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOwn) "You deleted this message" else "This message was deleted",
                    style = bubbleTextStyle.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                )
            }
        } else {
            ChatTextWithMetaLayout(
            text = {
                Column {
                    PhestyText(
                        text = message.content,
                        style = bubbleTextStyle,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 10
                    )
                    if (message.content.lines().size > 10 || message.content.length > 500) {
                        Text(
                            text = if (isExpanded) "Read less" else "... Read more",
                            style = masterMetaStyle.copy(color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .clickable { isExpanded = !isExpanded }
                                .padding(top = 4.dp)
                        )
                    }
                }
            },
            meta = {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(IntrinsicSize.Min)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (message.editedAt != null) {
                            Text(
                                text = "Edited",
                                style = masterMetaStyle.copy(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(text = formatTime(message.timestamp), style = masterMetaStyle)
                        if (isOwn) {
                            Spacer(modifier = Modifier.width(4.dp))
                            StatusIndicator(status = message.status)
                        }
                    }
                }
            }
        )
        }
    }

    if (isFocusedMode) {
        Box(contentAlignment = Alignment.BottomStart) {
            Column(
                modifier = Modifier
                    .animateContentSize()
                    .widthIn(max = 280.dp)
                    .shadow(elevation = shadowElevation, shape = bubbleShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .graphicsLayer {
                        clip = true
                        shape = bubbleShape
                    }
                    .background(backgroundColor, bubbleShape)
                    .border(width = 1.dp, color = if (isFriday) Color.Transparent else Color.White.copy(alpha = 0.2f), shape = bubbleShape)
                    .padding(start = if (isOwn) 12.dp else 28.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            ) {
                bubbleContent()
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = if (isPreviewMode) 0.dp else 16.dp, vertical = 4.dp),
            horizontalAlignment = if (isOwn && !isPreviewMode) Alignment.End else Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (isOwn && !isPreviewMode) Arrangement.End else Arrangement.Start
            ) {
                if (!isOwn) {
                    Box(contentAlignment = Alignment.BottomStart) {
                        Column(
                            modifier = Modifier
                                .animateContentSize()
                                .padding(start = 20.dp)
                                .widthIn(max = 280.dp)
                                .onGloballyPositioned { coordinates ->
                                    val pos = coordinates.positionInRoot()
                                    bubblePosition = IntOffset(pos.x.toInt(), pos.y.toInt())
                                    bubbleSize = coordinates.size
                                }
                                .shadow(elevation = shadowElevation, shape = bubbleShape, ambientColor = shadowColor, spotColor = shadowColor)
                                .graphicsLayer {
                                    clip = true
                                    shape = bubbleShape
                                }
                                .then(
                                    if (hazeState != null) {
                                        Modifier.hazeEffect(state = hazeState) {
                                            blurEffect {
                                                blurRadius = if (isFriday) 25.dp else 15.dp
                                                colorEffects = listOf(HazeColorEffect.tint(backgroundColor))
                                            }
                                        }
                                    } else Modifier.background(backgroundColor, bubbleShape)
                                )
                                .border(width = 1.dp, color = if (isFriday) Color.Transparent else Color.White.copy(alpha = 0.2f), shape = bubbleShape)
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLongPress?.invoke(message, bubblePosition, bubbleSize)
                                    }
                                )
                                .padding(start = 28.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            bubbleContent()
                        }
                        AvatarIsland(participant = participant, isFriday = isFriday)
                    }
                } else {
                    Column(horizontalAlignment = if (isPreviewMode) Alignment.Start else Alignment.End) {
                        Column(
                            modifier = Modifier
                                .animateContentSize()
                                .widthIn(max = 280.dp)
                                .onGloballyPositioned { coordinates ->
                                    val pos = coordinates.positionInRoot()
                                    bubblePosition = IntOffset(pos.x.toInt(), pos.y.toInt())
                                    bubbleSize = coordinates.size
                                }
                                .shadow(elevation = shadowElevation, shape = bubbleShape, ambientColor = shadowColor, spotColor = shadowColor)
                                .graphicsLayer {
                                    clip = true
                                    shape = bubbleShape
                                }
                                .background(backgroundColor, bubbleShape)
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLongPress?.invoke(message, bubblePosition, bubbleSize)
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            bubbleContent()
                        }
                    }
                }
            }
            if (!message.isDeleted) {
                ReactionRow(
                    reactionsJson = message.reactions, 
                    modifier = Modifier.padding(
                        start = if (isOwn && !isPreviewMode) 0.dp else if (isOwn && isPreviewMode) 10.dp else 32.dp,
                        end = if (isOwn && !isPreviewMode) 8.dp else 0.dp
                    ),
                    onClick = { onReactionClick?.invoke(message, bubblePosition, bubbleSize) }
                )
            }
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

        val spacing = with(density) { 32.dp.roundToPx() }
        val fitsOnSameLine = (textWidth + metaWidth + spacing) <= constraints.maxWidth

        val totalWidth: Int
        val totalHeight: Int

        if (fitsOnSameLine) {
            totalWidth = max(textWidth + spacing + metaWidth, constraints.minWidth)
            totalHeight = textHeight + with(density) { 6.dp.roundToPx() }
        } else {
            totalWidth = max(textWidth, metaWidth)
            totalHeight = textHeight + metaHeight - with(density) { 2.dp.roundToPx() }
        }

        layout(totalWidth, totalHeight) {
            textPlaceable.placeRelative(0, 0)
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
            .size(36.dp)
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
        } else {
            val fallbackUrl = if (isFriday) "https://img.icons8.com/fluency/48/artificial-intelligence.png" else "https://img.icons8.com/fluency/48/user-male-circle.png"
            AsyncImage(
                model = fallbackUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun StatusIndicator(status: String) {
    val iconSize = 12.dp
    when (status) {
        "PENDING" -> Icon(imageVector = Icons.Default.Done, contentDescription = "Pending", modifier = Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.4f))
        "SENT" -> Icon(imageVector = Icons.Default.Done, contentDescription = "Sent", modifier = Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.7f))
        "DELIVERED" -> Icon(imageVector = Icons.Default.DoneAll, contentDescription = "Delivered", modifier = Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.7f))
        "READ" -> Icon(imageVector = Icons.Default.DoneAll, contentDescription = "Read", modifier = Modifier.size(iconSize), tint = Color(0xFF80D8FF))
        "FAILED" -> Text(text = "!", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
