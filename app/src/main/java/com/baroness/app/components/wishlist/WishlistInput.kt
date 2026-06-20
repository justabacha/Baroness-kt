package com.baroness.app.components.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.baroness.app.utils.formatDateLabel

@Composable
fun WishlistInput(
    text: String,
    onTextChange: (String) -> Unit,
    selectedDate: String?,
    onCalendarClick: () -> Unit,
    onCast: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(40.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(40.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCalendarClick,
            modifier = Modifier.size(36.dp)
        ) {
            if (selectedDate != null) {
                Text(
                    text = formatDateLabel(selectedDate) ?: "",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                AsyncImage(
                    model = "https://img.icons8.com/fluency/48/calendar.png",
                    contentDescription = "Calendar",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = "Cust a new wish, Blud.....",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                }
                innerTextField()
            }
        )

        IconButton(
            onClick = onCast,
            modifier = Modifier.size(36.dp)
        ) {
            AsyncImage(
                model = "https://img.icons8.com/fluency/48/star.png",
                contentDescription = "Cast",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}