package com.baroness.app.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clock_items")
data class ClockItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "TIMER" or "ALARM"
    val triggerTimeMs: Long,
    val label: String,
    val isActive: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)
