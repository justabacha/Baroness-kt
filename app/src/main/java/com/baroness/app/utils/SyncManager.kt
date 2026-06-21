package com.baroness.app.utils

import android.content.Context
import com.baroness.app.api.WishlistApi
import com.baroness.app.data.local.dao.SyncQueueDao
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.data.local.database.SyncQueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SyncManager(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao: SyncQueueDao = db.syncQueueDao()

    suspend fun queueItem(
        tableName: String,
        recordId: String,
        operation: String,
        payload: String
    ) {
        val item = SyncQueueItem(
            tableName = tableName,
            recordId = recordId,
            operation = operation,
            payload = payload
        )
        dao.insert(item)
        // TODO: trigger immediate sync if online
    }

    suspend fun processQueue() {
        withContext(Dispatchers.IO) {
            val pending = dao.getAllPending()
            for (item in pending) {
                try {
                    val success = when (item.tableName) {
                        "wishlist_items" -> when (item.operation) {
                            "create" -> WishlistApi.createWish(item.payload) != null
                            "update" -> WishlistApi.updateWish(item.recordId, item.payload)
                            "delete" -> WishlistApi.deleteWish(item.recordId)
                            else -> false
                        }
                        "wishlist_reactions" -> {
                            val json = JSONObject(item.payload)
                            WishlistApi.upsertReaction(
                                json.getString("wish_id"),
                                json.getString("persona_id"),
                                json.getString("emoji")
                            )
                        }
                        "wishlist_ratings" -> {
                            val json = JSONObject(item.payload)
                            WishlistApi.upsertRating(
                                json.getString("wish_id"),
                                json.getString("persona_id"),
                                json.getInt("rating")
                            )
                        }
                        else -> false
                    }
                    if (success) {
                        dao.deleteById(item.id)
                    } else {
                        val updated = item.copy(retryCount = item.retryCount + 1)
                        dao.update(updated)
                    }
                } catch (_: Exception) {
                    val updated = item.copy(retryCount = item.retryCount + 1)
                    dao.update(updated)
                }
            }
        }
    }

    suspend fun clearQueue() {
        withContext(Dispatchers.IO) {
            val items = dao.getAllPending()
            items.forEach { dao.deleteById(it.id) }
        }
    }
}