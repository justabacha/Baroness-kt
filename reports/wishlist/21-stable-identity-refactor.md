# Wishlist Stable Identity Refactor

## Database migration

Applied migration: `20261011095608_wishlist_stable_client_identity`  
Local migration file: `supabase/migrations/20261011095608_wishlist_stable_client_identity.sql`

The migration tool returned:

```text
{"success":true}
```

Applied SQL:

```sql
ALTER TABLE public.wishlist_items
  ADD COLUMN client_uuid uuid;

UPDATE public.wishlist_items
  SET client_uuid = gen_random_uuid()
  WHERE client_uuid IS NULL;

ALTER TABLE public.wishlist_items
  ALTER COLUMN client_uuid SET NOT NULL;

CREATE UNIQUE INDEX wishlist_items_client_uuid_key
  ON public.wishlist_items (client_uuid);

ALTER TABLE public.wishlist_reactions
  ADD COLUMN wish_client_uuid uuid;

ALTER TABLE public.wishlist_ratings
  ADD COLUMN wish_client_uuid uuid;

UPDATE public.wishlist_reactions r
  SET wish_client_uuid = w.client_uuid
  FROM public.wishlist_items w
  WHERE r.wish_id = w.id;

UPDATE public.wishlist_ratings r
  SET wish_client_uuid = w.client_uuid
  FROM public.wishlist_items w
  WHERE r.wish_id = w.id;

ALTER TABLE public.wishlist_reactions
  ALTER COLUMN wish_client_uuid SET NOT NULL;

ALTER TABLE public.wishlist_ratings
  ALTER COLUMN wish_client_uuid SET NOT NULL;
```

Verification query output:

| Check | Actual |
|---|---:|
| `wishlist_items_null_client_uuid` | 0 |
| `wishlist_items_total` | 2 |
| `wishlist_items_distinct_client_uuid` | 2 |
| `wishlist_reactions_null_wish_client_uuid` | 0 |
| `wishlist_ratings_null_wish_client_uuid` | 0 |

The live catalog confirmed `wishlist_items_client_uuid_key` is a unique btree index on `client_uuid`. Existing `wish_id` columns and foreign keys remain. No RLS policies were changed.

## Room migration

Chose **Option A: destructive local wishlist rebuild**, implemented as `MIGRATION_9_10` with Room database version 10. It drops and recreates `wishes`, `reactions`, `ratings`, and `sync_queue`; chat and clock tables are not dropped.

**Operator warning:** this loses local-only wishes and all pending wishlist queue entries on an installed device. Drain pending wishlist operations before applying the app update. Once updated, the next server sync rebuilds the local wishlist using the server UUIDs.

## Files changed

| File | Reason |
|---|---|
| `supabase/migrations/20261011095608_wishlist_stable_client_identity.sql` | Adds and backfills server UUID columns, enforces non-null values, and creates the unique index. |
| `app/src/main/java/com/baroness/app/data/local/database/WishEntity.kt` | Makes `clientUuid` the Room key and stores `serverId` separately. |
| `app/src/main/java/com/baroness/app/data/local/database/ReactionEntity.kt` | Uses `(wishClientUuid, personaId)` as the composite key. |
| `app/src/main/java/com/baroness/app/data/local/database/RatingEntity.kt` | Uses `(wishClientUuid, personaId)` as the composite key. |
| `app/src/main/java/com/baroness/app/data/local/database/SyncQueueItem.kt` | Replaces record/temp IDs with `clientUuid` and removes the active in-progress field. |
| `app/src/main/java/com/baroness/app/data/local/database/AppDatabase.kt` | Increments Room to version 10 and registers the destructive wishlist migration. |
| `app/src/main/java/com/baroness/app/data/local/dao/WishDao.kt` | Reworks wish queries and mutations around UUID, retaining a server-ID lookup for Realtime deletes that only expose `id`. |
| `app/src/main/java/com/baroness/app/data/local/dao/SyncQueueDao.kt` | Adds per-wish UUID queue queries/acks and removes record/temp-ID queries. |
| `app/src/main/java/com/baroness/app/data/local/dao/ReactionDao.kt` | Changes reaction access and deletion to `wishClientUuid`. |
| `app/src/main/java/com/baroness/app/data/local/dao/RatingDao.kt` | Changes rating access and deletion to `wishClientUuid`. |
| `app/src/main/java/com/baroness/app/models/WishModels.kt` | Replaces domain `id` with stable `clientUuid` and optional `serverId`. |
| `app/src/main/java/com/baroness/app/repository/WishlistRepository.kt` | Adds transactional server-row/delete gates, routes fetch and Realtime through them, reconciles by UUID/outbox, and makes local mutations UUID-based and transactional. |
| `app/src/main/java/com/baroness/app/utils/SyncManager.kt` | Sends create/update/delete using stable UUIDs, resolves server IDs only for API calls, and acknowledges queue operations individually. |
| `app/src/main/java/com/baroness/app/api/WishlistApi.kt` | Maps UUID fields, sends idempotent create upserts on `client_uuid`, and includes both reaction/rating identifiers. |
| `app/src/main/java/com/baroness/app/viewmodels/WishlistViewModel.kt` | Generates client UUIDs and uses UUIDs for wishlist actions/state. |
| `app/src/main/java/com/baroness/app/screens/WishlistScreen.kt` | Uses `clientUuid` as the Compose key and routes rating actions by UUID. |
| `app/src/main/java/com/baroness/app/components/wishlist/WishItem.kt` | Uses `clientUuid` for item state and wish callbacks. |
| `app/src/main/java/com/baroness/app/components/wishlist/RatingModal.kt` | Keys remembered rating state by `clientUuid`. |
| `reports/wishlist/21-stable-identity-refactor.md` | Records migration, verification, code changes, validation, and operator checklist. |

## DAO surface

`WishDao`: `getAllWishes`, `getWishByUuid`, `getWishByServerId`, `getSyncedWishes`, `insertWish`, `insertAll`, `deleteWish`, `updateStatus`, `updateStatusAsPending`, `updateSyncStatus`, `setServerId`, `markPendingDelete`, `markFailedUnlessDeleting`, `markDeleteFailed`, `getTotalCount`, `getDustedCount`, `clearAll`, `getLastCreatedAt`.

`SyncQueueDao`: `getAllPending`, `getPendingForWish`, `insert`, `update`, `deleteById`, `deleteAllForWish`, `getPendingCount`, `getById`, `clearAll`.

`ReactionDao`: `getReactionsForWish`, `getAllReactions`, `insertReaction`, `deleteReaction`, `deleteReactionsForWish`, `clearAll`.

`RatingDao`: `getRatingsForWish`, `getAllRatings`, `insertRating`, `deleteRating`, `deleteRatingsForWish`, `clearAll`.

`getWishByServerId` supports mapping a Realtime delete whose old record contains only the server bigint ID; the Room primary key remains the client UUID.

## Identity conversion and obsolete-code search

Wishlist UI/model identity changes (`wish.id` to `wish.clientUuid`) are in:

- `app/src/main/java/com/baroness/app/components/wishlist/WishItem.kt`
- `app/src/main/java/com/baroness/app/components/wishlist/RatingModal.kt`
- `app/src/main/java/com/baroness/app/screens/WishlistScreen.kt`
- `app/src/main/java/com/baroness/app/viewmodels/WishlistViewModel.kt`
- `app/src/main/java/com/baroness/app/repository/WishlistRepository.kt`
- `app/src/main/java/com/baroness/app/models/WishModels.kt`

Server API operations use `serverId` in `app/src/main/java/com/baroness/app/utils/SyncManager.kt`; stable UUID remains the local/outbox identity.

Final source search across `app/src` found **no occurrences** of `updateWishId`, `localTempId`, `recordId`, `RECONCILIATION_GRACE_PERIOD_MS`, `upsertWithTimestampCheck`, `upsertAllWithTimestampCheck`, `deleteWishLocally`, `SQLiteConstraintException`, or `wish.id`.

No wishlist TODOs, placeholders, or ID-swap/grace-period/Compose-key workarounds remain. The historical `MIGRATION_8_9` still adds `inProgressAt` when upgrading a version-8 database; `MIGRATION_9_10` immediately drops and recreates `sync_queue`, so `inProgressAt` is not part of the version-10 queue schema. The existing general `.fallbackToDestructiveMigration()` builder policy is unchanged.

## Validation

- `:app:compileDebugKotlin` — passed.
- `:app:testDebugUnitTest` — passed.
- `:app:assembleDebug` — passed.
- APK: `app/build/outputs/apk/debug/baroness-v1.0.1.apk`
- No device or adb commands were run.

## Manual verification checklist for the operator

1. Create a wish offline. It appears immediately. It is at the correct sorted position (not #1).
2. Bring network back. The wish stays in the same position. Badge disappears when synced.
3. Peer device sees the wish appear at the correct position within seconds.
4. Dust a synced wish offline. It stays `DUSTED + [pending]`. Bring network back. It stays `DUSTED` throughout. No flicker to `PLANNING`.
5. Delete a synced wish offline. It disappears from the list. Peer device: after network returns, the wish disappears within seconds without user action.
6. Delete a synced wish while the peer is offline. Peer comes back. Wish disappears within seconds (Realtime reconnect or fetch reconciliation).
7. Create 5 wishes offline rapidly. Bring network back. Exactly 5 server rows exist. All 5 render at correct positions on both devices.
8. Kill the app after a pending create. Cold start. Wish is still there and still pending. Network returns. Syncs normally.
9. Kill the app after a pending delete. Cold start. Wish is still gone (hidden). Network returns. Remote delete completes.
10. Cold start offline with a warm cache. Wishlist renders correctly. No spurious fetches or flashes.
