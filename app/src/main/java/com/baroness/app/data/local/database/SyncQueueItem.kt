package com.baroness.app.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableName: String,        // "wishlist_items", "wishlist_reactions", "wishlist_ratings"
    val recordId: String,         // the local ID of the record (as String for flexibility)
    val localTempId: Long? = null, // ONLY for create operations: the negative temp ID to swap later
    val operation: String,        // "create", "update", "delete"
    val payload: String,          // JSON payload to send
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)
