package com.baroness.app.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.models.Conversation
import com.baroness.app.models.PersonaType
import com.baroness.app.utils.StorageManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val json = Json { ignoreUnknownKeys = true }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private var currentPersonaId: String? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            currentPersonaId = storageManager.getString("currentPersonaId")
            val otherId = if (currentPersonaId == "phesty_official") "baroness_official" else "phesty_official"

            // 1. Load from cache
            val cachedJson = storageManager.getString("cached_other_profile_$otherId")
            val cachedProfile = cachedJson?.let {
                try { json.decodeFromString<ChatProfileDto>(it) } catch (e: Exception) { null }
            }

            updateConversationList(otherId, cachedProfile)
            
            if (cachedProfile != null) {
                _isInitialLoading.value = false
            }

            // 2. Fetch from Supabase in background
            fetchRemoteProfile(otherId)
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
                    // Cache it
                    storageManager.saveString("cached_other_profile_$otherId", json.encodeToString(profile))
                    // Update UI
                    updateConversationList(otherId, profile)
                }
            } catch (e: Exception) {
                Log.e("ChatListViewModel", "Error fetching remote profile", e)
            } finally {
                _isInitialLoading.value = false
            }
        }
    }

    private fun updateConversationList(otherId: String, profile: ChatProfileDto?) {
        val humanName = profile?.display_name ?: if (otherId == "baroness_official") "Baroness" else "Phesty"
        val humanAvatar = profile?.avatar_url

        val newList = listOf(
            Conversation(
                id = otherId,
                displayName = humanName,
                avatarUrl = humanAvatar,
                lastMessage = "Can't wait to see you later! ❤️",
                timestamp = "14:20",
                isOnline = true,
                personaType = PersonaType.HUMAN
            ),
            Conversation(
                id = "friday",
                displayName = "Friday",
                avatarUrl = "https://img.icons8.com/fluency/96/artificial-intelligence.png",
                lastMessage = "I'm ready when you are. How can I help today?",
                timestamp = "Yesterday",
                isOnline = true,
                personaType = PersonaType.AI
            )
        )
        _conversations.value = newList
    }
}
