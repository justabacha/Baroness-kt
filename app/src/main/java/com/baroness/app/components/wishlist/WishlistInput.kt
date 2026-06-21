package com.baroness.app.components.wishlist

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    onCalendarPositioned: (Offset) -> Unit,
    onCast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateLabel = formatDateLabel(selectedDate)

    // The blurred background layer
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 120.dp)
    ) {
        // Background with blur effect - this is the frosted glass layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(radius = 20.dp)
                    } else Modifier
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(40.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(40.dp)
                )
        )

        // Content sits ON TOP of the blur - not blurred itself
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Calendar button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCalendarClick
                    )
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInRoot()
                        val size = coordinates.size
                        onCalendarPositioned(
                            Offset(position.x, position.y + size.height)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (dateLabel != null) {
                    Text(
                        text = dateLabel,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                } else {
                    AsyncImage(
                        model = "https://img.icons8.com/fluency/48/calendar.png",
                        contentDescription = "Pick date",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Text input - NOT blurred, sits on top
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCast() }),
                maxLines = 4,
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = "Cast a new wish, Blud.....",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Cast button - NOT blurred, sits on top
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCast
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = "https://img.icons8.com/fluency/48/star.png",
                    contentDescription = "Cast wish",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}