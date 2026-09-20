package com.baroness.app.viewmodels

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.baroness.app.models.ChatRoomUiState
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

private const val TAG = "FridayVM"

class FridayChatViewModel(
    private val repository: ChatRepository
) : ChatRoomViewModel() {

    private val _otherParticipant = MutableStateFlow<Participant?>(
        Participant(
            id = "friday",
            displayName = "FRIDAY",
            avatarUrl = "https://img.icons8.com/fluency/48/artificial-intelligence.png",
            isOnline = null
        )
    )
    override val otherParticipant: StateFlow<Participant?> = _otherParticipant.asStateFlow()

    override val isTyping: StateFlow<Boolean> = repository.isFridayTyping

    override val isSubscribed: StateFlow<Boolean> = repository.isSubscribed

    override val uiState: StateFlow<ChatRoomUiState> = repository.getMessages("friday")
        .map { messages -> ChatRoomUiState.Success(messages) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChatRoomUiState.Loading
        )

    override val pinnedMessages: StateFlow<List<Message>> = repository.getPinnedMessages("friday")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Observe typing and other states if needed
    }

    override fun onSendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            // Send user message - the rest is handled by the backend orchestrator via Supabase triggers
            repository.sendMessage("friday", text)
        }
    }

    override fun onDeleteMessageForMe(message: Message) {
        viewModelScope.launch {
            repository.deleteMessageForMe(message.id)
        }
    }

    override fun onDeleteMessageForEveryone(message: Message) {
        viewModelScope.launch {
            repository.deleteMessageForEveryone(message.id)
        }
    }

    override fun onEditMessage(message: Message, newContent: String) {
        viewModelScope.launch {
            repository.editMessage(message.id, newContent)
        }
    }

    override fun onReplyMessage(text: String, replyTo: Message) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(
                conversationId = "friday",
                content = text,
                replyToId = replyTo.id,
                replyToContent = replyTo.content,
                replyToSenderId = replyTo.senderId
            )
        }
    }

    override fun onReactToMessage(message: Message, emoji: String) {
        viewModelScope.launch {
            repository.reactToMessage(message.id, emoji)
        }
    }

    override fun onTogglePinMessage(message: Message) {
        viewModelScope.launch {
            repository.togglePinMessage(message.id)
        }
    }

    override fun setUserTyping(isTyping: Boolean) {
        // User typing for AI doesn't need to be broadcasted to server for now
    }
}
