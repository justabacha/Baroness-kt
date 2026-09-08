package com.baroness.app.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.components.PhestyText
import com.baroness.app.models.Message
import com.baroness.app.models.SettingsOptions
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    settingsViewModel: SettingsViewModel? = null,
    activeThemeId: String = SettingsOptions.DEFAULT_THEME_ID,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    onLongPress: ((Message, IntOffset) -> Unit)? = null
) {
    val typography = rememberChatTypography(settingsViewModel)
    val haptic = LocalHapticFeedback.current
    var bubblePosition = IntOffset.Zero

    val theme = SettingsOptions.themes.find { it.id == activeThemeId } ?: SettingsOptions.LavenderTheme
    val isFriday = message.senderId == "friday"

    val bubbleShape = if (isOwn) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = horizontalAlignment
    ) {
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
                .then(
                    // Apply Haze Local Blur for non-user bubbles (Human/Friday)
                    if (!isOwn && hazeState != null) {
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
                    // Crisp white border for Human Other only
                    if (!isOwn && !isFriday) {
                        Modifier.border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = bubbleShape
                        )
                    } else Modifier
                )
                .combinedClickable(
                    onClick = { /* Handle click if needed */ },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress?.invoke(message, bubblePosition)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            PhestyText(
                text = message.content,
                style = typography.body,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(message.timestamp),
                    style = typography.meta,
                    fontSize = 10.sp
                )
                
                if (isOwn) {
                    Spacer(modifier = Modifier.width(4.dp))
                    StatusIndicator(status = message.status)
                }
            }
        }
        
        ReactionRow(reactionsJson = message.reactions)
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
