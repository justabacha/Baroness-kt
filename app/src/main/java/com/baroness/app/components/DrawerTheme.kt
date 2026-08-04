package com.baroness.app.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.AppColors
import com.baroness.app.models.AppTheme
import com.baroness.app.models.SettingsOptions
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect

@Composable
fun DrawerTheme(
    state: ThemeBoxState,
    onStateChange: (ThemeBoxState) -> Unit,
    viewModel: SettingsViewModel,
    hazeState: HazeState
) {
    val activeThemeId by viewModel.activeTheme.collectAsState()
    val previewThemeId by viewModel.previewTheme.collectAsState()

    val title = if (state is ThemeBoxState.Preview) "THEME PREVIEW" else "THEME"
    val isExpanded = state != ThemeBoxState.Collapsed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .hazeEffect(state = hazeState) {
                blurEffect {
                    blurRadius = 20.dp
                    colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.08f)))
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .animateContentSize()
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when (state) {
                            ThemeBoxState.Collapsed -> onStateChange(ThemeBoxState.Expanded)
                            ThemeBoxState.Expanded -> onStateChange(ThemeBoxState.Collapsed)
                            is ThemeBoxState.Preview -> onStateChange(ThemeBoxState.Expanded)
                        }
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = AppFonts.PlayfairDisplay,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }

            // Content Box (ALWAYS present)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (state) {
                    ThemeBoxState.Collapsed -> {
                        ThemeCollapsedList(
                            activeId = activeThemeId,
                            onExpand = {
                                viewModel.previewTheme(it)
                                onStateChange(ThemeBoxState.Expanded)
                            }
                        )
                    }
                    ThemeBoxState.Expanded -> {
                        ThemeExpandedGrid(
                            activeId = activeThemeId,
                            onReview = {
                                viewModel.previewTheme(it)
                                onStateChange(ThemeBoxState.Preview(it))
                            }
                        )
                    }
                    is ThemeBoxState.Preview -> {
                        val previewTheme = SettingsOptions.themes.find { it.id == previewThemeId }
                            ?: SettingsOptions.themes.first()
                        ThemePreviewChat(
                            theme = previewTheme,
                            isActive = activeThemeId == previewThemeId,
                            onApply = {
                                viewModel.applyTheme()
                                onStateChange(ThemeBoxState.Expanded)
                            },
                            onRevert = {
                                viewModel.revertTheme()
                                onStateChange(ThemeBoxState.Expanded)
                            },
                            onRevertToDefault = {
                                viewModel.revertToDefault()
                                onStateChange(ThemeBoxState.Expanded)
                            },
                            showWarning = { msg, icon -> viewModel.showWarning(msg, icon) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeCollapsedList(activeId: String, onExpand: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsOptions.themes.forEach { theme ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpand(theme.id) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(theme.glowColor)
                        .border(
                            if (activeId == theme.id) 1.5.dp else 0.dp,
                            Color.White,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = theme.name,
                    color = Color.White,
                    fontFamily = AppFonts.Lifesavers,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (activeId == theme.id) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeExpandedGrid(activeId: String, onReview: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(SettingsOptions.themes) { theme ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(theme.glowColor)
                        .border(
                            width = if (activeId == theme.id) 2.dp else 0.dp,
                            color = theme.glowColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeId == theme.id) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (theme.id == "golden") Color.Black else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = theme.name,
                    color = Color.White,
                    fontFamily = AppFonts.Lifesavers,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onReview(theme.id) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("REVIEW", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun ThemePreviewChat(
    theme: AppTheme,
    isActive: Boolean,
    onApply: () -> Unit,
    onRevert: () -> Unit,
    onRevertToDefault: () -> Unit,
    showWarning: (String, Boolean) -> Unit
) {
    Column {
        // Mock Chat
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChatBubble(
                text = "hello, good morning",
                alignLeft = true,
                bubbleColor = theme.bubbleUserColor, // Sender (Left/Own)
                textColor = AppColors.textPrimary
            )
            ChatBubble(
                text = "how are you today",
                alignLeft = false,
                bubbleColor = theme.bubbleFridayColor, // Receiver (Right/Other)
                textColor = AppColors.textPrimary
            )
            ChatBubble(
                text = "am alright, you?",
                alignLeft = true,
                bubbleColor = theme.bubbleUserColor,
                textColor = AppColors.textPrimary
            )
            ChatBubble(
                text = "thats great catch up",
                alignLeft = false,
                bubbleColor = theme.bubbleFridayColor,
                textColor = AppColors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // REVERT Button
            Button(
                onClick = {
                    if (isActive) {
                        onRevertToDefault()
                        showWarning("Reverted to default theme", false)
                    } else {
                        showWarning("Nothing to revert", true)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFFFF453A) else Color.White.copy(alpha = 0.05f),
                    contentColor = Color.White.copy(alpha = if (isActive) 1f else 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("REVERT", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }

            // APPLY Button
            Button(
                onClick = {
                    if (isActive) {
                        showWarning("This theme is already in use", true)
                    } else {
                        onApply()
                        showWarning("Theme applied successfully", false)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color.White.copy(alpha = 0.05f) else theme.glowColor,
                    contentColor = if (isActive) Color.White.copy(alpha = 0.2f) else AppColors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("APPLY", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun ChatBubble(
    text: String,
    alignLeft: Boolean,
    bubbleColor: Color,
    textColor: Color,
    isGlass: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignLeft) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (alignLeft) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (alignLeft) 4.dp else 16.dp,
                        bottomEnd = if (alignLeft) 16.dp else 4.dp
                    )
                )
                .background(bubbleColor)
                .then(if (isGlass) Modifier.border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 4.dp
                )) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 13.sp
            )
        }

        if (!alignLeft) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}
