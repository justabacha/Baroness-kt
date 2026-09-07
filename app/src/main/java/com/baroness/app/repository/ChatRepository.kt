package com.baroness.app.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import com.baroness.app.api.ChatApi
import com.baroness.app.api.FridayMessageDto
import com.baroness.app.api.MessageDto
import com.baroness.app.api.ProfileDto
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.data.local.dao.MessageDao
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.data.local.database.MessageEntity
import com.baroness.app.models.Message
import com.baroness.app.models.Participant
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.parseIsoToLong
import com.baroness.app.workers.ChatSyncWorker
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "ChatRepository"

class ChatRepository private constructor(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val messageDao: MessageDao = db.messageDao()
    private val storageManager = StorageManager(context)
    private val supabase = SupabaseConfig.supabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    init {
        scope.launch {
            setupNetworkListener(context)
            setupLifecycleObserver()
            subscribeToSyncPipe()
        }
    }

    private fun setupNetworkListener(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available, triggering chat sync")
                triggerSync()
            }
        })
    }

    private fun setupLifecycleObserver() {
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    Log.d(TAG, "App resumed, triggering chat sync")
                    triggerSync()
                }
            })
        }
    }

    private suspend fun subscribeToSyncPipe() {
        val currentPersonaId = storageManager.getString("currentPersonaId") ?: return
        
        scope.launch {
            var retryDelay = 1000L
            while (isActive) {
                try {
                    Log.d(TAG, "Connecting to Chat Sync Pipe Realtime")
                    val channel = supabase.realtime.channel("chat_sync_pipe_$currentPersonaId")
                    val pipeFlow = channel.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    ) {
                        table = "chat_sync_pipe"
                    }

                    scope.launch {
                        pipeFlow.collect { action ->
                            if (action is PostgresAction.Insert) {
                                val recipientId = action.record["recipient_id"]?.jsonPrimitive?.content
                                if (recipientId == currentPersonaId) {
                                    handlePipeMessage(action.record)
                                }
                            }
                        }
                    }
                    channel.subscribe()
                    _isSubscribed.value = true
                    break
                } catch (e: Exception) {
                    _isSubscribed.value = false
                    Log.e(TAG, "Chat sync pipe subscription error: ${e.message}. Retrying in ${retryDelay}ms")
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30000L)
                }
            }
        }
    }

    private suspend fun handlePipeMessage(record: Map<String, JsonElement>) {
        try {
            val pipeId = record["id"]?.jsonPrimitive?.content ?: return
            val payload = record["payload"]?.jsonObject ?: return
            
            val messageId = payload["messageId"]?.jsonPrimitive?.content ?: return
            val conversationId = payload["conversationId"]?.jsonPrimitive?.content ?: return
            val senderId = payload["senderId"]?.jsonPrimitive?.content ?: return
            val content = payload["content"]?.jsonPrimitive?.content ?: ""
            val timestamp = payload["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

            val entity = MessageEntity(
                id = messageId,
                conversationId = conversationId,
                senderId = senderId,
                content = content,
                timestamp = timestamp,
                status = "SENT"
            )
            
            messageDao.insertMessage(entity)
            Log.d(TAG, "Received message from pipe: $messageId. Purging pipe item $pipeId")
            
            // Cleanup: Delete from sync pipe once consumed
            ChatApi.deletePipeItem(pipeId)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling pipe message: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun sendMessage(conversationId: String, content: String, senderId: String? = null) {
        val currentPersonaId = storageManager.getString("currentPersonaId") ?: "unknown"
        val actualSenderId = senderId ?: currentPersonaId
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // 1. Local Optimistic Update
        val entity = MessageEntity(
            id = messageId,
            conversationId = conversationId,
            senderId = actualSenderId,
            content = content,
            timestamp = timestamp,
            status = "PENDING"
        )
        messageDao.insertMessage(entity)

        // 2. Trigger Sync
        triggerSync()

        // 3. Realtime Broadcast (Optional for human chats)
        if (conversationId != "friday") {
            broadcastTyping(conversationId, false)
        }
    }

    suspend fun broadcastTyping(conversationId: String, isTyping: Boolean) {
        val currentPersonaId = storageManager.getString("currentPersonaId") ?: "unknown"
        try {
            val channel = supabase.realtime.channel("chat_$conversationId")
            channel.broadcast(
                event = "typing",
                message = buildJsonObject {
                    put("senderId", currentPersonaId)
                    put("isTyping", isTyping)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast typing: ${e.message}")
        }
    }

    fun subscribeToConversation(conversationId: String): Flow<JsonObject> {
        val channel = supabase.realtime.channel("chat_$conversationId")
        scope.launch {
            try {
                channel.subscribe()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to chat channel: ${e.message}")
            }
        }
        return channel.broadcastFlow<JsonObject>("typing")
    }

    suspend fun fetchMessagesFromServer(conversationId: String) {
        try {
            if (conversationId == "friday") {
                val currentPersonaId = storageManager.getString("currentPersonaId") ?: return
                val remoteMessages = ChatApi.fetchFridayMessages(currentPersonaId)
                val entities = remoteMessages.map { dto ->
                    MessageEntity(
                        id = dto.id ?: UUID.randomUUID().toString(),
                        conversationId = "friday",
                        senderId = dto.sender,
                        content = dto.message,
                        timestamp = parseIsoToLong(dto.createdAt),
                        status = "SENT"
                    )
                }
                messageDao.insertMessages(entities)
            } else {
                val remoteMessages = ChatApi.fetchMessages(conversationId)
                val entities = remoteMessages.map { dto ->
                    MessageEntity(
                        id = dto.id,
                        conversationId = dto.conversationId,
                        senderId = dto.senderId,
                        content = dto.content,
                        timestamp = parseIsoToLong(dto.createdAt),
                        status = "SENT",
                        reactions = dto.reactions
                    )
                }
                messageDao.insertMessages(entities)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch messages from server: ${e.message}")
        }
    }

    suspend fun getParticipant(id: String): Participant? {
        if (id == "friday") {
            return Participant(
                id = "friday",
                displayName = "FRIDAY",
                avatarUrl = "https://img.icons8.com/fluency/48/artificial-intelligence.png",
                isOnline = null
            )
        }

        return try {
            val profile = supabase.postgrest["profiles"]
                .select {
                    filter {
                        eq("id", id)
                    }
                }
                .decodeSingleOrNull<ProfileDto>()

            profile?.let {
                Participant(
                    id = it.id,
                    displayName = it.displayName ?: "Unknown",
                    avatarUrl = it.avatarUrl,
                    isOnline = null // Realtime online status could be added here
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch participant: ${e.message}")
            null
        }
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.softDeleteMessage(messageId)
        // Sync delete logic to be implemented in SyncWorker/Api
    }

    suspend fun editMessage(messageId: String, newContent: String) {
        val existing = messageDao.getMessageById(messageId) ?: return
        val updated = existing.copy(
            content = newContent,
            editedAt = System.currentTimeMillis(),
            status = "PENDING"
        )
        messageDao.updateMessage(updated)
        triggerSync()
    }

    suspend fun reactToMessage(messageId: String, emoji: String) {
        val existing = messageDao.getMessageById(messageId) ?: return
        // Basic reaction update logic - assuming reactions is a JSON string
        // In a real app, this would parse/update/stringify
        messageDao.updateMessage(existing.copy(reactions = emoji)) // Placeholder
    }

    suspend fun markMessagesAsRead(conversationId: String) {
        val currentPersonaId = storageManager.getString("currentPersonaId") ?: return
        try {
            // 2. Broadcast to Supabase
            val channel = supabase.realtime.channel("chat_$conversationId")
            channel.broadcast(
                event = "read_receipt",
                message = buildJsonObject {
                    put("readerId", currentPersonaId)
                    put("conversationId", conversationId)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark messages as read: ${e.message}")
        }
    }

    fun triggerSync() {
        val workRequest = OneTimeWorkRequestBuilder<ChatSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        workManager.enqueueUniqueWork("ChatSync", ExistingWorkPolicy.REPLACE, workRequest)
    }

    private fun MessageEntity.toDomain(): Message {
        return Message(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            content = content,
            timestamp = timestamp,
            editedAt = editedAt,
            serverTimestamp = serverTimestamp,
            status = status,
            isDeleted = isDeleted,
            reactions = reactions
        )
    }
}
