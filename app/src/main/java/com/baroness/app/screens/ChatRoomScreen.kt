package com.baroness.app.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.baroness.app.R
import com.baroness.app.components.EmojiPicker
import com.baroness.app.components.TopWarningBanner
import com.baroness.app.components.WallpaperOption
import com.baroness.app.components.WallpaperSource
import com.baroness.app.components.chat.*
import com.baroness.app.components.prebundledWallpapers
import com.baroness.app.models.ChatRoomUiState
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.ui.theme.ChatTypography
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.ChatRoomViewModel
import com.baroness.app.viewmodels.ChatRoomViewModelFactory
import com.baroness.app.viewmodels.SettingsViewModel
import com.baroness.app.viewmodels.SettingsViewModelFactory
import java.io.File
import kotlinx.coroutines.flow.map

@Composable
fun ChatRoomScreen(
    navController: NavController,
    conversationId: String,
    settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val viewModel: ChatRoomViewModel = viewModel(
        factory = ChatRoomViewModelFactory(context, conversationId)
    )
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val otherParticipant by viewModel.otherParticipant.collectAsStateWithLifecycle()
    val isOtherTyping by viewModel.isTyping.collectAsStateWithLifecycle()
    val activeWallpaperId by settingsViewModel.activeWallpaper.collectAsStateWithLifecycle()
    val activeThemeId by settingsViewModel.activeTheme.collectAsStateWithLifecycle()
    
    // In a real app, this would be fetched from a UserSessionManager or AuthRepository
    val currentPersonaId = "phesty_official" 

    val chatTypography = rememberChatTypography(settingsViewModel)
    val clipboardManager = LocalClipboardManager.current
    
    var contextMenuMessage by remember { mutableStateOf<Message?>(null) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle(initialValue = true)
    val warningMessage by settingsViewModel.warningMessage.collectAsStateWithLifecycle()
    val isWarningVisible by settingsViewModel.isWarningVisible.collectAsStateWithLifecycle()
    val showWarningIcon by settingsViewModel.showWarningIcon.collectAsStateWithLifecycle()

    LaunchedEffect(isSubscribed) {
        if (!isSubscribed) {
            settingsViewModel.showWarning("Connecting to live chat...")
        } else {
            settingsViewModel.dismissWarning()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DynamicBackground(activeWallpaperId = activeWallpaperId)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ChatTopBar(
                    participant = otherParticipant,
                    onBack = { navController.popBackStack() },
                    typography = chatTypography
                )
            },
            bottomBar = {
                Column {
                    if (isOtherTyping && otherParticipant != null) {
                        TypingIndicator(displayName = otherParticipant!!.displayName)
                    }
                    ChatInput(
                        onSendMessage = { viewModel.onSendMessage(it) },
                        onAttachmentClick = { /* Coming Soon */ }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val state = uiState) {
                    is ChatRoomUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    is ChatRoomUiState.Success -> {
                        if (state.messages.isEmpty()) {
                            EmptyChatState(
                                participantName = otherParticipant?.displayName ?: "someone",
                                typography = chatTypography
                            )
                        } else {
                            MessageList(
                                messages = state.messages,
                                currentPersonaId = currentPersonaId,
                                activeThemeId = activeThemeId,
                                onLongPress = { msg, offset ->
                                    contextMenuMessage = msg
                                    contextMenuOffset = offset
                                }
                            )
                        }
                    }
                    is ChatRoomUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = state.message, color = Color.Red)
                        }
                    }
                }
            }
        }

        TopWarningBanner(
            visible = isWarningVisible,
            message = warningMessage ?: "",
            showIcon = showWarningIcon,
            onDismiss = { settingsViewModel.dismissWarning() }
        )

        // Context Menu Overlay
        contextMenuMessage?.let { message ->
            ChatContextMenu(
                message = message,
                isOwn = message.senderId == currentPersonaId,
                offset = contextMenuOffset,
                activeThemeId = activeThemeId,
                onDismiss = { contextMenuMessage = null },
                onReact = { emoji ->
                    viewModel.onReactToMessage(message, emoji)
                    contextMenuMessage = null
                },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(message.content))
                    contextMenuMessage = null
                },
                onEdit = {
                    viewModel.onEditMessage(message, message.content)
                    contextMenuMessage = null
                },
                onDelete = {
                    viewModel.onDeleteMessage(message)
                    contextMenuMessage = null
                },
                onShowEmojiPicker = {
                    showEmojiPicker = true
                }
            )
        }

        EmojiPicker(
            visible = showEmojiPicker,
            onDismiss = { showEmojiPicker = false },
            onEmojiSelected = { emoji ->
                contextMenuMessage?.let { viewModel.onReactToMessage(it, emoji) }
                showEmojiPicker = false
                contextMenuMessage = null
            }
        )
    }
}

@Composable
fun DynamicBackground(activeWallpaperId: String) {
    val context = LocalContext.current
    val wallpaper = remember(activeWallpaperId) {
        val prebundled = prebundledWallpapers.find { it.id == activeWallpaperId }
        if (prebundled != null) prebundled else {
            val userFile = File(context.filesDir, "wallpapers/user_wallpaper.jpg")
            if (userFile.exists() && activeWallpaperId == "user_wallpaper") {
                WallpaperOption("user_wallpaper", "Custom", WallpaperSource.USER_GALLERY, filePath = userFile.absolutePath)
            } else {
                prebundledWallpapers.first()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
        // Dark Overlay Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    participant: Participant?,
    onBack: () -> Unit,
    typography: ChatTypography
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    if (participant?.avatarUrl != null) {
                        AsyncImage(
                            model = participant.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (participant?.id == "friday") {
                        AsyncImage(
                            model = "https://img.icons8.com/fluency/48/artificial-intelligence.png",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = participant?.displayName ?: "Loading...",
                        style = typography.title,
                        color = Color.White
                    )
                    Text(
                        text = if (participant?.id == "friday") "Friday AI" else "Online",
                        style = typography.meta,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.2f),
            titleContentColor = Color.White
        )
    )
}

@Composable
fun EmptyChatState(
    participantName: String,
    typography: ChatTypography
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Start your conversation with $participantName...",
            style = typography.body,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
