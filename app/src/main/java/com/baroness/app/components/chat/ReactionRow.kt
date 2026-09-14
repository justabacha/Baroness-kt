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
import kotlinx.serialization.json.jsonArray

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionRow(
    reactionsJson: String,
    modifier: Modifier = Modifier
) {
    if (reactionsJson == "{}" || reactionsJson.isBlank()) return

    // Parse the JSON reactions string: emoji -> List of UserIds
    val reactions = try {
        val json = Json.parseToJsonElement(reactionsJson).jsonObject
        json.keys.toList() // We only care about the emojis themselves
    } catch (e: Exception) {
        emptyList<String>()
    }

    if (reactions.isEmpty()) return

    // Requirement: Only one circle reaction for a bubble.
    // We take the last one (most recent in the map/list logic).
    val displayEmoji = reactions.last()

    Box(
        modifier = modifier
            .padding(top = 2.dp)
            .offset(y = (-11).dp), // Hanging on the wall - barely contact
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp) // Perfect Circle footprint
                .background(
                    color = Color.Black.copy(alpha = 0.8f), // Deep dark tint
                    shape = CircleShape
                )
                .border(
                    width = 0.8.dp, // Thin layer connection look
                    color = Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Emoji(
                emoji = displayEmoji,
                size = 18.dp, // Pumped size inside the 24dp circle
                modifier = Modifier.padding(1.dp)
            )
        }
    }
}
