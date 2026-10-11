# Wishlist Feature Audit — Overview & Executive Summary

## 1. Executive Summary
The Wishlist feature in the Baroness Android application is a collaborative date-anchored planning and tracking ledger shared between the two paired personas (`phesty_official` / "P" and `baroness_official` / "B"). Users can create wishes (anchored to a date, default status `"planning"`), transition them to `"dusted"` (completed), react with emojis, rate wishes (1-5 scale per persona), and delete wishes.

Following the application of Phase 3.2 mitigations, prior speculative audit findings regarding token-refresh staleness and replica identity have been resolved and verified against live code and live database state:
- **Token Refresh Handling**: Confirmed that `WishlistRepository.kt` (lines 103-107) collects `SessionTokenCache.tokenRefreshedFlow` and executes `restartRealtimeSubscriptions()`.
- **Replica Identity**: Live inspection confirms `relreplident` is `'f'` (FULL) across `wishlist_items`, `wishlist_reactions`, and `wishlist_ratings`.
- **RLS Policies**: Live policies enforce strict persona binding on inserts (`creator_id` / `persona_id = auth.jwt() ->> 'persona'`) and authenticated access on selects/updates/deletes.

However, a critical data integrity risk remains around temporary negative ID translation where associated local reactions and ratings can become orphaned if not atomically remapped alongside wishlist ID updates.

## 2. Feature Surface Map
- **UI Screens & Components**: [WishlistScreen.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/screens/WishlistScreen.kt), [WishItem.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/components/wishlist/WishItem.kt), [WishlistHeader.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/components/wishlist/WishlistHeader.kt), [WishlistInput.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/components/wishlist/WishlistInput.kt).
- **ViewModels**: [WishlistViewModel.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/viewmodels/WishlistViewModel.kt).
- **Repositories & API**: [WishlistRepository.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/repository/WishlistRepository.kt), [WishlistApi.kt](file:///C:/Baroness_Core/Baroness-kt/app/src/main/java/com/baroness/app/api/WishlistApi.kt).
- **Local Persistence**: `WishDao`, `ReactionDao`, `RatingDao`, `SyncQueueDao`, `SyncManager.kt`.
- **Remote Schema**: `wishlist_items`, `wishlist_reactions`, `wishlist_ratings`.

## 3. Findings by Confidence Level
- **[CONFIRMED] Temporary ID Mapping Orphan Risk**: When a wish created offline with a negative temp ID syncs and is assigned a positive server ID via `SyncManager.processWishItem` -> `WishDao.updateWishId`, only the `wishes` table primary key is updated; local child records in `reactions` and `ratings` referencing the negative temp `wishId` are not remapped.
- **[CONFIRMED] Silent Background Sync Failures**: Failed wishlist sync operations queue in `sync_queue` and retry via `SyncWorker`, but surface no UI error indicators to the user.
- **[CONFIRMED] Wall-Clock Conflict Resolution**: `upsertWithTimestampCheck` relies on client device wall clocks (`System.currentTimeMillis()`), exposing conflict resolution to device clock skew.

## 4. Open Questions
1. Should `SyncManager.processWishItem` wrap ID remapping and child reaction/rating foreign key updates in a single atomic Room transaction?
2. Should failed background sync attempts surface an explicit sync error badge or retry prompt in the UI?

## 5. Cross-Reference to Detailed Reports
- Architecture & Live Schema: [01-architecture.md](file:///C:/Baroness_Core/Baroness-kt/reports/wishlist/01-architecture.md)
- Full-Stack Flows & Traces: [02-flows.md](file:///C:/Baroness_Core/Baroness-kt/reports/wishlist/02-flows.md)
- Issues, Failure Modes & UX: [03-issues-and-ux.md](file:///C:/Baroness_Core/Baroness-kt/reports/wishlist/03-issues-and-ux.md)
