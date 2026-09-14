package com.baroness.app.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun TapbackBar(
    settingsViewModel: SettingsViewModel?,
    onReact: (String) -> Unit,
    onShowEmojiPicker: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val recentEmojis by (settingsViewModel?.recentEmojis ?: MutableStateFlow(listOf("❤️", "👍", "👎", "😂", "‼️", "❓", "✨"))).collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    
    Box(
        modifier = modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth(0.9f) // Prevents edge bleed on small screens
            .border(1.5.dp, Color.Black.copy(alpha = 0.8f), RoundedCornerShape(28.dp))
            .padding(0.5.dp)
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 15.dp
                            colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.8f)))
                        }
                    }
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.8f))
                }
            )
            .padding(vertical = 6.dp)
    ) {
        // 1. Emoji Ribbon - Full width, with Ghost Layer Vanishing Effect
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // Horizontal Fade Horizon (Ghost Layer) - Aggressive Vanish
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.0f to Color.Black,
                            0.70f to Color.Black,
                            0.80f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .horizontalScroll(scrollState)
                .padding(start = 12.dp, end = 56.dp) // Extra padding so last emoji clears the vanish zone
        ) {
            recentEmojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onReact(emoji) }
                        )
                        .padding(2.dp)
                )
            }
        }

        // 2. Integrated "+" Satellite (Integrated ghost-anchor)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .size(32.dp)
                .border(1.2.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
                .padding(0.5.dp)
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onShowEmojiPicker() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
