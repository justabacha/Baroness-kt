package com.baroness.app.screens.settings

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.baroness.app.R
import com.baroness.app.components.WallpaperOption
import com.baroness.app.components.WallpaperSource
import com.baroness.app.components.prebundledWallpapers
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.viewmodels.SettingsViewModel
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSettingsPage(
    navController: NavController,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val activeWallpaperId by settingsViewModel.activeWallpaper.collectAsStateWithLifecycle()
    val previewWallpaperId by settingsViewModel.previewWallpaper.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            settingsViewModel.revertWallpaper()
        }
    }

    val allWallpapers = remember(activeWallpaperId, previewWallpaperId) {
        val list = prebundledWallpapers.toMutableList()
        val userFile = File(context.filesDir, "wallpapers/user_wallpaper.jpg")
        if (userFile.exists()) {
            list.add(WallpaperOption("user_wallpaper", "Custom Gallery", WallpaperSource.USER_GALLERY, filePath = userFile.absolutePath))
        }
        list
    }

    val previewWallpaper = remember(previewWallpaperId, allWallpapers) {
        allWallpapers.find { it.id == previewWallpaperId } ?: allWallpapers.first()
    }

    val isCurrentlyActive = activeWallpaperId == previewWallpaperId
    val accentColor = MaterialTheme.colorScheme.primary
    val cardBorder = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            settingsViewModel.setUserWallpaper(it)
            Toast.makeText(context, "Custom wallpaper updated", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "WALLPAPER",
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
            // Carousel Selection Card
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "WALLPAPER GALLERY",
                        color = Color.White,
                        fontFamily = AppFonts.PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    val initialPage = remember(allWallpapers, previewWallpaperId) {
                        val index = allWallpapers.indexOfFirst { it.id == previewWallpaperId }
                        if (index != -1) index else 0
                    }
                    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { allWallpapers.size })

                    LaunchedEffect(pagerState.currentPage) {
                        if (pagerState.currentPage in allWallpapers.indices) {
                            val selectedId = allWallpapers[pagerState.currentPage].id
                            if (selectedId != previewWallpaperId) {
                                settingsViewModel.previewWallpaper(selectedId)
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) { page ->
                        val wallpaper = allWallpapers[page]
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .height(280.dp)
                                .graphicsLayer {
                                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                    val scale = lerp(0.82f, 1.0f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = lerp(0.5f, 1.0f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                                }
                                .clickable {
                                    settingsViewModel.previewWallpaper(wallpaper.id)
                                },
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(
                                if (wallpaper.id == previewWallpaperId) 2.dp else 1.dp,
                                if (wallpaper.id == previewWallpaperId) accentColor else Color.White.copy(alpha = 0.2f)
                            )
                        ) {
                            val painter = when (wallpaper.source) {
                                WallpaperSource.PREBUNDLED -> painterResource(id = wallpaper.resId!!)
                                WallpaperSource.USER_GALLERY -> {
                                    val bitmap = try { BitmapFactory.decodeFile(wallpaper.filePath!!) } catch (_: Throwable) { null }
                                    if (bitmap != null) BitmapPainter(bitmap.asImageBitmap()) else painterResource(id = R.drawable.image_39)
                                }
                            }
                            Image(
                                painter = painter,
                                contentDescription = wallpaper.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add from Gallery",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Preview Card
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
                            text = previewWallpaper.name,
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    val painter = when (previewWallpaper.source) {
                        WallpaperSource.PREBUNDLED -> painterResource(id = previewWallpaper.resId!!)
                        WallpaperSource.USER_GALLERY -> {
                            val bitmap = try { BitmapFactory.decodeFile(previewWallpaper.filePath!!) } catch (_: Throwable) { null }
                            if (bitmap != null) BitmapPainter(bitmap.asImageBitmap()) else painterResource(id = R.drawable.image_39)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = previewWallpaper.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
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
                            settingsViewModel.revertWallpaperToDefault()
                            Toast.makeText(context, "Reverted to default wallpaper", Toast.LENGTH_SHORT).show()
                        } else {
                            settingsViewModel.revertWallpaper()
                            Toast.makeText(context, "Reverted wallpaper preview", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "This wallpaper is already active", Toast.LENGTH_SHORT).show()
                        } else {
                            settingsViewModel.applyWallpaper()
                            Toast.makeText(context, "Wallpaper applied successfully", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentlyActive) Color.White.copy(alpha = 0.15f) else accentColor,
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
