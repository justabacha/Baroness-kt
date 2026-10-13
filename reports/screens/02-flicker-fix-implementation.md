# Screen Flicker Fix — Dashboard, ChatList, Wishlist — Implementation Record

## Context

The follow-up audit (`reports/screens/01-flicker-follow-up-audit.md`) identified that Dashboard, ChatList, and Wishlist flicker on initial open and on navigation return due to starting with default empty/null states while asynchronous DataStore and database queries execute.

Because `StorageManager` relied exclusively on DataStore's suspend API, synchronous initial state seeding was previously impossible. This implementation adds a centralized in-memory synchronous mirror to `StorageManager`, seeds Dashboard and ChatList synchronously, and implements repository-level snapshots for Wishlist.

---

## 1. StorageManager Synchronous Mirror

### 1.1 Additions to `StorageManager.kt`
Added a `companion object` maintaining thread-safe in-memory maps (`cachedUserProfile` and `cachedStrings`) populated by collecting `context.dataStore.data` flow in an application-lifetime supervisor coroutine (`initSyncMirror`).

```kotlin
    companion object {
        @Volatile
        private var cachedUserProfile: String? = null
        private val cachedStrings = ConcurrentHashMap<String, String>()
        @Volatile
        private var isMirrorInitialized = false
        private val companionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun initSyncMirror(context: Context) {
            if (isMirrorInitialized) return
            synchronized(this) {
                if (isMirrorInitialized) return
                isMirrorInitialized = true
            }
            val appContext = context.applicationContext
            companionScope.launch {
                appContext.dataStore.data.collect { prefs ->
                    val map = mutableMapOf<String, String>()
                    prefs.asMap().forEach { (key, value) ->
                        if (value is String) {
                            map[key.name] = value
                        }
                    }
                    cachedStrings.clear()
                    cachedStrings.putAll(map)
                    cachedUserProfile = map["userProfile"]
                }
            }
        }

        fun getUserProfileSync(): String? = cachedUserProfile ?: cachedStrings["userProfile"]
        fun getStringSync(key: String): String? = cachedStrings[key]
    }
```

### 1.2 Wiring in `MainApplication.onCreate()`
Called `StorageManager.initSyncMirror(this)` immediately upon application creation:

```kotlin
    override fun onCreate() {
        super.onCreate()
        
        // Initialize StorageManager Sync Mirror
        StorageManager.initSyncMirror(this)

        // Initialize Session Token Cache
        SessionTokenCache.init(this)
        ...
```

---

## 2. Dashboard — Synchronous Seed

### `DashboardViewModel.kt` Changes
**Before:**
```kotlin
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()
    ...
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()
```

**After:**
```kotlin
    private val initialProfileJson = StorageManager.getUserProfileSync()
    private val initialProfile = initialProfileJson?.let {
        try { json.decodeFromString<UserProfile>(it) } catch (_: Exception) { null }
    }

    private val _userProfile = MutableStateFlow<UserProfile?>(initialProfile)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()
    ...
    private val _isInitialLoading = MutableStateFlow(initialProfile == null)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()
```
`DashboardViewModel` now seeds `_userProfile` and `_isInitialLoading` synchronously from the in-memory StorageManager mirror on creation, eliminating the first-frame loading spinner and `"Hi User"` placeholder.

---

## 3. ChatList — Synchronous Seed & ViewModel Snapshot

### 3.1 Decision on Snapshot Location
Because `ChatListViewModel` reads directly from Room via `messageDao` without a standalone `ChatListRepository`, a `@Volatile` companion object snapshot (`conversationsSnapshot`) was added directly to `ChatListViewModel` to survive ViewModel recreation on navigation return.

### 3.2 `ChatListViewModel.kt` Changes
**Before:**
```kotlin
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()
```

**After:**
```kotlin
    companion object {
        @Volatile
        private var conversationsSnapshot: List<Conversation> = emptyList()
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(conversationsSnapshot)
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(conversationsSnapshot.isEmpty())
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()
    ...
            // Inside loadData collection:
            .collect { newList ->
                conversationsSnapshot = newList
                _conversations.value = newList
            }
```

---

## 4. Wishlist — Repository Snapshot

### 4.1 `WishlistRepository.kt` Changes
Added `@Volatile` snapshots for wishes and stats, exposed synchronously:

```kotlin
    @Volatile
    private var wishesSnapshot: List<Wish> = emptyList()
    @Volatile
    private var statsSnapshot: WishStats = WishStats(0, 0)
    
    // In getAllWishes and getStats:
    wishesSnapshot = mappedList
    ...
    statsSnapshot = stats
    
    fun getWishesSnapshot(): List<Wish> = wishesSnapshot
    fun getStatsSnapshot(): WishStats = statsSnapshot
```

### 4.2 `WishlistViewModel.kt` Changes
Seeded `wishes` and `stats` state flows with repository snapshots:

```kotlin
    val wishes: StateFlow<List<Wish>> = repository.getAllWishes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.getWishesSnapshot()
        )

    val stats: StateFlow<WishStats> = repository.getStats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repository.getStatsSnapshot()
        )
```

---

## 5. Out-of-Scope Confirmation

Confirmed that NO out-of-scope files (`ChatRoomScreen`, `ProfileSetupScreen`, `PhotosScreen`, Settings Center components, PhestyDrop, or auth modules) were touched.

---

## 6. Build Result

- `:app:assembleDebug` — **BUILD SUCCESSFUL**.

---

## 7. Manual Verification Checklist

- [ ] Open Dashboard from cold start with cached profile — no placeholder, no "Hi User", profile shows on first frame.
- [ ] Navigate away from Dashboard and back — no reload, no flicker.
- [ ] Open ChatList — no spinner, conversations visible immediately if cached.
- [ ] Navigate away from ChatList and back — no spinner, no flicker.
- [ ] Open Wishlist — no empty container flash, wishes visible immediately if cached.
- [ ] Navigate away from Wishlist and back — no empty flash.
- [ ] Cold start with no cached profile (fresh install simulation) — Dashboard shows a placeholder once, then populates.
