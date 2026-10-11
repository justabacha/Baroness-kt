# Wishlist Sync Status UI Implementation

## Changes

- **`WishEntity`**: Documented `"failed"` as an allowed `syncStatus`. The column remains a Kotlin `String`, which Room stores as SQLite `TEXT`; it accepts `"failed"` without a schema change. No Room migration or database-version change was required.
- **`Wish`**: Exposed `syncStatus: String`, defaulting to `"synced"` for remote-origin and other existing constructors. `WishlistRepository.getAllWishes()` now maps the entity value into the domain model.
- **`SyncManager`**: Leaves exhausted queue entries in `sync_queue`. When a wishlist item reaches five failures, or an already-exhausted item is skipped, it marks the corresponding entity `"failed"`: creates use `localTempId`; updates and deletes use `recordId`. A successful create still changes the temp ID and sets the entity to `"synced"`; successful updates now also set `"synced"`, including updates retried from `"failed"`.
- **`WishlistRepository`**:
  - `retryWishSync(wishId)` locates the create item by `localTempId` or an update item by `recordId`, resets `retryCount` to `0` and `lastError` to `null`, sets the entity to `"pending_create"` or `"pending_update"`, then calls `syncNow()`.
  - A locally missing wish, an already-synced wish, or a missing/non-retryable queue item is a no-op with a log entry. Missing queue items are logged as warnings. Update/reaction/rating writes are guarded so unsynced wishes cannot send temp IDs to the server.
  - Dusting now marks a wish `"pending_update"` until the remote update succeeds.
  - Delete distinguishes never-synced wishes from remote-backed wishes. A negative-ID or `"pending_create"` wish, or a `"failed"` wish whose queue item is a create, is removed with its local reactions, ratings, and wishlist queue entries in a transaction; no remote delete is queued. A failed update is not treated as never-synced: its row already exists remotely, so deletion follows the remote-delete path.
- **`WishlistViewModel`**: Exposes `retryWish(wishId)` and gates dust, reaction, and rating actions to `"synced"` wishes.
- **`WishItem` and `WishlistScreen`**: Synced planning items retain their existing delete/dust controls, and synced dusted items retain download/reaction/rating controls. Pending and failed items show delete plus a `[pending]` or `[failed]` chip; planning dust controls are hidden. Failed items replace the left index number with a refresh icon that retries the wish. Unsynced dusted items do not expose download, reaction, or rating actions.
- **DAOs**: Added sync-status update and wish-queue lookup/deletion operations required by the repository and sync manager.

## Verification

- `:app:testDebugUnitTest` — passed.
- `:app:assembleDebug` — passed.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`

## Manual verification checklist for the operator

1. Create a wish offline (airplane mode). Item shows `[pending]` badge + delete icon. No dust checkmark.
2. Bring network back. Item syncs. Badge disappears, dust checkmark appears.
3. Create a wish offline, then simulate a permanent sync failure (e.g. force a bad response or use an invalid test payload). After retries exhaust, item shows `[failed]` badge + retry icon in place of the number.
4. Tap the retry icon with network available. Item transitions back to `[pending]` and then to synced.
5. Delete a pending item offline. Item disappears. No remote delete queued (verify via logs).
6. Delete a synced item. Item disappears. Remote delete queued as before.
7. Verify a synced planning item renders exactly as before this change.
