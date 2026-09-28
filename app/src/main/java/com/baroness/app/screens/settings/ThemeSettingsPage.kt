package com.baroness.app.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.baroness.app.components.ChatBubble
import com.baroness.app.models.AppColors
import com.baroness.app.models.SettingsOptions
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsPage(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val activeThemeId by settingsViewModel.activeTheme.collectAsStateWithLifecycle()
    val previewThemeId by settingsViewModel.previewTheme.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            settingsViewModel.revertTheme()
        }
    }

    val previewTheme = remember(previewThemeId) {
        SettingsOptions.themes.find { it.id == previewThemeId } ?: SettingsOptions.LavenderTheme
    }

    val isCurrentlyActive = activeThemeId == previewThemeId
    val accentColor = MaterialTheme.colorScheme.primary
    val cardBorder = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "THEME",
                        color = Color.White,
                        fontFamily = AppFonts.Gamaamli,
                        fontWeight = FontWeight.Normal,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Theme Grid Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                border = cardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "AVAILABLE THEMES",
                        color = Color.White,
                        fontFamily = AppFonts.PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsOptions.themes.forEach { theme ->
                            val isSelected = theme.id == previewThemeId
                            val isApplied = theme.id == activeThemeId

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) Color.White.copy(alpha = 0.12f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) theme.glowColor else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        settingsViewModel.previewTheme(theme.id)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(theme.glowColor)
                                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isApplied) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active",
                                                tint = if (theme.id == "golden") Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = theme.name,
                                        color = Color.White,
                                        fontFamily = AppFonts.Lifesavers,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                if (isApplied) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = theme.glowColor.copy(alpha = 0.2f),
                                        border = BorderStroke(0.5.dp, theme.glowColor)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Chat Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                border = cardBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PREVIEW",
                            color = Color.White,
                            fontFamily = AppFonts.PlayfairDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = previewTheme.name,
                            color = previewTheme.glowColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChatBubble(
                            text = "hello, good morning",
                            alignLeft = true,
                            bubbleColor = previewTheme.bubbleUserColor,
                            textColor = AppColors.textPrimary
                        )
                        ChatBubble(
                            text = "how are you today",
                            alignLeft = false,
                            bubbleColor = previewTheme.bubbleFridayColor,
                            textColor = AppColors.textPrimary
                        )
                        ChatBubble(
                            text = "am alright, you?",
                            alignLeft = true,
                            bubbleColor = previewTheme.bubbleUserColor,
                            textColor = AppColors.textPrimary
                        )
                        ChatBubble(
                            text = "thats great catch up",
                            alignLeft = false,
                            bubbleColor = previewTheme.bubbleFridayColor,
                            textColor = AppColors.textPrimary
                        )
                    }
                }
            }

            // Action Buttons Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // REVERT Button
                Button(
                    onClick = {
                        if (isCurrentlyActive) {
                            settingsViewModel.revertToDefault()
                            Toast.makeText(context, "Reverted to default theme", Toast.LENGTH_SHORT).show()
                        } else {
                            settingsViewModel.revertTheme()
                            Toast.makeText(context, "Reverted theme preview", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentlyActive) Color(0xFFFF453A).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("REVERT", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }

                // APPLY Button
                Button(
                    onClick = {
                        if (isCurrentlyActive) {
                            Toast.makeText(context, "This theme is already active", Toast.LENGTH_SHORT).show()
                        } else {
                            settingsViewModel.applyTheme()
                            Toast.makeText(context, "Theme applied successfully", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentlyActive) Color.White.copy(alpha = 0.15f) else previewTheme.glowColor,
                        contentColor = if (isCurrentlyActive) Color.White.copy(alpha = 0.4f) else Color.Black
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("APPLY", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}
