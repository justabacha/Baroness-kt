# Wishlist Sync Pipeline Audit Report — Duplicate Creates & Race Conditions

## 1. Executive Summary

Live testing of the wishlist sync status UI revealed severe concurrency and race condition bugs in the background sync pipeline:
1. **Duplicate Server Creates**: Multiple concurrent triggers (`syncNow()` called from network callbacks, resume observers, and UI actions) enqueue overlapping `SyncWorker` tasks. Because `SyncManager` lacks concurrency locking or "in-progress" status marking on `sync_queue` items, multiple worker threads pick up the same pending create item simultaneously and execute `WishlistApi.createWish()` multiple times, creating duplicate rows on Supabase.
2. **Reconciliation Deleting Newly Created Wishes**: When a local wish successfully creates on the server, `initialSync()` (`fetchRemoteWishes()`) can execute concurrently or immediately afterward before Supabase's read replica reflects the new row. `reconcileSyncedWishes()` mistakenly flags the newly created wish as "stale" (absent from the fetch result) and deletes it locally (`Removed stale synced wish id=...`), causing the item to vanish from the UI and triggering subsequent crashes when dangling references are accessed.
3. **Dust Update / Peer Sync Latency**: PostgREST updates correctly target server IDs, but background sync coordination gaps require explicit refreshes or race with fetch cycles.

---

## 2. Duplicate Creates & Worker Concurrency

### 2.1 Enqueue Triggers
- `WishlistRepository` calls `syncNow()` upon:
  - Wish creation, deletion, status updates, reactions, and ratings.
  - Network availability callback (`setupNetworkListener`).
  - Lifecycle `ON_RESUME` event (`setupLifecycleObserver`).
- **WorkManager Configuration**: `syncNow()` enqueues `SyncWorker` using `WorkManager.enqueue(workRequest)` with a linear backoff policy (`WishlistRepository.kt`, line 542). It does **not** use `enqueueUniqueWork` with `ExistingWorkPolicy.KEEP` or `REPLACE`.

### 2.2 Concurrency Defect
- Multiple concurrent triggers (e.g., app resume + network callback + user action) enqueue multiple independent `SyncWorker` jobs.
- `SyncManager.processQueue()` reads all pending items (`dao.getAllPending()`) without marking them as `"in_progress"` or locking rows.
- Two worker instances running in parallel on `Dispatchers.IO` fetch the same pending `"create"` queue item at the same millisecond and execute `WishlistApi.createWish()` concurrently against Supabase, producing duplicate rows.

---

## 3. Reconciliation Racing Creates (`Removed stale synced wish`)

### Root Cause
1. User creates a wish offline (temp ID `-123`).
2. `SyncWorker` successfully calls `WishlistApi.createWish()`, receiving server ID `172`.
3. `SyncManager` updates local Room ID (`updateWishId(-123 -> 172)`).
4. Concurrently or immediately after, `initialSync()` runs (`fetchRemoteWishes()`). Because of slight network/replication latency, the remote fetch query `WishlistApi.fetchAllWishes()` returns before ID `172` is visible in the query snapshot.
5. `reconcileSyncedWishes` computes `staleWishIds` by comparing local synced IDs against remote IDs. Because `172` is missing from the delayed fetch result, reconciliation treats `172` as stale and executes `deleteWishAndRelations(172)` (`Removed stale synced wish id=172`).
6. The local wish vanishes from the UI, and subsequent operations against the deleted ID trigger crashes.

---

## 4. ID Remap Failure & Peer Dust Update

- **Remap**: `SyncManager.processWishItem` successfully swaps negative temp IDs to positive server IDs via `wishDao.updateWishId`. However, because reactions and ratings reference `wishId`, any concurrent action referencing the old negative ID during the swap window can result in foreign key mismatches.
- **Dust Update**: `updateWishStatus` updates status to `"dusted"` and queues an update operation. Realtime broadcasts propagate the update, but concurrent local/remote sync sync cycles can cause ordering race conditions.

---

## 5. Crash on Wishlist Open / Peer Refresh Requirement

- **Crash Path**: Caused by `reconcileSyncedWishes` deleting a newly synced wish that hasn't synchronized into the remote fetch snapshot yet. Subsequent UI bindings or relationship queries attempting to access the deleted wish ID throw null pointer or foreign key exceptions.
- **Own-Refresh Requirement**: Due to race conditions between local optimistic updates, queue processing, and remote fetching, local state can temporarily diverge from server state until a manual pull-to-refresh forces a clean re-sync.

---

## 6. Corrective Recommendations (Audit Only — Do Not Implement)

1. **Unique Work Enqueue**: Replace `WorkManager.enqueue(workRequest)` in `WishlistRepository.syncNow()` with `WorkManager.enqueueUniqueWork("WishlistSync", ExistingWorkPolicy.KEEP, workRequest)` to prevent concurrent worker execution.
2. **Atomic Queue Locking**: Mark items as `"in_progress"` or lock rows in `SyncManager` during queue processing to prevent multi-worker re-entrancy.
3. **Reconciliation Grace Period**: Add a timestamp grace period (e.g., ignore wishes created within the last 30-60 seconds) in `reconcileSyncedWishes` so newly created local wishes are never incorrectly purged as stale.
