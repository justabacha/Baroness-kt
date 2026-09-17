package com.baroness.app.components.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.ui.theme.ChatTypography
import com.baroness.app.ui.theme.rememberChatTypography
import com.baroness.app.viewmodels.SettingsViewModel
import com.baroness.app.components.chat.actions.ChatCommunicationActions
import com.baroness.app.components.chat.actions.ChatFridayActions
import com.baroness.app.components.chat.actions.ChatRepositoryActions
import com.baroness.app.components.chat.actions.ChatUtilityActions
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect

@Composable
fun ChatContextMenu(
    message: Message,
    isOwn: Boolean,
    offset: IntOffset,
    bubbleSize: IntSize,
    activeThemeId: String,
    participant: Participant? = null,
    settingsViewModel: SettingsViewModel? = null,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onTranslate: () -> Unit,
    onTogglePin: (Message) -> Unit,
    onShowEmojiPicker: () -> Unit
) {
    var isLaunched by remember { mutableStateOf(false) }
    var isMoreOpen by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    BackHandler {
        if (isMoreOpen) {
            isMoreOpen = false
        } else {
            onDismiss()
        }
    }
    
    LaunchedEffect(Unit) { 
        isLaunched = true 
        while(true) {
            delay(10000) // Update every 10s for window accuracy
            currentTime = System.currentTimeMillis()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isLaunched && !isMoreOpen) 1.05f else if (isMoreOpen) 0.8f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "scale"
    )
    
    val menuAlpha by animateFloatAsState(
        targetValue = if (isMoreOpen) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "menuAlpha"
    )

    val menuShift by animateFloatAsState(
        targetValue = if (isMoreOpen) -100f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "menuShift"
    )

    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val spaceAbove = offset.y.toFloat()
    val spaceBelow = screenHeightPx - (offset.y + bubbleSize.height).toFloat()
    
    // Thresholds matching components size estimates
    val isTopCollision = spaceAbove < with(density) { (56 + 48).dp.toPx() }
    val isBottomCollision = !isTopCollision && spaceBelow < with(density) { 240.dp.toPx() }

    var tapbackHeight by remember { mutableIntStateOf(0) }
    var menuHeight by remember { mutableIntStateOf(0) }
    var columnWidth by remember { mutableIntStateOf(0) }
    var isMeasured by remember { mutableStateOf(false) }
    
    val gapPx = with(density) { 8.dp.roundToPx() }
    
    // Temporal Logic
    val timeElapsed = currentTime - message.timestamp
    val isWithinUndoWindow = timeElapsed < 2 * 60 * 1000 // 2 minutes
    val isWithinEditWindow = timeElapsed < 10 * 60 * 1000 // 10 minutes

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // 1. Background Blur Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurEffect {
                                blurRadius = if (isMoreOpen) 24.dp else 8.dp
                                colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = if (isMoreOpen) 0.6f else 0.45f))) 
                            }
                        }
                    } else {
                        Modifier.background(Color.Black.copy(alpha = if (isMoreOpen) 0.6f else 0.4f))
                    }
                )
        )

        // 2. Focused Content Column
        Column(
            modifier = Modifier
                .onGloballyPositioned { 
                    columnWidth = it.size.width
                    isMeasured = true 
                }
                .graphicsLayer {
                    // Alpha shield to prevent measurement jump + Menu Fade
                    alpha = (if (isMeasured) 1f else 0f) * menuAlpha
                    scaleX = scale
                    scaleY = scale
                    
                    translationX = if (isOwn) {
                        (offset.x + bubbleSize.width - columnWidth).toFloat()
                    } else {
                        offset.x.toFloat()
                    }
                    
                    translationY = when {
                        isTopCollision -> {
                            offset.y.toFloat()
                        }
                        isBottomCollision -> {
                            (offset.y - menuHeight - gapPx).toFloat()
                        }
                        else -> {
                            (offset.y - tapbackHeight - gapPx).toFloat()
                        }
                    } + menuShift
                },
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tapbackBarBlock = @Composable {
                TapbackBar(
                    settingsViewModel = settingsViewModel,
                    onReact = { emoji ->
                        settingsViewModel?.onEmojiUsed(emoji)
                        onReact(emoji)
                    },
                    onShowEmojiPicker = onShowEmojiPicker,
                    hazeState = hazeState,
                    modifier = Modifier.onGloballyPositioned { tapbackHeight = it.size.height }
                )
            }

            val messageBubbleBlock = @Composable {
                MessageBubble(
                    message = message,
                    isOwn = isOwn,
                    participant = null,
                    settingsViewModel = settingsViewModel,
                    activeThemeId = activeThemeId,
                    hazeState = null, // Sharp
                    isFocusedMode = true,
                    modifier = Modifier.padding(horizontal = 0.dp)
                )
            }

            val actionMenuBlock = @Composable {
                ActionMenu(
                    message = message,
                    isOwn = isOwn,
                    isWithinEditWindow = isWithinEditWindow,
                    isWithinUndoWindow = isWithinUndoWindow,
                    settingsViewModel = settingsViewModel,
                    hazeState = hazeState,
                    onReply = onReply,
                    onCopy = onCopy,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onInfo = onInfo,
                    onTranslate = onTranslate,
                    onMore = { isMoreOpen = true },
                    modifier = Modifier.onGloballyPositioned { menuHeight = it.size.height }
                )
            }

            // Inversion Layout Selector
            when {
                isTopCollision -> {
                    messageBubbleBlock()
                    tapbackBarBlock()
                    actionMenuBlock()
                }
                isBottomCollision -> {
                    actionMenuBlock()
                    messageBubbleBlock()
                    tapbackBarBlock()
                }
                else -> {
                    tapbackBarBlock()
                    messageBubbleBlock()
                    actionMenuBlock()
                }
            }
        }
        
        // 3. Obsidian Sheet (70% Bottom Modal)
        AnimatedVisibility(
            visible = isMoreOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ObsidianSheet(
                message = message,
                hazeState = hazeState,
                settingsViewModel = settingsViewModel,
                onDismiss = { isMoreOpen = false },
                onTogglePin = onTogglePin,
                onTranslate = onTranslate
            )
        }
    }
}


@Composable
private fun ActionMenu(
    message: Message,
    isOwn: Boolean,
    isWithinEditWindow: Boolean = true,
    isWithinUndoWindow: Boolean = true,
    settingsViewModel: SettingsViewModel? = null,
    hazeState: HazeState? = null,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onTranslate: () -> Unit,
    onMore: () -> Unit = {},
    modifier: Modifier = Modifier,
    onReply: () -> Unit
) {
    val typography = rememberChatTypography(settingsViewModel)
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .width(200.dp)
            .border(1.5.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(0.5.dp)
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 15.dp
                            colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.8f)))
                        }
                    }
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.8f))
                }
            )
    ) {
        // Slot 1: Reply
        ActionItem(
            text = "Reply", 
            icon = Icons.AutoMirrored.Filled.Reply, 
            onClick = { 
                ChatCommunicationActions.reply(message)
                onReply()
            },
            typography = typography
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        // Slot 2: Copy
        ActionItem(
            text = "Copy", 
            icon = Icons.Default.ContentCopy, 
            onClick = { 
                ChatUtilityActions.copy(context, message)
                onCopy() 
            }, 
            typography = typography
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        // Slot 3: Edit or Message Info
        if (isOwn) {
            AnimatedContent(targetState = isWithinEditWindow, label = "editMorph") { withinWindow ->
                if (withinWindow) {
                    ActionItem(
                        text = "Edit", 
                        icon = Icons.Default.Edit, 
                        onClick = { 
                            ChatCommunicationActions.edit(message)
                            onEdit() 
                        }, 
                        typography = typography
                    )
                } else {
                    ActionItem(
                        text = "Message Info", 
                        icon = Icons.Default.Info, 
                        onClick = { 
                            ChatRepositoryActions.messageInfo(message)
                            onInfo()
                        },
                        typography = typography
                    )
                }
            }
        } else {
            ActionItem(
                text = "Message Info", 
                icon = Icons.Default.Info, 
                onClick = { 
                    ChatRepositoryActions.messageInfo(message)
                    onInfo()
                },
                typography = typography
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        // Slot 4: Unsend or Delete
        if (isOwn) {
            AnimatedContent(targetState = isWithinUndoWindow, label = "undoMorph") { withinWindow ->
                if (withinWindow) {
                    ActionItem(
                        text = "Unsend", 
                        icon = Icons.AutoMirrored.Filled.Reply, 
                        onClick = { 
                            ChatCommunicationActions.unsend(context, message)
                            onDelete()
                        }, 
                        typography = typography,
                        isDestructive = true 
                    )
                } else {
                    ActionItem(
                        text = "Delete", 
                        icon = Icons.Default.Delete, 
                        onClick = { 
                            onDelete()
                        }, 
                        typography = typography,
                        isDestructive = true
                    )
                }
            }
        } else {
            ActionItem(
                text = "Delete", 
                icon = Icons.Default.Delete, 
                onClick = { 
                    ChatCommunicationActions.delete(context, message)
                    onDelete()
                }, 
                typography = typography,
                isDestructive = true
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        // Slot 5: More
        ActionItem(text = "More...", icon = Icons.Default.MoreVert, onClick = onMore, typography = typography)
    }
}

@Composable
private fun ObsidianSheet(
    message: Message,
    hazeState: HazeState?,
    settingsViewModel: SettingsViewModel?,
    onDismiss: () -> Unit,
    onTogglePin: (Message) -> Unit,
    onTranslate: () -> Unit
) {
    val typography = rememberChatTypography(settingsViewModel)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val context = LocalContext.current
    val sheetHeight = configuration.screenHeightDp.dp * 0.7f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .border(1.5.dp, Color.Black.copy(alpha = 0.8f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .padding(0.5.dp)
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            blurRadius = 20.dp
                            colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.85f)))
                        }
                    }
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.85f))
                }
            )
            .padding(24.dp)
    ) {
        // Handle Bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "More Options",
            style = typography.title.copy(fontSize = 20.sp),
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SheetItem("Ask Friday", Icons.Default.AutoAwesome, typography) { 
                ChatFridayActions.askFriday(message)
            }
            SheetItem("Star", Icons.Default.Star, typography) { 
                ChatRepositoryActions.star(context, message)
            }
            SheetItem(if (message.isPinned) "Unpin Message" else "Pin Message", Icons.Default.PushPin, typography) { 
                onTogglePin(message)
            }
            SheetItem("Translate", Icons.Default.Translate, typography) { 
                ChatUtilityActions.translate(message)
                onTranslate()
            }
            SheetItem("Remind Me", Icons.Default.Notifications, typography) { 
                ChatRepositoryActions.remindMe(message)
            }
            SheetItem("Search Within Chat", Icons.Default.Search, typography) { 
                ChatUtilityActions.searchWithinChat(message)
            }
            SheetItem("Share", Icons.Default.Share, typography) { 
                ChatUtilityActions.share(message)
            }
            SheetItem("Read Aloud", Icons.Default.VolumeUp, typography) { 
                ChatUtilityActions.readAloud(message)
            }
            SheetItem("Create a Wish", Icons.Default.Event, typography) { 
                ChatRepositoryActions.createWish(context, message)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetItem(
    text: String,
    icon: ImageVector,
    typography: ChatTypography,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = typography.body,
            color = Color.White
        )
    }
}

@Composable
private fun ActionItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    typography: ChatTypography,
    isDestructive: Boolean = false
) {
    val glyphShadow = Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = typography.body.copy(shadow = glyphShadow),
            color = if (isDestructive) Color(0xFFFF4D4D) else Color.White
        )
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isDestructive) Color(0xFFFF4D4D) else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
