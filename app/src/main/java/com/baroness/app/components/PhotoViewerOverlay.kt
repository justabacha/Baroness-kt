package com.baroness.app.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.baroness.app.models.PhotoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerOverlay(
    flatList: List<PhotoItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { flatList.size }
    val coroutineScope = rememberCoroutineScope()
    var immersiveMode by remember { mutableStateOf(false) }

    // Mock favorite state per photo item id to make interaction alive
    val favorites = remember { mutableStateMapOf<String, Boolean>() }

    // Dialog state for showing Photo Info
    var showInfoDialog by remember { mutableStateOf<PhotoItem?>(null) }

    val currentPhoto = flatList.getOrNull(pagerState.currentPage)

    // Backdrop background color that animates transparency when swiping down to dismiss
    var backgroundAlpha by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
    ) {
        // Horizontal pager displaying pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1
        ) { page ->
            val photo = flatList[page]
            PhotoViewerItem(
                photo = photo,
                isActivePage = pagerState.currentPage == page,
                onToggleImmersive = { immersiveMode = !immersiveMode },
                onDismiss = onDismiss,
                onDragAlphaChange = { alpha -> backgroundAlpha = alpha }
            )
        }

        // Top App Bar
        AnimatedVisibility(
            visible = !immersiveMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Gallery",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentPhoto?.title ?: "Photo",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} / ${flatList.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Bottom Navigation Bar with Actions
        AnimatedVisibility(
            visible = !immersiveMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp, horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Mock Button
                    IconButton(onClick = { /* Share stub */ }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }

                    // Favorite Button
                    IconButton(
                        onClick = {
                            currentPhoto?.let {
                                val currentFav = favorites[it.id] ?: false
                                favorites[it.id] = !currentFav
                            }
                        }
                    ) {
                        val isFav = currentPhoto?.let { favorites[it.id] } ?: false
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) Color.Red else Color.White
                        )
                    }

                    // Information Button
                    IconButton(
                        onClick = { showInfoDialog = currentPhoto }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Information",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Information Modal Dialog
        showInfoDialog?.let { photo ->
            AlertDialog(
                onDismissRequest = { showInfoDialog = null },
                title = { Text(text = photo.title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Date: ${photo.dateString}", style = MaterialTheme.typography.bodyMedium)
                        photo.location?.let {
                            Text(text = "Location: $it", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(text = "Source: Unsplash Photography", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun PhotoViewerItem(
    photo: PhotoItem,
    isActivePage: Boolean,
    onToggleImmersive: () -> Unit,
    onDismiss: () -> Unit,
    onDragAlphaChange: (Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Scroll offset for drag-down-to-dismiss
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }

    // Reset zoom state when user navigates to another page
    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            scale = 1f
            offset = Offset.Zero
            dismissOffsetY = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(scale) {
                // Handle vertical drag to dismiss when scale is 1.0 (unzoomed)
                if (scale <= 1f) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dismissOffsetY += dragAmount.y
                            // Calculate background alpha based on vertical offset
                            val alpha = (1f - (kotlin.math.abs(dismissOffsetY) / (containerSize.height / 2f))).coerceIn(0f, 1f)
                            onDragAlphaChange(alpha)
                        },
                        onDragEnd = {
                            if (dismissOffsetY > 250f || dismissOffsetY < -250f) {
                                onDismiss()
                            } else {
                                // Reset position
                                coroutineScope.launch {
                                    animate(dismissOffsetY, 0f) { value, _ ->
                                        dismissOffsetY = value
                                        val alpha = (1f - (kotlin.math.abs(dismissOffsetY) / (containerSize.height / 2f))).coerceIn(0f, 1f)
                                        onDragAlphaChange(alpha)
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            dismissOffsetY = 0f
                            onDragAlphaChange(1f)
                        }
                    )
                }
            }
            .pointerInput(Unit) {
                // Taps & double taps
                detectTapGestures(
                    onDoubleTap = { position ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            // Center zoom simple reset
                            offset = Offset.Zero
                        }
                    },
                    onTap = {
                        onToggleImmersive()
                    }
                )
            }
            .pointerInput(scale) {
                // Pinch to zoom and pan gestures
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale > 1f) {
                        // Max boundaries for panning to keep image in view
                        val maxOffsetX = (containerSize.width * (scale - 1)) / 2
                        val maxOffsetY = (containerSize.height * (scale - 1)) / 2
                        offset = Offset(
                            x = (offset.x + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX),
                            y = (offset.y + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.url + "?auto=format&fit=crop&w=1200&q=85")
                .crossfade(true)
                .build(),
            contentDescription = photo.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y + dismissOffsetY
                }
        )
    }
}
