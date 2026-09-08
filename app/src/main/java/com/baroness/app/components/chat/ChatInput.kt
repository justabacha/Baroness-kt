package com.baroness.app.components.chat

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.SettingsOptions
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect

@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    onAttachmentClick: () -> Unit,
    settingsViewModel: SettingsViewModel? = null,
    hazeState: HazeState? = null,
    activeThemeId: String = "lavender",
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val typography = rememberChatTypography(settingsViewModel)
    
    val theme = SettingsOptions.themes.find { it.id == activeThemeId } ?: SettingsOptions.LavenderTheme
    val isSendEnabled = text.isNotBlank()
    
    val accentColor by animateColorAsState(
        targetValue = if (isSendEnabled) theme.glowColor else Color.White.copy(alpha = 0.8f),
        label = "accentColor"
    )

    val glyphShadow = Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Attachment Satellite Island
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(1.5.dp, Color.Black.copy(alpha = 0.6f), CircleShape)
                .padding(0.5.dp)
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .clip(CircleShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurEffect {
                                blurRadius = 15.dp
                                colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.08f)))
                            }
                        }
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.05f))
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAttachmentClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = "Attach",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Input Pill Island (Glassmorphic Lens)
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp, max = 150.dp)
                .border(1.5.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(0.5.dp)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
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
            // TextField
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = typography.body.copy(color = Color.White, shadow = glyphShadow),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Message...",
                                style = typography.body,
                                color = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // Send Satellite Island
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(1.5.dp, Color.Black.copy(alpha = 0.6f), CircleShape)
                .padding(0.5.dp)
                .border(1.dp, accentColor.copy(alpha = 0.9f), CircleShape)
                .clip(CircleShape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurEffect {
                                blurRadius = 15.dp
                                colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.08f)))
                            }
                        }
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.05f))
                    }
                )
                .clickable(
                    enabled = isSendEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        onSendMessage(text)
                        text = ""
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewChatInput() {
    ChatInput(onSendMessage = {}, onAttachmentClick = {}, settingsViewModel = null)
}
