package com.baroness.app.repository

import android.content.Context
import android.util.Log
import com.baroness.app.api.BackupLogDto
import com.baroness.app.api.ChatApi
import com.baroness.app.api.MessageDto
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.utils.formatLongToIso
import com.baroness.app.utils.parseIsoToLong
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

private const val TAG = "BackupManager"

class BackupManager(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val messageDao = db.messageDao()
    private val supabase = SupabaseConfig.supabase

    suspend fun createBackup(personaId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting backup for $personaId")
            
            // 1. Get all messages for this persona
            val entities = messageDao.getMessagesForBackup(personaId)
            val messagesToBackup = entities.map { entity ->
                MessageDto(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    senderId = entity.senderId,
                    receiverId = if (entity.conversationId == "baroness") "baroness_official" else if (entity.conversationId == "phesty") "phesty_official" else "",
                    content = entity.content,
                    createdAt = formatLongToIso(entity.timestamp),
                    reactions = entity.reactions
                )
            }

            if (messagesToBackup.isEmpty()) {
                Log.d(TAG, "No messages to backup")
                return@withContext true
            }

            // 2. Package to JSON
            val jsonString = Json.encodeToString(messagesToBackup)
            val fileName = "${personaId}_official_backup.json"
            val file = File(context.cacheDir, fileName)
            file.writeText(jsonString)

            // 3. Upload to Supabase Storage
            val bucket = supabase.storage.from("chat_backups")
            bucket.upload("backups/$fileName", file.readBytes()) {
                upsert = true
            }

            // 4. Update Backup Log
            ChatApi.updateBackupLog(
                BackupLogDto(
                    personaId = personaId,
                    lastBackupAt = formatLongToIso(System.currentTimeMillis()),
                    messageCountAtBackup = messagesToBackup.size
                )
            )

            file.delete()
            Log.d(TAG, "Backup successful for $personaId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}", e)
            false
        }
    }

    suspend fun restoreBackup(personaId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting restore for $personaId")
            val fileName = "${personaId}_official_backup.json"
            
            // 1. Download from Supabase Storage
            val bucket = supabase.storage.from("chat_backups")
            val bytes = bucket.downloadAuthenticated("backups/$fileName")
            val jsonString = String(bytes)

            // 2. Parse JSON
            val messages = Json.decodeFromString<List<MessageDto>>(jsonString)

            // 3. Insert into Room
            val entities = messages.map { dto ->
                com.baroness.app.data.local.database.MessageEntity(
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

            Log.d(TAG, "Restore successful for $personaId. Restored ${entities.size} messages.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
            false
        }
    }
}
