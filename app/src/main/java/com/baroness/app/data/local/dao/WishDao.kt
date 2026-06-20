package com.baroness.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baroness.app.data.local.database.WishEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishDao {
    @Query("SELECT * FROM wishes ORDER BY wishDate ASC, createdAt ASC")
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
}