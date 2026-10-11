# Wishlist Feature — Comprehensive Full-Stack Audit Report

## 00. Overview & Architecture

### Executive Summary
The Wishlist feature in the Baroness Android application is designed as a collaborative, date-anchored task/goal planning and tracking ledger shared between the two paired personas (`phesty_official` / "P" and `baroness_official` / "B"). Users can create wishes (anchored to a specific date, default status `"planning"`), transition them to `"dusted"` (completed), react with emojis, rate wishes (1-5 scale per persona), and delete wishes. 

While the feature implements a robust offline-first architecture via Room (`wishes`, `reactions`, `ratings`, `sync_queue`), local persistence, and Supabase REST/Realtime sync, our full-stack audit surfaces critical correctness issues around timestamp reconciliation, background queue sync fragility, cross-persona concurrency races, and Realtime channel lifecycle management.

### Feature Surface Map
- **UI Screens & Components**:
  - `WishlistScreen.kt`: Main Compose screen with pull-to-refresh, header, input bar, and dynamic item list.
  - `WishItem.kt`: Row item component handling planning vs dusted states, reactions, ratings, and actions.
  - `WishlistHeader.kt`, `WishlistInput.kt`, `CustomCalendar.kt`, `RatingModal.kt`, `ConfirmModal.kt`.
- **ViewModels**:
  - `WishlistViewModel.kt`: Bridges repository reactive flows to UI state.
- **Repositories & API**:
  - `WishlistRepository.kt`: Central coordinator for Room, PostgREST (`WishlistApi`), Realtime subscriptions, and background WorkManager sync.
  - `WishlistApi.kt`: Network operations against Supabase `wishlist_items`, `wishlist_reactions`, and `wishlist_ratings`.
- **Local Persistence (Room)**:
  - `WishDao`, `ReactionDao`, `RatingDao`, `SyncQueueDao`.
  - `WishEntity`, `ReactionEntity`, `RatingEntity`, `SyncQueueItem`.
- **Background Workers**:
  - `SyncWorker`: Processes queued sync items (`sync_queue`) via WorkManager.

### Top 5 Findings Ranked by Severity
1. **[SEVERITY: CRITICAL] Negative Timestamp ID Collision / Race Risk on Offline Creation**
   - Locally created wishes use negative temporary IDs (`-System.currentTimeMillis()`). When synced and assigned server IDs (Long), local Room deletes and re-inserts can orphan existing reactions or ratings if foreign keys cascade or IDs change without transaction safety.
2. **[SEVERITY: HIGH] Realtime Channel Invalidation Across Token Refresh**
   - `WishlistRepository` initializes Realtime channels once at startup with timestamp suffixes. When tokens refresh or WebSocket transport drops, active wishlist subscriptions do not automatically re-authenticate or re-subscribe.
3. **[SEVERITY: MEDIUM] Unbounded Growth of `sync_queue` on Persistent Network Failure**
   - Failed wishlist mutations queue items in `sync_queue` without retry limits or dead-letter handling, leading to potential duplicate payload submission storms upon reconnection.
4. **[SEVERITY: MEDIUM] Last-Write-Wins Without Clock Synchronization**
   - Timestamp checks (`updatedAt > existing.updatedAt`) rely on local device wall clocks, making conflict resolution vulnerable to device clock skew between Phesty and Baroness devices.
5. **[SEVERITY: LOW] Optimistic Creation Missing Instant Feedback on Sync Failures**
   - Network insertion failures queue items for background WorkManager retry without surfacing inline UI feedback to the user regarding sync status.

---

## 01. Full-Stack Flow Traces

### 1. Viewing Wishes
- **Entry**: User navigates to the Wishlist screen (`WishlistScreen`).
- **Data Load**:
  1. `WishlistViewModel` initializes `wishes` flow collecting from `WishlistRepository.getAllWishes()` (combining `WishDao`, `ReactionDao`, `RatingDao`).
  2. Initially loads whatever is persisted locally in Room.
  3. `LaunchedEffect(Unit)` triggers `viewModel.syncInBackground()`, which calls `repository.fetchRemoteWishes()` (`initialSync()`).
  4. `initialSync()` fetches all remote wishes, reactions, and ratings from Supabase via `WishlistApi` and upserts them into Room using timestamp comparison checks (`upsertWithTimestampCheck`).
- **Loading State**: `isInitialLoading` is set to `true` until either wishes are non-empty or a 300ms fallback timer expires, showing a `CircularProgressIndicator` overlay.
- **Empty / Error / Offline**:
  - **Empty**: Displays empty list container with header stats (`0 total, 0 dusted`).
  - **Offline**: Gracefully falls back to local Room cache; network exceptions in `initialSync()` are caught and logged without crashing the UI.

### 2. Creating a Wish
- **User Action**: User enters text in `WishlistInput`, picks a date via calendar, and taps cast button.
- **Local Write**:
  1. `WishlistViewModel.createWish` generates a temporary negative ID (`-System.currentTimeMillis()`) and creates a `Wish` model with `creator = currentUserKey` ("P" or "B").
  2. `WishlistRepository.insertWish` maps this to a `WishEntity` with `syncStatus = "pending_create"` and inserts it into Room.
  3. UI updates instantly via Room's reactive `Flow`.
- **Sync & Retry**:
  1. `insertWish` serializes the wish payload and queues an item in `sync_queue` via `SyncManager`.
  2. Calls `syncNow()`, which enqueues a `SyncWorker` job via WorkManager (constrained to active network).
  3. `SyncWorker` processes the queue by calling `WishlistApi.createWish()`, which upserts the record to Supabase and returns the server-assigned ID.
- **Other Persona Visibility**: Once synced to Supabase, Realtime postgresChangeFlow (`wishlist_items` channel) broadcasts the INSERT event to the peer device, triggering `handleWishChange()` and inserting the record into the peer's Room database.

### 3. Editing a Wish
- *Finding*: Direct inline editing of wish text is **not supported** in the current UI. Wishes are immutable once created (aside from status transitions like planning -> dusted, reactions, and ratings).

### 4. Deleting a Wish
- **User Action**: User taps trash icon on a wish, opens `ConfirmModal`, and confirms deletion.
- **Execution**:
  1. `WishlistViewModel.deleteWish` calls `WishlistRepository.deleteWish(wishId)`.
  2. Repository immediately deletes the wish, reactions, and ratings from Room (`wishDao.deleteWish`, `reactionDao.deleteReactionsForWish`, `ratingDao.deleteRatingsForWish`).
  3. Queues a `"delete"` operation in `sync_queue` and triggers `syncNow()`.
  4. `SyncWorker` executes `WishlistApi.deleteWish(wishId)` on Supabase.
  5. Realtime broadcast (`PostgresAction.Delete`) propagates the deletion to the peer device.

### 5. Reacting to a Wish
- **User Action**: User taps smiley icon, selects an emoji from `EmojiPicker`.
- **Execution**:
  1. `WishlistViewModel.saveReaction` invokes `WishlistRepository.saveReaction(wishId, personaId, emoji)`.
  2. Immediately upserts into local Room via `reactionDao.insertReaction`.
  3. Queues `"upsert"` operation in `sync_queue` for `wishlist_reactions` (conflict target `wish_id, persona_id`) and triggers `syncNow()`.
  4. `SyncWorker` executes `WishlistApi.upsertReaction`.
  5. Realtime broadcast updates peer Room database.

### 6. Rating a Wish
- **User Action**: User taps rating box, opens `RatingModal`, selects 1-5 stars.
- **Execution**:
  1. `WishlistViewModel.saveRating` invokes `WishlistRepository.saveRating(wishId, personaId, rating)`.
  2. Immediately upserts into local Room via `ratingDao.insertRating`.
  3. Queues `"upsert"` in `sync_queue` for `wishlist_ratings` (conflict target `wish_id, persona_id`) and triggers `syncNow()`.
  4. `SyncWorker` executes `WishlistApi.upsertRating`.
  5. Realtime updates peer Room database.

### 7. Cross-Persona Behavior
- **Concurrent Creation**: Both devices can create wishes offline; negative temp IDs are reconciled when server IDs return.
- **Concurrent Reactions/Ratings**: Composite primary keys (`wishId, personaId`) on reactions and ratings ensure personas cannot overwrite each other's reactions or ratings; upserts merge independently.
- **Realtime Push**: When Phesty adds a wish, Baroness's app receives the Realtime INSERT event, inserts it into Room, and triggers an in-app notification banner (`notificationViewModel?.showInAppNotification`).

---

## 02. Data Model & Persistence

### Local Persistence (Room)
1. **`wishes` (`WishEntity`)**:
   - Primary Key: `id` (Long). Local temp IDs are negative (`-System.currentTimeMillis()`); server IDs are positive identities.
   - Columns: `text`, `wishDate` ("yyyy-MM-dd"), `status` ("planning" or "dusted"), `creatorId` ("phesty_official" or "baroness_official"), `createdAt` (Long), `updatedAt` (Long), `syncStatus` ("synced", "pending_create", "pending_update", "pending_delete").
   - DAO: `WishDao` provides getAllWishes flow, upsertWithTimestampCheck, updateStatus, deleteWish.
2. **`reactions` (`ReactionEntity`)**:
   - Composite Primary Key: `["wishId", "personaId"]`.
   - Columns: `wishId`, `personaId`, `emoji`.
   - DAO: `ReactionDao`.
3. **`ratings` (`RatingEntity`)**:
   - Composite Primary Key: `["wishId", "personaId"]`.
   - Columns: `wishId`, `personaId`, `rating` (1 to 5).
   - DAO: `RatingDao`.
4. **`sync_queue` (`SyncQueueItem`)**:
   - Primary Key: `id` (auto-generate Long).
   - Columns: `tableName`, `recordId`, `operation`, `payload`, `localTempId`, `createdAt`.

### Remote Schema (Supabase PostgreSQL)
- **`wishlist_items`**: `id` (bigint identity), `text` (text), `wish_date` (date), `status` (text, check planning/dusted), `creator_id` (text), `created_at` (timestamptz), `updated_at` (timestamptz).
- **`wishlist_reactions`**: `id` (bigint), `wish_id` (bigint), `persona_id` (text), `emoji` (text), `created_at` (timestamptz). Unique constraint on `(wish_id, persona_id)`.
- **`wishlist_ratings`**: `id` (bigint), `wish_id` (bigint), `persona_id` (text), `rating` (integer 1-5), `created_at` (timestamptz). Unique constraint on `(wish_id, persona_id)`.

### Drift & Consistency Analysis
- **Timestamps**: Local `createdAt` and `updatedAt` use client wall clock (`System.currentTimeMillis()`), while remote uses PostgreSQL `now()`. Device clock skew between Phesty and Baroness can lead to incorrect winner determination in `upsertWithTimestampCheck`.
- **Foreign Keys**: Remote foreign keys (`wish_id` referencing `wishlist_items(id)`) cascade or restrict, while local Room database relies on repository logic (`deleteReactionsForWish`, `deleteRatingsForWish`) rather than foreign key constraints.

---

## 03. Repository and API Layer

### Repository Architecture
- **Singleton Pattern**: Instantiated via `WishlistRepository.getInstance(context)`.
- **Initialization**: Launches a background coroutine in `init` fetching profiles, subscribing to Realtime, registering a network availability callback, and setting up process lifecycle observation (`ON_RESUME`).
- **Data Combination**: Exposes `getAllWishes()` as a reactive Flow combining `wishDao.getAllWishes()`, `reactionDao.getAllReactions()`, and `ratingDao.getAllRatings()`.

### API Call Shapes
- All methods (`createWish`, `updateWish`, `deleteWish`, `upsertReaction`, `upsertRating`, `fetchAllWishes`, etc.) execute via `supabase.postgrest[...]` utilizing PostgREST query builders.
- **Error Handling**: Network operations are wrapped in `try-catch` blocks logging errors and returning null or `false` on failure, failing silently without surfacing UI error banners to the user.
- **Idempotency**: `upsertReaction` and `upsertRating` specify `onConflict = "wish_id,persona_id"`, ensuring idempotent upserts. `createWish` and `deleteWish` rely on unique IDs and primary keys.

### RLS Policy Alignment
- Under Phase 3 strict RLS policies, wishlist tables (`wishlist_items`, `wishlist_reactions`, `wishlist_ratings`) require authenticated sessions with a valid persona claim (`auth.jwt() ->> 'persona' IS NOT NULL`).
- `WishlistApi` methods execute via `SupabaseConfig.supabase`, which supplies the current JWT via `accessToken = { SessionTokenCache.getValidTokenOrRefresh() }`.

---

## 04. Realtime and Sync Model

### Channel Lifecycle
- **Subscription**: `WishlistRepository.subscribeToRealtime()` establishes three separate channels (`wishlist-items-$timestamp`, `reactions-$timestamp`, `ratings-$timestamp`) listening to `postgresChangeFlow` on `wishlist_items`, `wishlist_reactions`, and `wishlist_ratings`.
- **Token Refresh Gap**: Unlike `ChatRepository` (which observes session/persona changes), `WishlistRepository` initializes channels once at startup. If an in-process token refresh occurs, active Realtime channels are not automatically torn down and re-subscribed, leading to silent connection stale states if the WebSocket transport reconnects without re-authenticating active channel joins.

### Sync Model & Reconciliation
- **Source of Truth**: Hybrid architecture. Local Room DB serves as the immediate source of truth for UI rendering; Supabase serves as the authoritative remote store.
- **Conflict Resolution**: `upsertWithTimestampCheck` compares `wish.updatedAt > existing.updatedAt`. However, because timestamps rely on client wall clocks (`System.currentTimeMillis()`), unsynchronized device clocks can cause newer edits to be overwritten by older edits from another device.
- **Deletes**: Deletes are propagated locally and queued for remote execution via `sync_queue`.

### Replica Identity
- Live inspection (`pg_class.relreplident`) confirmed wishlist tables are set to `'d'` (default primary key). Setting them to `REPLICA IDENTITY FULL` (`'F'`) is recommended for robust UPDATE/DELETE change broadcasting.

---

## 05. UI & UX Review

### Screen & Component Inventory
- `WishlistScreen.kt`: Main container with pull-to-refresh (`PullToRefreshBox`), loading overlay, header, input bar, and `LazyColumn`.
- `WishItem.kt`: Individual list item supporting planning vs dusted states, expanding text, emoji reactions, and rating badges.
- `WishlistHeader.kt`: Displays total and dusted statistics alongside avatar indicators for Phesty and Baroness.
- `WishlistInput.kt`: Text input field and date picker trigger.
- **Modals**: `CustomCalendar`, `EmojiPicker`, `RatingModal`, `ConfirmModal`.

### States & User Feedback
- **Loading**: `isInitialLoading` shows a full-screen semi-transparent overlay with `CircularProgressIndicator`.
- **Empty**: Displays empty list state with header stats.
- **Error Handling**: Network failures in `createWish`, `deleteWish`, `saveReaction`, or `saveRating` are caught silently in the repository/API layer. The user receives no toast or error banner if a network sync fails in the background, relying entirely on optimistic UI updates.
- **Accessibility & Contrast**: Dark theme styling with high-contrast text and explicit color codes for creators (Green for Phesty, Blue for Baroness).

---

## 06. Edge Cases and Failure Modes

### Enumerated Failure Modes
1. **Clock Skew Conflict Resolution Failure**: Phesty's device clock is 2 minutes ahead of Baroness's. Baroness edits a wish, then Phesty edits it. Because Phesty's timestamp is numerically larger, Phesty's edit wins even though it occurred chronologically earlier.
2. **Orphaned Reactions on ID Translation**: A wish is created offline with a negative temp ID (`-123`). Local reactions are inserted into Room with `wishId = -123`. When the wish syncs and receives a positive server ID (`456`), local Room updates the wish ID (`updateWishId`), but if reactions are not atomically updated with the new `wishId`, local reaction mappings become orphaned.
3. **Queue Storm on Persistent Offline Mutations**: A user performs multiple creations/reactions offline. Upon reconnection, `SyncWorker` processes the `sync_queue`. If a network error occurs mid-batch, WorkManager retries with exponential backoff, potentially duplicating HTTP requests if idempotency keys or unique constraints aren't strictly enforced by PostgREST upserts.
4. **Realtime Stale Subscription After Token Refresh**: After 7 days, the access token refreshes. Realtime channels established at app startup remain bound to old state unless explicitly re-subscribed, leading to silent Realtime drops.
5. **Silent Sync Failures**: Network request fails during wish creation. The wish is queued in `sync_queue`, but the user has no visual indication that the wish is only stored locally and has failed to sync.

---

## 07. Cross-Feature Interactions

### Cross-Feature Touchpoints
- **Notifications (FCM & In-App)**: When a new wishlist item is inserted via Realtime (`handleWishChange`), `WishlistRepository` triggers an in-app notification via `notificationViewModel?.showInAppNotification(...)` displaying the creator's avatar, display name, and wish text.
- **Session / Identity**: Wishlist relies entirely on `storageManager.getString("currentPersonaId")` and `UserSessionManager` to identify whether the current user is `phesty_official` ("P") or `baroness_official` ("B") for reactions and ratings.
- **Settings / Appearance**: `WishlistScreen` accepts `SettingsViewModel` to render the active dynamic background wallpaper.
- **Other Features (Friday, HumanChat, Gallery)**: No direct coupling exists between Wishlist and Friday AI chat, HumanChat messages, or Gallery storage beyond shared Supabase profile data.

---

## 08. Findings, Questions, and Next-Phase Candidates

### Consolidated Findings by Severity
- **CRITICAL**: Negative Temp ID ID-Mapping Fragility (Local reactions/ratings tied to negative temp IDs may fail to cascade or remap when server assigns positive IDs).
- **HIGH**: Realtime Subscription Token-Refresh Staleness (`WishlistRepository` does not re-subscribe its Realtime channels upon token refresh); Device Clock Skew Vulnerability (last-write-wins relies on client wall clocks).
- **MEDIUM**: Silent Background Sync Failures (failed mutations queue without UI feedback); Lack of Inline Editing (wishes cannot be edited once created).

### Operator Questions
1. Should temp ID remapping in Room (`updateWishId`) be refactored into a single atomic transaction that also updates all associated `reactions` and `ratings` foreign keys?
2. Should failed wishlist sync operations surface a UI indicator (e.g. sync badge or error banner) rather than failing silently in the background?
3. Should wishlist item editing be supported in the UI, or is immutable-with-delete-and-recreate the intended design?

### Suggested Next-Phase Candidates (Do Not Implement)
- Implement token-refresh channel restart observers in `WishlistRepository` (mirroring `ChatRepository`).
- Add atomic foreign key remapping for reactions and ratings during temporary-to-server ID translation.
- Expose sync status indicators on wishlist items for robust offline feedback.
