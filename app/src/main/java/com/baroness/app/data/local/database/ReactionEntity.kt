package com.baroness.app.data.local.database

import androidx.room.Entity

@Entity(
    tableName = "reactions",
    primaryKeys = ["wishClientUuid", "personaId"]
)
data class ReactionEntity(
    val wishClientUuid: String,
    val personaId: String,
    val emoji: String
)
