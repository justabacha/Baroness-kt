package com.baroness.app.components.chat

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.Message
import com.baroness.app.ui.theme.ChatTypography
import com.baroness.app.ui.theme.rememberChatTypography

@Composable
fun ChatContextMenu(
    message: Message,
    isOwn: Boolean,
    offset: IntOffset,
    activeThemeId: String,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowEmojiPicker: () -> Unit
) {
    val typography = rememberChatTypography()
    
    // Scale animation for the bubble lift
    val scale by animateFloatAsState(
        targetValue = 1.05f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Blur background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(15.dp)
                    } else Modifier
                )
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
        ) {
            // Context Menu Content at calculated position
            Box(
                modifier = Modifier
                    .offset { offset }
                    .widthIn(max = 280.dp)
                    .scale(scale)
            ) {
                Column(
                    horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
                ) {
                    // 1. Tapback Bar
                    TapbackBar(
                        onReact = onReact,
                        onShowEmojiPicker = onShowEmojiPicker,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 2. Cloned Bubble
                    MessageBubble(
                        message = message,
                        isOwn = isOwn,
                        activeThemeId = activeThemeId,
                        modifier = Modifier.padding(horizontal = 0.dp) // Reset padding for clone
                    )

                    // 3. Action Menu
                    ActionMenu(
                        isOwn = isOwn,
                        onCopy = onCopy,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TapbackBar(
    onReact: (String) -> Unit,
    onShowEmojiPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emojis = listOf("❤️", "👍", "👎", "😂", "‼️", "❓")
    
    Box(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable { onReact(emoji) }
                        .padding(4.dp)
                )
            }
            Text(
                text = "+",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier
                    .clickable { onShowEmojiPicker() }
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun ActionMenu(
    isOwn: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberChatTypography()
    
    Column(
        modifier = modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(20.dp)
                } else Modifier
            )
    ) {
        ActionItem(
            text = "Reply",
            icon = Icons.Default.Reply,
            onClick = {}, // Stub
            typography = typography
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        ActionItem(
            text = "Copy",
            icon = Icons.Default.ContentCopy,
            onClick = onCopy,
            typography = typography
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        if (isOwn) {
            ActionItem(
                text = "Edit",
                icon = Icons.Default.Edit,
                onClick = onEdit,
                typography = typography
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        }
        
        ActionItem(
            text = "Delete",
            icon = Icons.Default.Delete,
            onClick = onDelete,
            typography = typography,
            isDestructive = true
        )
    }
}

@Composable
private fun ActionItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    typography: ChatTypography,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = typography.body,
            color = if (isDestructive) Color(0xFFFF4D4D) else Color.White
        )
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isDestructive) Color(0xFFFF4D4D) else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}
