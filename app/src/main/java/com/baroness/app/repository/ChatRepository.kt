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
import com.baroness.app.command.LocalCommandExecutor
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
    private val commandExecutor = LocalCommandExecutor(context)

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _isFridayTyping = MutableStateFlow(false)
    val isFridayTyping: StateFlow<Boolean> = _isFridayTyping.asStateFlow()

    private var syncPipeJob: Job? = null

    init {
        scope.launch {
            setupNetworkListener(context)
            setupLifecycleObserver()
            
            // Fix 2: Reactive Identity Handling with change detection
            storageManager.getStringFlow("currentPersonaId")
                .distinctUntilChanged()
                .collect { personaId ->
                    if (personaId != null) {
                        restartSyncPipeSubscription(personaId)
                    } else {
                        syncPipeJob?.cancel()
                        _isSubscribed.value = false
                    }
                }
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
                    Log.d(TAG, "App resumed, triggering chat sync and checking for offline messages")
                    triggerSync()
                    fetchOfflineMessages()
                }
            })
        }
    }

    private fun restartSyncPipeSubscription(personaId: String) {
        syncPipeJob?.cancel()
        syncPipeJob = scope.launch {
            var retryDelay = 2000L
            while (isActive) {
                try {
                    Log.d(TAG, ">>> [REALTIME] Attempting connection for: $personaId")
                    val channel = supabase.realtime.channel("chat_sync_pipe_$personaId")
                    
                    val pipeFlow = channel.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    ) {
                        table = "chat_sync_pipe"
                    }

                    // Observe status to update UI
                    val statusJob = launch {
                        channel.status.collect { status ->
                            Log.d(TAG, ">>> [REALTIME] Status changed to: $status")
                            _isSubscribed.value = (status == RealtimeChannel.Status.SUBSCRIBED)
                        }
                    }

                    val collectorJob = launch {
                        pipeFlow.collect { action ->
                            Log.d(TAG, ">>> [REALTIME] Received Action: $action")
                            if (action is PostgresAction.Insert) {
                                val recipientId = action.record["recipient_id"]?.jsonPrimitive?.content
                                if (recipientId == personaId) {
                                    handlePipeMessage(action.record)
                                }
                            }
                        }
                    }
                    
                    Log.d(TAG, ">>> [REALTIME] Calling subscribe()...")
                    channel.subscribe(blockUntilSubscribed = true)
                    Log.d(TAG, ">>> [REALTIME] Subscribe call finished.")
                    
                    // Immediately fetch any messages that might have arrived while we were offline
                    fetchOfflineMessages()

                    try {
                        awaitCancellation()
                    } finally {
                        Log.d(TAG, ">>> [REALTIME] Cleaning up channel...")
                        statusJob.cancel()
                        collectorJob.cancel()
                        channel.unsubscribe()
                        _isSubscribed.value = false
                    }
                } catch (e: Exception) {
                    _isSubscribed.value = false
                    Log.e(TAG, ">>> [REALTIME] CONNECTION ERROR: ${e.message}")
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
            
            val type = payload["type"]?.jsonPrimitive?.content ?: "NEW_MESSAGE"
            val messageId = payload["messageId"]?.jsonPrimitive?.content ?: return

            if (type == "DELETE_MESSAGE") {
                messageDao.softDeleteMessage(messageId)
                Log.d(TAG, "Soft-deleted message from pipe (Tombstone): $messageId")
                ChatApi.deletePipeItem(pipeId)
            } else if (type == "START_TYPING") {
                if (payload["conversationId"]?.jsonPrimitive?.content == "friday") {
                    _isFridayTyping.value = true
                }
                ChatApi.deletePipeItem(pipeId)
            } else if (type == "COMMAND") {
                val intent = payload["intent"]?.jsonPrimitive?.content
                val params = payload["parameters"]?.jsonObject?.let { 
                    // Simple mapping of JsonObject to Map
                    it.mapValues { (_, v) -> 
                        when(v) {
                            is JsonPrimitive -> v.contentOrNull ?: v.longOrNull ?: v.doubleOrNull ?: v.booleanOrNull
                            else -> v.toString()
                        }
                    }
                }
                
                // Commands still get an acknowledgment message usually
                val content = payload["content"]?.jsonPrimitive?.content ?: "On it"
                val conversationId = "friday"
                val senderId = "friday"
                val timestamp = payload["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                
                saveMessageWithTyping(
                    messageId, conversationId, senderId, content, timestamp, 
                    typingDurationMs = 300, isPinned = false
                )

                // Execute local intent
                withContext(Dispatchers.Main) {
                    commandExecutor.execute(intent, params)
                }
                
                ChatApi.deletePipeItem(pipeId)
            } else {
                var conversationId = payload["conversationId"]?.jsonPrimitive?.content ?: return
                val senderId = payload["senderId"]?.jsonPrimitive?.content ?: return
                
                // Fix: If the message is sent TO us, the local conversation context is the SENDER
                val currentPersonaId = storageManager.getString("currentPersonaId")
                if (conversationId == currentPersonaId) {
                    conversationId = senderId
                }

                // Alias Mapping: Ensure conversationId matches UI aliases (e.g., "phesty" instead of "phesty_official")
                val localConversationId = when (conversationId) {
                    "baroness_official" -> "baroness"
                    "phesty_official" -> "phesty"
                    else -> conversationId
                }
                
                val content = payload["content"]?.jsonPrimitive?.content ?: ""
                val timestamp = payload["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                val typingDurationMs = payload["typing_duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L
                val isPinned = payload["isPinned"]?.jsonPrimitive?.booleanOrNull ?: false
                
                val reactions = payload["reactions"]?.jsonPrimitive?.contentOrNull ?: "{}"
                val editedAt = payload["editedAt"]?.jsonPrimitive?.longOrNull
                
                val replyToId = payload["replyToId"]?.jsonPrimitive?.contentOrNull
                val replyToContent = payload["replyToContent"]?.jsonPrimitive?.contentOrNull
                val replyToSenderId = payload["replyToSenderId"]?.jsonPrimitive?.contentOrNull
                val deliveredAt = payload["deliveredAt"]?.jsonPrimitive?.longOrNull
                val readAt = payload["readAt"]?.jsonPrimitive?.longOrNull

                saveMessageWithMetadata(
                    messageId, localConversationId, senderId, content, timestamp, 
                    typingDurationMs, isPinned, reactions, editedAt, 
                    replyToId, replyToContent, replyToSenderId, deliveredAt, readAt
                )

                ChatApi.deletePipeItem(pipeId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling pipe message: ${e.message}")
        }
    }

    private suspend fun saveMessageWithMetadata(
        messageId: String,
        conversationId: String,
        senderId: String,
        content: String,
        timestamp: Long,
        typingDurationMs: Long,
        isPinned: Boolean,
        reactions: String = "{}",
        editedAt: Long? = null,
        replyToId: String? = null,
        replyToContent: String? = null,
        replyToSenderId: String? = null,
        deliveredAt: Long? = null,
        readAt: Long? = null
    ) {
        if (typingDurationMs > 0 && conversationId == "friday") {
            _isFridayTyping.value = true
            delay(typingDurationMs)
            _isFridayTyping.value = false
        }

        val entity = MessageEntity(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            content = content,
            timestamp = timestamp,
            status = "SENT",
            editedAt = editedAt,
            replyToId = replyToId,
            replyToContent = replyToContent,
            replyToSenderId = replyToSenderId,
            deliveredAt = deliveredAt,
            readAt = readAt,
            isPinned = isPinned,
            reactions = reactions
        )
        
        try {
            messageDao.insertMessage(entity)
            Log.d(TAG, "Persisted message from pipe: $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist message: ${e.message}")
        }
    }

    private suspend fun saveMessageWithTyping(
        messageId: String,
        conversationId: String,
        senderId: String,
        content: String,
        timestamp: Long,
        typingDurationMs: Long,
        isPinned: Boolean,
        replyToId: String? = null,
        replyToContent: String? = null,
        replyToSenderId: String? = null,
        deliveredAt: Long? = null,
        readAt: Long? = null
    ) {
        if (typingDurationMs > 0 && conversationId == "friday") {
            _isFridayTyping.value = true
            delay(typingDurationMs)
            _isFridayTyping.value = false
        }

        val entity = MessageEntity(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            content = content,
            timestamp = timestamp,
            status = "SENT",
            replyToId = replyToId,
            replyToContent = replyToContent,
            replyToSenderId = replyToSenderId,
            deliveredAt = deliveredAt,
            readAt = readAt,
            isPinned = isPinned
        )
        
        try {
            messageDao.insertMessage(entity)
            Log.d(TAG, "Persisted message from pipe: $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist message: ${e.message}")
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

    fun getPinnedMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.getPinnedMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun togglePinMessage(messageId: String) {
        val existing = messageDao.getMessageById(messageId) ?: return
        val updated = existing.copy(
            isPinned = !existing.isPinned,
            status = "PENDING"
        )
        messageDao.updateMessage(updated)
        triggerSync()
    }

    suspend fun sendMessage(
        conversationId: String, 
        content: String, 
        senderId: String? = null,
        replyToId: String? = null,
        replyToContent: String? = null,
        replyToSenderId: String? = null
    ) {
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
            status = "PENDING",
            replyToId = replyToId,
            replyToContent = replyToContent,
            replyToSenderId = replyToSenderId
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
            val sharedChannel = getSharedChannelId(conversationId, currentPersonaId)
            val channel = supabase.realtime.channel("chat_$sharedChannel")
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
        val flow = MutableSharedFlow<JsonObject>(extraBufferCapacity = 1)
        
        scope.launch {
            val currentPersonaId = storageManager.getString("currentPersonaId") ?: "unknown"
            val sharedChannel = getSharedChannelId(conversationId, currentPersonaId)
            val channel = supabase.realtime.channel("chat_$sharedChannel")
            
            try {
                channel.subscribe()
                
                merge(
                    channel.broadcastFlow<JsonObject>("typing"),
                    channel.broadcastFlow<JsonObject>("read_receipt")
                ).collect {
                    flow.emit(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to chat channel: ${e.message}")
            }
        }
        return flow
    }

    private fun getSharedChannelId(conversationId: String, currentPersonaId: String): String {
        if (conversationId == "friday") return "friday_$currentPersonaId"
        
        val otherId = when (conversationId) {
            "baroness" -> "baroness_official"
            "phesty" -> "phesty_official"
            else -> conversationId
        }
        
        // Use full standard format for channels: phesty_official_baroness_official
        val sortedList = listOf(currentPersonaId, otherId).sorted()
        return "${sortedList[0]}_${sortedList[1]}"
    }

    suspend fun fetchMessagesFromServer(conversationId: String) {
        val currentPersonaId = storageManager.getString("currentPersonaId") ?: return
        try {
            if (conversationId == "friday") {
                val remoteMessages: List<FridayMessageDto> = ChatApi.fetchFridayMessages(currentPersonaId)
                val entities = remoteMessages.map { dto ->
                    MessageEntity(
                        id = dto.id ?: UUID.randomUUID().toString(),
                        conversationId = "friday",
                        senderId = dto.sender,
                        content = dto.message,
                        timestamp = parseIsoToLong(dto.createdAt),
                        status = "SENT",
                        isPinned = dto.isPinned
                    )
                }
                messageDao.insertMessages(entities)
                
                // Track last seen for proactive pulse checks
                remoteMessages.maxByOrNull { parseIsoToLong(it.createdAt) }?.createdAt?.let {
                    storageManager.saveString("last_seen_friday_at", it)
                }
            } else {
                // Map conversationId alias to full ID for the server query
                val otherParticipantId = when (conversationId) {
                    "baroness" -> "baroness_official"
                    "phesty" -> "phesty_official"
                    else -> conversationId
                }

                val remoteMessages: List<MessageDto> = ChatApi.fetchMessages(currentPersonaId, otherParticipantId)
                val entities = remoteMessages.map { dto ->
                    val rawConversationId = if (dto.senderId == currentPersonaId) dto.receiverId else dto.senderId
                    val mappedConversationId = when (rawConversationId) {
                        "baroness_official" -> "baroness"
                        "phesty_official" -> "phesty"
                        else -> rawConversationId
                    }

                    MessageEntity(
                        id = dto.id,
                        conversationId = mappedConversationId,
                        senderId = dto.senderId,
                        content = dto.content,
                        timestamp = parseIsoToLong(dto.createdAt),
                        status = "SENT",
                        reactions = dto.reactions,
                        replyToId = dto.replyToId,
                        replyToContent = dto.replyToContent,
                        replyToSenderId = dto.replyToSenderId,
                        deliveredAt = parseIsoToLong(dto.deliveredAt),
                        readAt = parseIsoToLong(dto.readAt),
                        isPinned = dto.isPinned
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
        messageDao.hardDeleteMessage(messageId)
    }

    suspend fun deleteMessageForMe(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        
        // Soft delete locally first
        messageDao.updateMessage(message.copy(isDeleted = true, status = "PENDING"))
        
        // Trigger sync to notify server
        triggerSync()
    }

    suspend fun deleteMessageForEveryone(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        
        // For Friday chat, "Everyone" = "Me" since it's a private bot chat
        // For human chat, it notifies the other participant
        messageDao.updateMessage(message.copy(isDeleted = true, status = "PENDING"))
        triggerSync()
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
        val currentPersonaId = storageManager.getString("currentPersonaId") ?: "unknown"
        val existing = messageDao.getMessageById(messageId) ?: return
        
        val updatedReactions = try {
            val json = Json.parseToJsonElement(existing.reactions).jsonObject.toMutableMap()
            
            // Check if user has already reacted with THIS exact emoji
            val alreadyReactedWithThis = json[emoji]?.jsonArray?.any { it.jsonPrimitive.content == currentPersonaId } ?: false
            
            // POLICY: One user = One reaction per message.
            // Clear any existing reaction by this user across all emojis.
            val newJson = mutableMapOf<String, JsonElement>()
            json.forEach { (k, v) ->
                val filteredList = v.jsonArray.filter { it.jsonPrimitive.content != currentPersonaId }
                if (filteredList.isNotEmpty()) {
                    newJson[k] = buildJsonArray { filteredList.forEach { add(it) } }
                }
            }
            
            // If they weren't clicking the same emoji to remove it, add the new one.
            if (!alreadyReactedWithThis) {
                val currentUsersForEmoji = newJson[emoji]?.jsonArray?.toMutableList() ?: mutableListOf()
                newJson[emoji] = buildJsonArray {
                    currentUsersForEmoji.forEach { add(it) }
                    add(currentPersonaId)
                }
            }
            
            buildJsonObject {
                newJson.forEach { (k, v) -> put(k, v) }
            }.toString()
            
        } catch (e: Exception) {
            // Fallback for corrupted/empty JSON
            buildJsonObject {
                put(emoji, buildJsonArray { add(currentPersonaId) })
            }.toString()
        }

        messageDao.updateMessage(existing.copy(reactions = updatedReactions))
        triggerSync()
    }

    suspend fun markMessagesAsRead(conversationId: String) {
        val currentPersonaId = storageManager.getString("currentPersonaId") ?: return
        try {
            val sharedChannel = getSharedChannelId(conversationId, currentPersonaId)
            val channel = supabase.realtime.channel("chat_$sharedChannel")
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

    fun fetchOfflineMessages() {
        scope.launch {
            val currentPersonaId = storageManager.getString("currentPersonaId") ?: return@launch
            
            // 1. Fetch Friday Proactive Messages
            val since = storageManager.getString("last_seen_friday_at") ?: "1970-01-01T00:00:00Z"
            val pendingFriday = ChatApi.fetchPendingFridayMessages(currentPersonaId, since)
            if (pendingFriday.isNotEmpty()) {
                val entities = pendingFriday.map { dto ->
                    MessageEntity(
                        id = dto.id ?: UUID.randomUUID().toString(),
                        conversationId = "friday",
                        senderId = dto.sender,
                        content = dto.message,
                        timestamp = parseIsoToLong(dto.createdAt),
                        status = "SENT",
                        isPinned = dto.isPinned
                    )
                }
                messageDao.insertMessages(entities)
                
                pendingFriday.maxByOrNull { parseIsoToLong(it.createdAt) }?.createdAt?.let {
                    storageManager.saveString("last_seen_friday_at", it)
                }
            }

            // 2. Fetch Human Offline Messages from Sync Pipe
            val pipeItems = ChatApi.fetchSyncPipe(currentPersonaId)
            if (pipeItems.isNotEmpty()) {
                Log.d(TAG, "Fetched ${pipeItems.size} offline items from sync pipe")
                for (item in pipeItems) {
                    handlePipeMessage(mapOf(
                        "id" to JsonPrimitive(item.id),
                        "recipient_id" to JsonPrimitive(item.recipientId),
                        "payload" to item.payload
                    ))
                }
            }
        }
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
            reactions = reactions,
            replyToId = replyToId,
            replyToContent = replyToContent,
            replyToSenderId = replyToSenderId,
            deliveredAt = deliveredAt,
            readAt = readAt,
            isPinned = isPinned
        )
    }
}
