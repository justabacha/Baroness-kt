package com.baroness.app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baroness.app.api.ChatApi
import com.baroness.app.api.FridayMessageDto
import com.baroness.app.api.MessageDto
import com.baroness.app.api.SyncPipeDto
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.utils.StorageManager
import com.baroness.app.utils.formatLongToIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "ChatSyncWorker"

class ChatSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = AppDatabase.getInstance(context)
    private val messageDao = db.messageDao()
    private val storageManager = StorageManager(context)

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val pendingMessages = messageDao.getPendingMessages()
                if (pendingMessages.isEmpty()) return@withContext Result.success()

                Log.d(TAG, "Syncing ${pendingMessages.size} pending messages")

                var allSuccess = true
                for (message in pendingMessages) {
                    val success = when {
                        message.isDeleted -> syncDeleteMessage(message)
                        message.conversationId == "friday" -> syncFridayMessage(message)
                        else -> syncHumanMessage(message)
                    }

                    if (success) {
                        messageDao.updateMessage(message.copy(status = "SENT"))
                    } else {
                        allSuccess = false
                    }
                }

                if (allSuccess) Result.success() else Result.retry()
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed: ${e.message}")
                Result.retry()
            }
        }
    }

    private suspend fun syncDeleteMessage(message: com.baroness.app.data.local.database.MessageEntity): Boolean {
        val remoteSuccess = ChatApi.markMessageAsDeleted(message.id)
        if (!remoteSuccess) return false

        // Notify other participant if human chat
        if (message.conversationId != "friday") {
            val receiverId = if (message.conversationId == "baroness") "baroness_official" else "phesty_official"
            val pipePayload = buildJsonObject {
                put("type", "DELETE_MESSAGE")
                put("messageId", message.id)
                put("conversationId", message.conversationId)
            }
            ChatApi.pushToSyncPipe(receiverId, pipePayload)
        }
        return true
    }

    private suspend fun syncHumanMessage(message: com.baroness.app.data.local.database.MessageEntity): Boolean {
        // Determine receiver
        val receiverId = if (message.conversationId == "baroness") "baroness_official" else "phesty_official"
        
        val dto = MessageDto(
            id = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            receiverId = receiverId,
            content = message.content,
            createdAt = formatLongToIso(message.timestamp),
            reactions = message.reactions,
            replyToId = message.replyToId,
            replyToContent = message.replyToContent,
            replyToSenderId = message.replyToSenderId,
            deliveredAt = message.deliveredAt?.let { formatLongToIso(it) },
            readAt = message.readAt?.let { formatLongToIso(it) },
            isPinned = message.isPinned
        )

        val sentToMessages = ChatApi.sendMessage(dto)
        
        // Also push to sync pipe for realtime delivery if receiver is offline
        val pipePayload = buildJsonObject {
            put("type", "NEW_MESSAGE")
            put("messageId", message.id)
            put("conversationId", message.conversationId)
            put("senderId", message.senderId)
            put("content", message.content)
            put("timestamp", message.timestamp)
            message.replyToId?.let { put("replyToId", it) }
            message.replyToContent?.let { put("replyToContent", it) }
            message.replyToSenderId?.let { put("replyToSenderId", it) }
            message.deliveredAt?.let { put("deliveredAt", it) }
            message.readAt?.let { put("readAt", it) }
            put("isPinned", message.isPinned)
        }
        val sentToPipe = ChatApi.pushToSyncPipe(receiverId, pipePayload)

        return sentToMessages && sentToPipe
    }

    private suspend fun syncFridayMessage(message: com.baroness.app.data.local.database.MessageEntity): Boolean {
        val currentPersonaId = storageManager.getString("currentPersonaId") ?: return false
        val dto = FridayMessageDto(
            id = message.id,
            ownerId = currentPersonaId, // User who owns the conversation
            sender = message.senderId,   // Either user's ID or "friday"
            message = message.content,
            createdAt = formatLongToIso(message.timestamp),
            isPinned = message.isPinned
        )
        return ChatApi.sendFridayMessage(dto)
    }
}
