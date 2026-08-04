package com.baroness.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.baroness.app.models.Conversation
import com.baroness.app.ui.theme.ChatTypography
import com.baroness.app.ui.theme.Colors
import com.baroness.app.ui.theme.rememberChatTypography

@Composable
fun ChatEntry(
    conversation: Conversation,
    onClick: () -> Unit,
    chatTypography: ChatTypography = rememberChatTypography()
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with accent border and fallback
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(1.5.dp, Colors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!conversation.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = conversation.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initial = conversation.displayName.take(1).uppercase()
                    PhestyText(
                        text = initial,
                        style = chatTypography.title,
                        color = Color.White,
                        fontFamily = chatTypography.title.fontFamily,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhestyText(
                        text = conversation.displayName,
                        style = chatTypography.title,
                        color = Color.White,
                        fontFamily = chatTypography.title.fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = conversation.timestamp,
                        style = chatTypography.meta
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                PhestyText(
                    text = conversation.lastMessage,
                    style = chatTypography.subtitle,
                    fontFamily = chatTypography.subtitle.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
