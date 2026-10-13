package com.baroness.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baroness.app.data.local.database.WishEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishDao {
    @Query("SELECT * FROM wishes WHERE syncStatus NOT IN ('pending_delete', 'failed_delete') ORDER BY createdAt ASC, clientUuid ASC")
    fun getAllWishes(): Flow<List<WishEntity>>

    @Query("SELECT * FROM wishes WHERE syncStatus NOT IN ('pending_delete', 'failed_delete') ORDER BY createdAt ASC, clientUuid ASC")
    fun getAllWishesSync(): List<WishEntity>

    @Query("SELECT * FROM wishes WHERE clientUuid = :uuid")
    suspend fun getWishByUuid(uuid: String): WishEntity?

    @Query("SELECT * FROM wishes WHERE serverId = :serverId")
    suspend fun getWishByServerId(serverId: Long): WishEntity?

    @Query("SELECT * FROM wishes WHERE syncStatus = 'synced'")
    suspend fun getSyncedWishes(): List<WishEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWish(wish: WishEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wishes: List<WishEntity>)

    @Query("DELETE FROM wishes WHERE clientUuid = :uuid")
    suspend fun deleteWish(uuid: String)

    @Query("UPDATE wishes SET status = :status WHERE clientUuid = :uuid")
    suspend fun updateStatus(uuid: String, status: String)

    @Query("UPDATE wishes SET status = :status, syncStatus = 'pending_update', updatedAt = :updatedAt WHERE clientUuid = :uuid")
    suspend fun updateStatusAsPending(uuid: String, status: String, updatedAt: Long)

    @Query("UPDATE wishes SET syncStatus = :status WHERE clientUuid = :uuid")
    suspend fun updateSyncStatus(uuid: String, status: String)

    @Query("UPDATE wishes SET serverId = :serverId WHERE clientUuid = :uuid")
    suspend fun setServerId(uuid: String, serverId: Long)

    @Query("UPDATE wishes SET status = 'deleted', syncStatus = 'pending_delete' WHERE clientUuid = :uuid")
    suspend fun markPendingDelete(uuid: String)

    @Query("UPDATE wishes SET syncStatus = 'failed' WHERE clientUuid = :uuid AND syncStatus NOT IN ('pending_delete', 'failed_delete')")
    suspend fun markFailedUnlessDeleting(uuid: String)

    @Query("UPDATE wishes SET syncStatus = 'failed_delete' WHERE clientUuid = :uuid")
    suspend fun markDeleteFailed(uuid: String)

    @Query("SELECT COUNT(*) FROM wishes")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM wishes WHERE status = 'dusted'")
    suspend fun getDustedCount(): Int

    @Query("DELETE FROM wishes")
    suspend fun clearAll()

    @Query("SELECT MAX(createdAt) FROM wishes")
    suspend fun getLastCreatedAt(): Long?
}
