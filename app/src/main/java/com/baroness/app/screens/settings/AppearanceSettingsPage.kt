package com.baroness.app.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.baroness.app.components.fontFamilies
import com.baroness.app.components.prebundledWallpapers
import com.baroness.app.models.SettingsOptions
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsPage(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val activeThemeId by settingsViewModel.activeTheme.collectAsStateWithLifecycle()
    val activeFontId by settingsViewModel.activeFont.collectAsStateWithLifecycle()
    val activeWallpaperId by settingsViewModel.activeWallpaper.collectAsStateWithLifecycle()

    val currentThemeName = remember(activeThemeId) {
        SettingsOptions.themes.find { it.id == activeThemeId }?.name ?: "Lavender"
    }

    val currentFontName = remember(activeFontId) {
        fontFamilies.find { activeFontId.startsWith(it.id) }?.familyName ?: "Playfair Display"
    }

    val currentWallpaperName = remember(activeWallpaperId) {
        if (activeWallpaperId == "user_wallpaper") "Custom Gallery"
        else prebundledWallpapers.find { it.id == activeWallpaperId }?.name ?: "Sunrise"
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val cardBorder = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "APPEARANCE",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppearanceOptionCard(
                title = "Theme Customization",
                subtitle = "Active: $currentThemeName",
                icon = Icons.Default.Palette,
                cardBorder = cardBorder,
                onClick = {
                    navController.navigate("settings/appearance/theme") {
                        launchSingleTop = true
                    }
                }
            )

            AppearanceOptionCard(
                title = "Typography & Fonts",
                subtitle = "Active: $currentFontName",
                icon = Icons.Default.TextFields,
                cardBorder = cardBorder,
                onClick = {
                    navController.navigate("settings/appearance/font") {
                        launchSingleTop = true
                    }
                }
            )

            AppearanceOptionCard(
                title = "Wallpaper & Background",
                subtitle = "Active: $currentWallpaperName",
                icon = Icons.Default.Image,
                cardBorder = cardBorder,
                onClick = {
                    navController.navigate("settings/appearance/wallpaper") {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

@Composable
private fun AppearanceOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    cardBorder: BorderStroke,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = AppFonts.PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
