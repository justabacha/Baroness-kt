# Wishlist Optimistic State Preservation Fix

## Position investigation and cause

- `WishlistViewModel.createWish()` assigns `createdAt = System.currentTimeMillis()` (positive Unix epoch milliseconds); the negative `tempId` is a separate primary-key value (`WishlistViewModel.kt:174-193`).
- Before this change, `WishlistRepository.insertWish()` copied `wish.createdAt` directly to `WishEntity.createdAt` (`WishlistRepository.kt:613-628`). Existing rows have the `createdAt` parsed from remote `created_at` on fetch/Realtime, or retained from local storage (`WishlistRepository.kt:236-249, 498-508`; `WishDao.kt:53-64`). The maximum existing value is available from `WishDao.getLastCreatedAt()` (`WishDao.kt:74-76`), but was not consulted when creating a wish.
- The Room visible-list query itself orders by `createdAt ASC` (`WishDao.kt:16-18`). Repository mapping preserves that sequence (`WishlistRepository.kt:570-603`); `WishlistViewModel.wishes` starts with an empty `StateFlow` value before collecting Room (`WishlistViewModel.kt:32-40`), and `WishlistScreen` passes the collected list directly to `itemsIndexed` without another sort (`WishlistScreen.kt:141-154`). There is no rendered insertion-order version of the new row.
- **Cause:** the new row had a wall-clock timestamp but no guarantee that it exceeded the greatest timestamp already in the local Room dataset. The bubble is the current rank, not a stored position. If the local high-water timestamp is later than the new row's timestamp, Room correctly puts the new item first; previously uncached remote rows can also change the rank when they arrive. The submitted source cannot reveal the operator's actual persisted timestamps, but it confirms the missing high-water comparison and the timestamp ordering mechanism.

## Changes

- **Position:** In `WishlistRepository.insertWish()`, the persisted `createdAt` is now `maxOf(wish.createdAt, latestLocalCreatedAt + 1)` when a local maximum exists. The exact effective timestamp is used both in the Room entity and create payload, so the ID remap preserves the same ordering timestamp. The existing insert log now includes the chosen timestamp and previous local maximum. `createdAt ASC` was not changed. When the local cache is incomplete, a later fetch can still add older wishes that were unknown while offline; an offline client cannot determine ranks against rows it has not fetched.
- **Dust timestamp:** Added `WishDao.updateStatusAsPending()` to write `status`, `syncStatus = "pending_update"`, and `updatedAt` in one SQL update. `WishlistRepository.updateWishStatus()` captures one timestamp and uses it for both that local update and the remote update payload, keeping the optimistic row's timestamp aligned with the queued/server value.
- **Pending upsert protection:** `upsertWithTimestampCheck()` now skips and logs remote rows when the existing local row is `pending_update`, `pending_delete`, or `failed_delete`. It does not skip `pending_create`; that status is absent from the guard. Both `initialSync()` and `handleWishChange()` call this shared upsert method.
- **Reverse direction:** On successful remote update, `SyncManager` changes local `syncStatus` to `"synced"` (unless the row is a delete tombstone). The upsert guard then allows later remote rows. The timestamp comparison still avoids replacing a row with an equal/older timestamp; after this change, equal timestamps represent the same timestamp sent in the update payload.
- **Delete tombstone:** The prior `pending_delete`/`failed_delete` tombstone behavior, queue completion, and visibility filtering were not changed.

## Verification

- `:app:testDebugUnitTest` — passed.
- `:app:assembleDebug` — passed.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`

## Manual verification checklist for the operator

1. Create a wish offline. It should render in the correct sorted position immediately, not at #1.
2. Bring network back. Item stays in the same position, badge disappears.
3. Dust a synced wish offline. It should show `DUSTED + [pending]` and stay in position.
4. Bring network back. It should stay `DUSTED` throughout the transition, no flicker to PLANNING.
5. Cold start. Item is still `DUSTED + synced`.
6. Repeat 3-5 with a wish dusted on the peer device — the peer's dust should arrive cleanly.
