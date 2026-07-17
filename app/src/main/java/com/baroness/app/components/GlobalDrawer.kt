package com.baroness.app.components

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.SettingsOptions
import com.baroness.app.viewmodels.SettingsViewModel

@Composable
fun GlobalDrawer(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val selectedFont by viewModel.selectedFont.collectAsState()
    val selectedWallpaper by viewModel.selectedWallpaper.collectAsState()

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(enabled = isVisible, onClick = onDismiss)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.8f)
                    .align(Alignment.CenterEnd)
                    .clickable(enabled = true, onClick = {}) // Prevent dismiss when clicking inside
            ) {
                // Blurred background layer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(20.dp)
                            } else Modifier
                        )
                )

                // Crisp content layer
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Settings",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    SettingsSection(title = "Theme") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(SettingsOptions.themes) { theme ->
                                ThemeItem(
                                    theme = theme,
                                    isSelected = selectedTheme == theme.id,
                                    onClick = { viewModel.setTheme(theme.id) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    SettingsSection(title = "Font") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(SettingsOptions.fonts) { font ->
                                FontItem(
                                    font = font,
                                    isSelected = selectedFont == font.id,
                                    onClick = { viewModel.setFont(font.id) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    SettingsSection(title = "Wallpaper") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(SettingsOptions.wallpapers) { wallpaper ->
                                WallpaperItem(
                                    wallpaper = wallpaper,
                                    isSelected = selectedWallpaper == wallpaper.id,
                                    onClick = { viewModel.setWallpaper(wallpaper.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun ThemeItem(theme: com.baroness.app.models.ThemeOption, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(theme.primaryColor)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = Color.White,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = theme.name, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun FontItem(font: com.baroness.app.models.FontOption, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = font.name, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun WallpaperItem(wallpaper: com.baroness.app.models.WallpaperOption, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp, 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(wallpaper.colors))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp)
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = wallpaper.name, color = Color.White, fontSize = 12.sp)
    }
}
