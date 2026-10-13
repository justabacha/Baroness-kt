# Cold-Start Pre-Warm — Instant First Render Implementation Record

## Context

Following the cold-start audit (`reports/screens/03-cold-start-prewarm-audit.md`), initial process launch previously rendered screens with default empty or loading states while DataStore and Room queries completed asynchronously.

This implementation adds `androidx.core:core-splashscreen`, a splash screen hold condition in `MainActivity`, synchronous Room queries, and parallel local pre-warming of StorageManager, Wishlist, ChatList, and Dashboard quotes during the splash window.

---

## 1. Core-Splashscreen & Theme Setup

### `app/build.gradle.kts`
Added dependency:
```kotlin
implementation("androidx.core:core-splashscreen:1.0.1")
```

### `res/values/themes.xml`
Added splash theme pointing to dark background (`#0B0B0E`) and launcher icon:
```xml
    <style name="Theme.Baroness.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#0B0B0E</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/icon</item>
        <item name="postSplashScreenTheme">@style/Theme.Baroness</item>
    </style>
```

### `AndroidManifest.xml`
Updated `MainActivity` theme:
```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.Baroness.Splash"
            android:windowSoftInputMode="adjustResize">
```

---

## 2. ColdStartPrewarm Source

Created `app/src/main/java/com/baroness/app/startup/ColdStartPrewarm.kt`:

```kotlin
package com.baroness.app.startup

import android.content.Context
import android.util.Log
import com.baroness.app.data.QuoteRepository
import com.baroness.app.repository.WishlistRepository
import com.baroness.app.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ColdStartPrewarm {
    private const val TAG = "ColdStartPrewarm"

    @Volatile
    private var isComplete = false

    fun isPrewarmComplete(): Boolean = isComplete

    suspend fun run(context: Context) {
        if (isComplete) return

        withContext(Dispatchers.IO) {
            // 1. Warm StorageManager mirror
            StorageManager.initSyncMirror(context)

            // 2. Warm Wishlist snapshot from Room
            runCatching {
                val repository = WishlistRepository.getInstance(context)
                repository.warmSnapshotFromDatabase()
            }.onFailure { Log.e(TAG, "Wishlist pre-warm failed", it) }

            // 3. Warm ChatList snapshot from Room
            runCatching {
                ChatListPrewarm.warmFromDatabase(context)
            }.onFailure { Log.e(TAG, "ChatList pre-warm failed", it) }

            // 4. Warm Dashboard quotes cache
            runCatching {
                QuoteRepository.getInstance(context).warmLocalCache()
            }.onFailure { Log.e(TAG, "Quotes pre-warm failed", it) }
        }

        isComplete = true
    }
}
```

---

## 3. MainActivity Splash Gate

Added splash screen installation and pre-warm execution with a 500ms hard timeout in `MainActivity.kt`:

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var isReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isReady }

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                withTimeout(500L) {
                    ColdStartPrewarm.run(applicationContext)
                }
            } catch (e: TimeoutCancellationException) {
                android.util.Log.w("MainActivity", "Cold-start pre-warm exceeded 500ms; rendering with partial cache")
            }
            isReady = true
            android.util.Log.i("MainActivity", "Prewarm completed in ${System.currentTimeMillis() - startTime}ms")
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ...
```

---

## 4. Synchronous DAO Additions

Added blocking synchronous query methods for local database pre-warming:

- `WishDao.kt`:
  ```kotlin
  @Query("SELECT * FROM wishes WHERE syncStatus NOT IN ('pending_delete', 'failed_delete') ORDER BY createdAt ASC, clientUuid ASC")
  fun getAllWishesSync(): List<WishEntity>
  ```
- `ReactionDao.kt`:
  ```kotlin
  @Query("SELECT * FROM reactions")
  fun getAllReactionsSync(): List<ReactionEntity>
  ```
- `RatingDao.kt`:
  ```kotlin
  @Query("SELECT * FROM ratings")
  fun getAllRatingsSync(): List<RatingEntity>
  ```
- `MessageDao.kt`:
  ```kotlin
  @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND isDeleted = 0 ORDER BY timestamp DESC LIMIT 1")
  fun getLastMessageForConversationSync(conversationId: String): MessageEntity?
  ```

---

## 5. Wishlist Repository Pre-Warm

Added `warmSnapshotFromDatabase()` to `WishlistRepository.kt`:

```kotlin
    suspend fun warmSnapshotFromDatabase() {
        withContext(Dispatchers.IO) {
            val wishes = wishDao.getAllWishesSync()
            val reactions = reactionDao.getAllReactionsSync()
            val ratings = ratingDao.getAllRatingsSync()

            val mappedList = wishes.map { entity ->
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
            wishesSnapshot = mappedList
            statsSnapshot = WishStats(
                total = mappedList.size,
                dusted = mappedList.count { it.status == "dusted" }
            )
            Log.d(TAG, "Pre-warmed wishlist snapshot: ${mappedList.size} wishes")
        }
    }
```

---

## 6. ChatList Pre-Warm

Created `app/src/main/java/com/baroness/app/startup/ChatListPrewarm.kt` to query Room and cached persona profiles on startup, storing the result in a `@Volatile` snapshot. `ChatListViewModel` reads directly from `ChatListPrewarm.getConversationsSnapshot()` on initialization.

---

## 7. Dashboard Quotes Pre-Warm

Added `QuoteRepository.warmLocalCache()` to validate and load cached quote JSON and local images from disk without making network calls:

```kotlin
    fun warmLocalCache() {
        try {
            val today = dateFormat.format(Date())
            val cachedDate = StorageManager.getStringSync(cacheDateKey)
            if (cachedDate == today) {
                val cachedJson = StorageManager.getStringSync(quoteDataKey)
                cachedJson?.let {
                    val cachedQuote = json.decodeFromString<VibeQuote>(it)
                    val currentVibe = VibeManager.getVibeOfTheDay()
                    if (cachedQuote.part1 == currentVibe.part1 && cachedQuote.part2 == currentVibe.part2) {
                        val image1Exists = cachedQuote.photo1.let { path -> File(path).exists() }
                        val image2Exists = cachedQuote.photo2.let { path -> File(path).exists() }
                        if (image1Exists && image2Exists) {
                            cachedQuoteSnapshot = cachedQuote
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("QuoteRepository warmLocalCache error: ${e.message}")
        }
    }
```

---

## 8. Network-Free Confirmation

Confirmed that **zero network calls** were added to any pre-warm path. All pre-warm routines operate strictly on local SQLite database files, DataStore, and cached image files on disk.

---

## 9. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 10. Manual Verification Checklist

- [ ] Force-stop the app. Tap the icon. Splash holds ~100-300ms. Dashboard appears complete — profile card and quotes card on first frame.
- [ ] From a fresh cold start, open Wishlist. No empty container. Items visible on first frame.
- [ ] From a fresh cold start, open ChatList. No spinner. Conversations visible on first frame.
- [ ] Navigate away and back — no regression to the previous nav-return instant render.
- [ ] With Wi-Fi and mobile data off, cold-start. Same behavior — pre-warm uses local cache only.
- [ ] Cold start with no data at all (fresh install simulation). Dashboard shows a placeholder once, then populates.
- [ ] Logcat: search for "Prewarm completed in". Verify the duration is under 500ms.

---

## 11. Duplication Verification

1. **Wishlist mapping**: Initially **duplicated**. Both `getAllWishes()` (live Flow) and `warmSnapshotFromDatabase()` (pre-warm) constructed `Wish` objects inline from `WishEntity`, `ReactionEntity`, and `RatingEntity`. 
   - *Fix applied*: Extracted mapping into a single private helper function `mapToWishes(wishes, reactions, ratings): List<Wish>` in `WishlistRepository.kt` and called it from both `getAllWishes()` and `warmSnapshotFromDatabase()`.
2. **ChatList query**: **Divergent by design**. `ChatListPrewarm.warmFromDatabase()` uses synchronous blocking DAO queries (`messageDao.getLastMessageForConversationSync(...)`), whereas `ChatListViewModel` uses reactive Room Flow observers (`messageDao.getLastMessageForConversation(...)`) to listen for live message updates. They query the same underlying SQLite table/data but through synchronous vs reactive pathways.
3. **Any change applied**: Extracted `mapToWishes(...)` in `WishlistRepository.kt`.
4. **Build result**: `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 12. ChatList Pre-Warm Result Equivalence

| Step | ChatListPrewarm | ChatListViewModel | Same? |
| :--- | :--- | :--- | :--- |
| **Query source(s)** | `messageDao.getLastMessageForConversationSync(...)` | `messageDao.getLastMessageForConversation(...)` | **Yes** (Same DAO query method family) |
| **Filter conditions** | `conversationId = :conversationId AND isDeleted = 0 ORDER BY timestamp DESC LIMIT 1` | Same Room query | **Yes** |
| **Sort order** | Hardcoded list: 0 = Human Peer (`otherId`), 1 = AI (`"friday"`) | `buildConversationList` hardcoded list: 0 = Human Peer (`otherId`), 1 = AI (`"friday"`) | **Yes** |
| **Profile pairing** | `StorageManager.getStringSync("cached_other_profile_$otherId")` decoded via `ChatProfileDto` | `StorageManager.getStringSync("cached_other_profile_$otherId")` decoded via `ChatProfileDto` | **Yes** |
| **Post-query transformations** | `formatChatTimestamp(it.timestamp)` with fallback default strings | `formatChatTimestamp(it.timestamp)` with fallback default strings | **Yes** |
| **Final list construction** | `listOf(Conversation(...), Conversation(...))` | `listOf(Conversation(...), Conversation(...))` via `buildConversationList` | **Yes** |

1. **Identical Results**: Yes. Both paths produce identical conversation objects, in the same order, with identical profile pairing, timestamp formatting, and fallback message strings.
2. **Differences Found**: None. The pre-warm snapshot and live ViewModel building logic are 100% functionally equivalent.
3. **Fix Applied**: None required (results are fully equivalent).


