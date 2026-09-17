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
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.serialization.json.*

@Composable
fun MessageInfoSheet(
    message: Message,
    isOwn: Boolean,
    participant: Participant?,
    activeThemeId: String,
    hazeState: HazeState?,
    settingsViewModel: SettingsViewModel?,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val typography = rememberChatTypography(settingsViewModel)
    val configuration = LocalConfiguration.current
    val sheetHeight = configuration.screenHeightDp.dp * 0.75f 

    val theme = com.baroness.app.models.SettingsOptions.themes.find { it.id == activeThemeId } 
        ?: com.baroness.app.models.SettingsOptions.LavenderTheme

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

        // 1. The Subject (Hero Focus)
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
                activeThemeId = activeThemeId,
                isPreviewMode = true,
                modifier = Modifier.padding(start = 0.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 2. Metadata Section
            Text(
                text = "Details",
                style = typography.title.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = Color.White.copy(alpha = 0.4f)
            )

            InfoRow(label = "Sender", value = if (isOwn) "You" else participant?.displayName ?: "Unknown", typography = typography, accentColor = theme.glowColor)
            InfoRow(label = "Sent", value = formatPrecisionDate(message.timestamp), typography = typography, accentColor = theme.glowColor)
            
            if (isOwn) {
                if (message.deliveredAt != null) {
                    InfoRow(label = "Delivered", value = formatPrecisionDate(message.deliveredAt), typography = typography, accentColor = theme.glowColor)
                } else {
                    InfoRow(label = "Delivered", value = "Pending", typography = typography, accentColor = Color.White.copy(alpha = 0.3f))
                }

                if (message.readAt != null) {
                    InfoRow(label = "Read", value = formatPrecisionDate(message.readAt), typography = typography, accentColor = Color(0xFF80D8FF))
                } else {
                    InfoRow(label = "Read", value = "Not yet", typography = typography, accentColor = Color.White.copy(alpha = 0.3f))
                }
            }

            InfoRow(label = "Status", value = message.status.lowercase().replaceFirstChar { it.uppercase() }, typography = typography, accentColor = theme.glowColor)
            
            if (message.editedAt != null) {
                InfoRow(label = "Edited", value = formatPrecisionDate(message.editedAt), typography = typography, accentColor = theme.glowColor)
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // 3. Reactions Section
            val reactionMap = try {
                Json.parseToJsonElement(message.reactions).jsonObject
            } catch (e: Exception) {
                emptyMap<String, JsonElement>()
            }

            if (reactionMap.isNotEmpty()) {
                Text(
                    text = "Reactions",
                    style = typography.title.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = Color.White.copy(alpha = 0.4f)
                )

                reactionMap.forEach { (emoji, userList) ->
                    val users = userList.jsonArray.size
                    InfoRow(label = emoji, value = "$users ${if (users == 1) "person" else "people"}", typography = typography, accentColor = theme.glowColor)
                }
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }

            // 4. Friday Insights
            if (message.senderId == "friday") {
                Text(
                    text = "Friday Insights",
                    style = typography.title.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = Color.White.copy(alpha = 0.4f)
                )
                InfoRow(label = "Intelligence", value = "Powered by Friday AI", typography = typography, accentColor = Color(0xFF80D8FF))
                InfoRow(label = "Response Type", value = "Synthetic Neural", typography = typography, accentColor = Color(0xFF80D8FF))
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }
        }

        // 5. Actions Footer
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            InfoOptionItem(
                text = "Close",
                color = Color.White,
                onClick = onDismiss,
                typography = typography
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    typography: com.baroness.app.ui.theme.ChatTypography,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.baroness.app.components.PhestyText(
            text = label,
            style = typography.body.copy(fontSize = 16.sp),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp
        )
        Text(
            text = value,
            style = typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = accentColor
        )
    }
}

@Composable
private fun InfoOptionItem(
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

private fun formatPrecisionDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
