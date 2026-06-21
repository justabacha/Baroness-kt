package com.baroness.app.utils

import android.content.Context
import android.util.Log
import com.baroness.app.api.WishlistApi
import com.baroness.app.api.WishDto
import com.baroness.app.api.ReactionDto
import com.baroness.app.api.RatingDto
import com.baroness.app.data.local.dao.SyncQueueDao
import com.baroness.app.data.local.dao.WishDao
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.data.local.database.SyncQueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "SyncManager"

class SyncManager(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao: SyncQueueDao = db.syncQueueDao()
    private val wishDao: WishDao = db.wishDao()

    suspend fun queueItem(
        tableName: String,
        recordId: String,
        operation: String,
        payload: String,
        localTempId: Long? = null
    ) {
        val item = SyncQueueItem(
            tableName = tableName,
            recordId = recordId,
            operation = operation,
            payload = payload,
            localTempId = localTempId
        )
        dao.insert(item)
        Log.d(TAG, "Queued: $operation for $tableName (recordId=$recordId, tempId=$localTempId)")
    }

    suspend fun processQueue() {
        withContext(Dispatchers.IO) {
            val pending = dao.getAllPending()
            Log.d(TAG, "Processing ${pending.size} pending items")

            if (pending.isEmpty()) {
                Log.d(TAG, "Queue is empty, nothing to sync")
                return@withContext
            }

            for (item in pending) {
                if (item.retryCount >= 5) {
                    Log.w(TAG, "Max retries reached for item ${item.id}, skipping")
                    continue
                }

                try {
                    Log.d(TAG, "Processing item ${item.id}: ${item.operation} ${item.tableName}")
                    val success = when (item.tableName) {
                        "wishlist_items" -> processWishItem(item)
                        "wishlist_reactions" -> processReaction(item)
                        "wishlist_ratings" -> processRating(item)
                        else -> {
                            Log.w(TAG, "Unknown table: ${item.tableName}")
                            false
                        }
                    }

                    if (success) {
                        dao.deleteById(item.id)
                        Log.d(TAG, "Synced and removed item ${item.id}")
                    } else {
                        val updated = item.copy(retryCount = item.retryCount + 1)
                        dao.update(updated)
                        Log.w(TAG, "Failed to sync item ${item.id}, retry ${updated.retryCount}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing item ${item.id}: ${e.message}", e)
                    val updated = item.copy(
                        retryCount = item.retryCount + 1,
                        lastError = e.message
                    )
                    dao.update(updated)
                }
            }
        }
    }

    private suspend fun processWishItem(item: SyncQueueItem): Boolean {
        return when (item.operation) {
            "create" -> {
                Log.d(TAG, "Processing CREATE for wish")

                val json = JSONObject(item.payload)
                val text = json.getString("text")
                val wishDate = json.getString("wish_date")
                val status = json.getString("status")
                val creatorId = json.getString("creator_id")
                val createdAt = json.getLong("created_at")

                val result = WishlistApi.createWish(
                    text = text,
                    wishDate = wishDate,
                    status = status,
                    creatorId = creatorId,
                    createdAt = createdAt
                )

                if (result != null && result.id != null) {
                    val tempId = item.localTempId
                    val serverId = result.id

                    if (tempId != null && tempId < 0) {
                        Log.d(TAG, "ID swap: $tempId -> $serverId")
                        wishDao.updateWishId(oldTempId = tempId, newServerId = serverId)
                        Log.d(TAG, "ID swap complete")
                    } else {
                        Log.d(TAG, "No temp ID to swap (tempId=$tempId)")
                    }
                    true
                } else {
                    Log.e(TAG, "Create returned null or no ID")
                    false
                }
            }
            "update" -> {
                Log.d(TAG, "Processing UPDATE for wish ${item.recordId}")
                val json = JSONObject(item.payload)
                val status = json.getString("status")
                val updatedAt = json.getLong("updated_at")
                val wishId = item.recordId.toLongOrNull() ?: return false
                WishlistApi.updateWish(wishId, status, updatedAt)
            }
            "delete" -> {
                Log.d(TAG, "Processing DELETE for wish ${item.recordId}")
                val wishId = item.recordId.toLongOrNull() ?: return false
                WishlistApi.deleteWish(wishId)
            }
            else -> {
                Log.w(TAG, "Unknown operation: ${item.operation}")
                false
            }
        }
    }

    private suspend fun processReaction(item: SyncQueueItem): Boolean {
        return try {
            val json = JSONObject(item.payload)
            val reaction = ReactionDto(
                wishId = json.getLong("wish_id"),
                personaId = json.getString("persona_id"),
                emoji = json.getString("emoji")
            )
            WishlistApi.upsertReaction(reaction)
        } catch (e: Exception) {
            Log.e(TAG, "Reaction processing error: ${e.message}", e)
            false
        }
    }

    private suspend fun processRating(item: SyncQueueItem): Boolean {
        return try {
            val json = JSONObject(item.payload)
            val rating = RatingDto(
                wishId = json.getLong("wish_id"),
                personaId = json.getString("persona_id"),
                rating = json.getInt("rating")
            )
            WishlistApi.upsertRating(rating)
        } catch (e: Exception) {
            Log.e(TAG, "Rating processing error: ${e.message}", e)
            false
        }
    }

    suspend fun clearQueue() {
        withContext(Dispatchers.IO) {
            val items = dao.getAllPending()
            items.forEach { dao.deleteById(it.id) }
            Log.d(TAG, "Cleared ${items.size} items from queue")
        }
    }
}
