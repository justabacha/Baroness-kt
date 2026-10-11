package com.baroness.app.utils

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.baroness.app.api.RatingDto
import com.baroness.app.api.ReactionDto
import com.baroness.app.api.WishlistApi
import com.baroness.app.data.local.dao.RatingDao
import com.baroness.app.data.local.dao.ReactionDao
import com.baroness.app.data.local.dao.SyncQueueDao
import com.baroness.app.data.local.dao.WishDao
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.data.local.database.SyncQueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "SyncManager"

class SyncManager(
    context: Context,
    private val applyWishDelete: suspend (String) -> Unit
) {
    private val db = AppDatabase.getInstance(context)
    private val dao: SyncQueueDao = db.syncQueueDao()
    private val wishDao: WishDao = db.wishDao()
    private val reactionDao: ReactionDao = db.reactionDao()
    private val ratingDao: RatingDao = db.ratingDao()

    suspend fun queueItem(
        tableName: String,
        clientUuid: String,
        operation: String,
        payload: String
    ) {
        dao.insert(
            SyncQueueItem(
                tableName = tableName,
                clientUuid = clientUuid,
                operation = operation,
                payload = payload
            )
        )
        Log.d(TAG, "Queued: $operation for $tableName (clientUuid=$clientUuid)")
    }

    suspend fun processQueue() {
        withContext(Dispatchers.IO) {
            val processedItemIds = mutableSetOf<Long>()
            while (true) {
                val eligible = dao.getAllPending().filter { it.id !in processedItemIds }
                if (eligible.isEmpty()) break
                Log.d(TAG, "Processing ${eligible.size} queue items")

                for (item in eligible) {
                    processedItemIds += item.id
                    if (item.retryCount >= 5) {
                        markWishFailed(item)
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
                            if (item.tableName == "wishlist_items" && item.operation == "delete") {
                                applyWishDelete(item.clientUuid)
                            } else {
                                acknowledgeWishOperation(item)
                            }
                            dao.deleteById(item.id)
                            Log.d(TAG, "Synced and removed item ${item.id}")
                        } else {
                            recordFailure(item, "Sync attempt failed")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing item ${item.id}: ${e.message}", e)
                        recordFailure(item, e.message ?: "Unknown sync error")
                    }
                }
            }
        }
    }

    private suspend fun acknowledgeWishOperation(item: SyncQueueItem) {
        db.withTransaction {
            val remaining = dao.getPendingForWish(item.clientUuid)
            if (remaining.size <= 1) {
                val wish = wishDao.getWishByUuid(item.clientUuid) ?: return@withTransaction
                if (wish.syncStatus !in setOf("pending_delete", "failed_delete")) {
                    wishDao.updateSyncStatus(item.clientUuid, "synced")
                }
            }
        }
    }

    private suspend fun recordFailure(item: SyncQueueItem, message: String) {
        val updated = item.copy(
            retryCount = item.retryCount + 1,
            lastError = message
        )
        dao.update(updated)
        if (updated.retryCount >= 5) markWishFailed(item)
        Log.w(TAG, "Failed to sync item ${item.id}, retry ${updated.retryCount}")
    }

    private suspend fun markWishFailed(item: SyncQueueItem) {
        db.withTransaction {
            if (item.operation == "delete") {
                wishDao.markDeleteFailed(item.clientUuid)
            } else {
                wishDao.markFailedUnlessDeleting(item.clientUuid)
            }
        }
    }

    private suspend fun processWishItem(item: SyncQueueItem): Boolean {
        return when (item.operation) {
            "create" -> {
                val json = JSONObject(item.payload)
                val result = WishlistApi.createWish(
                    clientUuid = item.clientUuid,
                    text = json.getString("text"),
                    wishDate = json.getString("wish_date"),
                    status = json.getString("status"),
                    creatorId = json.getString("creator_id"),
                    createdAt = json.getLong("created_at")
                )
                val serverId = result?.id
                if (serverId == null) {
                    Log.e(TAG, "Create failed: result is null or missing server ID")
                    false
                } else {
                    db.withTransaction {
                        wishDao.setServerId(item.clientUuid, serverId)
                    }
                    true
                }
            }
            "update" -> {
                val json = JSONObject(item.payload)
                val wish = wishDao.getWishByUuid(item.clientUuid) ?: return false
                val serverId = wish.serverId ?: return false
                WishlistApi.updateWish(
                    serverId = serverId,
                    status = json.getString("status"),
                    updatedAt = json.getLong("updated_at")
                )
            }
            "delete" -> {
                val wish = wishDao.getWishByUuid(item.clientUuid) ?: return true
                val serverId = wish.serverId ?: return true
                WishlistApi.deleteWish(serverId)
            }
            else -> {
                Log.w(TAG, "Unknown wishlist operation: ${item.operation}")
                false
            }
        }
    }

    private suspend fun processReaction(item: SyncQueueItem): Boolean {
        val wish = wishDao.getWishByUuid(item.clientUuid) ?: return false
        val serverId = wish.serverId ?: return false
        val json = JSONObject(item.payload)
        return WishlistApi.upsertReaction(
            ReactionDto(
                wishId = serverId,
                wishClientUuid = item.clientUuid,
                personaId = json.getString("persona_id"),
                emoji = json.getString("emoji")
            )
        )
    }

    private suspend fun processRating(item: SyncQueueItem): Boolean {
        val wish = wishDao.getWishByUuid(item.clientUuid) ?: return false
        val serverId = wish.serverId ?: return false
        val json = JSONObject(item.payload)
        return WishlistApi.upsertRating(
            RatingDto(
                wishId = serverId,
                wishClientUuid = item.clientUuid,
                personaId = json.getString("persona_id"),
                rating = json.getInt("rating")
            )
        )
    }

    suspend fun clearQueue() {
        withContext(Dispatchers.IO) {
            val items = dao.getAllPending()
            items.forEach { dao.deleteById(it.id) }
            Log.d(TAG, "Cleared ${items.size} items from queue")
        }
    }
}
