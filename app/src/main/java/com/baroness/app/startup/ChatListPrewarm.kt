package com.baroness.app.startup

import android.content.Context
import android.util.Log
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.models.Conversation
import com.baroness.app.models.PersonaType
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.formatChatTimestamp
import com.baroness.app.viewmodels.ChatProfileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object ChatListPrewarm {
    private const val TAG = "ChatListPrewarm"
    @Volatile private var conversationsSnapshot: List<Conversation> = emptyList()

    fun getConversationsSnapshot(): List<Conversation> = conversationsSnapshot

    fun setConversationsSnapshot(list: List<Conversation>) {
        conversationsSnapshot = list
    }

    suspend fun warmFromDatabase(context: Context) {
        withContext(Dispatchers.IO) {
            val json = Json { ignoreUnknownKeys = true }
            val currentPersonaId = StorageManager.getStringSync("currentPersonaId")
                ?: StorageManager(context).getString("currentPersonaId")
            val otherId = if (currentPersonaId == "phesty_official") "baroness_official" else "phesty_official"
            val humanConversationId = if (otherId == "baroness_official") "baroness" else "phesty"

            val cachedJson = StorageManager.getStringSync("cached_other_profile_$otherId")
                ?: StorageManager(context).getString("cached_other_profile_$otherId")
            val profile = cachedJson?.let {
                try { json.decodeFromString<ChatProfileDto>(it) } catch (_: Exception) { null }
            }

            val db = AppDatabase.getInstance(context)
            val messageDao = db.messageDao()
            val humanMsg = messageDao.getLastMessageForConversationSync(humanConversationId)
            val fridayMsg = messageDao.getLastMessageForConversationSync("friday")

            val humanName = profile?.display_name ?: if (otherId == "baroness_official") "Baroness" else "Phesty"
            val humanAvatar = profile?.avatar_url

            val list = listOf(
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
            conversationsSnapshot = list
            Log.d(TAG, "Pre-warmed ChatList snapshot: ${list.size} conversations")
        }
    }
}
