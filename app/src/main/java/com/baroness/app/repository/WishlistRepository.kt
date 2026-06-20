package com.baroness.app.repository

import android.content.Context
import com.baroness.app.data.local.dao.WishDao
import com.baroness.app.data.local.dao.ReactionDao
import com.baroness.app.data.local.dao.RatingDao
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.data.local.database.WishEntity
import com.baroness.app.data.local.database.ReactionEntity
import com.baroness.app.data.local.database.RatingEntity
import com.baroness.app.models.Wish
import com.baroness.app.models.WishStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WishlistRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val wishDao: WishDao = db.wishDao()
    private val reactionDao: ReactionDao = db.reactionDao()
    private val ratingDao: RatingDao = db.ratingDao()

    fun getAllWishes(): Flow<List<Wish>> {
        return wishDao.getAllWishes().map { entities ->
            entities.map { entity ->
                val reactions = reactionDao.getReactionsForWish(entity.id)
                    .associate { reaction ->
                        val key = if (reaction.personaId == "phesty_official") "P" else "B"
                        key to reaction.emoji
                    }
                val ratings = ratingDao.getRatingsForWish(entity.id)
                    .associate { rating ->
                        val key = if (rating.personaId == "phesty_official") "P" else "B"
                        key to rating.rating
                    }
                Wish(
                    id = entity.id,
                    text = entity.text,
                    date = entity.wishDate,
                    status = entity.status,
                    creator = if (entity.creatorId == "phesty_official") "P" else "B",
                    reactions = reactions,
                    ratings = ratings,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    suspend fun getStats(): WishStats {
        val total = wishDao.getTotalCount()
        val dusted = wishDao.getDustedCount()
        return WishStats(total, dusted)
    }

    suspend fun insertWish(wish: Wish) {
        val entity = WishEntity(
            id = wish.id,
            text = wish.text,
            wishDate = wish.date,
            status = wish.status,
            creatorId = if (wish.creator == "P") "phesty_official" else "baroness_official",
            createdAt = wish.createdAt
        )
        wishDao.insertWish(entity)

        wish.reactions.forEach { (key, emoji) ->
            val personaId = if (key == "P") "phesty_official" else "baroness_official"
            reactionDao.insertReaction(ReactionEntity(wish.id, personaId, emoji))
        }
        wish.ratings.forEach { (key, rating) ->
            val personaId = if (key == "P") "phesty_official" else "baroness_official"
            ratingDao.insertRating(RatingEntity(wish.id, personaId, rating))
        }
    }

    suspend fun insertAllWishes(wishes: List<Wish>) {
        wishDao.clearAll()
        wishes.forEach { insertWish(it) }
    }

    suspend fun deleteWish(wishId: Long) {
        reactionDao.deleteReactionsForWish(wishId)
        ratingDao.deleteRatingsForWish(wishId)
        wishDao.deleteWish(wishId)
    }

    suspend fun updateWishStatus(wishId: Long, status: String) {
        wishDao.updateStatus(wishId, status)
    }

    suspend fun saveReaction(wishId: Long, personaId: String, emoji: String) {
        reactionDao.insertReaction(ReactionEntity(wishId, personaId, emoji))
    }

    suspend fun saveRating(wishId: Long, personaId: String, rating: Int) {
        ratingDao.insertRating(RatingEntity(wishId, personaId, rating))
    }
}