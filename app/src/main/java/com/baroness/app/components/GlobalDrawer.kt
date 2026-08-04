package com.baroness.app.components

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.R
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import java.io.File

@Composable
fun GlobalDrawer(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    
    // Accordion State: Only one can be expanded at a time
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    
    // Internal states for each category
    var themeBoxState by remember { mutableStateOf<ThemeBoxState>(ThemeBoxState.Collapsed) }
    var fontBoxState by remember { mutableStateOf<FontBoxState>(FontBoxState.Collapsed) }
    var wallpaperBoxState by remember { mutableStateOf<WallpaperBoxState>(WallpaperBoxState.Collapsed) }

    // Reset all categories when drawer opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            themeBoxState = ThemeBoxState.Collapsed
            fontBoxState = FontBoxState.Collapsed
            wallpaperBoxState = WallpaperBoxState.Collapsed
            expandedCategory = null
        }
    }

    // Accordion Logic: When another category expands, collapse others to State 1
    LaunchedEffect(expandedCategory) {
        if (expandedCategory != "THEME") {
            themeBoxState = ThemeBoxState.Collapsed
        }
        if (expandedCategory != "FONT") {
            fontBoxState = FontBoxState.Collapsed
        }
        if (expandedCategory != "WALLPAPER") {
            wallpaperBoxState = WallpaperBoxState.Collapsed
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    enabled = isVisible,
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterStart)
                    .clickable(
                        enabled = true,
                        onClick = {},
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
            ) {
                // Background Image - ALWAYS image_39
                Image(
                    painter = painterResource(id = R.drawable.image_39),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SETTINGS",
                            color = Color.White,
                            fontFamily = AppFonts.Gamaamli,
                            fontWeight = FontWeight.Normal,
                            fontSize = 22.sp
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // THEME Section
                        DrawerTheme(
                            state = themeBoxState,
                            onStateChange = { newState ->
                                themeBoxState = newState
                                if (newState != ThemeBoxState.Collapsed) {
                                    expandedCategory = "THEME"
                                }
                            },
                            viewModel = viewModel,
                            hazeState = hazeState
                        )

                        // FONT Section
                        DrawerFont(
                            state = fontBoxState,
                            onStateChange = { newState ->
                                fontBoxState = newState
                                if (newState != FontBoxState.Collapsed) {
                                    expandedCategory = "FONT"
                                }
                            },
                            viewModel = viewModel,
                            hazeState = hazeState
                        )

                        // WALLPAPER Section
                        DrawerWallpaper(
                            state = wallpaperBoxState,
                            onStateChange = { newState ->
                                wallpaperBoxState = newState
                                if (newState != WallpaperBoxState.Collapsed) {
                                    expandedCategory = "WALLPAPER"
                                }
                            },
                            viewModel = viewModel,
                            hazeState = hazeState
                        )

                        // SOUND & HAPTICS Section
                        DrawerSoundHaptics(
                            isExpanded = expandedCategory == "SOUND",
                            onToggle = {
                                expandedCategory = if (expandedCategory == "SOUND") null else "SOUND"
                            },
                            hazeState = hazeState
                        )

                        // NOTIFICATIONS Section
                        DrawerNotifications(
                            isExpanded = expandedCategory == "NOTIFICATIONS",
                            onToggle = {
                                expandedCategory = if (expandedCategory == "NOTIFICATIONS") null else "NOTIFICATIONS"
                            },
                            hazeState = hazeState
                        )

                        // PRIVACY Section
                        DrawerPrivacy(
                            isExpanded = expandedCategory == "PRIVACY",
                            onToggle = {
                                expandedCategory = if (expandedCategory == "PRIVACY") null else "PRIVACY"
                            },
                            hazeState = hazeState
                        )

                        // STORAGE Section
                        DrawerStorage(
                            isExpanded = expandedCategory == "STORAGE",
                            onToggle = {
                                expandedCategory = if (expandedCategory == "STORAGE") null else "STORAGE"
                            },
                            hazeState = hazeState
                        )

                        // ABOUT Section
                        DrawerAbout(
                            isExpanded = expandedCategory == "ABOUT",
                            onToggle = {
                                expandedCategory = if (expandedCategory == "ABOUT") null else "ABOUT"
                            },
                            hazeState = hazeState
                        )

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GlassCategoryBox(
    title: String,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    hazeState: HazeState,
    content: @Composable () -> Unit
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
            .animateContentSize()
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
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

            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    content()
                }
            }
        }
    }
}
