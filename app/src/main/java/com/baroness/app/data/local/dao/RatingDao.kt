package com.baroness.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baroness.app.data.local.database.RatingEntity

@Dao
interface RatingDao {
    @Query("SELECT * FROM ratings WHERE wishId = :wishId")
    suspend fun getRatingsForWish(wishId: Long): List<RatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: RatingEntity)

    @Query("DELETE FROM ratings WHERE wishId = :wishId AND personaId = :personaId")
    suspend fun deleteRating(wishId: Long, personaId: String)

    @Query("DELETE FROM ratings WHERE wishId = :wishId")
    suspend fun deleteRatingsForWish(wishId: Long)
}