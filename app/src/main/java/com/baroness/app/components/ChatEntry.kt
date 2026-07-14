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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.baroness.app.models.Conversation
import java.text.SimpleDateFormat
import java.util.*

object ChatTypography {
    val Inter = FontFamily.SansSerif // Fallback to SansSerif as .ttf cannot be added via agent

    val nameStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    )
    val messageStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = Color(0xFF8E8E93)
    )
    val timeStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = Color(0xFF8E8E93)
    )
}

@Composable
fun ChatEntry(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = conversation.avatarUrl,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.displayName,
                    color = Color.White,
                    style = ChatTypography.nameStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                conversation.lastMessageTimestamp?.let { timestamp ->
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = sdf.format(Date(timestamp)),
                        style = ChatTypography.timeStyle
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = conversation.lastMessage ?: "",
                style = ChatTypography.messageStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
