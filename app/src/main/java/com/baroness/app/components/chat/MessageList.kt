package com.baroness.app.components.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.viewmodels.SettingsViewModel
import dev.chrisbanes.haze.HazeState

@Composable
fun MessageList(
    messages: List<Message>,
    currentPersonaId: String,
    otherParticipant: Participant? = null,
    settingsViewModel: SettingsViewModel? = null,
    hazeState: HazeState? = null,
    activeThemeId: String = "lavender",
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp, top = 8.dp),
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    highlightedMessageId: String? = null,
    onLongPress: (Message, IntOffset, IntSize) -> Unit,
    onReactionClick: ((Message, IntOffset, IntSize) -> Unit)? = null,
    onReplyClick: ((String) -> Unit)? = null
) {
    // Scroll to bottom (index 0 in reverseLayout) when a new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex < 2) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = contentPadding
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            MessageBubble(
                message = message,
                isOwn = message.senderId == currentPersonaId,
                participant = otherParticipant,
                settingsViewModel = settingsViewModel,
                hazeState = hazeState,
                activeThemeId = activeThemeId,
                isHighlighted = message.id == highlightedMessageId,
                onLongPress = onLongPress,
                onReactionClick = onReactionClick,
                onReplyClick = onReplyClick
            )
        }
    }
}
