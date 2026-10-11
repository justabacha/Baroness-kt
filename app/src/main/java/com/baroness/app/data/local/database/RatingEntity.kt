package com.baroness.app.data.local.database

import androidx.room.Entity

@Entity(
    tableName = "ratings",
    primaryKeys = ["wishClientUuid", "personaId"]
)
data class RatingEntity(
    val wishClientUuid: String,
    val personaId: String,
    val rating: Int
)
