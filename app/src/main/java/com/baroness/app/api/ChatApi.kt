package com.baroness.app.api

import android.util.Log
import com.baroness.app.config.SupabaseConfig
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.broadcast
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "ChatApi"

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    val reactions: String = "{}"
)

@Serializable
data class FridayMessageDto(
    val id: String? = null,
    @SerialName("owner_id") val ownerId: String,
    val sender: String,
    val message: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SyncPipeDto(
    @SerialName("recipient_id") val recipientId: String,
    val payload: JsonObject
)

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class BackupLogDto(
    @SerialName("persona_id") val personaId: String,
    @SerialName("last_backup_at") val lastBackupAt: String? = null,
    @SerialName("message_count_at_backup") val messageCountAtBackup: Int
)

object ChatApi {
    private val supabase = SupabaseConfig.supabase

    suspend fun sendMessage(message: MessageDto): Boolean {
        return try {
            supabase.postgrest["messages"].insert(message)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
            false
        }
    }

    suspend fun sendFridayMessage(message: FridayMessageDto): Boolean {
        return try {
            supabase.postgrest["friday_messages"].insert(message)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Friday message: ${e.message}")
            false
        }
    }

    suspend fun pushToSyncPipe(recipientId: String, payload: JsonObject): Boolean {
        return try {
            val dto = SyncPipeDto(recipientId, payload)
            supabase.postgrest["chat_sync_pipe"].insert(dto)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push to sync pipe: ${e.message}")
            false
        }
    }

    suspend fun deletePipeItem(id: String): Boolean {
        return try {
            supabase.postgrest["chat_sync_pipe"].delete {
                filter { eq("id", id) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete pipe item: ${e.message}")
            false
        }
    }

    suspend fun fetchMessages(conversationId: String): List<MessageDto> {
        return try {
            supabase.postgrest["messages"]
                .select {
                    filter {
                        eq("conversation_id", conversationId)
                    }
                }
                .decodeList<MessageDto>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch messages: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchFridayMessages(ownerId: String): List<FridayMessageDto> {
        return try {
            supabase.postgrest["friday_messages"]
                .select {
                    filter {
                        eq("owner_id", ownerId)
                    }
                }
                .decodeList<FridayMessageDto>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Friday messages: ${e.message}")
            emptyList()
        }
    }

    suspend fun updateBackupLog(log: BackupLogDto): Boolean {
        return try {
            supabase.postgrest["backup_log"].upsert(log)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update backup log: ${e.message}")
            false
        }
    }
}
