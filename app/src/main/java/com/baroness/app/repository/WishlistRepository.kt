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
import com.baroness.app.utils.parseIsoToLong
import com.baroness.app.workers.SyncWorker
import com.baroness.app.utils.StorageManager
import com.baroness.app.data.models.NotificationData
import com.baroness.app.viewmodels.NotificationViewModel
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
import androidx.work.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "WishlistRepo"

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

class WishlistRepository private constructor(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val wishDao: WishDao = db.wishDao()
    private val reactionDao: ReactionDao = db.reactionDao()
    private val ratingDao: RatingDao = db.ratingDao()
    private val syncManager = SyncManager(context)
    private val storageManager = StorageManager(context)
    private val supabase = SupabaseConfig.supabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

    private var notificationViewModel: NotificationViewModel? = null

    fun setNotificationViewModel(viewModel: NotificationViewModel) {
        Log.d(TAG, "Setting NotificationViewModel for repository instance")
        this.notificationViewModel = viewModel
    }

    private var isSubscribed = false

    private val _profiles = MutableStateFlow<Map<String, ProfileDto>>(emptyMap())
    val profiles: StateFlow<Map<String, ProfileDto>> = _profiles.asStateFlow()

    init {
        scope.launch {
            fetchProfiles()
            subscribeToRealtime()
            setupNetworkListener(context)
            setupLifecycleObserver()
        }
    }

    private fun setupNetworkListener(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available, triggering sync")
                scope.launch {
                    fetchRemoteWishes()
                    syncNow()
                }
            }
        })
    }

    private fun setupLifecycleObserver() {
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    Log.d(TAG, "App resumed, triggering sync")
                    scope.launch {
                        fetchRemoteWishes()
                        syncNow()
                    }
                }
            })
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: WishlistRepository? = null

        fun getInstance(context: Context): WishlistRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WishlistRepository(context.applicationContext).also { INSTANCE = it }
            }
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

    suspend fun fetchRemoteWishes() {
        initialSync()
    }

    private suspend fun initialSync() {
        try {
            Log.d(TAG, "Starting initial sync from Supabase")

            val remoteWishes = WishlistApi.fetchAllWishes()
            val remoteReactions = WishlistApi.fetchAllReactions()
            val remoteRatings = WishlistApi.fetchAllRatings()

            Log.d(TAG, "Fetched ${remoteWishes.size} wishes, ${remoteReactions.size} reactions, ${remoteRatings.size} ratings")

            remoteWishes.forEach { dto ->
                val entity = WishEntity(
                    id = dto.id ?: return@forEach,
                    text = dto.text,
                    wishDate = dto.wishDate,
                    status = dto.status,
                    creatorId = dto.creatorId,
                    createdAt = parseIsoToLong(dto.createdAt),
                    updatedAt = parseIsoToLong(dto.updatedAt),
                    syncStatus = "synced"
                )
                wishDao.upsertWithTimestampCheck(entity)
                Log.d(TAG, "Upserted remote wish id=${dto.id}")
            }

            //insert reaction
            remoteReactions.forEach { dto ->
                reactionDao.insertReaction(ReactionEntity(
                    wishId = dto.wishId,
                    personaId = dto.personaId,
                    emoji = dto.emoji
                ))
            }
            Log.d(TAG, "Inserted ${remoteReactions.size} reactions")

            // Insert ratings
            remoteRatings.forEach { dto ->
                ratingDao.insertRating(RatingEntity(
                    wishId = dto.wishId,
                    personaId = dto.personaId,
                    rating = dto.rating
                ))
            }
            Log.d(TAG, "Inserted ${remoteRatings.size} ratings")

        } catch (e: Exception) {
            Log.e(TAG, "Initial sync failed: ${e.message}", e)
        }
    }

    private suspend fun subscribeToRealtime() {
        if (isSubscribed) {
            Log.d(TAG, "Already subscribed, skipping")
            return
        }

        scope.launch {
            var retryDelay = 1000L
            while (isActive) {
                try {
                    Log.d(TAG, "Connecting to Supabase realtime")
                    supabase.realtime.connect()

                    val channelsToRemove = supabase.realtime.subscriptions.keys.toList()
                    channelsToRemove.forEach { name ->
                        Log.d(TAG, "Removing old channel: $name")
                        supabase.realtime.removeChannel(supabase.realtime.subscriptions[name]!!)
                    }

                    val timestamp = System.currentTimeMillis()

                    // ─── WISHES CHANNEL ───
                    val wishChannel = supabase.realtime.channel("wishlist-items-$timestamp")
                    val wishFlow = wishChannel.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    ) {
                        table = "wishlist_items"
                    }
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
                    retryDelay = 1000L // Reset on success

                    // Break the loop once successfully subscribed.
                    // Supabase-kt's internal mechanisms handle reconnection of individual channels.
                    // If the entire connection is lost and cannot be recovered, this coroutine can be restarted.
                    break
                } catch (e: Exception) {
                    isSubscribed = false
                    Log.e(TAG, "Realtime subscription error: ${e.message}. Retrying in ${retryDelay}ms")
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30000L)
                }
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
                    val createdAtStr = record["created_at"]?.jsonPrimitive?.content
                    val updatedAtStr = record["updated_at"]?.jsonPrimitive?.content

                    Log.d(TAG, "Realtime wish change: id=$id, status=$status, creator=$creatorId")

                    val currentPersonaId = storageManager.getString("currentPersonaId")
                    Log.d(TAG, "Comparing creatorId=$creatorId with currentPersonaId=$currentPersonaId")
                    
                    if (action is PostgresAction.Insert && creatorId != currentPersonaId) {
                        Log.d(TAG, "Triggering in-app notification for new wish from $creatorId")
                        val senderProfile = _profiles.value[creatorId]
                        val senderName = senderProfile?.displayName ?: "Someone"
                        val senderAvatar = senderProfile?.avatarUrl

                        notificationViewModel?.showInAppNotification(
                            NotificationData(
                                title = "$senderName added a new wish",
                                body = text,
                                avatarUrl = senderAvatar,
                                featureType = "wishlist",
                                route = "Wishlist"
                            )
                        )
                    }

                    val entity = WishEntity(
                        id = id,
                        text = text,
                        wishDate = wishDate,
                        status = status,
                        creatorId = creatorId,
                        createdAt = parseIsoToLong(createdAtStr),
                        updatedAt = parseIsoToLong(updatedAtStr),
                        syncStatus = "synced"
                    )
                    wishDao.upsertWithTimestampCheck(entity)
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
        return combine(
            wishDao.getAllWishes(),
            reactionDao.getAllReactions(),
            ratingDao.getAllRatings()
        ) { wishes, reactions, ratings ->
            wishes.map { entity ->
                val wishReactions = reactions
                    .filter { it.wishId == entity.id }
                    .associate { reaction ->
                        val key = if (reaction.personaId == "phesty_official") "P" else "B"
                        key to reaction.emoji
                    }
                val wishRatings = ratings
                    .filter { it.wishId == entity.id }
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
                    reactions = wishReactions,
                    ratings = wishRatings,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    fun getStats(): Flow<WishStats> {
        return wishDao.getAllWishes().map { list ->
            WishStats(
                total = list.size,
                dusted = list.count { it.status == "dusted" }
            )
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
        Log.d(TAG, "Enqueuing sync work")
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                30,
                TimeUnit.SECONDS
            )
            .build()
        workManager.enqueue(workRequest)
    }
}
