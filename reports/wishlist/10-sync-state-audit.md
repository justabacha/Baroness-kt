# Wishlist Sync State Audit Report

## 1. Executive Summary

The planned wishlist sync-status UI requires distinguishing between **synced**, **pending**, and **failed** states for individual wish items, and gating secondary actions (reactions, ratings, dusting) until a wish has successfully synced to the server. 

Currently, `WishEntity` supports basic `syncStatus` values (`"synced"`, `"pending_create"`, `"pending_update"`, `"pending_delete"`), but there is **no explicit `"failed"` state** stored on `WishEntity` (failures are only logged in `SyncQueueItem.lastError` and increment `retryCount`), and UI components (`WishItem.kt`) are entirely unaware of `syncStatus`. Furthermore, reactions and ratings currently operate on local IDs without checking whether a wish has been assigned a server ID. To support the planned UI, `WishEntity` requires a dedicated failure tracking state, queue error propagation to the entity, UI state binding in `WishItem`, and action gating.

---

## 2. Room Sync State Inventory (`WishEntity`)

- **Distinct `syncStatus` values**:
  - `"synced"` (default / normal state)
  - `"pending_create"` (set in `WishlistRepository.insertWish` for offline/new wishes)
  - `"pending_update"` (defined in model comment, though status changes currently update directly)
  - `"pending_delete"` (defined in model comment)
- **Where Set / Cleared**:
  - Set to `"pending_create"` on local insert (`WishlistRepository.kt`, line 604).
  - Cleared/advanced to `"synced"` when temp ID is swapped for server ID (`WishDao.kt`, line 38: `UPDATE wishes SET id = :newServerId, syncStatus = 'synced' WHERE id = :oldTempId`).
- **Failed State Presence**: **None.** There is no `"failed"` state on `WishEntity`. When `SyncManager` hits max retries (5) or network errors, items remain in `"pending_create"` indefinitely while `SyncQueueItem` tracks `retryCount` and `lastError`.
- **`sync_queue` Retry Tracking**: `SyncQueueItem` maintains `retryCount: Int` and `lastError: String?`. If `retryCount >= 5`, `SyncManager.processQueue()` skips the item (`SyncManager.kt`, line 46).

---

## 3. Sync Retry Logic Review (`SyncManager` & `SyncWorker`)

- **API Failure Handling**: When an API call fails during `processQueue()`, `SyncManager` catches the exception, increments `retryCount`, stores `lastError`, and leaves the item in the queue.
- **WorkManager Retries**: `WishlistRepository` enqueues `SyncWorker` with a linear backoff policy (`SyncWorker.kt`).
- **Manual Retry**: There is **no UI mechanism** for the user to manually trigger a retry today.
- **Attempt Count**: Tracked in `SyncQueueItem.retryCount`.

---

## 4. UI State Logic Inventory (`WishItem.kt`)

- **Number Bubble**: Generated as the zero-based index (`index + 1`) in `WishlistScreen` (`LazyColumn` item index). It is not a stored database ID.
- **Right-Side Icons**: Distinguished by `isWish.status == "planning"` vs `"dusted"`. Planning shows trash + checkmark; dusted shows download + smiley + rating box.
- **Sync-Related State**: **None.** `WishItem` does not read `syncStatus` or `sync_queue` data.
- **Action Handlers**: Passed down from `WishlistScreen` via lambdas (`onDust`, `onDelete`, `onOpenEmoji`, `onOpenRating`, `onUploadPhotos`).
- **Gating**: None of the actions check `syncStatus` before executing.

---

## 5. Reaction / Rating Gate Review

- **Dust Action**: Triggers `WishlistViewModel.dustWish(wishId)` -> `WishlistRepository.updateWishStatus(wishId, "dusted")`. No sync check.
- **Reaction Action**: Triggers `WishlistViewModel.saveReaction` -> `WishlistRepository.saveReaction`. No sync check (creates local record and queues upsert).
- **Rating Action**: Triggers `WishlistViewModel.saveRating` -> `WishlistRepository.saveRating`. No sync check.
- **Model Exposure**: `Wish` model does not expose `syncStatus`.

---

## 6. Number Bubble Behavior

- The number is simply the display index (`index + 1`) in the `LazyColumn`.
- When an item is deleted or added at the top, the remaining items renumber dynamically because the index shifts.
- *UX Implication*: Replacing the number bubble with a retry icon on failure will cause the icon to shift position if items are added or deleted, which is expected behavior for list indices.

---

## 7. Proposed State Machine Table

| Wish State | Room Column Representation | Left Bubble Display | Right Side Display | Allowed User Actions |
| :--- | :--- | :--- | :--- | :--- |
| **Pending Sync** (`pending_create`) | `syncStatus = "pending_create"` (id < 0) | Index number (e.g. `1`) + `[pending]` badge | `[pending]` badge (replaces action icons) | Delete only |
| **Sync Failed** (`failed_create`) | `syncStatus = "failed_create"` (or queue check) | Retry Icon (replaces number) | `[failed]` badge | Delete, Manual Retry |
| **Synced** (`synced`, Planning) | `syncStatus = "synced"` (id > 0, status = "planning") | Index number (e.g. `1`) | Trash + Dust checkmark | Delete, Dust, (Reactions/Ratings if enabled) |
| **Synced** (`synced`, Dusted) | `syncStatus = "synced"` (id > 0, status = "dusted") | Index number (e.g. `1`) | Download + Emoji + Rating | Delete, React, Rate |

---

## 8. Gaps and Open Questions

1. **Missing Entity State**: `WishEntity` needs a distinct `"failed"` state (e.g., `syncStatus = "failed"`) when `SyncManager` exhausts retries.
2. **Missing Worker-to-Entity Signal**: `SyncManager` updates `SyncQueueItem` but does not propagate failure status back to `WishEntity.syncStatus`.
3. **Missing Manual Retry Hook**: No method exists to reset retry counts and re-trigger sync for a failed wish.
4. **Missing UI Gating**: `WishItem` and `WishlistViewModel` lack logic to inspect sync state and disable reaction/rating actions on unsynced wishes.
