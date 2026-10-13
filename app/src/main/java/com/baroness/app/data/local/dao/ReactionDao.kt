package com.baroness.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baroness.app.data.local.database.ReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReactionDao {
    @Query("SELECT * FROM reactions WHERE wishClientUuid = :uuid")
    fun getReactionsForWish(uuid: String): Flow<List<ReactionEntity>>

    @Query("SELECT * FROM reactions")
    fun getAllReactions(): Flow<List<ReactionEntity>>

    @Query("SELECT * FROM reactions")
    fun getAllReactionsSync(): List<ReactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: ReactionEntity)

    @Query("DELETE FROM reactions WHERE wishClientUuid = :uuid AND personaId = :personaId")
    suspend fun deleteReaction(uuid: String, personaId: String)

    @Query("DELETE FROM reactions WHERE wishClientUuid = :uuid")
    suspend fun deleteReactionsForWish(uuid: String)

    @Query("DELETE FROM reactions")
    suspend fun clearAll()
}
