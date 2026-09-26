package com.baroness.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baroness.app.data.local.database.ClockItemEntity

@Dao
interface ClockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClockItem(item: ClockItemEntity): Long

    @Query("SELECT * FROM clock_items WHERE isActive = 1 AND triggerTimeMs > :currentTimeMs ORDER BY triggerTimeMs ASC")
    suspend fun getActiveClockItems(currentTimeMs: Long = System.currentTimeMillis()): List<ClockItemEntity>

    @Query("SELECT * FROM clock_items WHERE id = :id")
    suspend fun getClockItemById(id: Long): ClockItemEntity?

    @Query("UPDATE clock_items SET isActive = 0 WHERE id = :id")
    suspend fun deactivateClockItem(id: Long)

    @Query("DELETE FROM clock_items WHERE id = :id")
    suspend fun deleteClockItem(id: Long)
}
