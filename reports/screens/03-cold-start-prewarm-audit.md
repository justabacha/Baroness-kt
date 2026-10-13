# Cold-Start Pre-Warm Audit — Instant First Render Report

## 1. Executive Summary

This audit evaluates cold-start rendering behavior across the Baroness application. While in-memory snapshots and ViewModel caching successfully prevent flicker on navigation return, a true cold start (process launch) leaves in-memory caches empty. Consequently, the first rendered screen (Dashboard) initially displays missing profile elements or delayed quote cards, while subsequent navigation to Wishlist or ChatList exhibits brief empty-container flashes or loading spinners while asynchronous DB queries resolve. By examining application initialization, splash screen behavior, and data source latencies, this audit determines which states can be pre-warmed synchronously at startup versus those requiring architectural caching additions.

---

## 2. Splash Screen Inventory (Section 1)

- **Explicit SplashScreen API**: The app does **not** explicitly use `androidx.core:core-splashscreen` or the `SplashScreen` API.
- **Manifest Theme**: `AndroidManifest.xml` declares `android:theme="@style/Theme.Baroness"` on both `<application>` and `<activity android:name=".MainActivity">`.
- **Android 12+ OS Behavior**: On Android 12+, the system automatically displays the standard platform splash screen (app icon over system background color) during process launch until the first window draws its initial frame.
- **Custom Gate / Launch Activities**: There is no custom launch activity or blocking auth gate activity; `MainActivity` is the sole entry point (`android.intent.action.MAIN` / `android.intent.category.LAUNCHER`), rendering `AppEntryPoint` inside `setContent`.
- **Total Visible Splash Duration**: The OS system splash remains visible only until the initial Compose UI tree renders its first frame (`setContent`). Because `MainApplication.onCreate()` and ViewModel initializers launch async coroutines on background threads without blocking the main thread, the OS splash disappears immediately (~100-300ms), resulting in the app launching to an empty or placeholder-laden UI before background cache reads complete.

---

## 3. Current Cold-Start Initialization (Section 2)

Execution order during app startup (`MainApplication` -> `MainActivity`):

1. **`MainApplication.onCreate()`**:
   - Initializes `SessionTokenCache.init(this)` (asynchronous token cache read/init).
   - Registers process lifecycle observer for token refresh.
   - Instantiates `NotificationManager(this)` and creates notification channels.
   - Launches background coroutine to fetch/register FCM token.
2. **`MainActivity.onCreate()`**:
   - Calls `enableEdgeToEdge()`.
   - Requests notification permission (Android 13+).
   - Enters `setContent { ... }`, initializing `SettingsViewModel`, `NotificationViewModel`, and `NavController`.
   - Renders `DashboardScreen` as the starting destination.
3. **`DashboardViewModel` Initialization (`init`)**:
   - Launches `loadInitialData()` in `viewModelScope`, which asynchronously reads DataStore (`userProfile`) and resolves local avatar cache via `AvatarRepository`.
   - Triggers weather loading and quote loading (`QuoteRepository.getTodayQuote()`), which involves checking cached JSON and/or downloading quote images.

*Blocking vs. Non-Blocking*: None of the startup coroutines block the main thread or the initial frame render; they all execute asynchronously in background coroutine scopes, causing the UI to render empty placeholders first and update reactively later.

---

## 4. What Each Screen Needs on First Frame (Section 3)

### 4.1 Dashboard
- **Profile Card**: Reads from DataStore (`userProfile`). Recently updated to seed from `StorageManager.getUserProfileSync()` (or cached mirror). On cold start, if DataStore cache hasn't loaded into memory yet, it briefly renders fallback/null state.
- **Quotes Card**: Provided by `QuoteRepository.getTodayQuote()`. Reads JSON from `StorageManager` and checks local image files in `filesDir/quote_images`. If the cache date matches today and local images exist, it returns instantly; otherwise, it executes a network/download pass.

### 4.2 Wishlist
- **Wishes & Stats**: Powered by Room DAO queries (`wishDao.getAllWishes()`, `reactionDao.getAllReactions()`, `ratingDao.getAllRatings()`) combined via `combine` flow.
- **Cold Start Behavior**: Room DAO returns cached data from local SQLite database. However, because `WishlistViewModel` exposes `stateIn` with `initialValue = emptyList()`, the first frame emits an empty list until the Room query emits its initial database snapshot (typically 10-50ms), producing the visible empty container flash.
- **Synchronous Path**: Room does not provide a synchronous one-shot query out-of-the-box in Flow flows, but `wishDao.getAllWishes()` can be queried synchronously via a blocking DAO call (`wishDao.getAllWishesSync()`) if added.

### 4.3 ChatList
- **Conversations & Peer Profiles**: Powered by Room DAO (`messageDao.getLastMessageForConversation`) combined with cached peer profiles (`StorageManager`).
- **Cold Start Behavior**: `ChatListViewModel` initializes with `_isInitialLoading.value = true` and reads cached profiles from `StorageManager`, displaying a `CircularProgressIndicator` spinner until the first combined flow emission completes.

---

## 5. Pre-Warm Feasibility Matrix (Section 4)

| Screen / Component | Data Source | Synchronous Read Possible Today? | Pre-Warm Feasible? | Implementation Approach |
| :--- | :--- | :--- | :--- | :--- |
| **Dashboard Profile** | DataStore / StorageManager | Partially (via async DataStore or cached mirror) | **Yes** | Maintain an in-memory `@Volatile` user profile cache in `StorageManager` populated at `MainApplication.onCreate()`. |
| **Dashboard Quotes** | File / StorageManager / Network | No (Async file check & network fetch) | **Partial** | Pre-load cached quote JSON and verify image file existence on a background thread during `MainApplication.onCreate()`. Network fetches remain async. |
| **Wishlist Items** | Room Database (`AppDatabase`) | Yes (via blocking Room DAO query) | **Yes** | Add a synchronous DAO query method (`getAllWishesSync()`) and pre-load into `WishlistRepository` static snapshot during app startup. |
| **ChatList Conversations** | Room Database + DataStore | Yes (via blocking Room DAO query) | **Yes** | Pre-load initial conversation list synchronously into `ChatListRepository` or ViewModel initial state. |

---

## 6. Existing Warm-Cache Patterns (Section 5)

- **`SessionTokenCache`**: Uses `companion object` with `@Volatile` / mutex locking to keep tokens cached in memory across the app lifecycle.
- **`WishlistRepository`**: Uses `@Volatile` snapshot properties (`wishesSnapshot`, `statsSnapshot`), though they are populated asynchronously in `init`.
- **Storage Cache Mirror**: DataStore operations are currently suspend-based (`first()`), requiring coroutine suspension. There is no global synchronous in-memory cache mirror for arbitrary DataStore keys across the app.

---

## 7. Splash Gate Feasibility (Section 6)

- **Insertion Point**: In `MainApplication.onCreate()` or an early app-level initialization routine, or via a splash-holding mechanism in `MainActivity.onCreate()` before `setContent`.
- **Splash API Support**: Android 12+ `androidx.core:core-splashscreen` allows keeping the system splash screen visible via `setKeepOnScreenCondition { ... }` until critical local caches are loaded.
- **Timeout Safeguard**: Any splash gate **must** enforce a strict timeout (e.g., max 500-800ms) to prevent indefinite hanging if a local database read or storage operation stalls.
- **Network Rule**: **Local-only reads**. Pre-warming must never block on network requests (Supabase REST / Realtime calls), as poor network conditions would trap the user on the splash screen indefinitely.

---

## 8. Recommended Pre-Warm Scope

1. **Pre-Warm Local Storage Caches in `MainApplication.onCreate()`**:
   - Load user profile and active persona keys synchronously into static in-memory volatiles so ViewModels can seed initial state with zero delay.
2. **Pre-Load Room Database Snapshots**:
   - Add synchronous DAO query wrappers for Wishlist and Chat messages so repositories hold pre-loaded snapshots upon creation.
3. **Keep Network Calls Async**:
   - Never block startup or pre-warming on network operations. Remote sync and Supabase fetches must continue occurring asynchronously in background coroutine scopes.
4. **SplashScreen API Integration (`core-splashscreen`)**:
   - Adopt `androidx.core:core-splashscreen` in `MainActivity` with a `setKeepOnScreenCondition` tied to local cache readiness (bounded by an 800ms max timeout).

---

## 9. Open Questions for the Operator

1. **Splash Screen Library**: Should we integrate `androidx.core:core-splashscreen` to officially control the launch window duration while local pre-warming completes?
2. **Blocking Timeout Limit**: Is an 800ms maximum timeout acceptable as a safety cutoff for local cache pre-warming before forcing UI display?
