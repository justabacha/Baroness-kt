package com.baroness.app.components.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.zIndex
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
    val isPlanning = wish.status == "planning"
    val creatorColor = if (wish.creator == "P") Color(0xFF34C759) else Color(0xFF4285F4)
    val displayDate = formatDateLabel(wish.date)
    var expanded by remember(wish.id) { mutableStateOf(false) }

    val shortText = if (wish.text.length > 15) wish.text.take(15) + "..." else wish.text

    val reactions = wish.reactions
    val pEmoji = reactions["P"] ?: ""
    val bEmoji = reactions["B"] ?: ""
    val emojisList = listOfNotNull(
        pEmoji.takeIf { it.isNotEmpty() },
        bEmoji.takeIf { it.isNotEmpty() }
    )

    // Bar card container - BOTH planning and dusted have rounded corner cards
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPlanning) {
                    // Planning: semi-transparent white bar card
                    Modifier
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                } else {
                    // Dusted: dark bar card
                    Modifier
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                }
            )
            .padding(horizontal = 12.dp, vertical = if (!isPlanning && expanded) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Colored star number with glow - color changes based on creator
        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = creatorColor.copy(alpha = 0.5f),
                    spotColor = creatorColor.copy(alpha = 0.5f)
                )
                .background(creatorColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Meta section (date + status)
        Column(
            modifier = Modifier.widthIn(min = 58.dp, max = 64.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = displayDate ?: "",
                color = if (!isPlanning) Color.Gray else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp,
                maxLines = 1
            )
            Text(
                text = if (!isPlanning) "!! DUSTED" else "PLANNING",
                color = if (!isPlanning) Color(0xFFff4d6d) else Color(0xFF4CAF50),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
        }

        if (isPlanning) {
            PhestyText(
                text = wish.text,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            )

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trash icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onDelete(wish.id) },
                    contentAlignment = Alignment.Center
                ) {
                    TrashIcon(size = 20.dp, color = Color.White.copy(alpha = 0.7f))
                }

                // Check button (green circle with checkmark)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                        .clickable { onDust(wish.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓✓",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            if (expanded) {
                PhestyText(
                    text = wish.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = false }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    text = shortText,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = true }
                        .padding(vertical = 2.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo upload
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onUploadPhotos),
                        contentAlignment = Alignment.Center
                    ) {
                        DownloadIcon(size = 20.dp, color = Color.White.copy(alpha = 0.7f))
                    }

                    // Emoji reaction
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onOpenEmoji(wish.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (emojisList.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy((-6).dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                emojisList.forEachIndexed { idx, emoji ->
                                    Emoji(
                                        emoji = emoji,
                                        size = 18.dp,
                                        modifier = Modifier
                                            .offset(x = if (idx > 0) (-6).dp else 0.dp)
                                            .zIndex(idx.toFloat())
                                    )
                                }
                            }
                        } else {
                            SmileyIcon(size = 20.dp, color = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    // Rating box
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2A2A2A), RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenRating)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val ratingText = if (wish.ratings.isEmpty()) {
                            "Rate"
                        } else {
                            wish.ratings.entries.joinToString(" ") { "${it.key}: ${it.value}" }
                        }
                        Text(
                            text = ratingText,
                            color = if (wish.ratings.isEmpty())
                                Color.White.copy(alpha = 0.5f)
                            else Color(0xFFFFD700),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}