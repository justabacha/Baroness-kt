package com.baroness.app.data.local.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"], name = "index_messages_conversationId"),
        Index(value = ["timestamp"], name = "index_messages_timestamp")
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val editedAt: Long? = null,
    val serverTimestamp: Long? = null,
    val status: String,
    val isDeleted: Boolean = false,
    val reactions: String = "{}" // JSON map of emoji -> List of userIds
)
