package com.baroness.app.viewmodels

import androidx.lifecycle.viewModelScope
import com.baroness.app.models.ChatRoomUiState
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

class HumanChatViewModel(
    private val conversationId: String,
    private val repository: ChatRepository
) : ChatRoomViewModel() {

    private val _otherParticipant = MutableStateFlow<Participant?>(null)
    override val otherParticipant: StateFlow<Participant?> = _otherParticipant.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    override val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    override val isSubscribed: StateFlow<Boolean> = repository.isSubscribed

    override val uiState: StateFlow<ChatRoomUiState> = repository.getMessages(conversationId)
        .map { messages -> ChatRoomUiState.Success(messages) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChatRoomUiState.Loading
        )

    init {
        fetchParticipant()
        observeTyping()
        observeReadReceipts()
    }

    private fun observeReadReceipts() {
        viewModelScope.launch {
            repository.subscribeToConversation(conversationId)
                .collect { /* Read receipts could update local status if needed */ }
        }
    }

    private fun fetchParticipant() {
        viewModelScope.launch {
            val otherId = when (conversationId) {
                "baroness" -> "baroness_official"
                "phesty" -> "phesty_official"
                else -> conversationId
            }
            _otherParticipant.value = repository.getParticipant(otherId)
        }
    }

    private fun observeTyping() {
        viewModelScope.launch {
            repository.subscribeToConversation(conversationId)
                .collect { payload ->
                    val senderId = payload["senderId"]?.jsonPrimitive?.content
                    val isOtherTyping = payload["isTyping"]?.jsonPrimitive?.booleanOrNull ?: false
                    
                    if (senderId != null && senderId == _otherParticipant.value?.id) {
                        _isTyping.value = isOtherTyping
                    }
                }
        }
    }

    override fun onSendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(conversationId, text)
        }
    }

    override fun onDeleteMessage(message: Message) {
        viewModelScope.launch {
            repository.deleteMessage(message.id)
        }
    }

    override fun onEditMessage(message: Message, newContent: String) {
        viewModelScope.launch {
            repository.editMessage(message.id, newContent)
        }
    }

    override fun onReactToMessage(message: Message, emoji: String) {
        viewModelScope.launch {
            repository.reactToMessage(message.id, emoji)
        }
    }

    override fun setUserTyping(isTyping: Boolean) {
        viewModelScope.launch {
            repository.broadcastTyping(conversationId, isTyping)
        }
    }
}
