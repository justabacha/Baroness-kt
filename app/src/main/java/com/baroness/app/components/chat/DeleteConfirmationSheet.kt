package com.baroness.app.components.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

@Composable
fun DeleteConfirmationSheet(
    message: Message,
    isOwn: Boolean,
    participant: Participant?,
    activeThemeId: String,
    hazeState: HazeState?,
    settingsViewModel: SettingsViewModel?,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }

    val typography = rememberChatTypography(settingsViewModel)
    val configuration = LocalConfiguration.current
    val sheetHeight = configuration.screenHeightDp.dp * 0.7f // Increased to 70%

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .border(1.5.dp, Color.Black.copy(alpha = 0.8f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(0.5.dp)
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 30.dp
                            colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.85f)))
                        }
                    }
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.85f))
                }
            )
            .padding(24.dp)
    ) {
        // Handle Bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // 1. The Subject (Inside a thin border rectangle)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            contentAlignment = Alignment.TopStart
        ) {
            MessageBubble(
                message = message,
                isOwn = isOwn,
                participant = participant,
                settingsViewModel = settingsViewModel,
                activeThemeId = activeThemeId, // Correctly pass theme
                isPreviewMode = true,
                modifier = Modifier.padding(start = 0.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. The Prompt (Outside the rectangle, writing from left)
        Text(
            text = "Delete message?",
            style = typography.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.weight(1f))

        // 3. The Actions (Listed from right, alignment from right)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val lightRed = Color(0xFFFF8A80) // Light red

            if (isOwn) {
                DeleteOptionItem(
                    text = "Delete for Everyone",
                    color = lightRed,
                    onClick = onDeleteForEveryone,
                    typography = typography
                )
                HorizontalDivider(modifier = Modifier.width(160.dp), color = Color.White.copy(alpha = 0.1f))
            }

            DeleteOptionItem(
                text = "Delete for Me",
                color = lightRed,
                onClick = onDeleteForMe,
                typography = typography
            )
            
            HorizontalDivider(modifier = Modifier.width(160.dp), color = Color.White.copy(alpha = 0.1f))

            DeleteOptionItem(
                text = "Cancel",
                color = Color.White,
                onClick = onCancel,
                typography = typography
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeleteOptionItem(
    text: String,
    color: Color,
    onClick: () -> Unit,
    typography: com.baroness.app.ui.theme.ChatTypography
) {
    Box(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Text(
            text = text,
            style = typography.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            textAlign = TextAlign.End
        )
    }
}
