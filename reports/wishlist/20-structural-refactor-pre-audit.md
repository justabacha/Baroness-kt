# Wishlist Structural Refactor Pre-Audit Report (Function Body Extraction & Evidence Follow-Up)

## 1. Executive Summary

This pre-audit report provides the exhaustive, evidence-backed evaluation of the three structural refactoring candidates (**R1 — Client-generated UUID identity**, **R2 — Centralized remote-merge gate**, and **R3 — Revision-aware acknowledgements**). By inspecting verbatim function bodies across `WishlistRepository.kt`, `WishDao.kt`, `SyncManager.kt`, `WishlistScreen.kt`, and `WishlistViewModel.kt`, we demonstrate that **R2 (Centralized remote-merge gate)** is the most robust, lowest-risk architectural evolution for the Baroness wishlist. R2 directly solves remote synchronization race conditions, outbox contention, and local desync without requiring database migrations or breaking client-server DTO contracts.

---

## 2. Verbatim Code Excerpts

### 2.1 From `WishlistRepository.kt`

#### 1. `handleWishChange()`
```kotlin
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
                deleteWishLocally(id)
            }
            else -> {}
        }
    } catch (e: Exception) {
        Log.e(TAG, "Wish change error: ${e.message}", e)
    }
}
```

#### 2. `upsertWithTimestampCheck()` (Repository Helper)
*Note: In the repository, upsert logic delegates directly to `wishDao.upsertWithTimestampCheck(entity)`.*

#### 3. `reconcileSyncedWishes()`
```kotlin
private suspend fun reconcileSyncedWishes(remoteWishIds: Set<Long>) {
    db.withTransaction {
        val cutoff = System.currentTimeMillis() - RECONCILIATION_GRACE_PERIOD_MS
        val syncedWishes = wishDao.getSyncedWishes()
        val missingRemoteWishes = syncedWishes.filter { it.id !in remoteWishIds }

        val staleWishIds = missingRemoteWishes
            .asSequence()
            .filter { wish ->
                val isRecent = wish.createdAt >= cutoff
                val isPendingCreate = wish.syncStatus == "pending_create"
                val shouldSkip = isRecent && isPendingCreate
                if (shouldSkip) {
                    Log.i(TAG, "Reconciliation grace period skipped row id=${wish.id} with syncStatus=${wish.syncStatus}")
                }
                !shouldSkip
            }
            .map { it.id }
            .toList()

        Log.i(TAG, "Reconciliation checked missing remote wishes: total missing=${missingRemoteWishes.size}, stale to delete=${staleWishIds.size}")

        staleWishIds.forEach { wishId ->
            deleteWishAndRelations(wishId)
            Log.d(TAG, "Removed stale synced wish id=$wishId")
        }
    }
}
```

#### 4. `initialSync()` / `fetchRemoteWishes()`
```kotlin
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
        if (remoteWishes.any { it.id == null }) {
            throw IllegalStateException("Wishlist fetch returned a wish without a server ID")
        }

        val wishEntities = remoteWishes.map { dto ->
            WishEntity(
                id = dto.id ?: error("Validated remote wish has no server ID"),
                text = dto.text,
                wishDate = dto.wishDate,
                status = dto.status,
                creatorId = dto.creatorId,
                createdAt = parseIsoToLong(dto.createdAt),
                updatedAt = parseIsoToLong(dto.updatedAt),
                syncStatus = "synced"
            )
        }
        wishDao.upsertAllWithTimestampCheck(wishEntities)
        Log.d(TAG, "Upserted ${wishEntities.size} remote wishes atomically")

        remoteReactions.forEach { dto ->
            reactionDao.insertReaction(ReactionEntity(
                wishId = dto.wishId,
                personaId = dto.personaId,
                emoji = dto.emoji
            ))
        }
        Log.d(TAG, "Inserted ${remoteReactions.size} reactions")

        remoteRatings.forEach { dto ->
            ratingDao.insertRating(RatingEntity(
                wishId = dto.wishId,
                personaId = dto.personaId,
                rating = dto.rating
            ))
        }
        Log.d(TAG, "Inserted ${remoteRatings.size} ratings")

        reconcileSyncedWishes(remoteWishes.mapNotNull { it.id }.toSet())

    } catch (e: Exception) {
        Log.e(TAG, "Initial sync failed: ${e.message}", e)
        throw e
    }
}
```

#### 5. `insertWish()`
```kotlin
suspend fun insertWish(wish: Wish) {
    Log.d(TAG, "Inserting wish: id=${wish.id}, text=${wish.text.take(20)}, creator=${wish.creator}")

    val isTempId = wish.id < 0
    val latestCreatedAt = wishDao.getLastCreatedAt()
    val createdAt = maxOf(wish.createdAt, latestCreatedAt?.plus(1) ?: wish.createdAt)
    val entity = WishEntity(
        id = wish.id,
        text = wish.text,
        wishDate = wish.date,
        status = wish.status,
        creatorId = if (wish.creator == "P") "phesty_official" else "baroness_official",
        createdAt = createdAt,
        syncStatus = if (isTempId) "pending_create" else "synced"
    )
    wishDao.insertWish(entity)
    Log.d(
        TAG,
        "Wish inserted to Room: id=${wish.id}, creatorId=${entity.creatorId}, " +
            "createdAt=${entity.createdAt}, previousLatestCreatedAt=$latestCreatedAt"
    )

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
        put("created_at", createdAt)
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
```

#### 6. `deleteWish()`
```kotlin
suspend fun deleteWish(wishId: Long) {
    Log.d(TAG, "Deleting wish: id=$wishId")
    val wish = wishDao.getWishById(wishId)
    val wishSyncItem = syncQueueDao.getWishSyncItem(wishId, wishId.toString())
    val isNeverSynced = wish?.syncStatus == "pending_create" ||
        wishId < 0 ||
        (wish?.syncStatus == "failed" && wishSyncItem?.operation == "create")

    if (isNeverSynced) {
        db.withTransaction {
            deleteWishAndRelations(wishId)
            syncQueueDao.deleteWishItems(wishId, wishId.toString())
        }
        Log.i(TAG, "Deleted unsynced wish locally without queuing remote delete: id=$wishId")
        return
    }

    db.withTransaction {
        wishDao.markPendingDelete(wishId)
        reactionDao.deleteReactionsForWish(wishId)
        ratingDao.deleteRatingsForWish(wishId)
    }
    Log.i(TAG, "Marked synced wish for remote deletion: id=$wishId")
    syncManager.queueItem("wishlist_items", wishId.toString(), "delete", "")
    syncNow()
}
```

#### 7. `updateWishStatus()`
```kotlin
suspend fun updateWishStatus(wishId: Long, status: String) {
    Log.d(TAG, "Updating wish status: id=$wishId, status=$status")
    val wish = wishDao.getWishById(wishId)
    if (wish?.syncStatus != "synced") {
        Log.w(TAG, "Ignoring status update for unsynced wish id=$wishId")
        return
    }
    val updatedAt = System.currentTimeMillis()
    wishDao.updateStatusAsPending(wishId, status, updatedAt)
    val payload = JSONObject().apply {
        put("status", status)
        put("updated_at", updatedAt)
    }.toString()
    syncManager.queueItem("wishlist_items", wishId.toString(), "update", payload)
    syncNow()
}
```

#### 8. `syncNow()`
```kotlin
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
    workManager.enqueue(workRequest)
}
```

#### 9. Realtime Subscription Setup (`subscribeToRealtime()`)
```kotlin
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

                // ─── REACTIONS & RATINGS CHANNELS (omitted for brevity) ───
                ...

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
```

---

### 2.2 From `WishDao.kt`

11. `getAllWishes()`:
```kotlin
@Query("SELECT * FROM wishes WHERE syncStatus NOT IN ('pending_delete', 'failed_delete') ORDER BY createdAt ASC")
fun getAllWishes(): Flow<List<WishEntity>>
```

12. `upsertWithTimestampCheck()`:
```kotlin
@Transaction
suspend fun upsertWithTimestampCheck(wish: WishEntity) {
    val existing = getWishById(wish.id)
    if (existing != null && existing.syncStatus in setOf(
            "pending_update",
            "pending_delete",
            "failed_delete"
        )
    ) {
        Log.i(TAG, "Skipping remote upsert for locally pending wish id=${wish.id} status=${existing.syncStatus}")
        return
    }
    if (existing == null || wish.updatedAt > existing.updatedAt) {
        insertWish(wish)
    }
}
```

13. `updateWishId()`:
```kotlin
@Query("UPDATE wishes SET id = :newServerId, syncStatus = 'synced' WHERE id = :oldTempId")
suspend fun updateWishId(oldTempId: Long, newServerId: Long)
```

14. `insertWish()`:
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertWish(wish: WishEntity)
```

15. `deleteWish()`:
```kotlin
@Query("DELETE FROM wishes WHERE id = :wishId")
suspend fun deleteWish(wishId: Long)
```

16. Other queries touching `syncStatus` or `id`:
- `markPendingDelete`: `@Query("UPDATE wishes SET status = 'deleted', syncStatus = 'pending_delete' WHERE id = :wishId")`
- `updateSyncStatus`: `@Query("UPDATE wishes SET syncStatus = :syncStatus WHERE id = :wishId")`

---

### 2.3 From `SyncManager.kt`

17. `processQueue()`:
```kotlin
suspend fun processQueue() {
    withContext(Dispatchers.IO) {
        val pending = dao.getAllPending()
        Log.d(TAG, "Processing ${pending.size} pending items")

        if (pending.isEmpty()) {
            Log.d(TAG, "Queue is empty, nothing to sync")
            return@withContext
        }

        for (item in pending) {
            if (item.retryCount >= 5) {
                Log.w(TAG, "Max retries reached for item ${item.id}, skipping")
                continue
            }

            try {
                Log.d(TAG, "Processing item ${item.id}: ${item.operation} ${item.tableName}")
                val success = when (item.tableName) {
                    "wishlist_items" -> processWishItem(item)
                    "wishlist_reactions" -> processReaction(item)
                    "wishlist_ratings" -> processRating(item)
                    else -> {
                        Log.w(TAG, "Unknown table: ${item.tableName}")
                        false
                    }
                }

                if (success) {
                    dao.deleteById(item.id)
                    Log.d(TAG, "Synced and removed item ${item.id}")
                } else {
                    val updated = item.copy(retryCount = item.retryCount + 1)
                    dao.update(updated)
                    Log.w(TAG, "Failed to sync item ${item.id}, retry ${updated.retryCount}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing item ${item.id}: ${e.message}", e)
                val updated = item.copy(
                    retryCount = item.retryCount + 1,
                    lastError = e.message
                )
                dao.update(updated)
            }
        }
    }
}
```

18. `processWishItem()`:
```kotlin
private suspend fun processWishItem(item: SyncQueueItem): Boolean {
    return when (item.operation) {
        "create" -> {
            Log.d(TAG, "Processing CREATE for wish. Payload: ${item.payload}")
            val json = JSONObject(item.payload)
            val text = json.getString("text")
            val wishDate = json.getString("wish_date")
            val status = json.getString("status")
            val creatorId = json.getString("creator_id")
            val createdAt = json.getLong("created_at")

            val result = WishlistApi.createWish(
                text = text,
                wishDate = wishDate,
                status = status,
                creatorId = creatorId,
                createdAt = createdAt
            )

            if (result != null && result.id != null) {
                Log.d(TAG, "Wish created successfully on server with ID: ${result.id}")
                val tempId = item.localTempId
                val serverId = result.id
                if (tempId != null && tempId < 0) {
                    Log.d(TAG, "Performing ID swap in Room: $tempId -> $serverId")
                    try {
                        wishDao.updateWishId(oldTempId = tempId, newServerId = serverId)
                        Log.d(TAG, "ID swap complete")
                    } catch (e: SQLiteConstraintException) {
                        Log.w(TAG, "ID swap failed: Server ID $serverId already exists in Room. Deleting temp record $tempId.")
                        wishDao.deleteWish(tempId)
                    }
                }
                true
            } else {
                Log.e(TAG, "Create failed: result is null or missing server ID")
                false
            }
        }
        "update" -> {
            Log.d(TAG, "Processing UPDATE for wish ${item.recordId}")
            val json = JSONObject(item.payload)
            val status = json.getString("status")
            val updatedAt = json.getLong("updated_at")
            val wishId = item.recordId.toLongOrNull() ?: return false
            WishlistApi.updateWish(wishId, status, updatedAt)
        }
        "delete" -> {
            Log.d(TAG, "Processing DELETE for wish ${item.recordId}")
            val wishId = item.recordId.toLongOrNull() ?: return false
            WishlistApi.deleteWish(wishId)
        }
        else -> {
            Log.w(TAG, "Unknown operation: ${item.operation}")
            false
        }
    }
}
```

19-21. **Successful Create / Update / Delete Path Execution**:
- **Create**: Calls `WishlistApi.createWish()` -> receives server ID -> calls `wishDao.updateWishId(oldTempId, newServerId)` -> deletes queue item via `dao.deleteById(item.id)`.
- **Update**: Calls `WishlistApi.updateWish()` -> on success, deletes queue item via `dao.deleteById(item.id)`.
- **Delete**: Calls `WishlistApi.deleteWish()` -> on success, deletes queue item via `dao.deleteById(item.id)`.

---

### 2.4 From `WishlistScreen.kt`

22. **Full `LazyColumn` block**:
```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .weight(1f),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(bottom = 16.dp)
) {
    itemsIndexed(
        items = wishes,
        key = { _, wish -> "${wish.creator}-${wish.createdAt}-${wish.text.hashCode()}" }
    ) { index, wish ->
        WishItem(
            wish = wish,
            index = index,
            currentUserKey = currentUserKey,
            onDust = { viewModel.dustWish(it) },
            onDelete = { viewModel.toggleConfirmDialog(true, it) },
            onRetry = { viewModel.retryWish(it) },
            onOpenEmoji = { viewModel.toggleEmojiPicker(true, it) },
            onOpenRating = { viewModel.toggleRatingModal(true, wish) },
            onUploadPhotos = { viewModel.togglePhotoModal(true) }
        )
    }
}
```

---

### 2.5 From `WishlistViewModel.kt`

23. **Flow derivation for `wishes`**:
```kotlin
val wishes: StateFlow<List<Wish>> = repository.getAllWishes()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
```

---

### 2.6 From `SyncQueueItem.kt` & `WishEntity.kt`

24. **`SyncQueueItem.kt`**:
```kotlin
@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableName: String,        // "wishlist_items", "wishlist_reactions", "wishlist_ratings"
    val recordId: String,         // the local ID of the record (as String for flexibility)
    val localTempId: Long? = null, // ONLY for create operations: the negative temp ID to swap later
    val operation: String,        // "create", "update", "delete"
    val payload: String,          // JSON payload to send
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)
```

25. **`WishEntity.kt`**:
```kotlin
@Entity(tableName = "wishes")
data class WishEntity(
    @PrimaryKey
    val id: Long,
    val text: String,
    val wishDate: String,          // "yyyy-MM-dd"
    val status: String,            // "planning" or "dusted"
    val creatorId: String,         // "phesty_official" or "baroness_official"
    val createdAt: Long,            // timestamp
    val updatedAt: Long = System.currentTimeMillis(), // last modified timestamp
    val syncStatus: String = "synced"  // "synced" | "pending_create" | "pending_update" | "pending_delete"
)
```

---

## 3. Specific Questions Answered with Code Evidence

### 3.1 Is `handleWishChange` already a centralized merge gate?
- **Answer**: No. `handleWishChange()` handles Realtime `Insert` and `Update` actions by mapping records and calling `wishDao.upsertWithTimestampCheck(entity)` (Section 2.1, item 1). However, `initialSync()` (`fetchRemoteWishes()`) has its own independent write path using `wishDao.upsertAllWithTimestampCheck(wishEntities)` (Section 2.1, item 4). They are separate paths that both write to Room, though both delegate to the timestamp check.

### 3.2 What is the exact current grace period predicate?
- **Answer**: In `reconcileSyncedWishes()` (Section 2.1, item 3):
  ```kotlin
  val isRecent = wish.createdAt >= cutoff
  val isPendingCreate = wish.syncStatus == "pending_create"
  val shouldSkip = isRecent && isPendingCreate
  ```
  It checks both `createdAt >= cutoff` AND `syncStatus == "pending_create"`.

### 3.3 Does `updateWishId` touch reactions and ratings?
- **Answer**: **No.** In `WishDao.kt` (Section 2.2, item 13):
  ```kotlin
  @Query("UPDATE wishes SET id = :newServerId, syncStatus = 'synced' WHERE id = :oldTempId")
  suspend fun updateWishId(oldTempId: Long, newServerId: Long)
  ```
  It updates only `wishes.id`. It does not touch `reactions` or `ratings`.

### 3.4 What is the actual Compose key today?
- **Answer**: In `WishlistScreen.kt` (Section 2.4, item 22):
  `key = { _, wish -> "${wish.creator}-${wish.createdAt}-${wish.text.hashCode()}" }`.
  This was chosen to remain stable across ID swaps (since temp vs server ID is not part of the key string).

### 3.5 What is the exact reference from outbox to wish?
- **Answer**: `SyncQueueItem` references the wish via `recordId` (String representation of the ID) and `localTempId` (Long?, storing the negative temp ID for creates). When a create succeeds, `SyncManager` reads `item.localTempId` and executes `wishDao.updateWishId(oldTempId = tempId, newServerId = serverId)`. However, the outbox entry itself is deleted upon success (`dao.deleteById(item.id)`), so its recordId is not updated in-place; rather, the outbox item is removed and no subsequent queue items reference the temp ID once created.

---

## 4. R1 — Real Effort Count

### 4.1 Files
1. `WishEntity.kt` (add `uuid` field)
2. `WishDao.kt` (add uuid lookup / queries)
3. `WishlistRepository.kt` (use uuid for outbox and sync references)
4. `SyncQueueItem.kt` (reference uuid instead of temp Long ID)
5. `SyncManager.kt` (process queue using uuid)
6. `WishlistApi.kt` (send uuid to server if server schema supports it, or map uuid)

### 4.2 Functions
~10 functions across DAO, Repository, and SyncManager (`insertWish`, `deleteWish`, `updateWishStatus`, `processWishItem`, `updateWishId` replacement, etc.).

### 4.3 Schema
- Server-side `ALTER TABLE wishlist_items ADD COLUMN uuid uuid DEFAULT gen_random_uuid();` (or client generated).
- Backfill query for existing rows.
- UUID as a secondary unique column (`uuid`), keeping `id: Long` as the primary key for foreign keys (`wish_id` in reactions/ratings), or migrating reactions/ratings to foreign key on `uuid`.

### 4.4 Expected Regressions
- Foreign key mismatches between Room and Supabase if `wish_id` references `id` (Long) vs `uuid`.
- Realtime payload mapping mismatch if Realtime actions broadcast bigint `id` instead of uuid.

### 4.5 Estimate Breakdown
- **R1 alone**: Large effort (schema migration, foreign key rewrites, uuid propagation).
- **R2 alone**: Medium effort (centralized `applyServerRow` function in repository, zero schema changes).
- **R1 + R2**: Large effort.

---

## 5. R2 — Concrete Design

### 5.1 Where does it live?
Inside `WishlistRepository.kt`.

### 5.2 Signature
```kotlin
suspend fun applyServerRow(remoteDto: WishDto)
suspend fun applyServerDelete(remoteId: Long)
```

### 5.3 Outbox Lookup
`SyncQueueDao` can be queried: `syncQueueDao.getPendingCreateForRecord(recordId)` or checking if `syncStatus` in Room is in `('pending_create', 'pending_update', 'pending_delete')`.

### 5.4 Can R2 be built today without R1?
**Yes.** R2 operates entirely within the existing Long ID and `syncStatus` model in Room.

### 5.5 Pseudocode for `applyServerRow`
```kotlin
suspend fun applyServerRow(remoteDto: WishDto) {
    db.withTransaction {
        val existing = wishDao.getWishById(remoteDto.id)
        if (existing != null && existing.syncStatus in setOf("pending_update", "pending_delete", "failed_delete")) {
            // Respect local un-synced mutations
            return@withTransaction
        }
        // Apply remote authoritative state
        wishDao.insertWish(remoteDto.toEntity(syncStatus = "synced"))
    }
}
```

### 5.6 Call Sites
- `initialSync()` (`fetchRemoteWishes`)
- `handleWishChange()` (Realtime Insert/Update)

---

## 6. R3 — Dependency Analysis
In the scenario (create -> dust -> sync -> dust again -> create ack):
- **R2 alone**: Correctly checks outbox state; if a pending update exists, the server row update is gated or merged gracefully.
- **R3 alone**: Tracks revisions, but without R2's gate, outbox operations could still race.
- **Verdict**: R3 is **redundant** given a correct R2 implementation in a two-person app.

---

## 7. Recommendation Rewritten with Evidence

1. **Adopt R2 (Centralized Remote-Merge Gate)** as the primary structural refactor. It requires zero server schema migrations, leverages existing `syncStatus` states in Room, and unifies `initialSync()` and Realtime into a single transactional merge method.
2. **Atomic ID Remap**: Ensure `updateWishId` in Room correctly handles or verifies child foreign key consistency.
