package com.baroness.app.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.data.local.database.MessageEntity
import com.baroness.app.models.Conversation
import com.baroness.app.models.PersonaType
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.formatChatTimestamp
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ChatProfileDto(
    val id: String,
    val display_name: String? = null,
    val avatar_url: String? = null
)

class ChatListViewModel(context: Context) : ViewModel() {
    private val storageManager = StorageManager(context.applicationContext)
    private val db = AppDatabase.getInstance(context.applicationContext)
    private val messageDao = db.messageDao()
    private val json = Json { ignoreUnknownKeys = true }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private var currentPersonaId: String? = null
    private val otherProfile = MutableStateFlow<ChatProfileDto?>(null)

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            currentPersonaId = storageManager.getString("currentPersonaId")
            val otherId = if (currentPersonaId == "phesty_official") "baroness_official" else "phesty_official"
            val humanConversationId = if (otherId == "baroness_official") "baroness" else "phesty"

            // 1. Load profile from cache
            val cachedJson = storageManager.getString("cached_other_profile_$otherId")
            otherProfile.value = cachedJson?.let {
                try { json.decodeFromString<ChatProfileDto>(it) } catch (e: Exception) { null }
            }
            
            if (otherProfile.value != null) {
                _isInitialLoading.value = false
            }

            // 2. Fetch from Supabase in background
            fetchRemoteProfile(otherId)

            // 3. Observe messages and profiles to build conversation list
            combine(
                messageDao.getLastMessageForConversation(humanConversationId),
                messageDao.getLastMessageForConversation("friday"),
                otherProfile
            ) { humanMsg, fridayMsg, profile ->
                buildConversationList(otherId, profile, humanMsg, fridayMsg)
            }.collect { newList ->
                _conversations.value = newList
            }
        }
    }

    private fun fetchRemoteProfile(otherId: String) {
        viewModelScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    SupabaseConfig.supabase.postgrest["profiles"]
                        .select {
                            filter {
                                eq("id", otherId)
                            }
                        }
                        .decodeSingleOrNull<ChatProfileDto>()
                }

                if (profile != null) {
                    storageManager.saveString("cached_other_profile_$otherId", json.encodeToString(profile))
                    otherProfile.value = profile
                }
            } catch (e: Exception) {
                Log.e("ChatListViewModel", "Error fetching remote profile", e)
            } finally {
                _isInitialLoading.value = false
            }
        }
    }

    private fun buildConversationList(
        otherId: String,
        profile: ChatProfileDto?,
        humanMsg: MessageEntity?,
        fridayMsg: MessageEntity?
    ): List<Conversation> {
        val humanName = profile?.display_name ?: if (otherId == "baroness_official") "Baroness" else "Phesty"
        val humanAvatar = profile?.avatar_url

        return listOf(
            Conversation(
                id = otherId,
                displayName = humanName,
                avatarUrl = humanAvatar,
                lastMessage = humanMsg?.content ?: "Start your conversation with $humanName...",
                timestamp = humanMsg?.let { formatChatTimestamp(it.timestamp) } ?: "",
                isOnline = true,
                personaType = PersonaType.HUMAN
            ),
            Conversation(
                id = "friday",
                displayName = "Friday",
                avatarUrl = "https://img.icons8.com/fluency/96/artificial-intelligence.png",
                lastMessage = fridayMsg?.content ?: "I'm ready when you are🫡. How can I help today?",
                timestamp = fridayMsg?.let { formatChatTimestamp(it.timestamp) } ?: "",
                isOnline = true,
                personaType = PersonaType.AI
            )
        )
    }
}
