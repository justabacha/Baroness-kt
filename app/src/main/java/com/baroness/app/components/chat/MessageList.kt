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
import androidx.compose.ui.unit.dp
import com.baroness.app.models.Message

@Composable
fun MessageList(
    messages: List<Message>,
    currentPersonaId: String,
    activeThemeId: String = "lavender",
    modifier: Modifier = Modifier,
    onLongPress: (Message, IntOffset) -> Unit
) {
    val listState = rememberLazyListState()

    // Scroll to bottom (index 0 in reverseLayout) when a new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            MessageBubble(
                message = message,
                isOwn = message.senderId == currentPersonaId,
                activeThemeId = activeThemeId,
                onLongPress = onLongPress
            )
        }
    }
}
