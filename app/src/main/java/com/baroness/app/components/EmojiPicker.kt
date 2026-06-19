package com.baroness.app.components

import android.os.Build
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.baroness.app.data.emojiCategories

private const val TAG = "EmojiPicker"

@Composable
fun EmojiPicker(
    visible: Boolean,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat()

    val pickerWidth = minOf(
        with(density) { (screenWidthPx * 0.9f).toDp() },
        320.dp
    )
    val pickerMaxHeight = 280.dp

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, bottom = 85.dp)
                        .width(pickerWidth)
                        .heightIn(max = pickerMaxHeight)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(12.dp)
                                } else {
                                    Modifier
                                }
                            )
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.65f),
                                        Color.Black.copy(alpha = 0.80f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        emojiCategories.forEach { category ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = category.name.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                val emojisPerRow = 6
                                val rows = category.emojis.chunked(emojisPerRow)

                                rows.forEach { rowEmojis ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        rowEmojis.forEach { emojiChar ->
                                            Box(
                                                modifier = Modifier
                                                    .size(45.dp, 42.dp)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        Log.d(TAG, "Emoji selected: $emojiChar")
                                                        onEmojiSelected(emojiChar)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Emoji(emoji = emojiChar, size = 30.dp)
                                            }
                                        }
                                        repeat(emojisPerRow - rowEmojis.size) {
                                            Spacer(modifier = Modifier.size(45.dp, 42.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}