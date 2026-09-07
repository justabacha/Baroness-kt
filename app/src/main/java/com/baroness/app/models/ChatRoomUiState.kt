package com.baroness.app.models

sealed class ChatRoomUiState {
    object Loading : ChatRoomUiState()
    data class Success(val messages: List<Message>) : ChatRoomUiState()
    data class Error(val message: String) : ChatRoomUiState()
}
