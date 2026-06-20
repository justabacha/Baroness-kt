package com.baroness.app.components.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var rating by remember { mutableIntStateOf(wish.ratings[currentUserKey] ?: 0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1A1A),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "RATE THE EXPERIENCE",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                val otherKey = if (currentUserKey == "P") "B" else "P"
                val otherRating = wish.ratings[otherKey] ?: 0
                Text(
                    text = if (otherRating > 0) {
                        "${userNames[otherKey]?.uppercase() ?: "OTHER"} RATED: $otherRating ★".repeat(otherRating)
                    } else {
                        "WAITING FOR ${userNames[otherKey]?.uppercase() ?: "OTHER"}..."
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = { rating = star },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text(
                                text = "★",
                                fontSize = 32.sp,
                                color = if (star <= rating) {
                                    when {
                                        rating <= 2 -> Color(0xFFFF5252)
                                        rating == 3 -> Color(0xFF64F46C)
                                        else -> Color(0xFFFFD700)
                                    }
                                } else Color(0xFF2A2A2A)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { if (rating > 0) onRate(rating) },
                        enabled = rating > 0,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7FF871))
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}