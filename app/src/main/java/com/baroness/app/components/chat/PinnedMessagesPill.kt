package com.baroness.app.components.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.Message
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

@Composable
fun PinnedMessagesPill(
    pinnedMessages: List<Message>,
    activeThemeId: String,
    settingsViewModel: SettingsViewModel?,
    hazeState: HazeState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = com.baroness.app.models.SettingsOptions.themes.find { it.id == activeThemeId } 
        ?: com.baroness.app.models.SettingsOptions.LavenderTheme
    
    val typography = rememberChatTypography(settingsViewModel)

    if (pinnedMessages.isEmpty()) return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 10.dp
                            colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.2f)))
                        }
                    }
                } else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                tint = theme.glowColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${pinnedMessages.size} pinned messages",
                style = typography.body.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            )
        }
    }
}

@Composable
fun PinnedMessagesLedger(
    pinnedMessages: List<Message>,
    activeThemeId: String,
    settingsViewModel: SettingsViewModel?,
    hazeState: HazeState?,
    onMessageClick: (Message) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = com.baroness.app.models.SettingsOptions.themes.find { it.id == activeThemeId } 
        ?: com.baroness.app.models.SettingsOptions.LavenderTheme
    
    val typography = rememberChatTypography(settingsViewModel)

    Column(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .padding(top = 8.dp)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 20.dp
                            colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.4f)))
                        }
                    }
                } else Modifier
            )
            .padding(vertical = 8.dp)
    ) {
        pinnedMessages.forEachIndexed { index, message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        onMessageClick(message)
                        onDismiss()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = theme.glowColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                com.baroness.app.components.PhestyText(
                    text = message.content,
                    style = typography.body.copy(fontSize = 14.sp),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (index < pinnedMessages.size - 1) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}
