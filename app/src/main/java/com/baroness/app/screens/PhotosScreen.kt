package com.baroness.app.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.baroness.app.components.PhotoViewerOverlay
import com.baroness.app.models.PhotoItem
import com.baroness.app.viewmodels.PhotosUiState
import com.baroness.app.viewmodels.PhotosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    navController: NavController,
    viewModel: PhotosViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPhotoIndex by viewModel.selectedPhotoIndex.collectAsState()

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Photos",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        if (uiState is PhotosUiState.Success) {
                            val total = (uiState as PhotosUiState.Success).totalCount
                            Text(
                                text = "$total Photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is PhotosUiState.Loading -> {
                    PhotosGridPlaceholder()
                }
                is PhotosUiState.Error -> {
                    PhotosErrorView(message = state.message, onRetry = { viewModel.loadPhotos() })
                }
                is PhotosUiState.Success -> {
                    PhotosGrid(
                        groupedPhotos = state.groupedPhotos,
                        flatList = state.flatList,
                        onPhotoClick = { index -> viewModel.selectPhoto(index) }
                    )
                }
            }

            // Photo Viewer Overlay
            if (uiState is PhotosUiState.Success) {
                val successState = uiState as PhotosUiState.Success
                selectedPhotoIndex?.let { index ->
                    PhotoViewerOverlay(
                        flatList = successState.flatList,
                        initialIndex = index,
                        onDismiss = { viewModel.selectPhoto(null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotosGrid(
    groupedPhotos: Map<String, List<PhotoItem>>,
    flatList: List<PhotoItem>,
    onPhotoClick: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groupedPhotos.forEach { (sectionHeader, photos) ->
            // Section Header spanning full line
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = sectionHeader,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .fillMaxWidth()
                )
            }

            // Grid items for the section
            itemsIndexed(photos) { _, photo ->
                val flatIndex = flatList.indexOfFirst { it.id == photo.id }
                PhotoThumbnail(
                    photo = photo,
                    onClick = { if (flatIndex != -1) onPhotoClick(flatIndex) }
                )
            }
        }

        // Bottom space to clear the navigation bar smoothly
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photo: PhotoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isImageLoaded by remember { mutableStateOf(false) }
    val shimmerBrush = rememberShimmerBrush()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        // Shimmer placeholder if not loaded yet
        if (!isImageLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(shimmerBrush)
            )
        }

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photo.url + "?auto=format&fit=crop&w=350&q=80") // Optimize width for thumbnails
                .crossfade(true)
                .build(),
            contentDescription = photo.title,
            contentScale = ContentScale.Crop,
            onSuccess = { isImageLoaded = true },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PhotosGridPlaceholder() {
    val shimmerBrush = rememberShimmerBrush()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false
    ) {
        // Sections & dummy items
        repeat(3) { sectionIdx ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .width(120.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
            items(if (sectionIdx == 0) 4 else if (sectionIdx == 1) 3 else 8) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
private fun PhotosErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Failed to load photos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim)
    )
}
