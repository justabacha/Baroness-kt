package com.baroness.app.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.baroness.app.api.WishlistApi
import com.baroness.app.api.WishDto
import com.baroness.app.auth.SessionTokenCache
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
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "WishlistRepo"
private const val CACHED_PROFILES_KEY = "wishlist_profiles_cache"

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

class WishlistRepository private constructor(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val wishDao: WishDao = db.wishDao()
    private val syncQueueDao = db.syncQueueDao()
    private val reactionDao: ReactionDao = db.reactionDao()
    private val ratingDao: RatingDao = db.ratingDao()
    private val syncManager = SyncManager(context) { clientUuid ->
        applyServerDelete(clientUuid)
    }
    private val syncMutex = Mutex()
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
    private var realtimeSubscriptionJob: Job? = null
    private val realtimeChannels = mutableListOf<RealtimeChannel>()
    private val realtimeCollectorJobs = mutableListOf<Job>()

    private val _profiles = MutableStateFlow<Map<String, ProfileDto>>(emptyMap())
    val profiles: StateFlow<Map<String, ProfileDto>> = _profiles.asStateFlow()

    @Volatile
    private var wishesSnapshot: List<Wish> = emptyList()
    @Volatile
    private var statsSnapshot: WishStats = WishStats(0, 0)

    init {
        scope.launch {
            loadCachedProfiles()
            fetchProfiles()
            subscribeToRealtime()
            setupNetworkListener(context)
            setupLifecycleObserver()
        }
        scope.launch {
            SessionTokenCache.tokenRefreshedFlow.collect {
                Log.d(TAG, "Auth token refreshed; restarting wishlist Realtime subscriptions")
                restartRealtimeSubscriptions()
            }
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
                    Log.d(TAG, "App resumed, refreshing profiles and wishlist")
                    scope.launch {
                        fetchProfiles()
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
            if (profileMap.isNotEmpty()) {
                try {
                    storageManager.saveString(CACHED_PROFILES_KEY, Json.encodeToString(profiles))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist fetched profiles: ${e.message}", e)
                }
                _profiles.value = profileMap
            } else {
                Log.w(TAG, "Profile fetch returned no rows; retaining last known profiles")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile fetch failed: ${e.message}", e)
        }
    }

    private suspend fun loadCachedProfiles() {
        try {
            val cachedProfiles = storageManager.getString(CACHED_PROFILES_KEY)
                ?.let { Json.decodeFromString<List<ProfileDto>>(it) }
                .orEmpty()
            if (cachedProfiles.isNotEmpty()) {
                _profiles.value = cachedProfiles.associateBy { it.id }
                Log.d(TAG, "Loaded ${cachedProfiles.size} cached profiles")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached profiles: ${e.message}", e)
        }
    }

    suspend fun fetchRemoteWishes() {
        initialSync()
    }

    suspend fun processSyncQueue() {
        syncMutex.withLock {
            syncManager.processQueue()
        }
    }

    private suspend fun initialSync() {
        syncMutex.withLock {
            try {
                Log.d(TAG, "Starting initial sync from Supabase")

                val remoteWishes = WishlistApi.fetchAllWishes()
                val remoteReactions = WishlistApi.fetchAllReactions()
                val remoteRatings = WishlistApi.fetchAllRatings()

                Log.d(TAG, "Fetched ${remoteWishes.size} wishes, ${remoteReactions.size} reactions, ${remoteRatings.size} ratings")
                if (remoteWishes.any { it.id == null || it.clientUuid == null }) {
                    throw IllegalStateException("Wishlist fetch returned a wish without a server ID or client UUID")
                }

                remoteWishes.forEach { applyServerRow(it) }
                Log.d(TAG, "Applied ${remoteWishes.size} remote wishes through the merge gate")

                remoteReactions.forEach { dto ->
                    reactionDao.insertReaction(ReactionEntity(
                        wishClientUuid = dto.wishClientUuid,
                        personaId = dto.personaId,
                        emoji = dto.emoji
                    ))
                }
                Log.d(TAG, "Inserted ${remoteReactions.size} reactions")

                remoteRatings.forEach { dto ->
                    ratingDao.insertRating(RatingEntity(
                        wishClientUuid = dto.wishClientUuid,
                        personaId = dto.personaId,
                        rating = dto.rating
                    ))
                }
                Log.d(TAG, "Inserted ${remoteRatings.size} ratings")

                reconcileSyncedWishes(remoteWishes.mapNotNull { it.clientUuid }.toSet())

            } catch (e: Exception) {
                Log.e(TAG, "Initial sync failed: ${e.message}", e)
            }
        }
    }

    suspend fun applyServerRow(dto: WishDto) {
        val uuid = dto.clientUuid ?: return
        db.withTransaction {
            val local = wishDao.getWishByUuid(uuid)
            val pending = syncQueueDao.getPendingForWish(uuid)
            val hasPendingCreate = pending.any { it.tableName == "wishlist_items" && it.operation == "create" }
            val hasPendingUpdate = pending.any { it.operation == "update" }
            val hasPendingDelete = pending.any { it.tableName == "wishlist_items" && it.operation == "delete" }
            val hasFailedPending = pending.any { it.retryCount >= 5 }

            if (hasPendingDelete) return@withTransaction
            if (hasPendingCreate) {
                if (local != null) {
                    val serverId = dto.id ?: return@withTransaction
                    wishDao.setServerId(uuid, serverId)
                }
                return@withTransaction
            }

            if (local == null) {
                val createdAt = dto.createdAt?.let(::parseIsoToLong) ?: System.currentTimeMillis()
                wishDao.insertWish(
                    WishEntity(
                        clientUuid = uuid,
                        serverId = dto.id,
                        text = dto.text,
                        wishDate = dto.wishDate,
                        status = dto.status,
                        creatorId = dto.creatorId,
                        createdAt = createdAt,
                        updatedAt = dto.updatedAt?.let(::parseIsoToLong) ?: createdAt,
                        syncStatus = when {
                            pending.isEmpty() -> "synced"
                            hasFailedPending -> "failed"
                            else -> "pending_update"
                        }
                    )
                )
                return@withTransaction
            }

            wishDao.insertWish(
                local.copy(
                    serverId = dto.id ?: local.serverId,
                    text = dto.text,
                    wishDate = dto.wishDate,
                    creatorId = dto.creatorId,
                    createdAt = local.createdAt,
                    updatedAt = dto.updatedAt?.let(::parseIsoToLong) ?: local.updatedAt,
                    status = if (hasPendingUpdate) local.status else dto.status,
                    syncStatus = when {
                        pending.isEmpty() -> "synced"
                        hasFailedPending -> "failed"
                        else -> "pending_update"
                    }
                )
            )
        }
    }

    suspend fun applyServerDelete(clientUuid: String) {
        db.withTransaction {
            reactionDao.deleteReactionsForWish(clientUuid)
            ratingDao.deleteRatingsForWish(clientUuid)
            wishDao.deleteWish(clientUuid)
            syncQueueDao.deleteAllForWish(clientUuid)
        }
    }

    private suspend fun reconcileSyncedWishes(remoteUuids: Set<String>) {
        db.withTransaction {
            val localSynced = wishDao.getSyncedWishes()
            val pendingUuids = syncQueueDao.getAllPending().map { it.clientUuid }.toSet()
            val stale = localSynced.filter {
                it.clientUuid !in remoteUuids && it.clientUuid !in pendingUuids
            }
            stale.forEach { wish ->
                reactionDao.deleteReactionsForWish(wish.clientUuid)
                ratingDao.deleteRatingsForWish(wish.clientUuid)
                wishDao.deleteWish(wish.clientUuid)
                Log.d(TAG, "Removed stale synced wish uuid=${wish.clientUuid}")
            }
        }
    }

    private suspend fun subscribeToRealtime() {
        if (isSubscribed) {
            Log.d(TAG, "Already subscribed, skipping")
            return
        }

        realtimeSubscriptionJob = scope.launch {
            var retryDelay = 1000L
            while (isActive) {
                try {
                    Log.d(TAG, "Connecting to Supabase realtime")
                    supabase.realtime.connect()

                    val channelsToRemove = supabase.realtime.subscriptions.keys
                        .filter {
                            it.startsWith("wishlist-items-") ||
                                it.startsWith("reactions-") ||
                                it.startsWith("ratings-") ||
                                it.startsWith("profiles-")
                        }
                        .toList()
                    channelsToRemove.forEach { name ->
                        Log.d(TAG, "Removing old channel: $name")
                        supabase.realtime.removeChannel(supabase.realtime.subscriptions[name]!!)
                    }

                    val timestamp = System.currentTimeMillis()

                    // ─── WISHES CHANNEL ───
                    val wishChannel = supabase.realtime.channel("wishlist-items-$timestamp")
                    realtimeChannels.add(wishChannel)
                    val wishFlow = wishChannel.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    ) {
                        table = "wishlist_items"
                    }
                    realtimeCollectorJobs.add(scope.launch {
                        try {
                            wishFlow.collect { action ->
                                Log.d(TAG, "Realtime wish action: ${action::class.simpleName}")
                                handleWishChange(action)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Wish flow collection error: ${e.message}", e)
                        }
                    })
                    wishChannel.subscribe()
                    Log.d(TAG, "Subscribed to wishlist_items channel")

                    // ─── REACTIONS CHANNEL ───
                    val rxChannel = supabase.realtime.channel("reactions-$timestamp")
                    realtimeChannels.add(rxChannel)
                    val reactionFlow = rxChannel.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    ) {
                        table = "wishlist_reactions"
                    }
                    realtimeCollectorJobs.add(scope.launch {
                        try {
                            reactionFlow.collect { action ->
                                Log.d(TAG, "Realtime reaction action: ${action::class.simpleName}")
                                handleReactionChange(action)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Reaction flow collection error: ${e.message}", e)
                        }
                    })
                    rxChannel.subscribe()
                    Log.d(TAG, "Subscribed to reactions channel")

                    // ─── RATINGS CHANNEL ───
                    val ratChannel = supabase.realtime.channel("ratings-$timestamp")
                    realtimeChannels.add(ratChannel)
                    val ratingFlow = ratChannel.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    ) {
                        table = "wishlist_ratings"
                    }
                    realtimeCollectorJobs.add(scope.launch {
                        try {
                            ratingFlow.collect { action ->
                                Log.d(TAG, "Realtime rating action: ${action::class.simpleName}")
                                handleRatingChange(action)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Rating flow collection error: ${e.message}", e)
                        }
                    })
                    ratChannel.subscribe()
                    Log.d(TAG, "Subscribed to ratings channel")

                    // ─── PROFILES CHANNEL ───
                    val profileChannel = supabase.realtime.channel("profiles-$timestamp")
                    realtimeChannels.add(profileChannel)
                    val profileFlow = profileChannel.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    ) {
                        table = "profiles"
                    }
                    realtimeCollectorJobs.add(scope.launch {
                        try {
                            profileFlow.collect { action ->
                                val record = when (action) {
                                    is PostgresAction.Insert -> action.record
                                    is PostgresAction.Update -> action.record
                                    is PostgresAction.Delete -> action.oldRecord
                                    else -> null
                                }
                                val profileId = record
                                    ?.get("id")
                                    ?.jsonPrimitive
                                    ?.content
                                if (profileId == "phesty_official" || profileId == "baroness_official") {
                                    Log.d(TAG, "Profile changed: $profileId; refreshing profiles")
                                    fetchProfiles()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Profile flow collection error: ${e.message}", e)
                        }
                    })
                    profileChannel.subscribe()
                    Log.d(TAG, "Subscribed to profiles channel")

                    isSubscribed = true
                    Log.d(TAG, "All realtime channels subscribed successfully")
                    retryDelay = 1000L // Reset on success

                    scope.launch {
                        try {
                            fetchRemoteWishes()
                            Log.d(TAG, "Triggered fetch remote wishes on realtime reconnect")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch remote wishes on realtime reconnect: ${e.message}", e)
                        }
                    }

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

    private suspend fun restartRealtimeSubscriptions() {
        realtimeSubscriptionJob?.cancelAndJoin()
        realtimeCollectorJobs.toList().forEach { it.cancelAndJoin() }
        realtimeCollectorJobs.clear()
        realtimeChannels.toList().forEach { channel ->
            channel.unsubscribe()
            supabase.realtime.removeChannel(channel)
        }
        realtimeChannels.clear()
        isSubscribed = false
        subscribeToRealtime()
    }

    private suspend fun handleWishChange(action: PostgresAction) {
        try {
            when (action) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    val record = (action as? PostgresAction.Insert)?.record
                        ?: (action as? PostgresAction.Update)?.record ?: return

                    val id = record["id"]?.jsonPrimitive?.longOrNull
                    val clientUuid = record["client_uuid"]?.jsonPrimitive?.content ?: return
                    val text = record["text"]?.jsonPrimitive?.content ?: return
                    val wishDate = record["wish_date"]?.jsonPrimitive?.content ?: return
                    val status = record["status"]?.jsonPrimitive?.content ?: return
                    val creatorId = record["creator_id"]?.jsonPrimitive?.content ?: return
                    val createdAtStr = record["created_at"]?.jsonPrimitive?.content
                    val updatedAtStr = record["updated_at"]?.jsonPrimitive?.content

                    Log.d(TAG, "Realtime wish change: uuid=$clientUuid, status=$status, creator=$creatorId")

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

                    applyServerRow(
                        WishDto(
                            id = id,
                            clientUuid = clientUuid,
                            text = text,
                            wishDate = wishDate,
                            status = status,
                            creatorId = creatorId,
                            createdAt = createdAtStr,
                            updatedAt = updatedAtStr
                        )
                    )
                }
                is PostgresAction.Delete -> {
                    val uuid = action.oldRecord["client_uuid"]?.jsonPrimitive?.content
                        ?: action.oldRecord["id"]?.jsonPrimitive?.longOrNull
                            ?.let { wishDao.getWishByServerId(it)?.clientUuid }
                        ?: return
                    Log.d(TAG, "Realtime wish delete: uuid=$uuid")
                    applyServerDelete(uuid)
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

                    val wishUuid = record["wish_client_uuid"]?.jsonPrimitive?.content ?: return
                    val personaId = record["persona_id"]?.jsonPrimitive?.content ?: return
                    val emoji = record["emoji"]?.jsonPrimitive?.content ?: return
                    reactionDao.insertReaction(ReactionEntity(wishUuid, personaId, emoji))
                }
                is PostgresAction.Delete -> {
                    val wishUuid = action.oldRecord["wish_client_uuid"]?.jsonPrimitive?.content ?: return
                    val personaId = action.oldRecord["persona_id"]?.jsonPrimitive?.content ?: return
                    reactionDao.deleteReaction(wishUuid, personaId)
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

                    val wishUuid = record["wish_client_uuid"]?.jsonPrimitive?.content ?: return
                    val personaId = record["persona_id"]?.jsonPrimitive?.content ?: return
                    val rating = record["rating"]?.jsonPrimitive?.intOrNull ?: 0
                    ratingDao.insertRating(RatingEntity(wishUuid, personaId, rating))
                }
                is PostgresAction.Delete -> {
                    val wishUuid = action.oldRecord["wish_client_uuid"]?.jsonPrimitive?.content ?: return
                    val personaId = action.oldRecord["persona_id"]?.jsonPrimitive?.content ?: return
                    ratingDao.deleteRating(wishUuid, personaId)
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Rating change error: ${e.message}", e)
        }
    }

    private fun mapToWishes(
        wishes: List<WishEntity>,
        reactions: List<ReactionEntity>,
        ratings: List<RatingEntity>
    ): List<Wish> {
        return wishes.map { entity ->
            val wishReactions = reactions
                .filter { it.wishClientUuid == entity.clientUuid }
                .associate { reaction ->
                    val key = if (reaction.personaId == "phesty_official") "P" else "B"
                    key to reaction.emoji
                }
            val wishRatings = ratings
                .filter { it.wishClientUuid == entity.clientUuid }
                .associate { rating ->
                    val key = if (rating.personaId == "phesty_official") "P" else "B"
                    key to rating.rating
                }
            Wish(
                clientUuid = entity.clientUuid,
                serverId = entity.serverId,
                text = entity.text,
                date = entity.wishDate,
                status = entity.status,
                creator = if (entity.creatorId == "phesty_official") "P" else "B",
                reactions = wishReactions,
                ratings = wishRatings,
                createdAt = entity.createdAt,
                syncStatus = entity.syncStatus
            )
        }
    }

    fun getAllWishes(): Flow<List<Wish>> {
        return combine(
            wishDao.getAllWishes(),
            reactionDao.getAllReactions(),
            ratingDao.getAllRatings()
        ) { wishes, reactions, ratings ->
            val mappedList = mapToWishes(wishes, reactions, ratings)
            wishesSnapshot = mappedList
            mappedList
        }
    }

    fun getWishesSnapshot(): List<Wish> = wishesSnapshot

    suspend fun warmSnapshotFromDatabase() {
        withContext(Dispatchers.IO) {
            val wishes = wishDao.getAllWishesSync()
            val reactions = reactionDao.getAllReactionsSync()
            val ratings = ratingDao.getAllRatingsSync()

            val mappedList = mapToWishes(wishes, reactions, ratings)
            wishesSnapshot = mappedList
            statsSnapshot = WishStats(
                total = mappedList.size,
                dusted = mappedList.count { it.status == "dusted" }
            )
            Log.d(TAG, "Pre-warmed wishlist snapshot: ${mappedList.size} wishes")
        }
    }

    fun getStats(): Flow<WishStats> {
        return wishDao.getAllWishes().map { list ->
            val stats = WishStats(
                total = list.size,
                dusted = list.count { it.status == "dusted" }
            )
            statsSnapshot = stats
            stats
        }
    }

    fun getStatsSnapshot(): WishStats = statsSnapshot

    suspend fun insertWish(wish: Wish) {
        Log.d(TAG, "Inserting wish: uuid=${wish.clientUuid}, text=${wish.text.take(20)}, creator=${wish.creator}")
        val latestCreatedAt = wishDao.getLastCreatedAt()
        val createdAt = maxOf(wish.createdAt, latestCreatedAt?.plus(1) ?: wish.createdAt)
        val entity = WishEntity(
            clientUuid = wish.clientUuid,
            serverId = null,
            text = wish.text,
            wishDate = wish.date,
            status = wish.status,
            creatorId = if (wish.creator == "P") "phesty_official" else "baroness_official",
            createdAt = createdAt,
            updatedAt = createdAt,
            syncStatus = "pending_create"
        )

        val payload = JSONObject().apply {
            put("text", wish.text)
            put("wish_date", wish.date)
            put("status", wish.status)
            put("creator_id", if (wish.creator == "P") "phesty_official" else "baroness_official")
            put("created_at", createdAt)
        }.toString()

        db.withTransaction {
            wishDao.insertWish(entity)
            syncManager.queueItem("wishlist_items", wish.clientUuid, "create", payload)
        }
        Log.d(TAG, "Queued for sync: uuid=${wish.clientUuid}, previousLatestCreatedAt=$latestCreatedAt")
        syncNow()
    }

    suspend fun deleteWish(clientUuid: String) {
        db.withTransaction {
            val wish = wishDao.getWishByUuid(clientUuid) ?: return@withTransaction
            val pending = syncQueueDao.getPendingForWish(clientUuid)
            val hasPendingCreate = pending.any {
                it.tableName == "wishlist_items" && it.operation == "create"
            }
            if (wish.serverId == null || hasPendingCreate) {
                reactionDao.deleteReactionsForWish(clientUuid)
                ratingDao.deleteRatingsForWish(clientUuid)
                wishDao.deleteWish(clientUuid)
                syncQueueDao.deleteAllForWish(clientUuid)
                return@withTransaction
            }
            wishDao.markPendingDelete(clientUuid)
            reactionDao.deleteReactionsForWish(clientUuid)
            ratingDao.deleteRatingsForWish(clientUuid)
            syncQueueDao.deleteAllForWish(clientUuid)
            syncManager.queueItem("wishlist_items", clientUuid, "delete", "")
        }
        Log.i(TAG, "Queued delete for uuid=$clientUuid")
        syncNow()
    }

    suspend fun updateWishStatus(clientUuid: String, status: String) {
        val updatedAt = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("status", status)
            put("updated_at", updatedAt)
        }.toString()
        db.withTransaction {
            val wish = wishDao.getWishByUuid(clientUuid)
            if (wish?.syncStatus != "synced" || wish.serverId == null) {
                Log.w(TAG, "Ignoring status update for unsynced wish uuid=$clientUuid")
                return@withTransaction
            }
            wishDao.updateStatusAsPending(clientUuid, status, updatedAt)
            syncManager.queueItem("wishlist_items", clientUuid, "update", payload)
        }
        syncNow()
    }

    suspend fun saveReaction(clientUuid: String, personaId: String, emoji: String) {
        val payload = JSONObject().apply {
            put("persona_id", personaId)
            put("emoji", emoji)
        }.toString()
        db.withTransaction {
            val wish = wishDao.getWishByUuid(clientUuid)
            if (wish?.syncStatus != "synced" || wish.serverId == null) {
                Log.w(TAG, "Ignoring reaction for unsynced wish uuid=$clientUuid")
                return@withTransaction
            }
            reactionDao.insertReaction(ReactionEntity(clientUuid, personaId, emoji))
            wishDao.updateSyncStatus(clientUuid, "pending_update")
            syncManager.queueItem("wishlist_reactions", clientUuid, "update", payload)
        }
        syncNow()
    }

    suspend fun saveRating(clientUuid: String, personaId: String, rating: Int) {
        val payload = JSONObject().apply {
            put("persona_id", personaId)
            put("rating", rating)
        }.toString()
        db.withTransaction {
            val wish = wishDao.getWishByUuid(clientUuid)
            if (wish?.syncStatus != "synced" || wish.serverId == null) {
                Log.w(TAG, "Ignoring rating for unsynced wish uuid=$clientUuid")
                return@withTransaction
            }
            ratingDao.insertRating(RatingEntity(clientUuid, personaId, rating))
            wishDao.updateSyncStatus(clientUuid, "pending_update")
            syncManager.queueItem("wishlist_ratings", clientUuid, "update", payload)
        }
        syncNow()
    }

    suspend fun retryWishSync(clientUuid: String) {
        val wish = wishDao.getWishByUuid(clientUuid)
        if (wish == null) {
            Log.w(TAG, "Cannot retry wish uuid=$clientUuid: wish does not exist locally")
            return
        }
        if (wish.syncStatus == "synced") {
            Log.d(TAG, "Retry ignored for already-synced wish uuid=$clientUuid")
            return
        }

        val items = syncQueueDao.getPendingForWish(clientUuid)
        if (items.isEmpty()) {
            Log.w(TAG, "Cannot retry wish uuid=$clientUuid: no queue items exist")
            return
        }

        val pendingStatus = when {
            items.any { it.tableName == "wishlist_items" && it.operation == "delete" } -> "pending_delete"
            items.any { it.tableName == "wishlist_items" && it.operation == "create" } -> "pending_create"
            else -> "pending_update"
        }
        db.withTransaction {
            items.forEach { syncQueueDao.update(it.copy(retryCount = 0, lastError = null)) }
            wishDao.updateSyncStatus(clientUuid, pendingStatus)
        }
        Log.i(TAG, "Resetting wish sync for manual retry: uuid=$clientUuid, queueItems=${items.size}")
        syncNow()
    }

    suspend fun syncNow() {
        if (storageManager.getString("auth_token").isNullOrBlank()) return
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
        workManager.enqueueUniqueWork(
            "WishlistSync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }
}
