package com.baroness.app.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.baroness.app.R
import com.baroness.app.ui.theme.AppFonts
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerWallpaper(
    state: WallpaperBoxState,
    onStateChange: (WallpaperBoxState) -> Unit,
    viewModel: SettingsViewModel,
    hazeState: HazeState
) {
    val activeWallpaperId by viewModel.activeWallpaper.collectAsState()
    val previewWallpaperId by viewModel.previewWallpaper.collectAsState()
    val context = LocalContext.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Auto-scroll when expanding or previewing
    LaunchedEffect(state) {
        if (state is WallpaperBoxState.Expanded || state is WallpaperBoxState.Preview) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    val allWallpapers = remember(activeWallpaperId, previewWallpaperId) {
        val list = prebundledWallpapers.toMutableList()
        val userFile = File(context.filesDir, "wallpapers/user_wallpaper.jpg")
        if (userFile.exists()) {
            list.add(WallpaperOption("user_wallpaper", "Custom", WallpaperSource.USER_GALLERY, filePath = userFile.absolutePath))
        }
        list
    }

    val isExpanded = state != WallpaperBoxState.Collapsed
    val title = if (state is WallpaperBoxState.Preview) "WALLPAPER PREVIEW" else "WALLPAPER"

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
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when (state) {
                            WallpaperBoxState.Collapsed -> onStateChange(WallpaperBoxState.Expanded)
                            WallpaperBoxState.Expanded -> onStateChange(WallpaperBoxState.Collapsed)
                            is WallpaperBoxState.Preview -> onStateChange(WallpaperBoxState.Expanded)
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

            // Content Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (state) {
                    WallpaperBoxState.Collapsed -> {
                        WallpaperCollapsedList(
                            activeId = activeWallpaperId,
                            allWallpapers = allWallpapers,
                            onSelect = { id ->
                                viewModel.previewWallpaper(id)
                                onStateChange(WallpaperBoxState.Expanded)
                            }
                        )
                    }
                    WallpaperBoxState.Expanded -> {
                        WallpaperCarousel(
                            activeId = activeWallpaperId,
                            allWallpapers = allWallpapers,
                            onReview = { id ->
                                viewModel.previewWallpaper(id)
                                onStateChange(WallpaperBoxState.Preview(id))
                            },
                            onAddGallery = { uri ->
                                viewModel.setUserWallpaper(uri)
                            },
                            bringIntoViewRequester = bringIntoViewRequester
                        )
                    }
                    is WallpaperBoxState.Preview -> {
                        val previewWallpaper = allWallpapers.find { it.id == state.wallpaperId }
                            ?: allWallpapers.first()
                        WallpaperPreviewArea(
                            wallpaper = previewWallpaper,
                            isActive = activeWallpaperId == previewWallpaperId,
                            onApply = {
                                viewModel.applyWallpaper()
                                onStateChange(WallpaperBoxState.Expanded)
                            },
                            onRevert = {
                                viewModel.revertWallpaper()
                                onStateChange(WallpaperBoxState.Expanded)
                            },
                            onRevertToDefault = {
                                viewModel.revertWallpaperToDefault()
                                onStateChange(WallpaperBoxState.Expanded)
                            },
                            showWarning = { msg, icon -> viewModel.showWarning(msg, icon) },
                            bringIntoViewRequester = bringIntoViewRequester
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperCollapsedList(
    activeId: String,
    allWallpapers: List<WallpaperOption>,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        allWallpapers.forEach { wallpaper ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(wallpaper.id) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                ) {
                    val painter = when (wallpaper.source) {
                        WallpaperSource.PREBUNDLED -> painterResource(id = wallpaper.resId!!)
                        WallpaperSource.USER_GALLERY -> {
                            val bitmap = BitmapFactory.decodeFile(wallpaper.filePath!!)
                            if (bitmap != null) BitmapPainter(bitmap.asImageBitmap()) else painterResource(id = R.drawable.image_39)
                        }
                    }
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = wallpaper.name,
                    color = Color.White,
                    fontFamily = AppFonts.Lifesavers,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (activeId == wallpaper.id) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WallpaperCarousel(
    activeId: String,
    allWallpapers: List<WallpaperOption>,
    onReview: (String) -> Unit,
    onAddGallery: (Uri) -> Unit,
    bringIntoViewRequester: BringIntoViewRequester
) {
    val initialPage = remember(allWallpapers) {
        val index = allWallpapers.indexOfFirst { it.id == activeId }
        if (index != -1) index else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { allWallpapers.size })
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onAddGallery(uri)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 80.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        ) { page ->
            val wallpaper = allWallpapers[page]
            Card(
                modifier = Modifier
                    .width(260.dp)
                    .height(420.dp)
                    .graphicsLayer {
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        val scale = lerp(0.75f, 1.0f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        rotationY = pageOffset * -30f
                        alpha = lerp(0.4f, 1.0f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                        translationX = pageOffset * -20f
                    }
                    .clickable { onReview(wallpaper.id) },
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                val painter = when (wallpaper.source) {
                    WallpaperSource.PREBUNDLED -> painterResource(id = wallpaper.resId!!)
                    WallpaperSource.USER_GALLERY -> {
                        val bitmap = BitmapFactory.decodeFile(wallpaper.filePath!!)
                        if (bitmap != null) BitmapPainter(bitmap.asImageBitmap()) else painterResource(id = R.drawable.image_39)
                    }
                }
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add from Gallery",
                    fontFamily = AppFonts.PlayfairDisplay,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun WallpaperPreviewArea(
    wallpaper: WallpaperOption,
    isActive: Boolean,
    onApply: () -> Unit,
    onRevert: () -> Unit,
    onRevertToDefault: () -> Unit,
    showWarning: (String, Boolean) -> Unit,
    bringIntoViewRequester: BringIntoViewRequester
) {
    Column {
        val painter = when (wallpaper.source) {
            WallpaperSource.PREBUNDLED -> painterResource(id = wallpaper.resId!!)
            WallpaperSource.USER_GALLERY -> {
                val bitmap = BitmapFactory.decodeFile(wallpaper.filePath!!)
                if (bitmap != null) BitmapPainter(bitmap.asImageBitmap()) else painterResource(id = R.drawable.image_39)
            }
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = wallpaper.name,
            color = Color.White,
            fontFamily = AppFonts.Lifesavers,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .bringIntoViewRequester(bringIntoViewRequester),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // REVERT Button
            Button(
                onClick = {
                    if (isActive) {
                        onRevertToDefault()
                        showWarning("Reverted to default", true)
                    } else {
                        showWarning("Nothing to revert", true)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFFFF453A) else Color.White.copy(alpha = 0.05f),
                    contentColor = if (isActive) Color.White else Color.White.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("REVERT", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }

            // APPLY Button
            Button(
                onClick = {
                    if (isActive) {
                        showWarning("This wallpaper is already active", true)
                    } else {
                        onApply()
                        showWarning("Wallpaper applied", false)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color.White.copy(alpha = 0.05f) else Color(0xFF4FC3F7),
                    contentColor = Color.White.copy(alpha = if (isActive) 0.2f else 1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("APPLY", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
