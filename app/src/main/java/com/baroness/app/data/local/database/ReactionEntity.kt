package com.baroness.app.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "reactions",
    primaryKeys = ["wishId", "personaId"]   // composite key
)
data class ReactionEntity(
    val wishId: Long,
    val personaId: String,   // "phesty_official" or "baroness_official"
    val emoji: String
)