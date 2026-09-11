package com.baroness.app.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.baroness.app.components.DynamicBackground
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
import java.io.File
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect

@Composable
fun ChatRoomScreen(
    navController: NavController,
    conversationId: String,
    settingsViewModel: SettingsViewModel
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
    
    val storageManager = remember { com.baroness.app.utils.StorageManager(context) }
    val currentPersonaId by produceState(initialValue = "phesty_official") {
        value = storageManager.getString("currentPersonaId") ?: "phesty_official"
    }

    val chatTypography = rememberChatTypography(settingsViewModel)
    val clipboardManager = LocalClipboardManager.current
    val hazeState = remember { HazeState() }
    
    var contextMenuMessage by remember { mutableStateOf<Message?>(null) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle(initialValue = true)
    val warningMessage by settingsViewModel.warningMessage.collectAsStateWithLifecycle()
    val isWarningVisible by settingsViewModel.isWarningVisible.collectAsStateWithLifecycle()
    val showWarningIcon by settingsViewModel.showWarningIcon.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val fadeHorizonHeight = 115.dp
    val fadeHorizonPx = with(density) { fadeHorizonHeight.toPx() }

    LaunchedEffect(isSubscribed) {
        if (!isSubscribed) {
            settingsViewModel.showWarning("Connecting to live chat...")
        } else {
            settingsViewModel.dismissWarning()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DynamicBackground(activeWallpaperId = activeWallpaperId, dimmed = false, hazeState = hazeState)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Empty topBar to allow unbounded content
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding() // Resizes content area for keyboard
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.60f to Color.Transparent, // Vanish point slightly higher for better duration
                                0.88f to Color.Black.copy(alpha = 0.4f), // Smooth liquid curve
                                1.0f to Color.Black,
                                startY = 0f,
                                endY = fadeHorizonPx
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
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
                                otherParticipant = otherParticipant,
                                settingsViewModel = settingsViewModel,
                                hazeState = hazeState,
                                activeThemeId = activeThemeId,
                                contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp), // Space for floating island
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

                // Floating Dynamic Island (Input Bar)
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    if (isOtherTyping && otherParticipant != null) {
                        TypingIndicator(
                            displayName = otherParticipant!!.displayName,
                            settingsViewModel = settingsViewModel
                        )
                    }
                    ChatInput(
                        onSendMessage = { viewModel.onSendMessage(it) },
                        onAttachmentClick = { /* Coming Soon */ },
                        settingsViewModel = settingsViewModel,
                        hazeState = hazeState,
                        activeThemeId = activeThemeId
                    )
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
                settingsViewModel = settingsViewModel,
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

        // Floating Ghost Header (Zero-Surface)
        ChatTopBar(
            participant = otherParticipant,
            onBack = { navController.popBackStack() },
            typography = chatTypography,
            modifier = Modifier.statusBarsPadding(),
            onTestInject = {
                if (viewModel is com.baroness.app.viewmodels.HumanChatViewModel) {
                    (viewModel as com.baroness.app.viewmodels.HumanChatViewModel)
                        .onInjectTestMessage("Crystal Glass check! 💎✨")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    participant: Participant?,
    onBack: () -> Unit,
    typography: ChatTypography,
    modifier: Modifier = Modifier,
    onTestInject: (() -> Unit)? = null
) {
    // Sharp, crisp text outline shadow for localized contrast
    val textOutlineShadow = Shadow(
        color = Color.Black,
        offset = Offset(1f, 2f),
        blurRadius = 4f
    )

    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets(0),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar with crisp dual-border tracing for separation on any wallpaper
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.5.dp, Color.Black.copy(alpha = 0.75f), CircleShape)
                        .padding(0.5.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
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
                        style = typography.title.copy(shadow = textOutlineShadow),
                        color = Color.White
                    )
                    Text(
                        text = if (participant?.id == "friday") "Friday AI" else "Online",
                        style = typography.meta.copy(shadow = textOutlineShadow),
                        color = Color.White.copy(alpha = 0.9f)
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
        actions = {
            if (onTestInject != null) {
                IconButton(onClick = onTestInject) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Inject Mock Message",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
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
