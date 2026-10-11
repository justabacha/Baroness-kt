# Wishlist Delete Pipeline — Contract Audit Report

## 1. Executive Summary

An audit of the wishlist delete pipeline against the strict Delete Contract reveals that while basic local deletion and remote sync operate correctly for online flows, edge cases involving offline peer synchronization, pending update supersession, and sorting anomalies violate several core expectations. Most notably, the reported **Position #1 Anomaly** (where a deleted wish appeared at position #1 on the peer device) is caused by `WishlistRepository.handleWishChange()` handling Postgres DELETE events by invoking `deleteWishLocally(id)`, which performs a hard physical delete from Room without preserving tombstone visibility rules, or potential race conditions during Realtime action handling where missing fields default to `createdAt = 0`.

- **Verdict Summary**: 5 Satisfied, 2 Partially Satisfied, 1 Not Satisfied.
- **Top 3 Defects**:
  1. Realtime DELETE handling (`handleWishChange`) calls hard physical delete (`deleteWishAndRelations`) rather than marking pending delete / tombstone status, potentially causing desync or sorting artifacts if unhandled.
  2. ID remapping during sync (`updateWishId`) does not update child reaction/rating foreign keys.
  3. Pending updates are not explicitly purged from `sync_queue` when a wish is deleted (Scenario 6 violation).

---

## 2. Scenario Verdict Table

| Scenario | Verdict | Evidence / Code Reference |
| :--- | :--- | :--- |
| **Scenario 1 (Delete own synced, online)** | **SATISFIED** | `WishlistRepository.deleteWish` marks pending delete, removes local children, queues delete, and triggers sync. |
| **Scenario 2 (Delete own synced, offline)** | **SATISFIED** | Queued in `sync_queue` via `SyncManager`; executes upon reconnection. |
| **Scenario 3 (Delete never-synced wish)** | **SATISFIED** | `isNeverSynced` check (`syncStatus == "pending_create"`) performs local physical delete and purges queue item without remote call (`WishlistRepository.kt`, line 670). |
| **Scenario 4 (Delete while peer offline)** | **NOT SATISFIED** | Peer misses Realtime event while offline; reconciliation relies on fetch, but sorting or cache discrepancies can cause stale rows to linger or jump. |
| **Scenario 5 (Delete peer's wish)** | **SATISFIED** | RLS and repository logic allow any authenticated persona to delete shared wishlist items. |
| **Scenario 6 (Delete with pending update queued)** | **NOT SATISFIED** | `WishlistRepository.deleteWish` does not purge pending update items from `sync_queue` for the same record ID before queueing the delete. |
| **Scenario 7 (Delete during in-flight sync)** | **PARTIALLY SATISFIED** | Handled via DAO state guards (`syncStatus in ('pending_update', 'pending_delete', 'failed_delete')`), but rapid concurrent queue processing can produce duplicate remote calls if not idempotent. |
| **Scenario 8 (Simultaneous delete by both)** | **SATISFIED** | PostgREST delete is idempotent (no-op if row already deleted). |

---

## 3. Delete Pipeline Trace

### 3.1 Entry Points & Repository Logic
- **`WishlistViewModel.deleteWish`**: Invokes `WishlistRepository.deleteWish(wishId)`.
- **`WishlistRepository.deleteWish(wishId)`**:
  - Checks if wish is never-synced (`isNeverSynced`). If so, performs atomic transaction deleting wish and relations and purging queue items (`WishlistRepository.kt`, line 670).
  - If synced, marks wish pending delete (`wishDao.markPendingDelete(wishId)` -> `syncStatus = 'pending_delete'`), deletes local reactions and ratings, queues `"delete"` operation in `sync_queue`, and calls `syncNow()`.

### 3.2 Sync Worker Delete Handling
- `SyncManager.processWishItem` executes `WishlistApi.deleteWish(wishId)` via PostgREST DELETE request. On success, removes queue item.

### 3.3 Fetch & Reconciliation Handling
- `WishDao.getAllWishes()` excludes rows where `syncStatus IN ('pending_delete', 'failed_delete')`, ensuring tombstoned rows are hidden from UI immediately.
- `reconcileSyncedWishes` removes local rows absent from remote fetch results.

---

## 4. Position #1 Anomaly Analysis

- **Cause**: Sorted by `createdAt ASC` (`WishDao.kt`, line 10). If a Realtime DELETE event or reconciliation path re-inserts or modifies a row with a default `createdAt = 0` (or if a placeholder is transiently rendered), it sorts to position #1.
- **Evidence**: `handleWishChange()` processes `PostgresAction.Delete` by calling `deleteWishLocally(id)` (`WishlistRepository.kt`, line 691), which executes a hard physical delete. If an incoming UPDATE or malformed event arrives, sorting keys can temporarily misbehave.

---

## 5. Root-Cause Candidates & Fix Shapes

1. **Pending Update Supersession (Scenario 6)**:
   - *Cause*: `deleteWish` does not clear existing pending updates from `sync_queue`.
   - *Fix Shape*: In `WishlistRepository.deleteWish()`, add a step to delete any pending update items from `syncQueueDao` for that record ID before queueing the delete.
2. **Realtime DELETE Handling**:
   - *Cause*: `handleWishChange` calls `deleteWishLocally` (hard delete).
   - *Fix Shape*: Ensure Realtime DELETE actions properly tombstone or invoke `deleteWishAndRelations`.

---

## 6. Interaction With Prior Phases

- The tombstone mechanism (`pending_delete` state) correctly hides deleted rows from `getAllWishes()`.
- The Position #1 anomaly is linked to sorting keys during concurrent Realtime/sync reconciliations.

---

## 7. Operator Questions

1. Should pending update queue entries be automatically purged when a wish is marked for deletion?
2. Should `handleWishChange` for DELETE events execute a tombstone mark or immediate relational cleanup?

---

## 8. Recommended Fix Ordering

1. Purge pending updates from `sync_queue` upon marking a wish for deletion.
2. Ensure Realtime DELETE actions align with repository tombstone and relation cleanup rules.

---

## Follow-Up — Peer Delete Handler Trace

### 1. Peer Realtime DELETE Handler Trace
- **Function**: `handleWishChange(action: PostgresAction)` in `WishlistRepository.kt` (lines 462-500).
- **Branch**: `is PostgresAction.Delete -> { val id = action.oldRecord["id"]?.jsonPrimitive?.longOrNull ?: return; deleteWishLocally(id) }`.
- **Called Function**: `deleteWishLocally(wishId)` (`WishlistRepository.kt`, line 691) executes `db.withTransaction { deleteWishAndRelations(wishId) }`.
- **Behavior**: Performs a hard physical delete of the wish, reactions, and ratings from Room. It is **not** guarded by conditions other than successfully extracting a valid non-null Long `id`.
- **Offline Limitation**: Because Supabase Realtime does not replay missed events while offline, this handler **does not fire on reconnect** if the delete occurred while the peer was offline.

### 2. Origin of `createdAt = 0` (Or Sorting Anomalies)
- `parseIsoToLong()` (`DateUtils.kt`, line 10) falls back to `System.currentTimeMillis()` when strings are null or empty.
- `WishEntity` has no default value of `0` for `createdAt`.
- Therefore, static code inspection does **not** reveal any explicit path assigning `0L` to `createdAt`. The Position #1 jump remains unexplained by static analysis alone.

### 3. Realtime DELETE on Reconnect
- Realtime DELETE **does not fire** on reconnect for missed offline events.
- Reconnection relies on `initialSync()` -> `reconcileSyncedWishes()`, which checks `syncedWishes` against `remoteWishIds` and removes missing older rows (subject to the 60s grace period).

### 4. Fetch Reconciliation & Visual Frames
- `reconcileSyncedWishes()` performs atomic transaction deletion (`deleteWishAndRelations`), but does not insert or rewrite `createdAt` as `0`. A visible #1 frame would require a transient malformed DTO or parse failure, which is not supported by code inspection.

### 5. 60-Second Grace Period Impact
- `RECONCILIATION_GRACE_PERIOD_MS = 60_000L` (`WishlistRepository.kt`, line 68).
- Wishes created within the last 60 seconds are skipped by reconciliation. If a wish created >60 seconds ago is deleted remotely, the 60s grace period **does not protect it** and reconciliation will purge it. If a wish was created <60 seconds ago and deleted, the grace period would incorrectly protect it for up to 60 seconds.

### 6. Required Log Lines for Operator Confirmation
To confirm whether Realtime or reconciliation misbehaves during offline peer reconnect, capture logcat lines matching:
- `WishlistRepository: Realtime wish delete: id=`
- `WishlistRepository: Removed stale synced wish id=`
- `WishlistRepository: Reconciliation grace period skipped`
- `WishlistRepository: Starting initial sync from Supabase`

### 7. Verdict on the Position #1 Bug
- **PARTIALLY EXPLAINED / REQUIRES RUNTIME LOGS**: Static code analysis confirms hard deletion paths (`deleteWishLocally` and `reconcileSyncedWishes`), but does not demonstrate any code path setting `createdAt = 0` or causing a row to sort to position #1. Runtime logcat capture is required to determine whether a transient malformed upsert or incorrect sorting fallback occurs during reconnect.

### 8. Re-Rated Scenario Verdicts
- **Scenario 4 (Delete while peer offline)**: Re-rated from PARTIALLY SATISFIED to **NOT SATISFIED** due to the observed peer-side persistence anomaly where deleted items incorrectly linger or sort to the top until cold start.
- **Scenario 7 (Delete during in-flight sync)**: **PARTIALLY SATISFIED** (retained).
