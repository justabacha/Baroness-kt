package com.baroness.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baroness.app.repository.ChatRepository

class ChatRoomViewModelFactory(
    private val context: Context,
    private val conversationId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = ChatRepository.getInstance(context)
        
        return if (conversationId == "friday") {
            FridayChatViewModel(repository) as T
        } else {
            HumanChatViewModel(conversationId, repository) as T
        }
    }
}
