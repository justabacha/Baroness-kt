# Wishlist Feature Audit — Issues, Failure Modes, and UX

## 1. Evidence-Backed Failure Modes

1. **Orphaned Local Reactions/Ratings on Temp-ID Translation (Confirmed)**:
   - *Evidence*: `SyncManager.kt` lines 105-117 calls `wishDao.updateWishId(oldTempId = tempId, newServerId = serverId)`, which executes:
     `UPDATE wishes SET id = :newServerId, syncStatus = 'synced' WHERE id = :oldTempId` (`WishDao.kt`, line 39).
   - *Failure*: This updates only the primary key of `wishes`. If a user added reactions or ratings while offline (keyed to the negative temp ID), those child records remain tied to the negative temp ID in Room, becoming orphaned.
2. **Silent Background Sync Failures (Confirmed)**:
   - *Evidence*: `WishlistRepository.kt` and `WishlistApi.kt` catch network exceptions in `try-catch` blocks, log them, and return `false` or `null`. `SyncManager.processQueue()` increments `retryCount` but surfaces zero error indicators or retry buttons in the UI.
3. **Wall-Clock Conflict Resolution Vulnerability (Confirmed)**:
   - *Evidence*: `WishDao.upsertWithTimestampCheck` compares `wish.updatedAt > existing.updatedAt` using client wall-clock timestamps (`System.currentTimeMillis()`), making conflict resolution susceptible to device clock drift between peers.

---

## 2. UI State Table

| UI State | Trigger / Condition | User Experience / Visuals | Matches Reality? |
| :--- | :--- | :--- | :--- |
| **Initial Loading** | `isInitialLoading = true` on startup | Semi-transparent black overlay with `CircularProgressIndicator` | Yes; clears once Room flows emit or 300ms timeout elapses. |
| **Pull-to-Refresh** | User pulls down on list | Animated refresh indicator while `repository.fetchRemoteWishes()` runs | Yes. |
| **Offline Creation** | Network disconnected | Wish appears instantly with negative temp ID (`pending_create`) | Yes; stored locally. |
| **Offline Sync Failure** | Network drops during sync worker run | Wish remains in Room; queued in `sync_queue` | **No visible feedback**; user is unaware sync failed. |
| **Realtime Arrival** | Peer creates a wish | Wish appears in list and triggers an in-app notification banner | Yes, via `postgresChangeFlow`. |

---

## 3. Silent-Failure Inventory

Every location where network exceptions are caught and swallowed without UI notification:
1. `WishlistRepository.fetchProfiles()` (`WishlistRepository.kt`, line 164) — catches exception, logs error.
2. `WishlistRepository.initialSync()` (`WishlistRepository.kt`, line 188) — catches exception, logs error.
3. `WishlistRepository.subscribeToRealtime()` (`WishlistRepository.kt`, line 290) — catches exception, logs error, retries with exponential backoff.
4. `WishlistRepository.handleWishChange()`, `handleReactionChange()`, `handleRatingChange()` (`WishlistRepository.kt`, lines 349, 381, 404) — catch exception, log error.
5. `WishlistApi` methods (`WishlistApi.kt`) — catch exception, log error, return null/false.
6. `SyncManager.processQueue()` (`SyncManager.kt`, line 91) — catches exception, increments retry count, logs error.

---

## 4. Open Questions

1. Should `SyncManager.processWishItem` wrap ID remapping and child reaction/rating foreign key updates in a single atomic Room transaction (`updateWishId` + updating `reactions`/`ratings` foreign keys)?
2. Should failed background sync attempts surface an explicit sync error badge or retry prompt in the UI?
