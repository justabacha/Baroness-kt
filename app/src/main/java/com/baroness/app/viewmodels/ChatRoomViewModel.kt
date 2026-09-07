package com.baroness.app.viewmodels

import androidx.lifecycle.ViewModel
import com.baroness.app.models.ChatRoomUiState
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import kotlinx.coroutines.flow.StateFlow

abstract class ChatRoomViewModel : ViewModel() {
    abstract val uiState: StateFlow<ChatRoomUiState>
    abstract val isTyping: StateFlow<Boolean>
    abstract val isSubscribed: StateFlow<Boolean>
    abstract val otherParticipant: StateFlow<Participant?>

    abstract fun onSendMessage(text: String)
    abstract fun onDeleteMessage(message: Message)
    abstract fun onEditMessage(message: Message, newContent: String)
    abstract fun onReactToMessage(message: Message, emoji: String)
    abstract fun setUserTyping(isTyping: Boolean)
}
