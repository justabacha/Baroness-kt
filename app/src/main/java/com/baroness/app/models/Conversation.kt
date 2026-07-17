package com.baroness.app.models

/**
 * Represents a conversation in the inbox.
 * Phase 1: Using static data models.
 */
data class Conversation(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val personaType: PersonaType
)

enum class PersonaType {
    HUMAN,
    AI
}
