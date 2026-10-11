# Wishlist Sync Pipeline Concurrency and Reconciliation Fix

## Changes

- **Unique work enqueue — `WishlistRepository.syncNow()`**: Replaced ordinary `enqueue(workRequest)` with `enqueueUniqueWork("WishlistSync", ExistingWorkPolicy.KEEP, workRequest)`. The wishlist feature had no existing unique-work name; this introduces its only one. It coalesces duplicate triggers while a wishlist sync is enqueued or running.
- **In-progress marker — `SyncQueueItem` and Room**: Added nullable `inProgressAt: Long?` to queue rows. `AppDatabase.kt` defines and registers `MIGRATION_8_9`, which adds nullable SQLite `INTEGER` column `inProgressAt` to `sync_queue`; the database version is now 9. Before processing, the manager atomically claims an unclaimed or stale row with a timestamp. Rows marked within the last 60 seconds are skipped. Successful rows are deleted as before; failures increment `retryCount`, record `lastError`, and clear the marker. A stale marker can be reclaimed after 60 seconds if processing died before clearing it. The queue scan also checks for newly queued items during the same worker run without retrying an item twice in that run.
- **Reconciliation grace period**: `reconcileSyncedWishes()` excludes local synced rows absent from the fetched server IDs when their `createdAt` is within the previous 60 seconds. It logs how many missing rows the grace period skipped. Older missing rows remain reconciliation candidates.
- **Serialization and call paths**: Before this change, `initialSync()` was reached through `fetchRemoteWishes()` from network availability, app resume, and ViewModel refresh/background sync. `processQueue()` was called by `SyncWorker` directly. These paths could overlap. The singleton `WishlistRepository` now owns one `Mutex`; `initialSync()` locks it before fetching and holds it through upsert/reconciliation, while the worker calls `processSyncQueue()` to lock it around queue processing. Both operations therefore run serially app-wide. `syncNow()` is triggered by wish create/delete/status, reaction/rating, manual retry, network availability, and app resume.
- **Reconciliation child cleanup**: Confirmed stale-wish deletion already calls `deleteWishAndRelations()` inside `db.withTransaction`. That helper deletes reactions, ratings, and then the wish in the same transaction; retained unchanged.
- **Dust update**: Not changed. The audit did not establish a defect in the dust update path, and the reported position shift may result from duplicate creates. The operator should re-test dust and peer updates with this pipeline fix before a separate dust change is considered.

## Verification

- `:app:testDebugUnitTest` — passed.
- `:app:assembleDebug` — passed.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`

## Manual verification checklist for the operator

1. Create a wish offline. Bring network back. Exactly ONE server row exists. Item remains visible on the casting device.
2. Create three wishes offline. Bring network back. Exactly three server rows exist. All three visible on both devices.
3. Peer view: the received wishes render at their correct positions, no duplicates.
4. Dust a synced wish offline. Bring network back. Own device shows dusted immediately. Peer device shows dusted within a few seconds.
5. Delete an item. Confirm the wishlist screen does not crash on open afterward.
6. Force a sync failure (e.g. disable network mid-sync after queueing). After retries exhaust, item shows [failed]. Tap retry with network restored. Item syncs.
