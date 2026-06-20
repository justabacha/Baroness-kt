package com.baroness.app.components.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.components.Emoji
import com.baroness.app.components.PhestyText
import com.baroness.app.models.Wish
import com.baroness.app.utils.formatDateLabel

@Composable
fun WishItem(
    wish: Wish,
    index: Int,
    currentUserKey: String,
    onDust: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenEmoji: (Long) -> Unit,
    onOpenRating: () -> Unit,
    onUploadPhotos: () -> Unit
) {
    val isDusted = wish.status == "dusted"
    val creatorColor = if (wish.creator == "P") Color(0xFF34C759) else Color(0xFF4285F4)
    val displayDate = formatDateLabel(wish.date)

    val emojisList = listOfNotNull(
        wish.reactions["P"]?.takeIf { it.isNotEmpty() },
        wish.reactions["B"]?.takeIf { it.isNotEmpty() }
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDusted) Color(0xFF1A1A1A) else Color(0xFF2A2A2A)
        ),
        border = if (isDusted) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Star number
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(creatorColor, CircleShape)
                    .shadow(8.dp, CircleShape, ambientColor = creatorColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Meta (date + status)
            Column(
                modifier = Modifier.width(60.dp)
            ) {
                Text(
                    text = displayDate ?: "",
                    color = if (isDusted) Color.Gray else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isDusted) "!! DUSTED" else "PLANNING",
                    color = if (isDusted) Color(0xFFff4d6d) else Color(0xFF4CAF50),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isDusted) {
                var expanded by remember { mutableStateOf(false) }
                Column(modifier = Modifier.weight(1f)) {
                    if (expanded) {
                        PhestyText(
                            text = wish.text,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { expanded = !expanded }
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = wish.text.take(15) + if (wish.text.length > 15) "..." else "",
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable { expanded = !expanded }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onUploadPhotos, modifier = Modifier.size(28.dp)) {
                        DownloadIcon(size = 18.dp)
                    }
                    Box(modifier = Modifier.size(28.dp)) {
                        if (emojisList.isNotEmpty()) {
                            Row {
                                emojisList.forEach { emoji ->
                                    Emoji(emoji = emoji, size = 18.dp)
                                }
                            }
                        } else {
                            IconButton(onClick = { onOpenEmoji(wish.id) }, modifier = Modifier.size(28.dp)) {
                                SmileyIcon(size = 18.dp)
                            }
                        }
                    }
                    IconButton(onClick = onOpenRating, modifier = Modifier.size(28.dp)) {
                        val ratingText = if (wish.ratings.isNotEmpty()) {
                            wish.ratings.entries.joinToString(" ") { "${it.key}: ${it.value}" }
                        } else "Rate"
                        Text(
                            text = ratingText,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                PhestyText(
                    text = wish.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { onDelete(wish.id) }, modifier = Modifier.size(28.dp)) {
                        TrashIcon(size = 18.dp)
                    }
                    IconButton(onClick = { onDust(wish.id) }, modifier = Modifier.size(28.dp)) {
                        CheckIcon(size = 18.dp)
                    }
                }
            }
        }
    }
}