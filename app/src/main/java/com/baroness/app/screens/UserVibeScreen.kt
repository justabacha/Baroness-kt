package com.baroness.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.baroness.app.ui.theme.BaronessAppTheme

data class VibeItem(
    val id: String,
    val imageUrl: String,
    val likesCount: Int,
    val date: String,
)

@Composable
fun HeartIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = Color(0xFFFF4081),
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this@Canvas.size.width
        val path = Path().apply {
            moveTo(s * 0.5f, s * 0.8f)
            cubicTo(s * 0.5f, s * 0.8f, s * 0.1f, s * 0.5f, s * 0.1f, s * 0.3f)
            cubicTo(s * 0.1f, s * 0.15f, s * 0.25f, s * 0.05f, s * 0.4f, s * 0.15f)
            cubicTo(s * 0.5f, s * 0.22f, s * 0.5f, s * 0.22f, s * 0.5f, s * 0.22f)
            cubicTo(s * 0.5f, s * 0.22f, s * 0.5f, s * 0.22f, s * 0.6f, s * 0.15f)
            cubicTo(s * 0.75f, s * 0.05f, s * 0.9f, s * 0.15f, s * 0.9f, s * 0.3f)
            cubicTo(s * 0.9f, s * 0.5f, s * 0.5f, s * 0.8f, s * 0.5f, s * 0.8f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
fun VibeHeader(
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onNewVibeClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF161618))
                .clickable(onClick = onHomeClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "HOME",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }

        Text(
            text = "Users(P/B), CENTER",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .border(2.dp, Color(0xFFFFD700), CircleShape)
                .padding(2.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E22)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "P/B",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .clickable(onClick = onNewVibeClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "new vibe :)",
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun VibeCard(
    vibeItem: VibeItem,
    modifier: Modifier = Modifier,
    onDeleteClick: (String) -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = vibeItem.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f),
                            ),
                            startY = 150f,
                        )
                    ),
            )

            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HeartIcon(size = 14.dp, tint = Color(0xFFFF4081))
                    Text(
                        text = vibeItem.likesCount.toString(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = vibeItem.date,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )

                Button(
                    onClick = { onDeleteClick(vibeItem.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, Color(0xFFE53935).copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3D2220).copy(alpha = 0.85f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "DELETE",
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun UserVibeScreen(
    vibeItems: List<VibeItem>,
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onNewVibeClick: () -> Unit = {},
    onDeleteClick: (String) -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            VibeHeader(
                onHomeClick = onHomeClick,
                onNewVibeClick = onNewVibeClick,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vibeItems) { item ->
                    VibeCard(
                        vibeItem = item,
                        onDeleteClick = onDeleteClick,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun UserVibeScreenPreview() {
    BaronessAppTheme {
        val sampleVibes = listOf(
            VibeItem(
                id = "1",
                imageUrl = "https://images.unsplash.com/photo-1477959858617-67f30bc75b82",
                likesCount = 3,
                date = "13 MAY 2026",
            ),
            VibeItem(
                id = "2",
                imageUrl = "https://images.unsplash.com/photo-1432405972618-fb60b0ceb06f",
                likesCount = 1,
                date = "02 JULY 2026",
            ),
        )
        UserVibeScreen(vibeItems = sampleVibes)
    }
}
