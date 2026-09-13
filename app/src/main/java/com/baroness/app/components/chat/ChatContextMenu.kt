package com.baroness.app.components.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.models.SettingsOptions
import com.baroness.app.ui.theme.ChatTypography
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect

@Composable
fun ChatContextMenu(
    message: Message,
    isOwn: Boolean,
    offset: IntOffset,
    bubbleSize: IntSize,
    activeThemeId: String,
    participant: Participant? = null,
    settingsViewModel: SettingsViewModel? = null,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowEmojiPicker: () -> Unit
) {
    var isLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isLaunched = true }

    val scale by animateFloatAsState(
        targetValue = if (isLaunched) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "scale"
    )

    val density = LocalDensity.current
    var tapbackHeight by remember { mutableIntStateOf(0) }
    var columnWidth by remember { mutableIntStateOf(0) }
    var isMeasured by remember { mutableStateOf(false) }
    
    val gapPx = with(density) { 8.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // 1. Background Blur Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurEffect {
                                blurRadius = 15.dp 
                                colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.45f))) 
                            }
                        }
                    } else {
                        Modifier.background(Color.Black.copy(alpha = 0.4f))
                    }
                )
        )

        // 2. Focused Content Column
        Column(
            modifier = Modifier
                .onGloballyPositioned { 
                    columnWidth = it.size.width
                    isMeasured = true 
                }
                .graphicsLayer {
                    // Alpha shield to prevent measurement jump
                    alpha = if (isMeasured) 1f else 0f
                    scaleX = scale
                    scaleY = scale
                    
                    // X POSITIONING: 
                    // If Own: Align right edge of column with right edge of bubble
                    // If Other: Align left edge of column with left edge of bubble
                    translationX = if (isOwn) {
                        (offset.x + bubbleSize.width - columnWidth).toFloat()
                    } else {
                        offset.x.toFloat()
                    }
                    
                    // Y POSITIONING:
                    // Place the bubble part of the column exactly at 'offset.y'
                    translationY = (offset.y - tapbackHeight - gapPx).toFloat()
                },
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // A. Tapback Bar
            TapbackBar(
                onReact = onReact,
                onShowEmojiPicker = onShowEmojiPicker,
                hazeState = hazeState,
                modifier = Modifier.onGloballyPositioned { tapbackHeight = it.size.height }
            )

            // B. Focused Bubble (Sharp twin, no avatar)
            MessageBubble(
                message = message,
                isOwn = isOwn,
                participant = null,
                settingsViewModel = settingsViewModel,
                activeThemeId = activeThemeId,
                hazeState = null, // Sharp
                isFocusedMode = true,
                modifier = Modifier.padding(horizontal = 0.dp)
            )

            // C. Action Menu
            ActionMenu(
                isOwn = isOwn,
                settingsViewModel = settingsViewModel,
                hazeState = hazeState,
                onCopy = onCopy,
                onEdit = onEdit,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun TapbackBar(
    onReact: (String) -> Unit,
    onShowEmojiPicker: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val emojis = listOf("❤️", "👍", "👎", "😂", "‼️", "❓")
    
    Box(
        modifier = modifier
            .border(1.5.dp, Color.Black.copy(alpha = 0.8f), RoundedCornerShape(28.dp))
            .padding(0.5.dp)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 15.dp
                            colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.3f)))
                        }
                    }
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.4f))
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .clickable { onReact(emoji) }
                        .padding(2.dp)
                )
            }
            Text(
                text = "+",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier
                    .clickable { onShowEmojiPicker() }
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ActionMenu(
    isOwn: Boolean,
    settingsViewModel: SettingsViewModel? = null,
    hazeState: HazeState? = null,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typography = rememberChatTypography(settingsViewModel)
    
    Column(
        modifier = modifier
            .width(200.dp)
            .border(1.5.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(0.5.dp)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 15.dp
                            colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.08f)))
                        }
                    }
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.1f))
                }
            )
    ) {
        ActionItem(text = "Reply", icon = Icons.AutoMirrored.Filled.Reply, onClick = {}, typography = typography)
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        ActionItem(text = "Copy", icon = Icons.Default.ContentCopy, onClick = onCopy, typography = typography)
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        if (isOwn) {
            ActionItem(text = "Edit", icon = Icons.Default.Edit, onClick = onEdit, typography = typography)
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        }
        
        ActionItem(
            text = "Delete",
            icon = Icons.Default.Delete,
            onClick = onDelete,
            typography = typography,
            isDestructive = true
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        ActionItem(text = "More...", icon = Icons.Default.MoreVert, onClick = onMore, typography = typography)
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
    val glyphShadow = Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = typography.body.copy(shadow = glyphShadow),
            color = if (isDestructive) Color(0xFFFF4D4D) else Color.White
        )
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isDestructive) Color(0xFFFF4D4D) else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
