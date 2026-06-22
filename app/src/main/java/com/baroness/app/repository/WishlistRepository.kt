package com.baroness.app.repository

import android.content.Context
import android.util.Log
import com.baroness.app.api.WishlistApi
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
import com.baroness.app.utils.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import org.json.JSONObject

private const val TAG = "WishlistRepo"

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

class WishlistRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val wishDao: WishDao = db.wishDao()
    private val reactionDao: ReactionDao = db.reactionDao()
    private val ratingDao: RatingDao = db.ratingDao()
    private val syncManager = SyncManager(context)
    private val sessionManager = SessionManager(context)
    private val supabase = SupabaseConfig.supabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isSubscribed = false

    // Expose profiles as StateFlow so ViewModel + UI can observe
    private val _profiles = MutableStateFlow<Map<String, ProfileDto>>(emptyMap())
    val profiles: StateFlow<Map<String, ProfileDto>> = _profiles.asStateFlow()

    init {
        scope.launch {
            fetchProfiles()
            subscribeToRealtime()
            initialSync()
        }
    }

    private suspend fun fetchProfiles() {
        try {
            Log.d(TAG, "Fetching profiles")
            val profiles = supabase.postgrest["profiles"]
                .select {
                    filter {
                        or {
                            eq("id", "phesty_official")
                            eq("id", "baroness_official")
                        }
                    }
                }
                .decodeList<ProfileDto>()

            Log.d(TAG, "Fetched ${profiles.size} profiles")
            val profileMap = profiles.associateBy { it.id }
            profiles.forEach { profile ->
                Log.d(TAG, "Profile: ${profile.id} -> ${profile.displayName}, avatar=${profile.avatarUrl}")
            }
            _profiles.value = profileMap
        } catch (e: Exception) {
            Log.e(TAG, "Profile fetch failed: ${e.message}", e)
        }
    }

    private suspend fun initialSync() {
        try {
            Log.d(TAG, "Starting initial sync from Supabase")
            val remoteWishes = WishlistApi.fetchAllWishes()
            Log.d(TAG, "Fetched ${remoteWishes.size} wishes from Supabase")
            remoteWishes.forEach { dto ->
                val entity = WishEntity(
                    id = dto.id ?: return@forEach,
                    text = dto.text,
                    wishDate = dto.wishDate,
                    status = dto.status,
                    creatorId = dto.creatorId,
                    createdAt = System.currentTimeMillis(),
                    syncStatus = "synced"
                )
                wishDao.insertWish(entity)
                Log.d(TAG, "Inserted remote wish id=${dto.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initial sync failed: ${e.message}", e)
        }
    }

    // CRITICAL FIX: Remove old channels before creating new ones to avoid
    // "postgresChangeFlow after joining" error when app restarts
    private suspend fun subscribeToRealtime() {
        if (isSubscribed) {
            Log.d(TAG, "Already subscribed, skipping")
            return
        }

        try {
            Log.d(TAG, "Connecting to Supabase realtime")
            supabase.realtime.connect()

            // CRITICAL: Remove any existing channels with these names
            // to prevent "reusing subscribed channel" error
            supabase.realtime.subscriptions.entries.forEach { (name, channel) ->
                if (name.startsWith("wishlist-")) {
                    Log.d(TAG, "Removing old channel: $name")
                    supabase.realtime.removeChannel(channel)
                }
            }

            // Use unique channel names with timestamp to guarantee freshness
            val timestamp = System.currentTimeMillis()

            // ─── WISHES CHANNEL ───
            val wishChannel = supabase.realtime.channel("wishlist-items-$timestamp")
            val wishFlow = wishChannel.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "wishlist_items"
            }
            // Collect in separate coroutine so subscribe() can run after
            scope.launch {
                try {
                    wishFlow.collect { action ->
                        Log.d(TAG, "Realtime wish action: ${action::class.simpleName}")
                        handleWishChange(action)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Wish flow collection error: ${e.message}", e)
                }
            }
            wishChannel.subscribe()
            Log.d(TAG, "Subscribed to wishlist_items channel")

            // ─── REACTIONS CHANNEL ───
            val rxChannel = supabase.realtime.channel("reactions-$timestamp")
            val reactionFlow = rxChannel.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "wishlist_reactions"
            }
            scope.launch {
                try {
                    reactionFlow.collect { action ->
                        Log.d(TAG, "Realtime reaction action: ${action::class.simpleName}")
                        handleReactionChange(action)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Reaction flow collection error: ${e.message}", e)
                }
            }
            rxChannel.subscribe()
            Log.d(TAG, "Subscribed to reactions channel")

            // ─── RATINGS CHANNEL ───
            val ratChannel = supabase.realtime.channel("ratings-$timestamp")
            val ratingFlow = ratChannel.postgresChangeFlow<PostgresAction>(
                schema = "public"
            ) {
                table = "wishlist_ratings"
            }
            scope.launch {
                try {
                    ratingFlow.collect { action ->
                        Log.d(TAG, "Realtime rating action: ${action::class.simpleName}")
                        handleRatingChange(action)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Rating flow collection error: ${e.message}", e)
                }
            }
            ratChannel.subscribe()
            Log.d(TAG, "Subscribed to ratings channel")

            isSubscribed = true
            Log.d(TAG, "All realtime channels subscribed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Realtime subscription error: ${e.message}", e)
            isSubscribed = false
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

                    Log.d(TAG, "Realtime wish change: id=$id, status=$status, creator=$creatorId")

                    val entity = WishEntity(id, text, wishDate, status, creatorId, System.currentTimeMillis(), "synced")
                    wishDao.insertWish(entity)
                }
                is PostgresAction.Delete -> {
                    val id = action.oldRecord["id"]?.jsonPrimitive?.longOrNull ?: return
                    Log.d(TAG, "Realtime wish delete: id=$id")
                    wishDao.deleteWish(id)
                    reactionDao.deleteReactionsForWish(id)
                    ratingDao.deleteRatingsForWish(id)
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wish change error: ${e.message}", e)
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
            Log.e(TAG, "Reaction change error: ${e.message}", e)
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
            Log.e(TAG, "Rating change error: ${e.message}", e)
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

    fun getStats(): Flow<WishStats> {
        return combine(
            wishDao.getAllWishes().map { it.size },
            wishDao.getAllWishes().map { list -> list.count { it.status == "dusted" } }
        ) { total, dusted ->
            WishStats(total, dusted)
        }
    }

    suspend fun insertWish(wish: Wish) {
        Log.d(TAG, "Inserting wish: id=${wish.id}, text=${wish.text.take(20)}, creator=${wish.creator}")

        val isTempId = wish.id < 0
        val entity = WishEntity(
            id = wish.id,
            text = wish.text,
            wishDate = wish.date,
            status = wish.status,
            creatorId = if (wish.creator == "P") "phesty_official" else "baroness_official",
            createdAt = wish.createdAt,
            syncStatus = if (isTempId) "pending_create" else "synced"
        )
        wishDao.insertWish(entity)
        Log.d(TAG, "Wish inserted to Room: id=${wish.id}, creatorId=${entity.creatorId}")

        wish.reactions.forEach { (key, emoji) ->
            val personaId = if (key == "P") "phesty_official" else "baroness_official"
            reactionDao.insertReaction(ReactionEntity(wish.id, personaId, emoji))
        }
        wish.ratings.forEach { (key, rating) ->
            val personaId = if (key == "P") "phesty_official" else "baroness_official"
            ratingDao.insertRating(RatingEntity(wish.id, personaId, rating))
        }

        val payload = JSONObject().apply {
            put("text", wish.text)
            put("wish_date", wish.date)
            put("status", wish.status)
            put("creator_id", if (wish.creator == "P") "phesty_official" else "baroness_official")
            put("created_at", wish.createdAt)
        }.toString()

        syncManager.queueItem(
            tableName = "wishlist_items",
            recordId = wish.id.toString(),
            operation = "create",
            payload = payload,
            localTempId = if (isTempId) wish.id else null
        )

        Log.d(TAG, "Queued for sync: tempId=${wish.id}")
        syncNow()
    }

    suspend fun deleteWish(wishId: Long) {
        Log.d(TAG, "Deleting wish: id=$wishId")
        reactionDao.deleteReactionsForWish(wishId)
        ratingDao.deleteRatingsForWish(wishId)
        wishDao.deleteWish(wishId)
        syncManager.queueItem("wishlist_items", wishId.toString(), "delete", "")
        syncNow()
    }

    suspend fun updateWishStatus(wishId: Long, status: String) {
        Log.d(TAG, "Updating wish status: id=$wishId, status=$status")
        wishDao.updateStatus(wishId, status)
        val payload = JSONObject().apply {
            put("status", status)
            put("updated_at", System.currentTimeMillis())
        }.toString()
        syncManager.queueItem("wishlist_items", wishId.toString(), "update", payload)
        syncNow()
    }

    suspend fun saveReaction(wishId: Long, personaId: String, emoji: String) {
        Log.d(TAG, "Saving reaction: wishId=$wishId, persona=$personaId")
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
        Log.d(TAG, "Saving rating: wishId=$wishId, persona=$personaId, rating=$rating")
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
        Log.d(TAG, "Triggering sync")
        withContext(Dispatchers.IO) {
            syncManager.processQueue()
        }
    }
}