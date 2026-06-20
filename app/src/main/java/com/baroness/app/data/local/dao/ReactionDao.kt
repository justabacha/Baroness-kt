package com.baroness.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baroness.app.data.local.database.ReactionEntity

@Dao
interface ReactionDao {
    @Query("SELECT * FROM reactions WHERE wishId = :wishId")
    suspend fun getReactionsForWish(wishId: Long): List<ReactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: ReactionEntity)

    @Query("DELETE FROM reactions WHERE wishId = :wishId AND personaId = :personaId")
    suspend fun deleteReaction(wishId: Long, personaId: String)

    @Query("DELETE FROM reactions WHERE wishId = :wishId")
    suspend fun deleteReactionsForWish(wishId: Long)
}