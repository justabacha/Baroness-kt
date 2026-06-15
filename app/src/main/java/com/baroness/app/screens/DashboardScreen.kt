package com.baroness.app.screens

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.baroness.app.components.QuoteCard
import com.baroness.app.ui.theme.Colors
import com.baroness.app.viewmodels.DashboardViewModel
import kotlin.math.max
import kotlin.math.min

private fun clamp(minVal: Float, value: Float, maxVal: Float): Float = min(maxVal, max(minVal, value))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(context.applicationContext as Application)
    )

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()

    val outerPad = clamp(10f, screenWidth * 0.03f, 20f).dp
    val headerPad = clamp(15f, screenWidth * 0.04f, 20f).dp
    val headerMarginTop = clamp(10f, screenWidth * 0.03f, 20f).dp
    val headerMarginBottom = clamp(50f, screenWidth * 0.07f, 20f).dp
    val welcomeFont = clamp(19f, screenWidth * 0.04f, 26f).sp
    val greetFont = clamp(13f, screenWidth * 0.025f, 14f).sp
    val timeFont = clamp(12f, screenWidth * 0.025f, 14f).sp
    val avatarSize = clamp(45f, screenWidth * 0.12f, 55f).dp
    val bubbleSize = clamp(70f, screenWidth * 0.2f, 85f).dp
    val bubbleIconSize = clamp(16f, screenWidth * 0.03f, 24f).sp
    val bubbleValueSize = clamp(13f, screenWidth * 0.025f, 16f).sp
    val bubbleLabelSize = clamp(7f, screenWidth * 0.015f, 8f).sp
    val bubbleGap = clamp(8f, screenWidth * 0.02f, 12f).dp
    val gearSize = clamp(20f, screenWidth * 0.08f, 20f).sp
    val cardWidth = clamp(200f, screenWidth * 0.8f, 260f)
    val cardHeight = clamp(280f, screenHeight * 0.65f, 340f)
    val previewMarginBottom = clamp(60f, screenWidth * 0.05f, 40f).dp
    val card1RightOffset = clamp(-15f, -screenWidth * 0.02f, -5f).dp
    val card2TopOffset = clamp(15f, screenWidth * 0.04f, 30f).dp
    val btnPaddingVertical = clamp(10f, screenWidth * 0.025f, 12f).dp
    val btnPaddingHorizontal = clamp(15f, screenWidth * 0.04f, 25f).dp
    val btnFontSize = clamp(10f, screenWidth * 0.015f, 11f).sp
    val btnGap = clamp(8f, screenWidth * 0.12f, 50f).dp

    val userProfile by viewModel.userProfile.collectAsState()
    val greeting by viewModel.greeting.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val todayDate by viewModel.todayDate.collectAsState()
    val vibe by viewModel.vibe.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()

    if (isInitialLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Colors.pinkAccent)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Colors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(outerPad),
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
                        Spacer(modifier = Modifier.height(15.dp))
                        Text(
                            text = "$currentTime HRS || ${weather?.suggestion ?: "Loading weather..."}",
                            color = Color.White,
                            fontSize = timeFont,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .background(Color.Transparent)
                        )
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
                                    icon = "💧",
                                    value = "${weather?.humidity ?: "--"}",
                                    label = "Humidity",
                                    bubbleSize = bubbleSize,
                                    iconSize = bubbleIconSize,
                                    valueSize = bubbleValueSize,
                                    labelSize = bubbleLabelSize
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { navController.navigate("profile_setup/${userProfile?.id ?: ""}") },
                                    modifier = Modifier.size(gearSize.value.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = Color.White,
                                        modifier = Modifier.size(gearSize.value.dp)
                                    )
                                }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = previewMarginBottom),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        QuoteCard(
                            date = todayDate,
                            text = vibe!!.part1,
                            author = "!!Baroness",
                            imageUrl = vibe!!.photo1,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            tiltDeg = -10f,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .offset(x = card1RightOffset)
                        )
                        QuoteCard(
                            date = todayDate,
                            text = vibe!!.part2,
                            author = "!!Phesty",
                            imageUrl = vibe!!.photo2,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            tiltDeg = 10f,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .offset(y = card2TopOffset)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(btnGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { /* TODO: share Baroness card */ },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = btnPaddingVertical, horizontal = btnPaddingHorizontal),
                            shape = RoundedCornerShape(30.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                containerColor = Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Colors.greenAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(btnFontSize.value.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Baroness's Card", fontSize = btnFontSize, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { /* TODO: share Phesty card */ },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = btnPaddingVertical, horizontal = btnPaddingHorizontal),
                            shape = RoundedCornerShape(30.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                containerColor = Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Colors.greenAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(btnFontSize.value.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Phesty's Card", fontSize = btnFontSize, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleCard(
    icon: String,
    value: String,
    label: String,
    bubbleSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit
) {
    Surface(
        modifier = Modifier.size(bubbleSize),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
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