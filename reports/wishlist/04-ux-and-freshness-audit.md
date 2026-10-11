# Wishlist UX & Freshness Audit Report

## 1. Executive Summary

This audit investigates three operator-reported UX and behavioral regressions in the Wishlist feature:
1. **Avatar Staleness**: Caused by `WishlistRepository` fetching profiles only once at repository instantiation (`init`), omitting profile refetching during manual pull-to-refresh (`refreshWishes`) or lifecycle resume events, combined with Coil image caching.
2. **Wishlist Flicker on Open**: Caused by key churn in `LazyColumn` when temporary negative IDs (`-System.currentTimeMillis()`) are remapped to positive server IDs (`updateWishId`), forcing Compose to re-layout and reorder items.
3. **Blocking Loading Spinner on Every Open**: Caused by `WishlistViewModel` initializing `_isInitialLoading = true` on every screen entry, displaying a full-screen loading overlay even when offline-first Room data is already available.

---

## 2. Avatar Freshness

### 2.1 Current Avatar Load & Cache Model
- **Storage**: Stored in Supabase Storage (`avatars` bucket) and referenced by public URL in `profiles.avatar_url`.
- **Caching**: Rendered via Coil's `AsyncImage` component in `WishlistHeader.kt`, which caches decoded bitmaps in memory and disk keyed by the image URL string.
- **Fetch Triggers**: `WishlistRepository.fetchProfiles()` is invoked **once** inside the singleton repository's `init` block (`WishlistRepository.kt`, line 96). It is never re-invoked on app resume, manual pull-to-refresh (`refreshWishes()`), or Realtime profile updates.
- **Versioning**: Avatar file names include epoch timestamps (e.g., `avatar_1790546062184.jpg`), ensuring the URL string changes upon new upload. However, because the client never re-fetches the `profiles` table after initial startup, it never learns of the new URL string.

### 2.2 Trace the Staleness
- **Scenario A (Self)**: When a user uploads a new avatar (`ProfileManager.saveSetup`), `profiles.avatar_url` is updated remotely. The local DataStore profile cache updates, but `WishlistRepository.profiles` state flow remains stale until process death because `fetchProfiles()` is not re-triggered.
- **Scenario B (Peer)**: The local device has no subscription or periodic fetch for `profiles`. Unless the app is killed and cold-started, the peer's new avatar URL is never fetched.

### 2.3 Missing Signal & Recommended Fix
- **Backend Signal**: `profiles.updated_at` exists on the table and is bumped during profile updates (`ProfileManager.upsertProfile`, `updateFcmToken`).
- **Minimal Fix Proposal (Option B / A Combined)**:
  1. Update `WishlistRepository.refreshWishes()` (`initialSync()`) to also call `fetchProfiles()`.
  2. Register a Realtime postgresChange subscription on the `profiles` table in `WishlistRepository` so profile/avatar updates push instantly across peers without manual refresh.
  3. Ensure Coil invalidates cache or uses unique version keys if needed (though changing URL strings inherently bypasses URL-based caching).

---

## 3. Wishlist Flicker

### 3.1 Current Render Sequence
- **Room Query**: `WishDao` queries `SELECT * FROM wishes ORDER BY createdAt ASC` (`WishDao.kt`, line 10).
- **ViewModel State**: `WishlistViewModel` initializes `isInitialLoading = true`, launching a collection on `wishes` and a 300ms fallback delay (`WishlistViewModel.kt`, lines 117-133).
- **LazyColumn Keys**: `WishlistScreen.kt` uses `key = { _, wish -> wish.id }` (`WishlistScreen.kt`, line 143).

### 3.2 Root Cause of Flicker
When a wish is created offline, it receives a negative temporary ID (`-1710000000`). When `SyncWorker` successfully syncs it, Supabase assigns a positive server ID (`12`), and `SyncManager` executes `wishDao.updateWishId(oldTempId = tempId, newServerId = serverId)` (`WishDao.kt`, line 39). 
Because Compose's `LazyColumn` keys are tied directly to `wish.id`, changing an item's ID from negative to positive causes Compose to treat the old item as removed and the new item as inserted. This structural key change triggers a reorder and visual jump ("settling") on screen.

### 3.3 Proposed Minimal Fix
- **Stable Local Identity**: Introduce a stable immutable identifier (or keep `id` stable if server IDs are adopted immediately or UUIDs are used) OR ensure `LazyColumn` keys do not change during ID translation by using a separate stable surrogate key (e.g., a client-generated UUID stored in a `localUuid` column) for Compose item stability.

---

## 4. Loading Spinner on Every Open

### 4.1 Current Loading State Behavior
- `WishlistViewModel` sets `_isInitialLoading.value = true` upon instantiation. Every time the user navigates to `WishlistScreen`, a new instance of `WishlistViewModel` is created (or scoped to the composition lifecycle), resetting `isInitialLoading` to `true`.
- It displays a full-screen semi-transparent overlay with `CircularProgressIndicator` until `wishes` flow emits non-empty data or the 300ms delay expires.

### 4.2 Proposed Minimal Fix
- **Remove Blocking Spinner**: Remove `isInitialLoading` blocking overlay entirely. Because Room provides instant offline-first data, the wishlist should render immediately on first composition. Background synchronization (`syncInBackground()`) should execute silently without blocking UI interaction or flashing spinners. Pull-to-refresh (`refreshWishes()`) already provides a non-blocking pull indicator (`PullToRefreshBox`).

---

## 5. Cross-Issue Considerations
- **Avatar Freshness & Loading State**: Adding profile refetching to background sync or resume will operate asynchronously and will not block wishlist rendering or cause flicker.
- **Flicker & Instant Render**: Removing the loading spinner makes instant rendering critical; resolving the temp-ID key churn ensures that instant rendering is buttery smooth without visual reordering jumps.

---

## 6. Open Questions for the Operator
1. Should `profiles` table changes be broadcast via Supabase Realtime to achieve instant peer avatar updates without requiring a manual pull-to-refresh?
2. Should wishlist item IDs transition to client-generated UUIDs across local and remote schemas to eliminate negative-to-positive ID key churn entirely?
