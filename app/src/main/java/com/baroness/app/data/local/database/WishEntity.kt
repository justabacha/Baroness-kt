package com.baroness.app.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "wishes")
data class WishEntity(
    @PrimaryKey
    val id: Long,
    val text: String,
    val wishDate: String,          // "yyyy-MM-dd"
    val status: String,            // "planning" or "dusted"
    val creatorId: String,         // "phesty_official" or "baroness_official"
    val createdAt: Long            // timestamp
)