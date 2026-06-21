package com.baroness.app.components.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.baroness.app.models.Wish

@Composable
fun RatingModal(
    wish: Wish,
    currentUserKey: String,
    userNames: Map<String, String>,
    onDismiss: () -> Unit,
    onRate: (Int) -> Unit
) {
    var rating by remember(wish.id) { mutableIntStateOf(wish.ratings[currentUserKey] ?: 0) }

    val otherUserKey = if (currentUserKey == "P") "B" else "P"
    val otherRating = wish.ratings[otherUserKey] ?: 0
    val otherName = userNames[otherUserKey] ?: "Other"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .size(28.dp)
                        .background(Color(0x4D464545), RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "×",
                        color = Color.White,
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.offset(y = (-1).dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "RATE THE EXPERIENCE",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFF8F80A), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (otherRating > 0) {
                            "${otherName.uppercase()} RATED: $otherRating ${"★".repeat(otherRating)}"
                        } else {
                            "WAITING FOR ${otherName.uppercase()}..."
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { star ->
                        Text(
                            text = "★",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (star <= rating) {
                                when {
                                    rating <= 2 -> Color(0xFFFF5252)
                                    rating == 3 -> Color(0xFF64F46C)
                                    else -> Color(0xFFFFD700)
                                }
                            } else Color(0xFF2A2A2A),
                            modifier = Modifier
                                .clickable { rating = star }
                                .padding(horizontal = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF7FF871), RoundedCornerShape(12.dp))
                        .clickable(enabled = rating > 0) { if (rating > 0) onRate(rating) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CONFIRM RATING",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}