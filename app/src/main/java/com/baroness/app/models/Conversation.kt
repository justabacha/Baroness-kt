package com.baroness.app.models

data class Conversation(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageTimestamp: Long? = null,
    val type: String // e.g., "human", "ai", "group"
)
