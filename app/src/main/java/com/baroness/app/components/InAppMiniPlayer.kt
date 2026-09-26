package com.baroness.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.baroness.app.media.BaronessPlayerManager
import com.baroness.app.media.BaronessRepeatMode
import com.baroness.app.models.SettingsOptions
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.SettingsViewModel
import com.baroness.app.viewmodels.SettingsViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppMiniPlayer(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel? = null
) {
    val context = LocalContext.current
    val effectiveSettingsViewModel: SettingsViewModel = settingsViewModel ?: viewModel(
        factory = SettingsViewModelFactory(context)
    )
    val activeThemeId by effectiveSettingsViewModel.activeTheme.collectAsStateWithLifecycle()
    val activeTheme = remember(activeThemeId) {
        SettingsOptions.themes.find { it.id == activeThemeId } ?: SettingsOptions.LavenderTheme
    }

    val playerManager = remember { BaronessPlayerManager.getInstance(context) }
    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isShuffleEnabled by playerManager.isShuffleEnabled.collectAsState()
    val repeatMode by playerManager.repeatMode.collectAsState()
    val currentPositionMs by playerManager.currentPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val currentQueue by playerManager.currentQueue.collectAsState()

    val typography = rememberChatTypography(effectiveSettingsViewModel)
    var showQueueSheet by remember { mutableStateOf(false) }

    val accentColor = activeTheme.glowColor
    val cardBgColor = activeTheme.bgStart.copy(alpha = 0.95f)

    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        val song = currentSong ?: return@AnimatedVisibility

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = cardBgColor,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Top Close Button
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = { playerManager.stop() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Player",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Album Art + Song Title + Artist
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(100.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.albumArtUri != null) {
                                AsyncImage(
                                    model = song.albumArtUri,
                                    contentDescription = "Album Art",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Music",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        PhestyText(
                            text = song.title,
                            style = typography.title.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )

                        PhestyText(
                            text = song.artist,
                            style = typography.meta.copy(fontSize = 11.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Right Column: Waveform + Progress Slider + Controls
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Waveform Sound Bar Visualizer
                        AudioWaveformVisualizer(
                            isPlaying = isPlaying,
                            height = 26.dp,
                            activeColor = accentColor
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Progress Slider
                        val sliderPosition = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f
                        Slider(
                            value = sliderPosition.coerceIn(0f, 1f),
                            onValueChange = { percent ->
                                val targetMs = (percent * durationMs).toLong()
                                playerManager.seekToPosition(targetMs)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                        )

                        // Timestamps Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            PhestyText(
                                text = formatTimeMs(currentPositionMs),
                                style = typography.meta.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            PhestyText(
                                text = formatTimeMs(durationMs),
                                style = typography.meta.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Control Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Repeat Mode Button (3-state: OFF -> REPEAT ONE (1) -> REPEAT ALL)
                            IconButton(
                                onClick = { playerManager.toggleRepeatMode() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                val repeatIcon = if (repeatMode == BaronessRepeatMode.ONE) {
                                    Icons.Default.RepeatOne
                                } else {
                                    Icons.Default.Repeat
                                }
                                val repeatTint = if (repeatMode != BaronessRepeatMode.OFF) {
                                    accentColor
                                } else {
                                    Color.White.copy(alpha = 0.4f)
                                }
                                Icon(
                                    imageVector = repeatIcon,
                                    contentDescription = "Repeat Mode",
                                    tint = repeatTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Previous Track
                            IconButton(
                                onClick = { playerManager.skipPrevious() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Track",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Circular Outline Ring Play / Pause
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .border(1.5.dp, accentColor, CircleShape)
                                    .clip(CircleShape)
                                    .clickable { playerManager.playPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Next Track
                            IconButton(
                                onClick = { playerManager.skipNext() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Track",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Shuffle Button
                            IconButton(
                                onClick = { playerManager.toggleShuffle() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (isShuffleEnabled) accentColor else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Queue Sheet Button
                            IconButton(
                                onClick = { showQueueSheet = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = "Song Queue",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQueueSheet) {
        SongQueueSheet(
            queue = currentQueue,
            currentSong = currentSong,
            isPlaying = isPlaying,
            settingsViewModel = effectiveSettingsViewModel,
            onSongSelected = { index ->
                playerManager.seekToItem(index)
                showQueueSheet = false
            },
            onDismiss = { showQueueSheet = false }
        )
    }
}

private fun formatTimeMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
