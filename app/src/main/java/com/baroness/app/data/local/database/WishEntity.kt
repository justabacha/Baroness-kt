package com.baroness.app.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wishes")
data class WishEntity(
    @PrimaryKey val clientUuid: String,
    val serverId: Long?,
    val text: String,
    val wishDate: String,
    val status: String,
    val creatorId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String
)
