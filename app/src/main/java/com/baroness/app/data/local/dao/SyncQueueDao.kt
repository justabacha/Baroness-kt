package com.baroness.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.baroness.app.data.local.database.SyncQueueItem

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<SyncQueueItem>

    @Insert
    suspend fun insert(item: SyncQueueItem)

    @Update
    suspend fun update(item: SyncQueueItem)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE recordId = :recordId AND tableName = :tableName")
    suspend fun deleteByRecordId(recordId: String, tableName: String)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE retryCount < 5")
    suspend fun getPendingCount(): Int
}