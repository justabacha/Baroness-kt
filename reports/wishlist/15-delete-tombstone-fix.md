# Wishlist Delete Tombstone and Worker Wakeup Fix

## Changes

- **Delete tombstone:** `WishlistRepository.deleteWish()` still physically deletes never-synced creates locally and removes their queue entries. For a synced wish, it now keeps the wish row and in one Room transaction sets `status = "deleted"` and `syncStatus = "pending_delete"`, and deletes that wish's reactions and ratings. It then queues the remote delete and calls `syncNow()` as before. The existing string status field can hold `"deleted"`; no column or migration was added.
- **Visible DAO query:** `WishDao.getAllWishes()` now excludes `pending_delete` and `failed_delete`, keeping tombstones available to fetch/reconciliation while hiding them from the wishlist and the derived visible list.
- **Remote upsert protection:** `upsertWithTimestampCheck()` skips and logs a remote row when the existing local wish has `pending_delete` or `failed_delete`. Both initial-fetch and Realtime insert/update paths use this method, so neither can restore the tombstone to a visible synced wish.
- **Delete completion and exhausted failure:** After a remote delete succeeds, `SyncManager` deletes the wish, reactions, ratings, and queue row in one Room transaction. If the delete exhausts retries, the queue item remains and the wish is marked `failed_delete`, which stays hidden. Other wish failures remain `failed`. Successful earlier update operations also cannot overwrite a delete tombstone with `synced`.
- **Reconciliation:** Confirmed its existing DAO source is `getSyncedWishes()` (`syncStatus = 'synced'` only). Both tombstone states are excluded from reconciliation candidates. The existing 60-second grace period and transactional child deletion are unchanged.
- **Worker wakeup:** `WishlistRepository.syncNow()` now uses `ExistingWorkPolicy.APPEND_OR_REPLACE` for the existing `"WishlistSync"` unique chain. WorkManager appends the request behind the existing chain, retaining serialized execution rather than running a concurrent worker; if prior prerequisite work has failed or been cancelled, the appended request can still run.
- **Final queue scan:** When the processor's normal scan has no eligible items, it now performs an additional fresh scan before exiting. Newly added eligible rows are processed in the current run; it exits only if that fresh scan also has no new eligible rows. Recent in-progress claims remain excluded as owned by another worker.
- **Untouched:** Dust update behavior, position/sort logic, sync-status UI, reconciliation grace period/child cleanup, and avatar behavior were not changed.

## Verification

- `:app:testDebugUnitTest` — passed.
- `:app:assembleDebug` — passed.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`

## Manual verification checklist for the operator

1. Delete a synced wish on phone A. Immediately force a fetch on phone A (pull-to-refresh). The wish should NOT reappear.
2. Cold-start phone A. Wish should still be gone.
3. Peer phone B: after a few seconds, wish disappears.
4. Peer phone B cold-start: wish stays gone.
5. Delete a wish while a sync worker is actively running. The delete should still propagate without needing a cold start.
6. Create a wish offline. Sync. Then delete it. Both create and delete should land cleanly.
