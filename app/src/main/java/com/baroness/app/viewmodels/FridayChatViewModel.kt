package com.baroness.app.viewmodels

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.baroness.app.data.remote.groq.GroqApiService
import com.baroness.app.data.remote.groq.GroqMessage
import com.baroness.app.models.ChatRoomUiState
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

private const val TAG = "FridayVM"

class FridayChatViewModel(
    private val repository: ChatRepository
) : ChatRoomViewModel() {

    private val groqApiService = GroqApiService()

    private val _otherParticipant = MutableStateFlow<Participant?>(
        Participant(
            id = "friday",
            displayName = "FRIDAY",
            avatarUrl = "https://img.icons8.com/fluency/48/artificial-intelligence.png",
            isOnline = null
        )
    )
    override val otherParticipant: StateFlow<Participant?> = _otherParticipant.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    override val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    override val isSubscribed: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    override val uiState: StateFlow<ChatRoomUiState> = repository.getMessages("friday")
        .map { messages -> ChatRoomUiState.Success(messages) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChatRoomUiState.Loading
        )

    init {
        // Removed auto-fetch to respect offline-first/restore-only architecture
    }

    override fun onSendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            // 1. Send user message
            repository.sendMessage("friday", text)
            
            // 2. Friday response flow
            generateFridayResponse(text)
        }
    }

    private fun generateFridayResponse(userText: String) {
        viewModelScope.launch {
            Log.d(TAG, "Friday is preparing to respond to: $userText")
            delay(800) // Small natural delay before "typing" starts
            _isTyping.value = true
            
            // Get conversation history for context (Limited to 5 for stability)
            val history = (uiState.value as? ChatRoomUiState.Success)?.messages?.take(5)?.reversed() ?: emptyList()
            val groqMessages = mutableListOf<GroqMessage>()
            
            // System Prompt
            groqMessages.add(GroqMessage(
                role = "system",
                content = "You are FRIDAY, a highly intelligent and witty lady. Keep responses concise (under 3 sentences)."
            ))
            
            // History with size safety
            history.forEach { msg ->
                val role = if (msg.senderId == "friday") "assistant" else "user"
                // Trim message if it's somehow massive to avoid 413
                val safeContent = if (msg.content.length > 500) msg.content.take(500) + "..." else msg.content
                groqMessages.add(GroqMessage(role = role, content = safeContent))
            }
            
            Log.d(TAG, "Calling Groq API with ${groqMessages.size} messages in history")
            
            // Actual API Call
            val response = groqApiService.getChatCompletion(groqMessages)
            
            _isTyping.value = false
            
            if (response != null) {
                // CLEANUP: Remove "Thinking" blocks or reasoning chains
                val cleanResponse = response
                    .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
                    .replace(Regex("\\(Thinking:.*?\\)", RegexOption.DOT_MATCHES_ALL), "")
                    .trim()

                Log.d(TAG, "Received response from Friday: ${cleanResponse.take(30)}...")
                saveFridayMessage(cleanResponse)
            } else {
                Log.e(TAG, "Friday response was NULL")
                saveFridayMessage("Friday is currently resting... please try again in a bit.")
            }
        }
    }

    private suspend fun saveFridayMessage(content: String) {
        repository.sendMessage("friday", content, senderId = "friday")
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
        // User typing for AI doesn't need to be broadcasted to server for now
    }
}
