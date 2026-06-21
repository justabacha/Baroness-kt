package com.baroness.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baroness.app.data.local.database.WishEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishDao {
    // FIXED: Sort by createdAt ASC (oldest first = index 1) - consistent across devices
    @Query("SELECT * FROM wishes ORDER BY createdAt ASC")
    fun getAllWishes(): Flow<List<WishEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWish(wish: WishEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wishes: List<WishEntity>)

    @Query("DELETE FROM wishes WHERE id = :wishId")
    suspend fun deleteWish(wishId: Long)

    @Query("UPDATE wishes SET status = :status WHERE id = :wishId")
    suspend fun updateStatus(wishId: Long, status: String)

    @Query("SELECT COUNT(*) FROM wishes")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM wishes WHERE status = 'dusted'")
    suspend fun getDustedCount(): Int

    @Query("DELETE FROM wishes")
    suspend fun clearAll()

    @Query("UPDATE wishes SET id = :newServerId, syncStatus = 'synced' WHERE id = :oldTempId")
    suspend fun updateWishId(oldTempId: Long, newServerId: Long)

    @Query("SELECT * FROM wishes WHERE id = :wishId")
    suspend fun getWishById(wishId: Long): WishEntity?

    @Query("SELECT MAX(createdAt) FROM wishes")
    suspend fun getLastCreatedAt(): Long?
}
