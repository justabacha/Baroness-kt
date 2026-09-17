package com.baroness.app.components.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import com.baroness.app.data.remote.groq.GroqApiService
import kotlinx.coroutines.launch

@Composable
fun AskFridaySheet(
    message: Message,
    isOwn: Boolean,
    participant: Participant?,
    activeThemeId: String,
    hazeState: HazeState?,
    settingsViewModel: SettingsViewModel?,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val typography = rememberChatTypography(settingsViewModel)
    val configuration = LocalConfiguration.current
    val clipboardManager = LocalClipboardManager.current
    val sheetHeight = configuration.screenHeightDp.dp * 0.85f 
    val coroutineScope = rememberCoroutineScope()
    
    var analysisResult by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val theme = com.baroness.app.models.SettingsOptions.themes.find { it.id == activeThemeId } 
        ?: com.baroness.app.models.SettingsOptions.LavenderTheme

    val modes = listOf("Analyze Intent", "Detect Sarcasm", "Summarize Context", "Suggest a Reply")

    fun triggerAnalysis(mode: String) {
        selectedMode = mode
        coroutineScope.launch {
            try {
                isLoading = true
                error = null
                val api = GroqApiService()
                val result = api.analyzeMessage(message.content, mode)
                if (result != null) {
                    analysisResult = result
                } else {
                    error = "Friday is momentarily offline."
                }
            } catch (e: Exception) {
                error = "Neural link failed: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .border(1.5.dp, Color.Black.copy(alpha = 0.8f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(0.5.dp)
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 30.dp
                            colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.85f)))
                        }
                    }
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.85f))
                }
            )
            .padding(24.dp)
    ) {
        // Handle Bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // 1. The Subject (Hero)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            contentAlignment = Alignment.TopStart
        ) {
            MessageBubble(
                message = message,
                isOwn = isOwn,
                participant = participant,
                settingsViewModel = settingsViewModel,
                activeThemeId = activeThemeId,
                isPreviewMode = true,
                modifier = Modifier.padding(start = 0.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Intelligence Pills (Mode Selector)
        Text(
            text = "Friday Analysis",
            style = typography.title.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = Color.White.copy(alpha = 0.4f)
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(modes) { mode ->
                IntelligencePill(
                    text = mode,
                    isSelected = selectedMode == mode,
                    accentColor = Color(0xFF80D8FF),
                    onClick = { triggerAnalysis(mode) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Analysis Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Main Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF80D8FF), modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Friday is scanning neural subtext...",
                                    style = typography.body.copy(fontSize = 13.sp),
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    error != null -> {
                        Text(
                            text = error!!,
                            style = typography.body,
                            color = Color(0xFFFF8A80)
                        )
                    }
                    analysisResult != null -> {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            com.baroness.app.components.PhestyText(
                                text = analysisResult!!,
                                style = typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Select an analysis mode above to begin.",
                                style = typography.body.copy(fontSize = 14.sp),
                                color = Color.White.copy(alpha = 0.3f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // WALL-MOUNTED Components (Same as Translation)
            if (analysisResult != null && !isLoading) {
                // Copy Button Top-Right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-1).dp, y = 2.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(analysisResult!!))
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Tagline Bottom-Left
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 15.dp, y = (-6).dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF80D8FF),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Friday Neural Analysis",
                            style = typography.body.copy(
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = Color(0xFF80D8FF).copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Close Button
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(
                text = "Close",
                style = typography.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}

@Composable
private fun IntelligencePill(
    text: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) accentColor else Color.White.copy(alpha = 0.7f)
        )
    }
}
