package com.baroness.app.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.baroness.app.components.Emoji
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Composable
fun ReactionRow(
    reactionsJson: String,
    modifier: Modifier = Modifier
) {
    if (reactionsJson == "{}" || reactionsJson.isBlank()) return

    val reactions = try {
        val json = Json.parseToJsonElement(reactionsJson).jsonObject
        json.keys.toList()
    } catch (e: Exception) {
        emptyList<String>()
    }

    if (reactions.isEmpty()) return

    val displayEmoji = reactions.last()

    // Fixed height container to ensure next bubble is pushed down responsibly
    Box(
        modifier = modifier
            .height(22.dp) 
    ) {
        Box(
            modifier = Modifier
                .offset(y = (-3).dp) // Hanging on the wall - barely contact (2dp inside)
                .requiredSize(24.dp) // Unbreakable perfect circle
                .background(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = CircleShape
                )
                .border(
                    width = 0.8.dp,
                    color = Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Emoji(
                emoji = displayEmoji,
                modifier = Modifier.padding(1.dp),
                size = 18.dp
            )
        }
    }
}
