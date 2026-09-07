package com.baroness.app.models

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val editedAt: Long? = null,
    val serverTimestamp: Long? = null,
    val status: String,
    val isDeleted: Boolean = false,
    val reactions: String = "{}"
)
