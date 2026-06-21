package com.baroness.app.repository

import android.content.Context
import android.util.Log
import com.baroness.app.config.SupabaseConfig
import com.baroness.app.data.local.dao.WishDao
import com.baroness.app.data.local.dao.ReactionDao
import com.baroness.app.data.local.dao.RatingDao
import com.baroness.app.data.local.database.AppDatabase
import com.baroness.app.data.local.database.WishEntity
import com.baroness.app.data.local.database.ReactionEntity
import com.baroness.app.data.local.database.RatingEntity
import com.baroness.app.models.Wish
import com.baroness.app.models.WishStats
import com.baroness.app.utils.SyncManager
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import org.json.JSONObject

private const val TAG = "WishlistRepo"

class WishlistRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val wishDao: WishDao = db.wishDao()
    private val reactionDao: ReactionDao = db.reactionDao()
    private val ratingDao: RatingDao = db.ratingDao()
    private val syncManager = SyncManager(context)
    private val supabase = SupabaseConfig.supabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        subscribeToRealtime()
    }

    private fun subscribeToRealtime() {
        scope.launch {
            try {
                supabase.realtime.connect()

                val wishChannel = supabase.realtime.channel("wishlist-items-channel")
                val wishFlow = wishChannel.postgresChangeFlow<PostgresAction>(
                    schema = "public",
                    filter = { table = "wishlist_items" }
                )
                wishChannel.subscribe()

                val rxChannel = supabase.realtime.channel("reactions-channel")
                val reactionFlow = rxChannel.postgresChangeFlow<PostgresAction>(
                    schema = "public",
                    filter = { table = "wishlist_reactions" }
                )
                rxChannel.subscribe()

                val ratChannel = supabase.realtime.channel("ratings-channel")
                val ratingFlow = ratChannel.postgresChangeFlow<PostgresAction>(
                    schema = "public",
                    filter = { table = "wishlist_ratings" }
                )
                ratChannel.subscribe()

                Log.d(TAG, "Subscribed to realtime channels")

                launch { wishFlow.collect { action -> handleWishChange(action) } }
                launch { reactionFlow.collect { action -> handleReactionChange(action) } }
                launch { ratingFlow.collect { action -> handleRatingChange(action) } }

            } catch (e: Exception) {
                Log.e(TAG, "Realtime subscription error: ${e.message}")
            }
        }
    }
    private suspend fun handleWishChange(action: PostgresAction) {
        try {
            when (action) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    val record = (action as? PostgresAction.Insert)?.record
                        ?: (action as? PostgresAction.Update)?.record ?: return

                    val id = record["id"]?.jsonPrimitive?.longOrNull ?: return
                    val text = record["text"]?.jsonPrimitive?.content ?: ""
                    val wishDate = record["wish_date"]?.jsonPrimitive?.content ?: ""
                    val status = record["status"]?.jsonPrimitive?.content ?: "planning"
                    val creatorId = record["creator_id"]?.jsonPrimitive?.content ?: "phesty_official"
                    val createdAt = record["created_at"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

                    val entity = WishEntity(id, text, wishDate, status, creatorId, createdAt)
                    wishDao.insertWish(entity)
                }
                is PostgresAction.Delete -> {
                    val id = action.oldRecord["id"]?.jsonPrimitive?.longOrNull ?: return
                    wishDao.deleteWish(id)
                    reactionDao.deleteReactionsForWish(id)
                    ratingDao.deleteRatingsForWish(id)
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wish change error: ${e.message}")
        }
    }

    private suspend fun handleReactionChange(action: PostgresAction) {
        try {
            when (action) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    val record = (action as? PostgresAction.Insert)?.record
                        ?: (action as? PostgresAction.Update)?.record ?: return

                    val wishId = record["wish_id"]?.jsonPrimitive?.longOrNull ?: return
                    val personaId = record["persona_id"]?.jsonPrimitive?.content ?: return
                    val emoji = record["emoji"]?.jsonPrimitive?.content ?: return
                    reactionDao.insertReaction(ReactionEntity(wishId, personaId, emoji))
                }
                is PostgresAction.Delete -> {
                    val wishId = action.oldRecord["wish_id"]?.jsonPrimitive?.longOrNull ?: return
                    val personaId = action.oldRecord["persona_id"]?.jsonPrimitive?.content ?: return
                    reactionDao.deleteReaction(wishId, personaId)
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reaction change error: ${e.message}")
        }
    }

    private suspend fun handleRatingChange(action: PostgresAction) {
        try {
            when (action) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    val record = (action as? PostgresAction.Insert)?.record
                        ?: (action as? PostgresAction.Update)?.record ?: return

                    val wishId = record["wish_id"]?.jsonPrimitive?.longOrNull ?: return
                    val personaId = record["persona_id"]?.jsonPrimitive?.content ?: return
                    val rating = record["rating"]?.jsonPrimitive?.intOrNull ?: 0
                    ratingDao.insertRating(RatingEntity(wishId, personaId, rating))
                }
                is PostgresAction.Delete -> {
                    val wishId = action.oldRecord["wish_id"]?.jsonPrimitive?.longOrNull ?: return
                    val personaId = action.oldRecord["persona_id"]?.jsonPrimitive?.content ?: return
                    ratingDao.deleteRating(wishId, personaId)
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Rating change error: ${e.message}")
        }
    }
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

        val payload = JSONObject().apply {
            put("id", wish.id)
            put("text", wish.text)
            put("wish_date", wish.date)
            put("status", wish.status)
            put("creator_id", if (wish.creator == "P") "phesty_official" else "baroness_official")
            put("created_at", wish.createdAt)
        }.toString()

        syncManager.queueItem("wishlist_items", wish.id.toString(), "create", payload)
        syncNow()
    }

    suspend fun insertAllWishes(wishes: List<Wish>) {
        wishDao.clearAll()
        wishes.forEach { insertWish(it) }
    }

    suspend fun deleteWish(wishId: Long) {
        reactionDao.deleteReactionsForWish(wishId)
        ratingDao.deleteRatingsForWish(wishId)
        wishDao.deleteWish(wishId)
        syncManager.queueItem("wishlist_items", wishId.toString(), "delete", "")
        syncNow()
    }

    suspend fun updateWishStatus(wishId: Long, status: String) {
        wishDao.updateStatus(wishId, status)
        val payload = JSONObject().apply {
            put("status", status)
            put("updated_at", System.currentTimeMillis())
        }.toString()
        syncManager.queueItem("wishlist_items", wishId.toString(), "update", payload)
        syncNow()
    }

    suspend fun saveReaction(wishId: Long, personaId: String, emoji: String) {
        reactionDao.insertReaction(ReactionEntity(wishId, personaId, emoji))
        val payload = JSONObject().apply {
            put("wish_id", wishId)
            put("persona_id", personaId)
            put("emoji", emoji)
        }.toString()
        syncManager.queueItem("wishlist_reactions", "$wishId-$personaId", "upsert", payload)
        syncNow()
    }

    suspend fun saveRating(wishId: Long, personaId: String, rating: Int) {
        ratingDao.insertRating(RatingEntity(wishId, personaId, rating))
        val payload = JSONObject().apply {
            put("wish_id", wishId)
            put("persona_id", personaId)
            put("rating", rating)
        }.toString()
        syncManager.queueItem("wishlist_ratings", "$wishId-$personaId", "upsert", payload)
        syncNow()
    }

    suspend fun syncNow() {
        withContext(Dispatchers.IO) {
            syncManager.processQueue()
        }
    }

    fun cleanup() {
        scope.cancel()
    }
}