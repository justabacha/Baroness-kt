package com.baroness.app.data.local.database

import androidx.room.Entity

@Entity(
    tableName = "ratings",
    primaryKeys = ["wishId", "personaId"]
)
data class RatingEntity(
    val wishId: Long,
    val personaId: String,
    val rating: Int           // 1-5
)