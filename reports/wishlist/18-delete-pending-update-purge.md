# Wishlist Delete — Purge Pending Updates

## Changes

- **Synced-wish tombstone path:** `WishlistRepository.deleteWish()` now removes all `wishlist_items` queue entries for the wish inside the same `db.withTransaction` that marks the wish `pending_delete` and deletes its local reactions and ratings. The query matches either `recordId` or `localTempId`, so it removes pending updates/deletes as well as any create entry associated with that local ID. After the transaction completes, the existing code queues the new remote delete and calls `syncNow()`.
- **DAO:** Added `SyncQueueDao.deleteWishQueueEntries(wishId, recordId)`. It scopes deletion to `tableName = 'wishlist_items'` and matches `recordId = :recordId OR localTempId = :wishId`.
- **Never-synced branch:** Confirmed the existing local-only branch calls `deleteWishItems(wishId, wishId.toString())` in its transaction. That DAO query scopes to `wishlist_items` and matches either `localTempId` or `recordId`, purging the create entry and any associated update entries before returning without queuing a remote delete.
- Realtime DELETE handling and reconciliation were not modified.

## Verification

- `:app:testDebugUnitTest` — passed.
- `:app:assembleDebug` — passed.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`

## Manual verification checklist for the operator

1. Dust a wish offline. Immediately delete the same wish. Sync.
2. Check `sync_queue` — no queue entries for that wish should remain.
3. Confirm the wish did not reappear anywhere.
