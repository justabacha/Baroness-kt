package com.baroness.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.baroness.app.data.local.database.SyncQueueItem

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<SyncQueueItem>

    @Query("SELECT * FROM sync_queue WHERE clientUuid = :uuid ORDER BY createdAt ASC")
    suspend fun getPendingForWish(uuid: String): List<SyncQueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueItem)

    @Update
    suspend fun update(item: SyncQueueItem)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE clientUuid = :uuid")
    suspend fun deleteAllForWish(uuid: String)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE retryCount < 5")
    suspend fun getPendingCount(): Int

    @Query("SELECT * FROM sync_queue WHERE id = :id")
    suspend fun getById(id: Long): SyncQueueItem?

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}
