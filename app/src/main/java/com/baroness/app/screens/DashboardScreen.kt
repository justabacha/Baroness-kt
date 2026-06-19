package com.baroness.app.screens

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.baroness.app.components.QuoteCard
import com.baroness.app.components.FloatingMenu
import com.baroness.app.ui.theme.Colors
import com.baroness.app.utils.rememberCaptureManager
import com.baroness.app.viewmodels.DashboardViewModel
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import androidx.navigation.compose.currentBackStackEntryAsState

private fun clamp(minVal: Float, value: Float, maxVal: Float): Float = min(maxVal, max(minVal, value))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(context.applicationContext as Application)
    )

    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(androidx.compose.ui.platform.LocalDensity.current) { containerSize.width.toDp().value }
    val screenHeight = with(androidx.compose.ui.platform.LocalDensity.current) { containerSize.height.toDp().value }

    val outerPad             = clamp(10f,  screenWidth * 0.03f,  20f).dp
    val headerPad            = clamp(15f,  screenWidth * 0.04f,  20f).dp
    val headerMarginTop      = clamp(10f,  screenWidth * 0.03f,  20f).dp
    val headerMarginBottom   = clamp(50f,  screenWidth * 0.07f,  20f).dp
    val welcomeFont          = clamp(19f,  screenWidth * 0.04f,  26f).sp
    val greetFont            = clamp(13f,  screenWidth * 0.025f, 14f).sp
    val timeFont             = clamp(12f,  screenWidth * 0.025f, 14f).sp
    val avatarSize           = clamp(45f,  screenWidth * 0.12f,  55f).dp
    val bubbleSize           = clamp(70f,  screenWidth * 0.2f,   85f).dp
    val bubbleIconSize       = clamp(16f,  screenWidth * 0.03f,  24f).sp
    val bubbleValueSize      = clamp(13f,  screenWidth * 0.025f, 16f).sp
    val bubbleLabelSize      = clamp(7f,   screenWidth * 0.015f,  8f).sp
    val bubbleGap            = clamp(8f,   screenWidth * 0.02f,  12f).dp
    val cardWidth            = clamp(200f, screenWidth * 0.8f,  260f)
    val cardHeight           = clamp(280f, screenHeight * 0.65f, 340f)
    val previewMarginBottom  = clamp(60f,  screenWidth * 0.05f,  40f).dp
    val card1RightOffset     = clamp(-15f, -screenWidth * 0.02f,  -5f).dp
    val btnPaddingVertical   = clamp(10f,  screenWidth * 0.025f, 12f).dp
    val btnPaddingHorizontal = clamp(15f,  screenWidth * 0.04f,  25f).dp
    val btnFontSize          = clamp(10f,  screenWidth * 0.015f, 11f).sp
    val btnGap               = clamp(8f,   screenWidth * 0.12f,  50f).dp

    val userProfile      by viewModel.userProfile.collectAsState()
    val greeting         by viewModel.greeting.collectAsState()
    val currentTime      by viewModel.currentTime.collectAsState()
    val todayDate        by viewModel.todayDate.collectAsState()
    val vibe             by viewModel.vibe.collectAsState()
    val weather          by viewModel.weather.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val isRefreshing     by viewModel.isRefreshing.collectAsState()

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry) {
        if (currentEntry?.destination?.route == "dashboard") {
            viewModel.reloadProfile()
        }
    }
    // Hoisted above LazyColumn — these must be in @Composable scope
    val captureManager = rememberCaptureManager()
    val coroutineScope = rememberCoroutineScope()
    val card1Layer = rememberGraphicsLayer()
    val card2Layer = rememberGraphicsLayer()

    val fineLocationPermissionState = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(Unit) {
        if (!fineLocationPermissionState.status.isGranted) {
            fineLocationPermissionState.launchPermissionRequest()
        }
    }

    if (isInitialLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Colors.pinkAccent)
        }
        return
    }

    val pullRefreshState = rememberPullToRefreshState()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.bg)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = outerPad,
                    end = outerPad,
                    top = outerPad,
                    bottom = outerPad + navBarInset.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = headerMarginTop, bottom = headerMarginBottom)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(25.dp)),
                        shape = RoundedCornerShape(25.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(headerPad)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(userProfile?.avatar),
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(avatarSize)
                                        .clip(CircleShape)
                                        .border(1.5.dp, Colors.purpleAccent, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(clamp(10f, screenWidth * 0.03f, 15f).dp))
                                Column {
                                    Text(
                                        text = "Hi ${userProfile?.displayName ?: "User"}, Welcome back.",
                                        color = Color.White,
                                        fontSize = welcomeFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = greeting,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = greetFont
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(15.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "$currentTime HRS || ${weather?.suggestion ?: "Loading weather..."}",
                                color = Color.White,
                                fontSize = timeFont,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(15.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(bubbleGap)) {
                                    BubbleCard(
                                        icon = "🌡️",
                                        value = "${weather?.temp ?: "--"}",
                                        label = "Temperature",
                                        bubbleSize = bubbleSize,
                                        iconSize = bubbleIconSize,
                                        valueSize = bubbleValueSize,
                                        labelSize = bubbleLabelSize
                                    )
                                    BubbleCard(
                                        icon = "🫧",
                                        value = "${weather?.humidity ?: "--"}",
                                        label = "Humidity",
                                        bubbleSize = bubbleSize,
                                        iconSize = bubbleIconSize,
                                        valueSize = bubbleValueSize,
                                        labelSize = bubbleLabelSize
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            navController.navigate("profile_setup/${userProfile?.id ?: ""}") {
                                                launchSingleTop = true
                                            }
                                        }
                                        .padding(5.dp)
                                ) {
                                    SettingsGearIcon(size = 24.dp)
                                    Text(
                                        text = "Settings",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                if (vibe != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = previewMarginBottom),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.wrapContentHeight()
                            ) {
                                // Baroness card (left tilt)
                                Box(
                                    modifier = Modifier
                                        .offset(x = -card1RightOffset)
                                        .drawWithContent {
                                            card1Layer.record {
                                                this@drawWithContent.drawContent()
                                            }
                                            drawLayer(card1Layer)
                                        }
                                ) {
                                    QuoteCard(
                                        date = todayDate,
                                        text = vibe!!.part1,
                                        author = "!!Baroness",
                                        imageUrl = vibe!!.photo1,
                                        cardWidth = cardWidth.dp,
                                        cardHeight = cardHeight.dp,
                                        tiltDeg = -10f,
                                        modifier = Modifier
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))

                                // Phesty card (right tilt)
                                Box(
                                    modifier = Modifier
                                        .offset(x = -card1RightOffset)
                                        .drawWithContent {
                                            card2Layer.record {
                                                this@drawWithContent.drawContent()
                                            }
                                            drawLayer(card2Layer)
                                        }
                                ) {
                                    QuoteCard(
                                        date = todayDate,
                                        text = vibe!!.part2,
                                        author = "!!Phesty",
                                        imageUrl = vibe!!.photo2,
                                        cardWidth = cardWidth.dp,
                                        cardHeight = cardHeight.dp,
                                        tiltDeg = 10f,
                                        modifier = Modifier
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(btnGap, Alignment.CenterHorizontally)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        captureManager.captureAndShare(card1Layer, "baroness_card.png")
                                    }
                                },
                                contentPadding = PaddingValues(
                                    vertical = btnPaddingVertical,
                                    horizontal = btnPaddingHorizontal
                                ),
                                shape = RoundedCornerShape(30.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    containerColor = Color.Transparent
                                ),
                                border = BorderStroke(1.dp, Colors.greenAccent)
                            ) {
                                DownloadIcon(size = 14.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Save Baroness's Card",
                                    fontSize = btnFontSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        captureManager.captureAndShare(card2Layer, "phesty_card.png")
                                    }
                                },
                                contentPadding = PaddingValues(
                                    vertical = btnPaddingVertical,
                                    horizontal = btnPaddingHorizontal
                                ),
                                shape = RoundedCornerShape(30.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    containerColor = Color.Transparent
                                ),
                                border = BorderStroke(1.dp, Colors.greenAccent)
                            ) {
                                DownloadIcon(size = 14.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Save Phesty's Card",
                                    fontSize = btnFontSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            FloatingMenu(navController)
        }
    }
}

@Composable
private fun DownloadIcon(size: Dp, color: Color = Color.White) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size
        val scaleX = s.width / 24f
        val scaleY = s.height / 24f

        val stroke = Stroke(
            width = 2f * scaleX,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val trayPath = Path().apply {
            moveTo(21f * scaleX, 15f * scaleY)
            lineTo(21f * scaleX, 19f * scaleY)
            cubicTo(
                21f * scaleX, 20.1f * scaleY,
                20.1f * scaleX, 21f * scaleY,
                19f * scaleX, 21f * scaleY
            )
            lineTo(5f * scaleX, 21f * scaleY)
            cubicTo(
                3.9f * scaleX, 21f * scaleY,
                3f * scaleX, 20.1f * scaleY,
                3f * scaleX, 19f * scaleY
            )
            lineTo(3f * scaleX, 15f * scaleY)
        }
        drawPath(trayPath, color = color, style = stroke)

        val chevronPath = Path().apply {
            moveTo(7f * scaleX, 10f * scaleY)
            lineTo(12f * scaleX, 15f * scaleY)
            lineTo(17f * scaleX, 10f * scaleY)
        }
        drawPath(chevronPath, color = color, style = stroke)

        val stemPath = Path().apply {
            moveTo(12f * scaleX, 15f * scaleY)
            lineTo(12f * scaleX, 3f * scaleY)
        }
        drawPath(stemPath, color = color, style = stroke)
    }
}

@Composable
private fun SettingsGearIcon(size: Dp, color: Color = Color.White) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size
        val scaleX = s.width / 24f
        val scaleY = s.height / 24f

        val stroke = Stroke(
            width = 2f * scaleX,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val gearPath = Path().apply {
            moveTo(12.22f * scaleX, 2f * scaleY)
            lineTo(11.78f * scaleX, 2f * scaleY)
            cubicTo(10.67f * scaleX, 2f * scaleY, 9.78f * scaleX, 2.9f * scaleY, 9.78f * scaleX, 4f * scaleY)
            lineTo(9.78f * scaleX, 4.18f * scaleY)
            cubicTo(9.78f * scaleX, 4.88f * scaleY, 9.36f * scaleX, 5.52f * scaleY, 8.71f * scaleX, 5.85f * scaleY)
            lineTo(8.28f * scaleX, 6.1f * scaleY)
            cubicTo(7.63f * scaleX, 6.43f * scaleY, 6.85f * scaleX, 6.34f * scaleY, 6.28f * scaleX, 5.93f * scaleY)
            lineTo(6.13f * scaleX, 5.85f * scaleY)
            cubicTo(5.24f * scaleX, 5.33f * scaleY, 4.1f * scaleX, 5.63f * scaleY, 3.58f * scaleX, 6.52f * scaleY)
            lineTo(3.36f * scaleX, 6.9f * scaleY)
            cubicTo(2.84f * scaleX, 7.79f * scaleY, 3.14f * scaleX, 8.93f * scaleY, 4.03f * scaleX, 9.45f * scaleY)
            lineTo(4.18f * scaleX, 9.55f * scaleY)
            cubicTo(4.88f * scaleX, 9.97f * scaleY, 5.28f * scaleX, 10.72f * scaleY, 5.28f * scaleX, 11.51f * scaleY)
            lineTo(5.28f * scaleX, 12.02f * scaleY)
            cubicTo(5.28f * scaleX, 12.81f * scaleY, 4.88f * scaleX, 13.56f * scaleY, 4.18f * scaleX, 13.98f * scaleY)
            lineTo(4.03f * scaleX, 14.07f * scaleY)
            cubicTo(3.14f * scaleX, 14.59f * scaleY, 2.84f * scaleX, 15.73f * scaleY, 3.36f * scaleX, 16.62f * scaleY)
            lineTo(3.58f * scaleX, 17f * scaleY)
            cubicTo(4.1f * scaleX, 17.89f * scaleY, 5.24f * scaleX, 18.19f * scaleY, 6.13f * scaleX, 17.67f * scaleY)
            lineTo(6.28f * scaleX, 17.59f * scaleY)
            cubicTo(6.85f * scaleX, 17.18f * scaleY, 7.63f * scaleX, 17.09f * scaleY, 8.28f * scaleX, 17.42f * scaleY)
            lineTo(8.71f * scaleX, 17.67f * scaleY)
            cubicTo(9.36f * scaleX, 18f * scaleY, 9.78f * scaleX, 18.64f * scaleY, 9.78f * scaleX, 19.34f * scaleY)
            lineTo(9.78f * scaleX, 20f * scaleY)
            cubicTo(9.78f * scaleX, 21.1f * scaleY, 10.67f * scaleX, 22f * scaleY, 11.78f * scaleX, 22f * scaleY)
            lineTo(12.22f * scaleX, 22f * scaleY)
            cubicTo(13.33f * scaleX, 22f * scaleY, 14.22f * scaleX, 21.1f * scaleY, 14.22f * scaleX, 20f * scaleY)
            lineTo(14.22f * scaleX, 19.82f * scaleY)
            cubicTo(14.22f * scaleX, 19.12f * scaleY, 14.64f * scaleX, 18.48f * scaleY, 15.29f * scaleX, 18.15f * scaleY)
            lineTo(15.72f * scaleX, 17.9f * scaleY)
            cubicTo(16.37f * scaleX, 17.57f * scaleY, 17.15f * scaleX, 17.66f * scaleY, 17.72f * scaleX, 18.07f * scaleY)
            lineTo(17.87f * scaleX, 18.15f * scaleY)
            cubicTo(18.76f * scaleX, 18.67f * scaleY, 19.9f * scaleX, 18.37f * scaleY, 20.42f * scaleX, 17.48f * scaleY)
            lineTo(20.64f * scaleX, 17.09f * scaleY)
            cubicTo(21.16f * scaleX, 16.2f * scaleY, 20.86f * scaleX, 15.06f * scaleY, 19.97f * scaleX, 14.54f * scaleY)
            lineTo(19.82f * scaleX, 14.45f * scaleY)
            cubicTo(19.12f * scaleX, 14.03f * scaleY, 18.72f * scaleX, 13.28f * scaleY, 18.72f * scaleX, 12.49f * scaleY)
            lineTo(18.72f * scaleX, 11.99f * scaleY)
            cubicTo(18.72f * scaleX, 11.2f * scaleY, 19.12f * scaleX, 10.45f * scaleY, 19.82f * scaleX, 10.03f * scaleY)
            lineTo(19.97f * scaleX, 9.94f * scaleY)
            cubicTo(20.86f * scaleX, 9.42f * scaleY, 21.16f * scaleX, 8.28f * scaleY, 20.64f * scaleX, 7.39f * scaleY)
            lineTo(20.42f * scaleX, 7f * scaleY)
            cubicTo(19.9f * scaleX, 6.11f * scaleY, 18.76f * scaleX, 5.81f * scaleY, 17.87f * scaleX, 6.33f * scaleY)
            lineTo(17.72f * scaleX, 6.41f * scaleY)
            cubicTo(17.15f * scaleX, 6.82f * scaleY, 16.37f * scaleX, 6.91f * scaleY, 15.72f * scaleX, 6.58f * scaleY)
            lineTo(15.29f * scaleX, 6.33f * scaleY)
            cubicTo(14.64f * scaleX, 6f * scaleY, 14.22f * scaleX, 5.36f * scaleY, 14.22f * scaleX, 4.66f * scaleY)
            lineTo(14.22f * scaleX, 4f * scaleY)
            cubicTo(14.22f * scaleX, 2.9f * scaleY, 13.33f * scaleX, 2f * scaleY, 12.22f * scaleX, 2f * scaleY)
            close()
        }
        drawPath(gearPath, color = color, style = stroke)

        drawCircle(
            color = color,
            radius = 3f * scaleX,
            center = Offset(12f * scaleX, 12f * scaleY),
            style = stroke
        )
    }
}

@Composable
private fun BubbleCard(
    icon: String,
    value: String,
    label: String,
    bubbleSize: Dp,
    iconSize: TextUnit,
    valueSize: TextUnit,
    labelSize: TextUnit
) {
    Surface(
        modifier = Modifier.size(bubbleSize),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = iconSize)
            Text(text = value, color = Color.White, fontSize = valueSize, fontWeight = FontWeight.Bold)
            Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = labelSize, letterSpacing = 0.5.sp)
        }
    }
}

class DashboardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(application) as T
    }
}