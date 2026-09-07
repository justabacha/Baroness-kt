package com.baroness.app.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        json.mapValues { (_, value) -> 
            value.jsonArray.size 
        }
    } catch (e: Exception) {
        emptyMap<String, Int>()
    }

    if (reactions.isEmpty()) return

    FlowRow(
        modifier = modifier
            .padding(top = 2.dp)
            .offset(y = (-4).dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { (emoji, count) ->
            Box(
                modifier = Modifier
                    .background(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(text = emoji, fontSize = 12.sp)
                    if (count > 1) {
                        Text(
                            text = count.toString(),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
