package com.baroness.app.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.rememberAsyncImagePainter
import com.baroness.app.components.DynamicBackground
import com.baroness.app.data.AvatarRepository
import com.baroness.app.models.UserProfile
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.utils.StorageManager
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import kotlinx.serialization.json.Json

data class SettingsCategoryItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

private val SETTINGS_CATEGORIES = listOf(
    SettingsCategoryItem("profile", "Profile & Identity", "Display Name, Avatar, Persona Assignment", Icons.Default.Person),
    SettingsCategoryItem("appearance", "Appearance", "Theme Customization, Typography & Fonts, Wallpaper & Background", Icons.Default.Palette),
    SettingsCategoryItem("sound", "Sound & Haptics", "TTS Voice Speech Config, Alarm Ringtones, Timer Chimes, Vibration", Icons.AutoMirrored.Filled.VolumeUp),
    SettingsCategoryItem("friday", "FRIDAY", "Persona Voice Optimization, Voice Director Notes", Icons.Default.SmartToy),
    SettingsCategoryItem("notifications", "Notifications", "In-App Banners, System Channels", Icons.Default.Notifications),
    SettingsCategoryItem("privacy", "Privacy & Security", "Vault Gate Security, Session Data", Icons.Default.Security),
    SettingsCategoryItem("storage", "Storage & Data", "Database Backup & Restore, Voice Cache", Icons.Default.Storage),
    SettingsCategoryItem("about", "About Baroness", "App Story, Concept Credits, Version Info", Icons.Default.Info)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCenterScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val activeWallpaperId by settingsViewModel.activeWallpaper.collectAsState()
    val hazeState = remember { HazeState() }

    val storage = remember { StorageManager(context) }
    val avatarRepository = remember { AvatarRepository(context) }
    val json = remember { Json { ignoreUnknownKeys = true } }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry) {
        if (currentEntry?.destination?.route == "settings") {
            val profileJson = storage.getString("userProfile")
            val profile = profileJson?.let {
                try { json.decodeFromString<UserProfile>(it) } catch (_: Exception) { null }
            }
            val updatedProfile = if (profile != null && profile.avatar != null) {
                val localPath = avatarRepository.getCachedAvatar(profile.avatar)
                profile.copy(avatar = localPath ?: profile.avatar)
            } else {
                profile
            }
            userProfile = updatedProfile
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Active wallpaper background layer with haze source
        DynamicBackground(
            activeWallpaperId = activeWallpaperId,
            dimmed = true,
            hazeState = hazeState
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "SETTINGS CENTER",
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
            ) {
                // Header Profile Card
                item {
                    ProfileHeaderCard(
                        userProfile = userProfile,
                        hazeState = hazeState
                    )
                }

                // Discovery Hub Categories
                items(SETTINGS_CATEGORIES) { category ->
                    CategoryCard(
                        category = category,
                        hazeState = hazeState,
                        onClick = {
                            when (category.id) {
                                "profile" -> navController.navigate("settings/profile") { launchSingleTop = true }
                                "appearance" -> navController.navigate("settings/appearance") { launchSingleTop = true }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    userProfile: UserProfile?,
    hazeState: HazeState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .hazeEffect(state = hazeState) {
                blurEffect {
                    blurRadius = 10.dp
                    colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.08f)))
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (userProfile?.avatar != null) {
                Image(
                    painter = rememberAsyncImagePainter(userProfile.avatar),
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (userProfile?.displayName?.take(1) ?: "P").uppercase(),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = userProfile?.displayName ?: "Phesty",
                    color = Color.White,
                    fontFamily = AppFonts.PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Private Universe Settings • Version 2.5",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: SettingsCategoryItem,
    hazeState: HazeState,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .hazeEffect(state = hazeState) {
                blurEffect {
                    blurRadius = 10.dp
                    colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.08f)))
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = category.title,
                        color = Color.White,
                        fontFamily = AppFonts.PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.description,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        letterSpacing = 0.3.sp
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
